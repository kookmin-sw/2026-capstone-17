import asyncio
import logging
import os
from dataclasses import dataclass
from enum import Enum

from adapters.metadata_store import MetadataStore
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
    """RTSP → HLS relay via ffmpeg subprocess (no Python frame decode)."""

    def __init__(
        self,
        broadcast_id: str,
        stream_key: str,
        input_url: str,
        output_path: str,
        hls_url: str,
        avatar_id: str | None,
        fps: int,
        max_frame_lag_ms: int,
        metadata_store: MetadataStore,
        ffmpeg_log_level: str = "warning",
        gop_seconds: int = 1,
        video_bitrate: str = "2500k",
        maxrate: str = "2500k",
        bufsize: str = "5000k",
        hls_time: float = 1.0,
        hls_list_size: int = 6,
        hls_flags: str = "delete_segments+independent_segments+append_list+omit_endlist",
        **_kwargs,
    ) -> None:
        self.broadcast_id = broadcast_id
        self.stream_key = stream_key
        self.input_url = input_url
        self.output_path = output_path
        self.hls_url = hls_url
        self.avatar_id = avatar_id
        self._fps = fps
        self._metadata_store = metadata_store
        self._ffmpeg_log_level = ffmpeg_log_level
        self._gop_seconds = max(gop_seconds, 1)
        self._video_bitrate = video_bitrate
        self._maxrate = maxrate
        self._bufsize = bufsize
        self._hls_time = max(hls_time, 0.5)
        self._hls_list_size = max(hls_list_size, 3)
        self._hls_flags = hls_flags

        self._task: asyncio.Task[None] | None = None
        self._ffmpeg_proc: asyncio.subprocess.Process | None = None
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
            stream_key=self.stream_key,
            state=self._state.value,
            processed_frames=self._stats.processed_frames,
            dropped_frames=self._stats.dropped_frames,
            last_pts_us=self._stats.last_pts_us,
            input_url=self.input_url,
            output_path=self.output_path,
            hls_url=self.hls_url,
            detail=self._detail,
        )

    async def _run(self) -> None:
        self._state = PipelineState.RUNNING
        logger.info("pipeline_relay_started broadcast_id=%s input=%s", self.broadcast_id, self.input_url)

        output_dir = os.path.dirname(self.output_path)
        if output_dir:
            os.makedirs(output_dir, exist_ok=True)

        gop = max(self._fps * self._gop_seconds, 1)
        cmd = [
            "ffmpeg", "-y",
            "-loglevel", self._ffmpeg_log_level,
            "-fflags", "nobuffer",
            "-flags", "low_delay",
            "-analyzeduration", "0",
            "-probesize", "32k",
            "-rtsp_transport", "tcp",
            "-i", self.input_url,
            "-c:v", "libx264",
            "-preset", "veryfast",
            "-tune", "zerolatency",
            "-pix_fmt", "yuv420p",
            "-r", str(self._fps),
            "-g", str(gop),
            "-keyint_min", str(gop),
            "-sc_threshold", "0",
            "-force_key_frames", f"expr:gte(t,n_forced*{self._gop_seconds})",
            "-b:v", self._video_bitrate,
            "-maxrate", self._maxrate,
            "-bufsize", self._bufsize,
            "-f", "hls",
            "-hls_time", str(self._hls_time),
            "-hls_list_size", str(self._hls_list_size),
            "-hls_flags", self._hls_flags,
            self.output_path,
        ]

        try:
            self._ffmpeg_proc = await asyncio.create_subprocess_exec(
                *cmd,
                stdout=asyncio.subprocess.DEVNULL,
                stderr=asyncio.subprocess.PIPE,
            )
            logger.info("ffmpeg_relay_spawned broadcast_id=%s pid=%s", self.broadcast_id, self._ffmpeg_proc.pid)

            while not self._stop_event.is_set():
                if self._ffmpeg_proc.returncode is not None:
                    stderr_out = await self._ffmpeg_proc.stderr.read() if self._ffmpeg_proc.stderr else b""
                    msg = stderr_out.decode(errors="replace")[-500:]
                    raise RuntimeError(f"ffmpeg exited with code {self._ffmpeg_proc.returncode}: {msg}")

                self._stats.processed_frames += 1
                await asyncio.sleep(0.5)

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

    async def _kill_ffmpeg(self) -> None:
        proc = self._ffmpeg_proc
        if proc is None or proc.returncode is not None:
            return
        proc.terminate()
        try:
            await asyncio.wait_for(proc.wait(), timeout=5)
        except asyncio.TimeoutError:
            proc.kill()
            await proc.wait()
        logger.info("ffmpeg_relay_stopped broadcast_id=%s", self.broadcast_id)
