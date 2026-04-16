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
    ) -> None:
        self.output_url = output_url
        self.fps = fps
        self.width = width
        self.height = height
        self.is_hls = is_hls
        self._process: Optional[asyncio.subprocess.Process] = None
        self._initialized = False

    async def _start_ffmpeg(self, width: int, height: int) -> None:
        if self.is_hls:
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
            output_args = ["-f", "flv", self.output_url]

        command = [
            "ffmpeg",
            "-y",
            "-f", "rawvideo",
            "-vcodec", "rawvideo",
            "-pix_fmt", "rgb24",
            "-s", f"{width}x{height}",
            "-r", str(self.fps),
            "-i", "-",
            "-c:v", "libx264",
            "-preset", "veryfast",
            "-tune", "zerolatency",
            "-pix_fmt", "yuv420p",
        ] + output_args

        logger.info("starting_ffmpeg_sink url=%s", self.output_url)
        self._process = await asyncio.create_subprocess_exec(
            *command,
            stdin=asyncio.subprocess.PIPE,
            stdout=asyncio.subprocess.DEVNULL,
            stderr=asyncio.subprocess.DEVNULL,
        )
        self._initialized = True
        self.width = width
        self.height = height

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
            logger.info("ffmpeg_sink_closed url=%s", self.output_url)
            self._process = None


def create_frame_sink(output_path: str, fps: int = 30) -> FrameSink:
    if output_path.startswith("/tmp/test") or output_path.startswith("dummy"):
        return DummyHlsSink(output_path=output_path)

    is_hls = output_path.endswith(".m3u8") or not output_path.startswith(("rtmp://", "srt://"))
    if is_hls and not output_path.endswith(".m3u8"):
        output_path = os.path.join(output_path, "index.m3u8")

    return FFmpegProcessSink(output_url=output_path, fps=fps, is_hls=is_hls)
