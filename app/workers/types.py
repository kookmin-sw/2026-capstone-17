from dataclasses import dataclass


@dataclass(slots=True)
class VideoFrame:
    pts_us: int
    payload: bytes
