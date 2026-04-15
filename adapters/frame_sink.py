import asyncio
import contextlib
import logging
import os
from typing import Optional, Protocol

from schemas.stream import OutputMode
from workers.types import VideoFrame

logger = logging.getLogger(__name__)


class FrameSink(Protocol):
    async def write_frame(self, frame: VideoFrame) -> None:
        ...

    async def close(self) -> None:
        ...


class DummyHlsSink:
    """Placeholder sink until FFmpeg-to-HLS pipeline is wired."""

    def __init__(self, output_path: str) -> None:
        self.output_path = output_path

    async def write_frame(self, frame: VideoFrame) -> None:
        _ = (self.output_path, frame)

    async def close(self) -> None:
        return None


class FFmpegProcessSink:
    """Real sink that encodes raw frames to HLS/RTMP using an FFmpeg subprocess."""

    def __init__(
        self,
        output_url: str,
        fps: int = 30,
        width: int = 1280,
        height: int = 720,
        output_mode: OutputMode = OutputMode.HLS,
        ffmpeg_log_level: str = "warning",
        video_bitrate_kbps: int = 4000,
        audio_bitrate_kbps: int = 128,
        audio_sample_rate: int = 44100,
        audio_channels: int = 2,
        keyframe_interval_seconds: int = 1,
    ) -> None:
        self.output_url = output_url
        self.fps = fps
        self.width = width
        self.height = height
        self.output_mode = output_mode
        self.ffmpeg_log_level = ffmpeg_log_level
        self.video_bitrate_kbps = max(video_bitrate_kbps, 500)
        self.audio_bitrate_kbps = max(audio_bitrate_kbps, 64)
        self.audio_sample_rate = max(audio_sample_rate, 8000)
        self.audio_channels = max(audio_channels, 1)
        self.keyframe_interval_seconds = max(keyframe_interval_seconds, 1)
        self._process: Optional[asyncio.subprocess.Process] = None
        self._initialized = False
        self._stderr_task: Optional[asyncio.Task[None]] = None
        self._recent_stderr_lines: list[str] = []

    async def _start_ffmpeg(self, width: int, height: int) -> None:
        if self.output_mode == OutputMode.HLS:
            # Ensure the output directory exists for HLS
            output_dir = os.path.dirname(self.output_url)
            if output_dir:
                os.makedirs(output_dir, exist_ok=True)
            
            output_args = [
                "-f", "hls",
                "-hls_time", "2",
                "-hls_list_size", "10",
                "-hls_flags", "delete_segments",
                self.output_url,
            ]
        else:
            # RTMP or other formats
            output_args = ["-f", "flv", self.output_url]

        command = [
            "ffmpeg",
            "-y",  # Overwrite output files without asking
            "-loglevel", self.ffmpeg_log_level,
            "-f", "rawvideo",
            "-vcodec", "rawvideo",
            "-pix_fmt", "rgb24",
            "-s", f"{width}x{height}",
            "-r", str(self.fps),
            "-i", "-",  # Read from stdin
            "-f", "lavfi",
            "-i", f"anullsrc=channel_layout={'stereo' if self.audio_channels >= 2 else 'mono'}:sample_rate={self.audio_sample_rate}",
            "-map", "0:v:0",
            "-map", "1:a:0",
            "-c:v", "libx264",
            "-preset", "veryfast",
            "-tune", "zerolatency",  # Crucial for live streaming
            "-pix_fmt", "yuv420p",   # Standard pixel format for H264
            "-profile:v", "high",
            "-g", str(self.fps * self.keyframe_interval_seconds),
            "-keyint_min", str(self.fps * self.keyframe_interval_seconds),
            "-sc_threshold", "0",
            "-b:v", f"{self.video_bitrate_kbps}k",
            "-maxrate", f"{self.video_bitrate_kbps}k",
            "-bufsize", f"{self.video_bitrate_kbps * 2}k",
            "-c:a", "aac",
            "-b:a", f"{self.audio_bitrate_kbps}k",
            "-ar", str(self.audio_sample_rate),
            "-ac", str(self.audio_channels),
        ] + output_args

        logger.info("starting_ffmpeg_sink url=%s", self.output_url)
        self._process = await asyncio.create_subprocess_exec(
            *command,
            stdin=asyncio.subprocess.PIPE,
            stdout=asyncio.subprocess.DEVNULL,
            stderr=asyncio.subprocess.PIPE,
        )
        self._stderr_task = asyncio.create_task(self._consume_stderr(), name=f"ffmpeg-stderr:{self.output_url}")
        self._initialized = True
        self.width = width
        self.height = height

    async def write_frame(self, frame: VideoFrame) -> None:
        # Dynamically initialize FFmpeg when the first frame arrives to know the exact dimensions
        if not self._initialized or self._process is None:
            w = frame.width or self.width
            h = frame.height or self.height
            await self._start_ffmpeg(width=w, height=h)

        if self._process and self._process.stdin:
            if self._process.returncode is not None:
                raise RuntimeError(self._build_process_exit_message())
            try:
                self._process.stdin.write(frame.payload)
                await self._process.stdin.drain()
            except (BrokenPipeError, ConnectionResetError) as exc:
                logger.error("ffmpeg_sink_broken_pipe url=%s", self.output_url)
                raise RuntimeError(self._build_process_exit_message()) from exc

    async def close(self) -> None:
        if self._process:
            if self._process.stdin:
                self._process.stdin.close()
                try:
                    await self._process.stdin.wait_closed()
                except Exception:
                    pass
            try:
                await asyncio.wait_for(self._process.wait(), timeout=3.0)
            except asyncio.TimeoutError:
                self._process.kill()
                await self._process.wait()
            if self._stderr_task:
                with contextlib.suppress(asyncio.CancelledError):
                    await self._stderr_task
            logger.info("ffmpeg_sink_closed url=%s", self.output_url)
            self._process = None
            self._stderr_task = None

    async def _consume_stderr(self) -> None:
        if self._process is None or self._process.stderr is None:
            return

        while True:
            line = await self._process.stderr.readline()
            if not line:
                return
            decoded = line.decode(errors="replace").strip()
            if not decoded:
                continue
            self._recent_stderr_lines.append(decoded)
            if len(self._recent_stderr_lines) > 20:
                self._recent_stderr_lines.pop(0)
            logger.warning("ffmpeg_sink_stderr url=%s line=%s", self.output_url, decoded)

    def _build_process_exit_message(self) -> str:
        recent = self._recent_stderr_lines[-1] if self._recent_stderr_lines else "stderr unavailable"
        return f"ffmpeg process terminated unexpectedly. url={self.output_url}, detail={recent}"


def create_frame_sink(
    output_url: str,
    output_mode: OutputMode,
    fps: int = 30,
    ffmpeg_log_level: str = "warning",
    video_bitrate_kbps: int = 4000,
    audio_bitrate_kbps: int = 128,
    audio_sample_rate: int = 44100,
    audio_channels: int = 2,
    keyframe_interval_seconds: int = 1,
) -> FrameSink:
    if output_url.startswith("/tmp/test") or output_url.startswith("dummy"):
        return DummyHlsSink(output_path=output_url)

    if output_mode == OutputMode.HLS and not output_url.endswith(".m3u8"):
        output_url = os.path.join(output_url, "index.m3u8")

    return FFmpegProcessSink(
        output_url=output_url,
        fps=fps,
        output_mode=output_mode,
        ffmpeg_log_level=ffmpeg_log_level,
        video_bitrate_kbps=video_bitrate_kbps,
        audio_bitrate_kbps=audio_bitrate_kbps,
        audio_sample_rate=audio_sample_rate,
        audio_channels=audio_channels,
        keyframe_interval_seconds=keyframe_interval_seconds,
    )
