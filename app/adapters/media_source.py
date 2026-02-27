import asyncio
from typing import Protocol

from app.workers.types import VideoFrame


class MediaSource(Protocol):
    async def read_frame(self) -> VideoFrame | None:
        ...

    async def close(self) -> None:
        ...


class DummyMediaSource:
    """Placeholder source until PyAV-based MediaMTX integration is wired."""

    def __init__(self, fps: int = 30) -> None:
        self._interval_s = 1.0 / fps
        self._interval_us = int(1_000_000 / fps)
        self._current_pts = 0

    async def read_frame(self) -> VideoFrame:
        await asyncio.sleep(self._interval_s)
        self._current_pts += self._interval_us
        return VideoFrame(pts_us=self._current_pts, payload=b"raw_frame")

    async def close(self) -> None:
        return None
