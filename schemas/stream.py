from enum import Enum

from pydantic import BaseModel, ConfigDict, Field


class StreamState(str, Enum):
    STARTING = "starting"
    RUNNING = "running"
    STOPPING = "stopping"
    STOPPED = "stopped"
    FAILED = "failed"


class StreamStartRequest(BaseModel):
    broadcast_id: str = Field(
        min_length=1,
        max_length=100,
        description="Spring broadcast.id",
    )
    input_url: str = Field(description="MediaMTX ingest URL (e.g. srt://...).")
    output_path: str = Field(description="HLS output target path.")
    avatar_id: str | None = Field(default=None, max_length=100)

    model_config = ConfigDict(
        json_schema_extra={
            "example": {
                "broadcast_id": "bc_20260227_001",
                "input_url": "srt://mediamtx:8890/live/101",
                "output_path": "/var/www/hls/bc_20260227_001",
                "avatar_id": "avatar-a",
            }
        }
    )


class StreamStopRequest(BaseModel):
    broadcast_id: str = Field(min_length=1, max_length=100)

    model_config = ConfigDict(
        json_schema_extra={"example": {"broadcast_id": "bc_20260227_001"}}
    )


class StreamStatusResponse(BaseModel):
    broadcast_id: str
    state: StreamState
    processed_frames: int = Field(default=0, ge=0)
    dropped_frames: int = Field(default=0, ge=0)
    last_pts_us: int | None = Field(default=None, ge=0)
    detail: str | None = None

    model_config = ConfigDict(
        json_schema_extra={
            "example": {
                "broadcast_id": "bc_20260227_001",
                "state": "running",
                "processed_frames": 1842,
                "dropped_frames": 17,
                "last_pts_us": 61400000,
                "detail": None,
            }
        }
    )
