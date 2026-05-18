import asyncio
import logging
import os
from typing import Optional, Protocol

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
        is_hls: bool = True,
        hls_time: float = 2.0,
        hls_list_size: int = 10,
        hls_flags: str = "delete_segments",
        audio_bitrate: str = "128k",
        audio_sample_rate: int = 44100,
        audio_channels: int = 2,
        audio_source_url: str | None = None,
        video_bitrate: str = "2500k",
        maxrate: str = "2500k",
        bufsize: str = "5000k",
        gop_seconds: int = 1,
    ) -> None:
        self.output_url = output_url
        self.fps = fps
        self.width = width
        self.height = height
        self.is_hls = is_hls
        self.hls_time = max(float(hls_time), 0.5)
        self.hls_list_size = max(int(hls_list_size), 3)
        self.hls_flags = hls_flags
        self.audio_bitrate = audio_bitrate
        self.audio_sample_rate = int(audio_sample_rate)
        self.audio_channels = int(audio_channels)
        self.audio_source_url = audio_source_url
        self.video_bitrate = video_bitrate
        self.maxrate = maxrate
        self.bufsize = bufsize
        self.gop_seconds = max(int(gop_seconds), 1)
        self._process: Optional[asyncio.subprocess.Process] = None
        self._stderr_task: asyncio.Task[None] | None = None
        self._initialized = False

    async def _start_ffmpeg(self, width: int, height: int) -> None:
        if self.is_hls:
            output_dir = os.path.dirname(self.output_url)
            if output_dir:
                os.makedirs(output_dir, exist_ok=True)

            output_args = [
                "-f", "hls",
                "-hls_time", str(self.hls_time),
                "-hls_list_size", str(self.hls_list_size),
                "-hls_flags", self.hls_flags,
                self.output_url,
            ]
        else:
            output_args = ["-f", "flv", self.output_url]

        command = [
            "ffmpeg",
            "-y",
            "-loglevel", "warning",
            "-f", "rawvideo",
            "-vcodec", "rawvideo",
            "-pix_fmt", "rgb24",
            "-s", f"{width}x{height}",
            "-r", str(self.fps),
            "-i", "-",
        ]
        if self.audio_source_url:
            command += self._build_audio_source_input_args(self.audio_source_url)
        else:
            command += [
                "-f", "lavfi",
                "-i", f"anullsrc=channel_layout=stereo:sample_rate={self.audio_sample_rate}",
            ]

        gop = max(self.fps * self.gop_seconds, 1)
        command += [
            "-map", "0:v:0",
            "-map", "1:a:0",
            "-c:v", "libx264",
            "-preset", "veryfast",
            "-tune", "zerolatency",
            "-pix_fmt", "yuv420p",
            "-b:v", self.video_bitrate,
            "-maxrate", self.maxrate,
            "-bufsize", self.bufsize,
            "-g", str(gop),
            "-keyint_min", str(gop),
            "-sc_threshold", "0",
            "-c:a", "aac",
            "-b:a", self.audio_bitrate,
            "-ar", str(self.audio_sample_rate),
            "-ac", str(self.audio_channels),
            "-shortest",
        ] + output_args

        logger.info("starting_ffmpeg_sink url=%s", self.output_url)
        self._process = await asyncio.create_subprocess_exec(
            *command,
            stdin=asyncio.subprocess.PIPE,
            stdout=asyncio.subprocess.DEVNULL,
            stderr=asyncio.subprocess.PIPE,
        )
        self._stderr_task = asyncio.create_task(self._drain_stderr())
        self._initialized = True
        self.width = width
        self.height = height

    def _build_audio_source_input_args(self, audio_source_url: str) -> list[str]:
        args = ["-thread_queue_size", "512"]
        if audio_source_url.startswith("rtsp://"):
            # The video track is already consumed by PyAV for avatar rendering.
            # Ask FFmpeg to subscribe only to RTSP audio so MediaMTX does not
            # queue video frames for this secondary reader.
            args += ["-rtsp_transport", "tcp", "-allowed_media_types", "audio"]
        return args + ["-i", audio_source_url]

    async def write_frame(self, frame: VideoFrame) -> None:
        if not self._initialized or self._process is None:
            w = frame.width or self.width
            h = frame.height or self.height
            await self._start_ffmpeg(width=w, height=h)

        if self._process and self._process.stdin:
            try:
                self._process.stdin.write(frame.payload)
                await self._process.stdin.drain()
            except ConnectionResetError:
                logger.error("ffmpeg_sink_broken_pipe url=%s", self.output_url)

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
                await self._stderr_task
                self._stderr_task = None
            logger.info("ffmpeg_sink_closed url=%s", self.output_url)
            self._process = None

    async def _drain_stderr(self) -> None:
        if self._process is None or self._process.stderr is None:
            return
        while True:
            line = await self._process.stderr.readline()
            if not line:
                return
            logger.warning(
                "ffmpeg_sink_stderr url=%s detail=%s",
                self.output_url,
                line.decode(errors="replace").strip(),
            )


def create_frame_sink(
    output_path: str,
    fps: int = 30,
    hls_time: float = 2.0,
    hls_list_size: int = 10,
    hls_flags: str = "delete_segments",
    audio_bitrate: str = "128k",
    audio_sample_rate: int = 44100,
    audio_channels: int = 2,
    audio_source_url: str | None = None,
    video_bitrate: str = "2500k",
    maxrate: str = "2500k",
    bufsize: str = "5000k",
    gop_seconds: int = 1,
) -> FrameSink:
    if output_path.startswith("/tmp/test") or output_path.startswith("dummy"):
        return DummyHlsSink(output_path=output_path)

    is_hls = output_path.endswith(".m3u8") or not output_path.startswith(("rtmp://", "srt://"))
    if is_hls and not output_path.endswith(".m3u8"):
        output_path = os.path.join(output_path, "index.m3u8")

    return FFmpegProcessSink(
        output_url=output_path,
        fps=fps,
        is_hls=is_hls,
        hls_time=hls_time,
        hls_list_size=hls_list_size,
        hls_flags=hls_flags,
        audio_bitrate=audio_bitrate,
        audio_sample_rate=audio_sample_rate,
        audio_channels=audio_channels,
        audio_source_url=audio_source_url,
        video_bitrate=video_bitrate,
        maxrate=maxrate,
        bufsize=bufsize,
        gop_seconds=gop_seconds,
    )
