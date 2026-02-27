from typing import Protocol

from workers.types import VideoFrame


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
