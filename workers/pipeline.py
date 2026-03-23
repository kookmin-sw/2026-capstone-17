import asyncio
import logging
import time
from dataclasses import dataclass
from enum import Enum
from typing import Any

from adapters.frame_sink import FrameSink, create_frame_sink
from adapters.media_source import MediaSource, create_media_source
from adapters.metadata_store import MetadataStore
from model.renderer import AvatarRenderer
from schemas.stream import StreamStatusResponse

logger = logging.getLogger(__name__)


class PipelineState(str, Enum):
    STARTING = "starting"
    RUNNING = "running"
    STOPPING = "stopping"
    STOPPED = "stopped"
    FAILED = "failed"


@dataclass(slots=True)
class PipelineStats:
    processed_frames: int = 0
    dropped_frames: int = 0
    last_pts_us: int | None = None


class StreamPipeline:
    def __init__(
        self,
        broadcast_id: str,
        input_url: str,
        output_path: str,
        avatar_id: str | None,
        fps: int,
        max_frame_lag_ms: int,
        metadata_store: MetadataStore,
        media_source: MediaSource | None = None,
        frame_sink: FrameSink | None = None,
        renderer: AvatarRenderer | None = None,
    ) -> None:
        self.broadcast_id = broadcast_id
        self.input_url = input_url
        self.output_path = output_path
        self.avatar_id = avatar_id

        self._max_frame_lag_us = max_frame_lag_ms * 1_000
        self._metadata_store = metadata_store
        self._media_source = media_source or create_media_source(input_url=input_url, fps=fps)
        self._frame_sink = frame_sink or create_frame_sink(output_path=output_path, fps=fps)
        self._renderer = renderer or AvatarRenderer()

        self._task: asyncio.Task[None] | None = None
        self._stop_event = asyncio.Event()
        self._state = PipelineState.STOPPED
        self._detail: str | None = None
        self._stats = PipelineStats()

        self._anchor_pts_us: int | None = None
        self._anchor_clock_us: int | None = None

    @property
    def state(self) -> PipelineState:
        return self._state

    async def start(self) -> None:
        if self._task and not self._task.done():
            return

        self._state = PipelineState.STARTING
        self._detail = None
        self._stop_event.clear()
        self._task = asyncio.create_task(self._run(), name=f"pipeline:{self.broadcast_id}")

    async def stop(self) -> None:
        if self._state in {PipelineState.STOPPED, PipelineState.FAILED} and not self._task:
            return

        self._state = PipelineState.STOPPING
        self._stop_event.set()
        if self._task:
            await self._task
            self._task = None

    def snapshot(self) -> StreamStatusResponse:
        return StreamStatusResponse(
            broadcast_id=self.broadcast_id,
            state=self._state.value,
            processed_frames=self._stats.processed_frames,
            dropped_frames=self._stats.dropped_frames,
            last_pts_us=self._stats.last_pts_us,
            detail=self._detail,
        )

    async def _run(self) -> None:
        self._state = PipelineState.RUNNING
        logger.info("pipeline_started broadcast_id=%s input=%s", self.broadcast_id, self.input_url)

        try:
            while not self._stop_event.is_set():
                frame = await self._media_source.read_frame()
                if frame is None:
                    continue

                if self._should_drop_frame(frame.pts_us):
                    self._stats.dropped_frames += 1
                    continue

                face_metadata = None
                retry_count = 3
                retry_delay_s = 0.01  # 10ms wait per retry

                for attempt in range(retry_count):
                    try:
                        face_metadata = await self._metadata_store.get_face_metadata(
                            self.broadcast_id, frame.pts_us
                        )
                        if face_metadata is not None:
                            break  # Found metadata!
                        await asyncio.sleep(retry_delay_s)
                    except Exception:  # pragma: no cover
                        logger.warning(
                            "metadata_lookup_error broadcast_id=%s pts_us=%s",
                            self.broadcast_id,
                            frame.pts_us,
                        )
                        break

                if face_metadata is None:
                    logger.debug(
                        "metadata_not_found (timeout) broadcast_id=%s pts_us=%s",
                        self.broadcast_id,
                        frame.pts_us,
                    )

                try:
                    rendered = await self._renderer.render(frame, face_metadata, self.avatar_id)
                except Exception:  # pragma: no cover
                    logger.exception(
                        "render_failed broadcast_id=%s pts_us=%s",
                        self.broadcast_id,
                        frame.pts_us,
                    )
                    rendered = await self._renderer.emergency_fallback(frame)

                await self._frame_sink.write_frame(rendered)
                self._stats.processed_frames += 1
                self._stats.last_pts_us = frame.pts_us
        except Exception as exc:  # pragma: no cover
            self._state = PipelineState.FAILED
            self._detail = str(exc)
            logger.exception("pipeline_failed broadcast_id=%s", self.broadcast_id)
        finally:
            await self._close_components()
            if self._state != PipelineState.FAILED:
                self._state = PipelineState.STOPPED
            logger.info("pipeline_finished broadcast_id=%s state=%s", self.broadcast_id, self._state.value)

    def _should_drop_frame(self, pts_us: int) -> bool:
        now_us = int(time.monotonic() * 1_000_000)
        if self._anchor_pts_us is None or self._anchor_clock_us is None:
            self._anchor_pts_us = pts_us
            self._anchor_clock_us = now_us
            return False

        media_elapsed_us = pts_us - self._anchor_pts_us
        wall_elapsed_us = now_us - self._anchor_clock_us
        lag_us = wall_elapsed_us - media_elapsed_us
        return lag_us > self._max_frame_lag_us

    async def _close_components(self) -> None:
        await self._safe_close(self._frame_sink, "frame_sink")
        await self._safe_close(self._media_source, "media_source")
        await self._safe_close(self._metadata_store, "metadata_store")

    async def _safe_close(self, resource: Any, name: str) -> None:
        try:
            await resource.close()
        except Exception:  # pragma: no cover
            logger.warning(
                "resource_close_failed broadcast_id=%s resource=%s",
                self.broadcast_id,
                name,
            )
