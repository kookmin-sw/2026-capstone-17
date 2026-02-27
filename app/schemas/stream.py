from typing import Literal

from pydantic import BaseModel, Field


class StreamStartRequest(BaseModel):
    stream_id: str = Field(min_length=1, max_length=100)
    input_url: str = Field(description="MediaMTX ingest URL (e.g. srt://...).")
    output_path: str = Field(description="HLS output target path.")
    avatar_id: str | None = Field(default=None, max_length=100)


class StreamStopRequest(BaseModel):
    stream_id: str = Field(min_length=1, max_length=100)


class StreamStatusResponse(BaseModel):
    stream_id: str
    state: Literal["starting", "running", "stopping", "stopped", "failed"]
    processed_frames: int = 0
    dropped_frames: int = 0
    last_pts_us: int | None = None
    detail: str | None = None
