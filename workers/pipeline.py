import asyncio
import logging
import time
from dataclasses import dataclass
from enum import Enum
from typing import Any

from adapters.frame_sink import FrameSink, create_frame_sink
from adapters.media_source import MediaSource, create_media_source
from adapters.metadata_store import MetadataStore
# [아바타 합성 비활성화] 아바타 모델이 준비되면 아래 import 주석 해제
# from model.renderer import AvatarRenderer
from schemas.stream import OutputMode, StreamStatusResponse

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
        input_stream_key: str,
        input_url: str,
        output_mode: OutputMode,
        output_url: str,
        watch_url: str | None,
        avatar_id: str | None,
        fps: int,
        max_frame_lag_ms: int,
        output_retry_count: int,
        output_retry_backoff_ms: int,
        metadata_store: MetadataStore,
        ffmpeg_log_level: str,
        output_video_bitrate_kbps: int,
        output_audio_bitrate_kbps: int,
        output_audio_sample_rate: int,
        output_audio_channels: int,
        output_keyframe_interval_seconds: int,
        media_source: MediaSource | None = None,
        frame_sink: FrameSink | None = None,
        # [아바타 합성 비활성화] 아바타 모델 준비 후 renderer 파라미터 복원
        # renderer: AvatarRenderer | None = None,
    ) -> None:
        self.broadcast_id = broadcast_id
        self.input_stream_key = input_stream_key
        self.input_url = input_url
        self.output_mode = output_mode
        self.output_url = output_url
        self.watch_url = watch_url
        self.avatar_id = avatar_id

        self._max_frame_lag_us = max_frame_lag_ms * 1_000
        self._output_retry_count = max(output_retry_count, 0)
        self._output_retry_backoff_s = max(output_retry_backoff_ms, 0) / 1_000
        self._metadata_store = metadata_store
        self._media_source = media_source or create_media_source(input_url=input_url, fps=fps)
        self._frame_sink = frame_sink or create_frame_sink(
            output_url=output_url,
            output_mode=output_mode,
            fps=fps,
            ffmpeg_log_level=ffmpeg_log_level,
            video_bitrate_kbps=output_video_bitrate_kbps,
            audio_bitrate_kbps=output_audio_bitrate_kbps,
            audio_sample_rate=output_audio_sample_rate,
            audio_channels=output_audio_channels,
            keyframe_interval_seconds=output_keyframe_interval_seconds,
        )
        # [아바타 합성 비활성화] 아바타 모델 준비 후 아래 주석 해제
        # self._renderer = renderer or AvatarRenderer()

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
            input_stream_key=self.input_stream_key,
            stream_key=self.input_stream_key,
            state=self._state.value,
            processed_frames=self._stats.processed_frames,
            dropped_frames=self._stats.dropped_frames,
            last_pts_us=self._stats.last_pts_us,
            output_mode=self.output_mode,
            input_url=self.input_url,
            output_url=self.output_url,
            watch_url=self.watch_url,
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

                # ──────────────────────────────────────────────────────────
                # [아바타 합성 비활성화]
                # 현재 아바타 모델이 준비되지 않아, Redis 메타데이터 조회와
                # 아바타 렌더링 단계를 건너뛰고 카메라 원본 프레임을 그대로
                # 치지직(CHZZK)으로 송출합니다.
                # 아바타 모델이 준비되면 아래 주석을 해제하세요.
                # ──────────────────────────────────────────────────────────
                #
                # face_metadata = None
                # retry_count = 3
                # retry_delay_s = 0.01  # 메타데이터 대기 간격 10ms
                #
                # for attempt in range(retry_count):
                #     try:
                #         face_metadata = await self._metadata_store.get_face_metadata(
                #             self.broadcast_id, frame.pts_us
                #         )
                #         if face_metadata is not None:
                #             break
                #         await asyncio.sleep(retry_delay_s)
                #     except Exception:
                #         logger.warning(
                #             "metadata_lookup_error broadcast_id=%s pts_us=%s",
                #             self.broadcast_id,
                #             frame.pts_us,
                #         )
                #         break
                #
                # if face_metadata is None:
                #     logger.debug(
                #         "metadata_not_found (timeout) broadcast_id=%s pts_us=%s",
                #         self.broadcast_id,
                #         frame.pts_us,
                #     )
                #
                # try:
                #     rendered = await self._renderer.render(frame, face_metadata, self.avatar_id)
                # except Exception:
                #     logger.exception(
                #         "render_failed broadcast_id=%s pts_us=%s",
                #         self.broadcast_id,
                #         frame.pts_us,
                #     )
                #     rendered = await self._renderer.emergency_fallback(frame)

                # 카메라 원본 프레임을 그대로 출력 (패스스루 모드)
                await self._write_frame(frame)
                self._stats.processed_frames += 1
                self._stats.last_pts_us = frame.pts_us
        except RuntimeError as exc:  # pragma: no cover
            if "input stream ended" in str(exc):
                self._detail = str(exc)
                logger.info(
                    "pipeline_input_ended broadcast_id=%s detail=%s",
                    self.broadcast_id,
                    self._detail,
                )
            else:
                self._state = PipelineState.FAILED
                self._detail = str(exc)
                logger.exception("pipeline_failed broadcast_id=%s", self.broadcast_id)
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

    async def _write_frame(self, rendered) -> None:
        attempt = 0
        while True:
            try:
                await self._frame_sink.write_frame(rendered)
                return
            except Exception:
                if attempt >= self._output_retry_count:
                    raise
                attempt += 1
                logger.warning(
                    "frame_sink_write_retry broadcast_id=%s attempt=%s/%s",
                    self.broadcast_id,
                    attempt,
                    self._output_retry_count,
                )
                await asyncio.sleep(self._output_retry_backoff_s)

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
