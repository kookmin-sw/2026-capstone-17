import asyncio
import logging
import os
from dataclasses import dataclass
from enum import Enum

from adapters.frame_sink import FrameSink, create_frame_sink
from adapters.media_source import MediaSource, create_media_source
from adapters.metadata_store import MetadataStore
from model.renderer import AvatarRenderer
from schemas.stream import OutputMode, StreamStatusResponse
from services.avatar_assets import AvatarAssetResolver

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
        metadata_poll_attempts: int = 3,
        metadata_poll_interval_ms: int = 10,
        ffmpeg_log_level: str = "warning",
        gop_seconds: int = 1,
        video_bitrate: str = "2500k",
        maxrate: str = "2500k",
        bufsize: str = "5000k",
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
        self._metadata_poll_attempts = max(int(metadata_poll_attempts), 1)
        self._metadata_poll_interval_s = max(int(metadata_poll_interval_ms), 0) / 1000
        self._ffmpeg_log_level = ffmpeg_log_level
        self._gop_seconds = max(gop_seconds, 1)
        self._video_bitrate = video_bitrate
        self._maxrate = maxrate
        self._bufsize = bufsize
        self._hls_time = max(hls_time, 0.5)
        self._hls_list_size = max(hls_list_size, 3)
        self._hls_flags = hls_flags
        self.analysis_output_path = analysis_output_path
        self._input_open_retry_count = max(input_open_retry_count, 0)
        self._input_open_retry_backoff_s = max(input_open_retry_backoff_ms, 0) / 1000
        self._output_audio_bitrate = output_audio_bitrate
        self._output_audio_sample_rate = output_audio_sample_rate
        self._output_audio_channels = output_audio_channels

        self._task: asyncio.Task[None] | None = None
        self._ffmpeg_proc: asyncio.subprocess.Process | None = None
        self._media_source: MediaSource | None = None
        self._frame_sink: FrameSink | None = None
        self._stop_event = asyncio.Event()
        self._state = PipelineState.STOPPED
        self._detail: str | None = None
        self._stats = PipelineStats()

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
        renderer = AvatarRenderer(
            avatar_project_dir=self._avatar_project_dir,
            avatar_bank_dir=self._avatar_bank_dir,
            avatar_random_seed=self._avatar_random_seed,
        )
        self._media_source = create_media_source(self.input_url, fps=self._fps)
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
        )

        try:
            while not self._stop_event.is_set():
                frame = await self._media_source.read_frame()
                if frame is None:
                    break

                face_metadata = await self._read_face_metadata(frame.pts_us)
                if face_metadata is None:
                    self._stats.metadata_misses += 1
                else:
                    self._stats.metadata_hits += 1
                    await self._prepare_face_avatar_assets(face_metadata)
                rendered_frame = await renderer.render(
                    frame,
                    face_metadata=face_metadata,
                    avatar_id=self.avatar_id,
                )
                if face_metadata is not None and rendered_frame is not frame:
                    self._stats.avatar_rendered_frames += 1
                await self._frame_sink.write_frame(rendered_frame)
                self._stats.processed_frames += 1
                self._stats.last_pts_us = frame.pts_us
        finally:
            if self._media_source is not None:
                await self._media_source.close()
                self._media_source = None
            if self._frame_sink is not None:
                await self._frame_sink.close()
                self._frame_sink = None

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

    async def _prepare_face_avatar_assets(self, face_metadata: dict) -> None:
        if self._avatar_asset_resolver is None:
            return
        try:
            await self._avatar_asset_resolver.prepare_face_avatar_assets(face_metadata)
        except Exception as exc:
            logger.warning(
                "avatar_asset_prepare_failed broadcast_id=%s detail=%s",
                self.broadcast_id,
                exc,
            )

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
            "veryfast",
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
