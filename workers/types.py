from dataclasses import dataclass


@dataclass(slots=True)
class VideoFrame:
    pts_us: int
    payload: bytes
    width: int | None = None
    height: int | None = None
    pixel_format: str | None = None
