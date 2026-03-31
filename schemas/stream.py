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
    stream_key: str = Field(
        min_length=1,
        max_length=100,
        description="Spring broadcast.streamKey",
    )
    avatar_id: str | None = Field(default=None, max_length=100)
    input_url: str | None = Field(
        default=None,
        description="Debug override for MediaMTX read URL.",
    )
    output_path: str | None = Field(
        default=None,
        description="Debug override for HLS output path.",
    )

    model_config = ConfigDict(
        json_schema_extra={
            "example": {
                "broadcast_id": "bc_20260227_001",
                "stream_key": "live_101_stream_key",
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
    stream_key: str
    state: StreamState
    processed_frames: int = Field(default=0, ge=0)
    dropped_frames: int = Field(default=0, ge=0)
    last_pts_us: int | None = Field(default=None, ge=0)
    input_url: str
    output_path: str
    hls_url: str
    detail: str | None = None

    model_config = ConfigDict(
        json_schema_extra={
            "example": {
                "broadcast_id": "bc_20260227_001",
                "stream_key": "live_101_stream_key",
                "state": "running",
                "processed_frames": 1842,
                "dropped_frames": 17,
                "last_pts_us": 61400000,
                "input_url": "rtsp://localhost:8554/live/live_101_stream_key",
                "output_path": "/tmp/hls/bc_20260227_001/index.m3u8",
                "hls_url": "http://localhost:8000/hls/bc_20260227_001/index.m3u8",
                "detail": None,
            }
        }
    )
