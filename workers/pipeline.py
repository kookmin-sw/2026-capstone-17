import asyncio
import logging
import os
import time
from dataclasses import dataclass, field
from enum import Enum
from typing import Any

from adapters.frame_sink import FrameSink, create_frame_sink
from adapters.media_source import MediaSource, create_media_source
from adapters.metadata_store import MetadataStore
from model.renderer import AvatarRenderer
from schemas.stream import OutputMode, StreamStatusResponse
from services.avatar_assets import AvatarAssetResolver
from workers.types import VideoFrame

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
    metadata_hits: int = 0
    metadata_misses: int = 0
    avatar_rendered_frames: int = 0


@dataclass(slots=True)
class StageTiming:
    count: int = 0
    total_ms: float = 0.0
    max_ms: float = 0.0

    def record(self, started_at: float) -> None:
        elapsed_ms = (time.perf_counter() - started_at) * 1000
        self.count += 1
        self.total_ms += elapsed_ms
        self.max_ms = max(self.max_ms, elapsed_ms)

    def average_ms(self) -> float:
        if self.count == 0:
            return 0.0
        return self.total_ms / self.count

    def reset(self) -> None:
        self.count = 0
        self.total_ms = 0.0
        self.max_ms = 0.0


@dataclass(slots=True)
class PipelineTimingStats:
    input_read: StageTiming = field(default_factory=StageTiming)
    latest_wait: StageTiming = field(default_factory=StageTiming)
    metadata: StageTiming = field(default_factory=StageTiming)
    asset_prepare: StageTiming = field(default_factory=StageTiming)
    avatar_render: StageTiming = field(default_factory=StageTiming)
    output_write: StageTiming = field(default_factory=StageTiming)
    frame_total: StageTiming = field(default_factory=StageTiming)

    def reset(self) -> None:
        self.input_read.reset()
        self.latest_wait.reset()
        self.metadata.reset()
        self.asset_prepare.reset()
        self.avatar_render.reset()
        self.output_write.reset()
        self.frame_total.reset()


@dataclass(slots=True)
class AvatarPersonSlot:
    tracking_id: str
    avatar_id: str | None
    avatar_asset_key: str | None
    bbox: dict[str, float]
    last_seen_pts_us: int


class StreamPipeline:
    """RTSP relay by default, or frame-level avatar rendering when requested."""

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
        metadata_store: MetadataStore,
        avatar_rendering_enabled: bool = True,
        avatar_project_dir: str | None = None,
        avatar_bank_dir: str | list[str] | None = None,
        avatar_asset_resolver: AvatarAssetResolver | None = None,
        avatar_random_seed: int = 0,
        avatar_max_faces_per_frame: int = 1,
        avatar_metadata_grace_ms: int = 500,
        avatar_primary_reselect_grace_ms: int = 250,
        avatar_person_slot_grace_ms: int = 3000,
        avatar_person_slot_match_iou: float = 0.10,
        avatar_mosaic_non_selected_faces: bool = False,
        metadata_poll_attempts: int = 3,
        metadata_poll_interval_ms: int = 10,
        ffmpeg_log_level: str = "warning",
        gop_seconds: int = 1,
        video_bitrate: str = "2500k",
        maxrate: str = "2500k",
        bufsize: str = "5000k",
        max_frame_width: int = 0,
        max_frame_height: int = 0,
        x264_preset: str = "veryfast",
        hls_time: float = 1.0,
        hls_list_size: int = 6,
        hls_flags: str = "delete_segments+independent_segments+append_list+omit_endlist",
        analysis_output_path: str | None = None,
        input_open_retry_count: int = 5,
        input_open_retry_backoff_ms: int = 1000,
        output_audio_bitrate: str = "128k",
        output_audio_sample_rate: int = 44100,
        output_audio_channels: int = 2,
        **_kwargs,
    ) -> None:
        self.broadcast_id = broadcast_id
        self.input_stream_key = input_stream_key
        self.input_url = input_url
        self.output_mode = output_mode
        self.output_url = output_url
        self.watch_url = watch_url
        self.avatar_id = avatar_id
        self._fps = fps
        self._max_frame_lag_ms = max(max_frame_lag_ms, 0)
        self._metadata_store = metadata_store
        self._avatar_rendering_enabled = avatar_rendering_enabled
        self._avatar_project_dir = avatar_project_dir
        self._avatar_bank_dir = avatar_bank_dir
        self._avatar_asset_resolver = avatar_asset_resolver
        self._avatar_random_seed = int(avatar_random_seed)
        self._avatar_max_faces_per_frame = max(int(avatar_max_faces_per_frame), 0)
        self._avatar_metadata_grace_us = max(int(avatar_metadata_grace_ms), 0) * 1000
        self._avatar_primary_reselect_grace_us = max(int(avatar_primary_reselect_grace_ms), 0) * 1000
        self._avatar_person_slot_grace_us = max(int(avatar_person_slot_grace_ms), 0) * 1000
        self._avatar_person_slot_match_iou = max(float(avatar_person_slot_match_iou), 0.0)
        self._avatar_mosaic_non_selected_faces = bool(avatar_mosaic_non_selected_faces)
        self._metadata_poll_attempts = max(int(metadata_poll_attempts), 1)
        self._metadata_poll_interval_s = max(int(metadata_poll_interval_ms), 0) / 1000
        self._ffmpeg_log_level = ffmpeg_log_level
        self._gop_seconds = max(gop_seconds, 1)
        self._video_bitrate = video_bitrate
        self._maxrate = maxrate
        self._bufsize = bufsize
        self._max_frame_width = max(int(max_frame_width), 0)
        self._max_frame_height = max(int(max_frame_height), 0)
        self._x264_preset = x264_preset
        self._hls_time = max(hls_time, 0.5)
        self._hls_list_size = max(hls_list_size, 3)
        self._hls_flags = hls_flags
        self.analysis_output_path = analysis_output_path
        self._input_open_retry_count = max(input_open_retry_count, 0)
        self._input_open_retry_backoff_s = max(input_open_retry_backoff_ms, 0) / 1000
        self._output_audio_bitrate = output_audio_bitrate
        self._output_audio_sample_rate = output_audio_sample_rate
        self._output_audio_channels = output_audio_channels
        self._target_frame_interval_us = int(1_000_000 / max(self._fps, 1))
        self._next_render_pts_us: int | None = None
        self._last_render_progress_log_at = time.monotonic()
        self._last_progress_processed_frames = 0
        self._last_progress_dropped_frames = 0
        self._latest_frame: VideoFrame | None = None
        self._latest_frame_seq = 0
        self._rendered_frame_seq = 0
        self._latest_frame_event = asyncio.Event()
        self._reader_error: Exception | None = None
        self._last_renderable_face_metadata: dict | None = None
        self._last_renderable_face_pts_us: int | None = None
        self._last_ready_avatar_id: str | None = avatar_id
        self._primary_tracking_id: str | None = None
        self._primary_tracking_last_seen_pts_us: int | None = None
        self._avatar_person_slots_by_tracking_id: dict[str, AvatarPersonSlot] = {}
        self._avatar_asset_prepare_tasks: dict[tuple[str, str], asyncio.Task[None]] = {}

        self._task: asyncio.Task[None] | None = None
        self._ffmpeg_proc: asyncio.subprocess.Process | None = None
        self._media_source: MediaSource | None = None
        self._frame_sink: FrameSink | None = None
        self._analysis_frame_sink: FrameSink | None = None
        self._stop_event = asyncio.Event()
        self._state = PipelineState.STOPPED
        self._detail: str | None = None
        self._stats = PipelineStats()
        self._timings = PipelineTimingStats()

    @property
    def state(self) -> PipelineState:
        return self._state

    @property
    def output_path(self) -> str:
        """Backward-compatible alias for older analysis code."""
        return self.output_url

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
            metadata_hits=self._stats.metadata_hits,
            metadata_misses=self._stats.metadata_misses,
            avatar_rendered_frames=self._stats.avatar_rendered_frames,
            output_mode=self.output_mode,
            input_url=self.input_url,
            output_path=self.output_url,
            hls_url=self.watch_url or self.output_url,
            output_url=self.output_url,
            watch_url=self.watch_url,
            detail=self._detail,
        )

    async def _run(self) -> None:
        self._state = PipelineState.RUNNING
        logger.info(
            "pipeline_started broadcast_id=%s input=%s render_avatar=%s",
            self.broadcast_id,
            self.input_url,
            self._should_render_avatar(),
        )
        self._prepare_output_dirs()
        try:
            if self._should_render_avatar():
                await self._run_render_loop()
            else:
                await self._run_relay_loop()
        except Exception as exc:
            self._state = PipelineState.FAILED
            self._detail = str(exc)
            logger.exception("pipeline_failed broadcast_id=%s", self.broadcast_id)
        finally:
            await self._kill_ffmpeg()
            try:
                await self._metadata_store.close()
            except Exception:
                pass
            if self._state != PipelineState.FAILED:
                self._state = PipelineState.STOPPED
            logger.info("pipeline_finished broadcast_id=%s state=%s", self.broadcast_id, self._state.value)

    def _should_render_avatar(self) -> bool:
        return self._avatar_rendering_enabled

    async def _run_relay_loop(self) -> None:
        logger.info("pipeline_relay_started broadcast_id=%s input=%s", self.broadcast_id, self.input_url)
        retry_count = 0
        while not self._stop_event.is_set():
            await self._start_ffmpeg()
            should_restart = await self._monitor_ffmpeg(retry_count)
            if not should_restart:
                break
            retry_count += 1
            await self._kill_ffmpeg()
            await asyncio.sleep(self._input_open_retry_backoff_s)

    async def _run_render_loop(self) -> None:
        logger.info("pipeline_render_started broadcast_id=%s avatar_id=%s", self.broadcast_id, self.avatar_id)
        self._next_render_pts_us = None
        self._last_render_progress_log_at = time.monotonic()
        self._last_progress_processed_frames = 0
        self._last_progress_dropped_frames = 0
        self._timings.reset()
        self._latest_frame = None
        self._latest_frame_seq = 0
        self._rendered_frame_seq = 0
        self._reader_error = None
        self._last_renderable_face_metadata = None
        self._last_renderable_face_pts_us = None
        self._last_ready_avatar_id = self.avatar_id
        self._primary_tracking_id = None
        self._primary_tracking_last_seen_pts_us = None
        self._avatar_person_slots_by_tracking_id.clear()
        self._latest_frame_event.clear()
        renderer = AvatarRenderer(
            avatar_project_dir=self._avatar_project_dir,
            avatar_bank_dir=self._avatar_bank_dir,
            avatar_random_seed=self._avatar_random_seed,
        )
        self._media_source = create_media_source(
            self.input_url,
            fps=self._fps,
            max_frame_width=self._max_frame_width,
            max_frame_height=self._max_frame_height,
        )
        use_input_audio = await self._detect_input_audio()
        self._frame_sink = create_frame_sink(
            self.output_url,
            fps=self._fps,
            hls_time=self._hls_time,
            hls_list_size=self._hls_list_size,
            hls_flags=self._hls_flags,
            audio_bitrate=self._output_audio_bitrate,
            audio_sample_rate=self._output_audio_sample_rate,
            audio_channels=self._output_audio_channels,
            audio_source_url=self.input_url if use_input_audio else None,
            video_bitrate=self._video_bitrate,
            maxrate=self._maxrate,
            bufsize=self._bufsize,
            gop_seconds=self._gop_seconds,
            x264_preset=self._x264_preset,
        )
        if self.analysis_output_path:
            self._analysis_frame_sink = create_frame_sink(
                self.analysis_output_path,
                fps=self._fps,
                audio_bitrate=self._output_audio_bitrate,
                audio_sample_rate=self._output_audio_sample_rate,
                audio_channels=self._output_audio_channels,
                audio_source_url=self.input_url if use_input_audio else None,
                video_bitrate=self._video_bitrate,
                maxrate=self._maxrate,
                bufsize=self._bufsize,
                gop_seconds=self._gop_seconds,
                x264_preset=self._x264_preset,
            )
        reader_task = asyncio.create_task(
            self._read_latest_frames(),
            name=f"pipeline-reader:{self.broadcast_id}",
        )
        consumed_seq = 0

        try:
            while not self._stop_event.is_set():
                frame_started_at = time.perf_counter()
                latest_started_at = time.perf_counter()
                latest = await self._wait_for_latest_frame(consumed_seq, reader_task)
                self._timings.latest_wait.record(latest_started_at)
                if latest is None:
                    break
                consumed_seq, frame = latest
                self._rendered_frame_seq = consumed_seq
                metadata_started_at = time.perf_counter()
                face_metadata = await self._read_face_metadata(frame.pts_us)
                self._timings.metadata.record(metadata_started_at)
                if face_metadata is None:
                    self._stats.metadata_misses += 1
                else:
                    self._stats.metadata_hits += 1
                face_metadata = self._resolve_live_face_metadata(face_metadata, frame.pts_us)
                if face_metadata is not None:
                    face_metadata = self._prepare_metadata_for_live_render(face_metadata)
                    asset_started_at = time.perf_counter()
                    await self._prepare_face_avatar_assets(face_metadata)
                    self._timings.asset_prepare.record(asset_started_at)
                render_started_at = time.perf_counter()
                rendered_frame = await renderer.render(
                    frame,
                    face_metadata=face_metadata,
                    avatar_id=self._resolve_renderer_avatar_id(face_metadata),
                )
                self._timings.avatar_render.record(render_started_at)
                if face_metadata is not None and rendered_frame is not frame:
                    self._stats.avatar_rendered_frames += 1
                write_started_at = time.perf_counter()
                await self._frame_sink.write_frame(rendered_frame)
                if self._analysis_frame_sink is not None:
                    await self._analysis_frame_sink.write_frame(rendered_frame)
                self._timings.output_write.record(write_started_at)
                self._stats.processed_frames += 1
                self._stats.last_pts_us = frame.pts_us
                self._timings.frame_total.record(frame_started_at)
                self._log_render_progress()
        finally:
            reader_task.cancel()
            try:
                await reader_task
            except asyncio.CancelledError:
                pass
            if self._media_source is not None:
                await self._media_source.close()
                self._media_source = None
            if self._frame_sink is not None:
                await self._frame_sink.close()
                self._frame_sink = None
            if self._analysis_frame_sink is not None:
                await self._analysis_frame_sink.close()
                self._analysis_frame_sink = None
            self._cancel_avatar_asset_prepare_tasks()

    async def _read_latest_frames(self) -> None:
        if self._media_source is None:
            return
        try:
            while not self._stop_event.is_set():
                read_started_at = time.perf_counter()
                frame = await self._media_source.read_frame()
                self._timings.input_read.record(read_started_at)
                if frame is None:
                    return
                if self._should_drop_frame(frame.pts_us):
                    self._stats.dropped_frames += 1
                    self._log_render_progress()
                    continue
                if self._latest_frame_seq > self._rendered_frame_seq:
                    self._stats.dropped_frames += 1
                self._latest_frame_seq += 1
                self._latest_frame = frame
                self._latest_frame_event.set()
        except Exception as exc:
            self._reader_error = exc
            logger.exception("pipeline_reader_failed broadcast_id=%s", self.broadcast_id)
        finally:
            self._latest_frame_event.set()

    async def _wait_for_latest_frame(
        self,
        consumed_seq: int,
        reader_task: asyncio.Task[None],
    ) -> tuple[int, VideoFrame] | None:
        while not self._stop_event.is_set():
            if self._latest_frame_seq > consumed_seq and self._latest_frame is not None:
                return self._latest_frame_seq, self._latest_frame
            if reader_task.done():
                if self._reader_error is not None:
                    raise RuntimeError(f"input reader failed: {self._reader_error}") from self._reader_error
                return None
            self._latest_frame_event.clear()
            try:
                await asyncio.wait_for(self._latest_frame_event.wait(), timeout=0.5)
            except asyncio.TimeoutError:
                continue
        return None

    def _should_drop_frame(self, pts_us: int) -> bool:
        if self._next_render_pts_us is None:
            self._next_render_pts_us = pts_us + self._target_frame_interval_us
            return False
        if pts_us < self._next_render_pts_us:
            return True
        while self._next_render_pts_us <= pts_us:
            self._next_render_pts_us += self._target_frame_interval_us
        return False

    def _log_render_progress(self) -> None:
        now = time.monotonic()
        if now - self._last_render_progress_log_at < 5.0:
            return
        elapsed_s = max(now - self._last_render_progress_log_at, 0.001) if self._last_render_progress_log_at else 0.0
        processed_delta = self._stats.processed_frames - self._last_progress_processed_frames
        dropped_delta = self._stats.dropped_frames - self._last_progress_dropped_frames
        effective_fps = processed_delta / elapsed_s if elapsed_s > 0 else 0.0
        self._last_render_progress_log_at = now
        logger.info(
            (
                "pipeline_render_progress broadcast_id=%s processed=%s dropped=%s "
                "delta_processed=%s delta_dropped=%s effective_fps=%.2f hits=%s misses=%s avatar=%s "
                "last_pts_us=%s read_avg_ms=%.1f read_max_ms=%.1f wait_avg_ms=%.1f "
                "metadata_avg_ms=%.1f asset_avg_ms=%.1f render_avg_ms=%.1f render_max_ms=%.1f "
                "write_avg_ms=%.1f write_max_ms=%.1f frame_avg_ms=%.1f frame_max_ms=%.1f"
            ),
            self.broadcast_id,
            self._stats.processed_frames,
            self._stats.dropped_frames,
            processed_delta,
            dropped_delta,
            effective_fps,
            self._stats.metadata_hits,
            self._stats.metadata_misses,
            self._stats.avatar_rendered_frames,
            self._stats.last_pts_us,
            self._timings.input_read.average_ms(),
            self._timings.input_read.max_ms,
            self._timings.latest_wait.average_ms(),
            self._timings.metadata.average_ms(),
            self._timings.asset_prepare.average_ms(),
            self._timings.avatar_render.average_ms(),
            self._timings.avatar_render.max_ms,
            self._timings.output_write.average_ms(),
            self._timings.output_write.max_ms,
            self._timings.frame_total.average_ms(),
            self._timings.frame_total.max_ms,
        )
        self._last_progress_processed_frames = self._stats.processed_frames
        self._last_progress_dropped_frames = self._stats.dropped_frames
        self._timings.reset()

    async def _read_face_metadata(self, pts_us: int) -> dict | None:
        for attempt in range(self._metadata_poll_attempts):
            try:
                face_metadata = await self._metadata_store.get_face_metadata(self.broadcast_id, pts_us)
            except Exception as exc:
                logger.warning(
                    "metadata_read_failed broadcast_id=%s pts_us=%s detail=%s",
                    self.broadcast_id,
                    pts_us,
                    exc,
                )
                return None
            if face_metadata is not None:
                return face_metadata
            if attempt + 1 < self._metadata_poll_attempts and not self._stop_event.is_set():
                await asyncio.sleep(self._metadata_poll_interval_s)
        return None

    def _resolve_live_face_metadata(self, face_metadata: dict | None, pts_us: int) -> dict | None:
        if self._has_renderable_metadata(face_metadata):
            self._last_renderable_face_metadata = face_metadata
            self._last_renderable_face_pts_us = pts_us
            return face_metadata
        if self._has_explicit_mosaic_face(face_metadata):
            return face_metadata
        if self._can_reuse_last_renderable_metadata(pts_us):
            return self._reuse_last_renderable_metadata(pts_us)
        return face_metadata

    def _reuse_last_renderable_metadata(self, pts_us: int) -> dict:
        reused_metadata = dict(self._last_renderable_face_metadata or {})
        reused_metadata["pts_us"] = pts_us
        reused_metadata["ptsUs"] = pts_us
        logger.info(
            "avatar_metadata_reused broadcast_id=%s pts_us=%s last_pts_us=%s tracking_id=%s",
            self.broadcast_id,
            pts_us,
            self._last_renderable_face_pts_us,
            self._primary_tracking_id,
        )
        return reused_metadata

    def _has_renderable_metadata(self, face_metadata: dict | None) -> bool:
        if not isinstance(face_metadata, dict):
            return False
        raw_faces = face_metadata.get("faces")
        if not isinstance(raw_faces, list):
            return self._has_renderable_face_metadata(face_metadata)
        return any(isinstance(face, dict) and self._has_renderable_face_metadata(face) for face in raw_faces)

    def _has_explicit_mosaic_face(self, face_metadata: dict | None) -> bool:
        if not isinstance(face_metadata, dict):
            return False
        raw_faces = face_metadata.get("faces")
        if not isinstance(raw_faces, list):
            raw_faces = [face_metadata]
        for raw_face in raw_faces:
            if not isinstance(raw_face, dict):
                continue
            render_mode = str(raw_face.get("render_mode", raw_face.get("renderMode", ""))).upper()
            if render_mode in {"MOSAIC", "PIXELATE", "BLUR"}:
                return True
        return False

    def _metadata_contains_tracking_id(self, face_metadata: dict | None, tracking_id: str) -> bool:
        if not isinstance(face_metadata, dict):
            return False
        raw_faces = face_metadata.get("faces")
        if not isinstance(raw_faces, list):
            raw_faces = [face_metadata]
        return any(
            isinstance(face, dict)
            and self._extract_tracking_id(face) == tracking_id
            and self._has_renderable_face_metadata(face)
            for face in raw_faces
        )

    def _can_reuse_last_renderable_metadata(self, pts_us: int) -> bool:
        if self._avatar_metadata_grace_us <= 0:
            return False
        if self._last_renderable_face_metadata is None or self._last_renderable_face_pts_us is None:
            return False
        return abs(int(pts_us) - self._last_renderable_face_pts_us) <= self._avatar_metadata_grace_us

    def _prepare_metadata_for_live_render(self, face_metadata: dict) -> dict:
        if self._avatar_max_faces_per_frame <= 0:
            return face_metadata

        raw_faces = face_metadata.get("faces")
        if not isinstance(raw_faces, list) or not raw_faces:
            return face_metadata

        ranked_faces = [
            (index, self._bbox_area(face), self._extract_tracking_id(face))
            for index, face in enumerate(raw_faces)
            if isinstance(face, dict) and self._has_renderable_face_metadata(face)
        ]
        if not ranked_faces:
            mosaic_faces = [
                mosaic_face
                for raw_face in raw_faces
                if isinstance(raw_face, dict)
                for mosaic_face in [
                    self._build_mosaic_face(
                        raw_face,
                        require_explicit=not self._avatar_mosaic_non_selected_faces,
                    )
                ]
                if mosaic_face is not None
            ]
            if not mosaic_faces:
                return face_metadata
            normalized = dict(face_metadata)
            normalized["faces"] = mosaic_faces
            return normalized

        selected_indexes = self._select_live_avatar_indexes(ranked_faces, face_metadata)
        metadata_pts_us = self._extract_metadata_pts_us(face_metadata)
        current_tracking_ids = {
            tracking_id
            for raw_face in raw_faces
            if isinstance(raw_face, dict)
            for tracking_id in [self._extract_tracking_id(raw_face)]
            if tracking_id is not None
        }
        normalized_faces: list[Any] = []
        for index, raw_face in enumerate(raw_faces):
            if not isinstance(raw_face, dict):
                continue
            face = dict(raw_face)
            if index not in selected_indexes:
                mosaic_face = self._build_mosaic_face(face, require_explicit=not self._avatar_mosaic_non_selected_faces)
                if mosaic_face is not None:
                    normalized_faces.append(mosaic_face)
                continue
            face = self._stabilize_face_avatar_assignment(face, metadata_pts_us, current_tracking_ids)
            if self.avatar_id and not self._should_use_face_avatar_assignments([face]):
                # A selected broadcast avatar is already materialized before the
                # pipeline starts. Strip per-tracking assignments only in the
                # single-avatar mode. In multi-person mode, Spring/Redis
                # face-level assignments are the source of truth.
                face.pop("avatar_id", None)
                face.pop("avatarId", None)
                face.pop("avatar_asset_key", None)
                face.pop("avatarAssetKey", None)
            else:
                face = self._prepare_tracking_avatar_face(face)
            normalized_faces.append(face)

        normalized = dict(face_metadata)
        normalized["faces"] = normalized_faces
        return normalized

    def _select_live_avatar_indexes(
        self,
        ranked_faces: list[tuple[int, float, str | None]],
        face_metadata: dict,
    ) -> set[int]:
        metadata_pts_us = self._extract_metadata_pts_us(face_metadata)
        indexed_by_tracking_id = {
            tracking_id: index
            for index, _, tracking_id in ranked_faces
            if tracking_id is not None
        }
        if self._primary_tracking_id in indexed_by_tracking_id:
            self._primary_tracking_last_seen_pts_us = metadata_pts_us
            if self._avatar_max_faces_per_frame <= 1:
                return {indexed_by_tracking_id[str(self._primary_tracking_id)]}

        if self._avatar_max_faces_per_frame <= 1 and self._can_wait_for_primary_tracking(metadata_pts_us):
            return set()

        sorted_faces = sorted(ranked_faces, key=lambda item: item[1], reverse=True)
        if self._avatar_max_faces_per_frame <= 1:
            selected_faces = sorted_faces[: self._avatar_max_faces_per_frame]
        else:
            selected_faces = self._select_multi_person_faces(sorted_faces)
        selected_tracking_id = next((tracking_id for _, _, tracking_id in selected_faces if tracking_id), None)
        if selected_tracking_id:
            self._primary_tracking_id = selected_tracking_id
            self._primary_tracking_last_seen_pts_us = metadata_pts_us
            logger.info(
                "avatar_primary_tracking_selected broadcast_id=%s tracking_id=%s",
                self.broadcast_id,
                selected_tracking_id,
            )
        return {index for index, _, _ in selected_faces}

    def _select_multi_person_faces(
        self,
        sorted_faces: list[tuple[int, float, str | None]],
    ) -> list[tuple[int, float, str | None]]:
        selected: list[tuple[int, float, str | None]] = []
        active_tracking_ids = set(self._avatar_person_slots_by_tracking_id.keys())
        if self._primary_tracking_id:
            active_tracking_ids.add(str(self._primary_tracking_id))

        for item in sorted_faces:
            _, _, tracking_id = item
            if tracking_id is not None and tracking_id in active_tracking_ids:
                selected.append(item)
                if len(selected) >= self._avatar_max_faces_per_frame:
                    return selected

        selected_indexes = {index for index, _, _ in selected}
        for item in sorted_faces:
            index, _, _ = item
            if index in selected_indexes:
                continue
            selected.append(item)
            if len(selected) >= self._avatar_max_faces_per_frame:
                return selected
        return selected

    def _can_wait_for_primary_tracking(self, metadata_pts_us: int | None) -> bool:
        if self._primary_tracking_id is None:
            return False
        if metadata_pts_us is None or self._primary_tracking_last_seen_pts_us is None:
            return False
        return abs(metadata_pts_us - self._primary_tracking_last_seen_pts_us) <= self._avatar_primary_reselect_grace_us

    def _extract_metadata_pts_us(self, face_metadata: dict) -> int | None:
        raw_pts_us = face_metadata.get("pts_us", face_metadata.get("ptsUs"))
        try:
            if raw_pts_us is not None:
                return int(raw_pts_us)
        except (TypeError, ValueError):
            return None
        return None

    def _extract_tracking_id(self, face: dict) -> str | None:
        raw_tracking_id = face.get("tracking_id", face.get("trackingId"))
        if raw_tracking_id is None:
            return None
        tracking_id = str(raw_tracking_id).strip()
        return tracking_id or None

    def _build_mosaic_face(self, face: dict, require_explicit: bool = False) -> dict[str, Any] | None:
        render_mode = str(face.get("render_mode", face.get("renderMode", ""))).upper()
        if require_explicit and render_mode not in {"MOSAIC", "PIXELATE", "BLUR"}:
            return None
        bbox = face.get("bbox")
        if bbox is None:
            bbox = face.get("bounding_box", face.get("boundingBox"))
        if bbox is None:
            return None
        return {
            "tracking_id": face.get("tracking_id", face.get("trackingId")),
            "bbox": bbox,
            "render_mode": "MOSAIC",
        }

    def _stabilize_face_avatar_assignment(
        self,
        face: dict,
        metadata_pts_us: int | None,
        current_tracking_ids: set[str],
    ) -> dict:
        tracking_id = self._extract_tracking_id(face)
        bbox = self._normalize_bbox(face)
        if tracking_id is None or bbox is None:
            return face

        pts_us = int(metadata_pts_us or self._last_renderable_face_pts_us or 0)
        self._prune_stale_avatar_person_slots(pts_us)
        slot = self._avatar_person_slots_by_tracking_id.get(tracking_id)
        if slot is None:
            slot = self._find_matching_avatar_person_slot(tracking_id, bbox, pts_us, current_tracking_ids)

        if slot is None:
            slot = AvatarPersonSlot(
                tracking_id=tracking_id,
                avatar_id=self._extract_face_avatar_id(face),
                avatar_asset_key=self._extract_face_avatar_asset_key(face),
                bbox=bbox,
                last_seen_pts_us=pts_us,
            )
            self._avatar_person_slots_by_tracking_id[tracking_id] = slot
        else:
            if slot.tracking_id != tracking_id:
                logger.info(
                    "avatar_person_slot_relinked broadcast_id=%s old_tracking_id=%s new_tracking_id=%s avatar_id=%s",
                    self.broadcast_id,
                    slot.tracking_id,
                    tracking_id,
                    slot.avatar_id,
                )
                self._avatar_person_slots_by_tracking_id.pop(slot.tracking_id, None)
                slot.tracking_id = tracking_id
                self._avatar_person_slots_by_tracking_id[tracking_id] = slot
            if slot.avatar_id is None:
                slot.avatar_id = self._extract_face_avatar_id(face)
            if slot.avatar_asset_key is None:
                slot.avatar_asset_key = self._extract_face_avatar_asset_key(face)
            slot.bbox = bbox
            slot.last_seen_pts_us = pts_us

        if slot.avatar_id:
            face["avatar_id"] = slot.avatar_id
            face.pop("avatarId", None)
        if slot.avatar_asset_key:
            face["avatar_asset_key"] = slot.avatar_asset_key
            face.pop("avatarAssetKey", None)
        return face

    def _find_matching_avatar_person_slot(
        self,
        tracking_id: str,
        bbox: dict[str, float],
        pts_us: int,
        current_tracking_ids: set[str],
    ) -> AvatarPersonSlot | None:
        if self._avatar_person_slot_grace_us <= 0:
            return None
        best_slot: AvatarPersonSlot | None = None
        best_score = 0.0
        for slot in self._avatar_person_slots_by_tracking_id.values():
            if slot.tracking_id == tracking_id:
                continue
            if slot.tracking_id in current_tracking_ids:
                continue
            age_us = abs(pts_us - slot.last_seen_pts_us)
            if age_us > self._avatar_person_slot_grace_us:
                continue
            iou = self._bbox_iou(slot.bbox, bbox)
            center_score = self._bbox_center_similarity(slot.bbox, bbox)
            score = max(iou, center_score)
            if score > best_score:
                best_score = score
                best_slot = slot
        if best_slot is None or best_score < self._avatar_person_slot_match_iou:
            return None
        return best_slot

    def _prune_stale_avatar_person_slots(self, pts_us: int) -> None:
        if self._avatar_person_slot_grace_us <= 0:
            return
        stale_after_us = self._avatar_person_slot_grace_us * 3
        for tracking_id, slot in list(self._avatar_person_slots_by_tracking_id.items()):
            if abs(pts_us - slot.last_seen_pts_us) > stale_after_us:
                self._avatar_person_slots_by_tracking_id.pop(tracking_id, None)

    def _bbox_iou(self, left: dict[str, float], right: dict[str, float]) -> float:
        left_x1 = left["x"]
        left_y1 = left["y"]
        left_x2 = left_x1 + max(left["width"], 0.0)
        left_y2 = left_y1 + max(left["height"], 0.0)
        right_x1 = right["x"]
        right_y1 = right["y"]
        right_x2 = right_x1 + max(right["width"], 0.0)
        right_y2 = right_y1 + max(right["height"], 0.0)
        inter_w = max(min(left_x2, right_x2) - max(left_x1, right_x1), 0.0)
        inter_h = max(min(left_y2, right_y2) - max(left_y1, right_y1), 0.0)
        inter_area = inter_w * inter_h
        left_area = max(left["width"], 0.0) * max(left["height"], 0.0)
        right_area = max(right["width"], 0.0) * max(right["height"], 0.0)
        union_area = left_area + right_area - inter_area
        if union_area <= 0:
            return 0.0
        return inter_area / union_area

    def _bbox_center_similarity(self, left: dict[str, float], right: dict[str, float]) -> float:
        left_cx = left["x"] + left["width"] / 2
        left_cy = left["y"] + left["height"] / 2
        right_cx = right["x"] + right["width"] / 2
        right_cy = right["y"] + right["height"] / 2
        distance = ((left_cx - right_cx) ** 2 + (left_cy - right_cy) ** 2) ** 0.5
        scale = max(left["width"], left["height"], right["width"], right["height"], 1.0)
        return max(1.0 - distance / scale, 0.0)

    def _prepare_tracking_avatar_face(self, face: dict) -> dict:
        avatar_id = self._extract_face_avatar_id(face)
        avatar_asset_key = self._extract_face_avatar_asset_key(face)
        if not avatar_id or not avatar_asset_key or self._avatar_asset_resolver is None:
            return face
        if self._avatar_asset_resolver.is_avatar_cached(avatar_id):
            self._last_ready_avatar_id = avatar_id
            return face
        self._schedule_avatar_asset_prepare(avatar_id, avatar_asset_key)
        fallback_avatar_id = self._last_ready_avatar_id
        if fallback_avatar_id:
            face["avatar_id"] = fallback_avatar_id
            face.pop("avatarId", None)
            face.pop("avatar_asset_key", None)
            face.pop("avatarAssetKey", None)
            return face
        mosaic_face = self._build_mosaic_face(face)
        return mosaic_face if mosaic_face is not None else face

    def _resolve_renderer_avatar_id(self, face_metadata: dict | None) -> str | None:
        if self._should_use_face_avatar_assignments(self._extract_faces(face_metadata)):
            return None
        return self.avatar_id

    def _should_use_face_avatar_assignments(self, faces: list[dict]) -> bool:
        if self._avatar_max_faces_per_frame <= 1:
            return False
        return any(self._extract_face_avatar_id(face) for face in faces if isinstance(face, dict))

    def _extract_faces(self, face_metadata: dict | None) -> list[dict]:
        if not isinstance(face_metadata, dict):
            return []
        raw_faces = face_metadata.get("faces")
        if isinstance(raw_faces, list):
            return [face for face in raw_faces if isinstance(face, dict)]
        return [face_metadata]

    def _extract_face_avatar_id(self, face: dict) -> str | None:
        raw_avatar_id = face.get("avatar_id", face.get("avatarId"))
        if raw_avatar_id is None:
            return None
        avatar_id = str(raw_avatar_id).strip()
        return avatar_id or None

    def _extract_face_avatar_asset_key(self, face: dict) -> str | None:
        raw_avatar_asset_key = face.get("avatar_asset_key", face.get("avatarAssetKey"))
        if raw_avatar_asset_key is None:
            return None
        avatar_asset_key = str(raw_avatar_asset_key).strip()
        return avatar_asset_key or None

    def _has_renderable_face_metadata(self, face: dict) -> bool:
        return self._extract_coeffs(face) is not None and self._normalize_bbox(face) is not None

    def _extract_coeffs(self, face: dict) -> list | None:
        tdmm = face.get("tdmm_raw")
        if not isinstance(tdmm, dict):
            tdmm = face.get("tdmmRaw")
        if isinstance(tdmm, dict):
            coeffs = tdmm.get("coeffs")
            if isinstance(coeffs, list) and coeffs:
                return coeffs
        coeffs = face.get("coeffs")
        if isinstance(coeffs, list) and coeffs:
            return coeffs
        return None

    def _bbox_area(self, face: dict) -> float:
        bbox = self._normalize_bbox(face)
        if bbox is None:
            return 0.0
        return max(float(bbox["width"]), 0.0) * max(float(bbox["height"]), 0.0)

    def _normalize_bbox(self, face: dict) -> dict[str, float] | None:
        raw_bbox = face.get("bbox")
        if raw_bbox is None:
            raw_bbox = face.get("bounding_box", face.get("boundingBox"))
        if isinstance(raw_bbox, dict) and {"x", "y", "width", "height"}.issubset(raw_bbox.keys()):
            return {
                "x": float(raw_bbox["x"]),
                "y": float(raw_bbox["y"]),
                "width": float(raw_bbox["width"]),
                "height": float(raw_bbox["height"]),
            }
        if isinstance(raw_bbox, list) and len(raw_bbox) >= 4:
            return {
                "x": float(raw_bbox[0]),
                "y": float(raw_bbox[1]),
                "width": float(raw_bbox[2]),
                "height": float(raw_bbox[3]),
            }
        return None

    async def _prepare_face_avatar_assets(self, face_metadata: dict) -> None:
        if self._avatar_asset_resolver is None:
            return
        raw_faces = face_metadata.get("faces")
        if not isinstance(raw_faces, list):
            return
        for raw_face in raw_faces:
            if not isinstance(raw_face, dict):
                continue
            avatar_id = self._extract_face_avatar_id(raw_face)
            avatar_asset_key = self._extract_face_avatar_asset_key(raw_face)
            if not avatar_id or not avatar_asset_key:
                continue
            if self._avatar_asset_resolver.is_avatar_cached(avatar_id):
                self._last_ready_avatar_id = avatar_id
                continue
            self._schedule_avatar_asset_prepare(avatar_id, avatar_asset_key)

    def _schedule_avatar_asset_prepare(self, avatar_id: str, avatar_asset_key: str) -> None:
        if self._avatar_asset_resolver is None:
            return
        task_key = (avatar_id, avatar_asset_key)
        existing_task = self._avatar_asset_prepare_tasks.get(task_key)
        if existing_task is not None and not existing_task.done():
            return
        task = asyncio.create_task(
            self._prepare_avatar_asset_in_background(avatar_id, avatar_asset_key),
            name=f"avatar-asset:{self.broadcast_id}:{avatar_id}",
        )
        self._avatar_asset_prepare_tasks[task_key] = task
        task.add_done_callback(lambda completed_task: self._on_avatar_asset_prepare_done(task_key, completed_task))

    async def _prepare_avatar_asset_in_background(self, avatar_id: str, avatar_asset_key: str) -> None:
        if self._avatar_asset_resolver is None:
            return
        await self._avatar_asset_resolver.prepare_avatar_bank(avatar_id, avatar_asset_key)

    def _on_avatar_asset_prepare_done(
        self,
        task_key: tuple[str, str],
        completed_task: asyncio.Task[None],
    ) -> None:
        self._avatar_asset_prepare_tasks.pop(task_key, None)
        avatar_id, _ = task_key
        if completed_task.cancelled():
            return
        try:
            completed_task.result()
        except Exception as exc:
            logger.warning(
                "avatar_asset_prepare_failed broadcast_id=%s avatar_id=%s detail=%s",
                self.broadcast_id,
                avatar_id,
                exc,
            )
            return
        self._last_ready_avatar_id = avatar_id
        logger.info("avatar_asset_prepare_ready broadcast_id=%s avatar_id=%s", self.broadcast_id, avatar_id)

    def _cancel_avatar_asset_prepare_tasks(self) -> None:
        for task in self._avatar_asset_prepare_tasks.values():
            if not task.done():
                task.cancel()
        self._avatar_asset_prepare_tasks.clear()

    def _prepare_output_dirs(self) -> None:
        if self.output_mode == OutputMode.HLS:
            output_dir = os.path.dirname(self.output_url)
            if output_dir:
                os.makedirs(output_dir, exist_ok=True)
        if self.analysis_output_path:
            os.makedirs(os.path.dirname(self.analysis_output_path), exist_ok=True)

    async def _start_ffmpeg(self) -> None:
        use_input_audio = await self._detect_input_audio()
        cmd = self._build_ffmpeg_command(use_input_audio=use_input_audio)
        self._ffmpeg_proc = await asyncio.create_subprocess_exec(
            *cmd,
            stdin=asyncio.subprocess.PIPE,
            stdout=asyncio.subprocess.DEVNULL,
            stderr=asyncio.subprocess.PIPE,
        )
        logger.info(
            "ffmpeg_relay_spawned broadcast_id=%s pid=%s output_mode=%s",
            self.broadcast_id,
            self._ffmpeg_proc.pid,
            self.output_mode.value,
        )

    async def _monitor_ffmpeg(self, retry_count: int) -> bool:
        while not self._stop_event.is_set():
            if self._ffmpeg_proc and self._ffmpeg_proc.returncode is not None:
                message = await self._read_ffmpeg_error()
                if self._can_retry_input_open(retry_count, message):
                    logger.warning(
                        "ffmpeg_input_open_retry broadcast_id=%s attempt=%s/%s detail=%s",
                        self.broadcast_id,
                        retry_count + 1,
                        self._input_open_retry_count,
                        message[-200:],
                    )
                    return True
                raise RuntimeError(f"ffmpeg exited with code {self._ffmpeg_proc.returncode}: {message}")
            self._stats.processed_frames += 1
            await asyncio.sleep(0.5)
        return False

    async def _read_ffmpeg_error(self) -> str:
        if not self._ffmpeg_proc or not self._ffmpeg_proc.stderr:
            return ""
        stderr_out = await self._ffmpeg_proc.stderr.read()
        return stderr_out.decode(errors="replace")[-500:]

    def _can_retry_input_open(self, retry_count: int, message: str) -> bool:
        if retry_count >= self._input_open_retry_count:
            return False
        return "404 Not Found" in message or "Error opening input" in message

    async def _detect_input_audio(self) -> bool:
        cmd = [
            "ffprobe",
            "-v",
            "error",
            "-rtsp_transport",
            "tcp",
            "-select_streams",
            "a:0",
            "-show_entries",
            "stream=index",
            "-of",
            "csv=p=0",
            self.input_url,
        ]
        proc: asyncio.subprocess.Process | None = None
        try:
            proc = await asyncio.create_subprocess_exec(
                *cmd,
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.PIPE,
            )
            stdout, stderr = await asyncio.wait_for(proc.communicate(), timeout=5)
        except Exception as exc:
            if proc and proc.returncode is None:
                proc.kill()
                await proc.wait()
            logger.warning(
                "input_audio_probe_failed broadcast_id=%s input=%s detail=%s",
                self.broadcast_id,
                self.input_url,
                exc,
            )
            return False

        has_audio = proc.returncode == 0 and bool(stdout.strip())
        if has_audio:
            logger.info("input_audio_detected broadcast_id=%s", self.broadcast_id)
        else:
            logger.info(
                "input_audio_not_detected_using_silence broadcast_id=%s detail=%s",
                self.broadcast_id,
                stderr.decode(errors="replace")[-200:],
            )
        return has_audio

    def _build_ffmpeg_command(self, use_input_audio: bool) -> list[str]:
        command = self._build_input_args(use_input_audio=use_input_audio)
        command += self._build_encoded_output_args(use_input_audio=use_input_audio)
        command += self._build_primary_output_args()
        if self.analysis_output_path:
            command += self._build_encoded_output_args(use_input_audio=use_input_audio)
            command += ["-movflags", "+faststart", self.analysis_output_path]
        return command

    def _build_input_args(self, use_input_audio: bool) -> list[str]:
        command = [
            "ffmpeg",
            "-y",
            "-loglevel",
            self._ffmpeg_log_level,
            "-fflags",
            "nobuffer",
            "-flags",
            "low_delay",
            "-analyzeduration",
            "0",
            "-probesize",
            "32k",
            "-rtsp_transport",
            "tcp",
            "-i",
            self.input_url,
        ]
        if not use_input_audio:
            command += [
                "-f",
                "lavfi",
                "-i",
                f"anullsrc=channel_layout=stereo:sample_rate={self._output_audio_sample_rate}",
            ]
        return command

    def _build_encoded_output_args(self, use_input_audio: bool) -> list[str]:
        gop = max(self._fps * self._gop_seconds, 1)
        audio_map = "0:a:0" if use_input_audio else "1:a:0"
        return [
            "-map",
            "0:v:0",
            "-map",
            audio_map,
            "-c:v",
            "libx264",
            "-preset",
            self._x264_preset,
            "-tune",
            "zerolatency",
            "-pix_fmt",
            "yuv420p",
            "-r",
            str(self._fps),
            "-g",
            str(gop),
            "-keyint_min",
            str(gop),
            "-sc_threshold",
            "0",
            "-force_key_frames",
            f"expr:gte(t,n_forced*{self._gop_seconds})",
            "-b:v",
            self._video_bitrate,
            "-maxrate",
            self._maxrate,
            "-bufsize",
            self._bufsize,
            "-c:a",
            "aac",
            "-b:a",
            self._output_audio_bitrate,
            "-ar",
            str(self._output_audio_sample_rate),
            "-ac",
            str(self._output_audio_channels),
            "-shortest",
        ]

    def _build_primary_output_args(self) -> list[str]:
        if self.output_mode == OutputMode.HLS:
            return [
                "-f",
                "hls",
                "-hls_time",
                str(self._hls_time),
                "-hls_list_size",
                str(self._hls_list_size),
                "-hls_flags",
                self._hls_flags,
                self.output_url,
            ]
        return ["-f", "flv", self.output_url]

    async def _kill_ffmpeg(self) -> None:
        proc = self._ffmpeg_proc
        if proc is None or proc.returncode is not None:
            return
        if proc.stdin:
            try:
                proc.stdin.write(b"q")
                await proc.stdin.drain()
                proc.stdin.close()
                await asyncio.wait_for(proc.wait(), timeout=5)
                logger.info("ffmpeg_relay_stopped broadcast_id=%s", self.broadcast_id)
                return
            except Exception:
                logger.warning("ffmpeg_graceful_stop_failed broadcast_id=%s", self.broadcast_id)
        proc.terminate()
        try:
            await asyncio.wait_for(proc.wait(), timeout=5)
        except asyncio.TimeoutError:
            proc.kill()
            await proc.wait()
        logger.info("ffmpeg_relay_stopped broadcast_id=%s", self.broadcast_id)
