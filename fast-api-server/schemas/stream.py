from enum import Enum

from pydantic import BaseModel, ConfigDict, Field, model_validator


class StreamState(str, Enum):
    STARTING = "starting"
    RUNNING = "running"
    STOPPING = "stopping"
    STOPPED = "stopped"
    FAILED = "failed"


class OutputMode(str, Enum):
    HLS = "HLS"
    CHZZK_RTMP = "CHZZK_RTMP"


class StreamStartRequest(BaseModel):
    broadcast_id: str = Field(
        min_length=1,
        max_length=100,
        description="Spring broadcast.id",
    )
    input_stream_key: str | None = Field(
        default=None,
        min_length=1,
        max_length=100,
        description="Spring broadcast.streamKey used for internal MediaMTX ingest.",
    )
    stream_key: str | None = Field(
        default=None,
        min_length=1,
        max_length=100,
        description="Deprecated alias for input_stream_key.",
    )
    avatar_id: str | None = Field(default=None, max_length=100)
    input_url: str | None = Field(
        default=None,
        description="Debug override for MediaMTX read URL.",
    )
    output_mode: OutputMode | None = Field(
        default=None,
        description="Output target mode. Defaults to server setting.",
    )
    output_url: str | None = Field(
        default=None,
        description="Output URL or file path. Required for RTMP outputs.",
    )
    watch_url: str | None = Field(
        default=None,
        description="External watch URL for the target platform.",
    )
    output_path: str | None = Field(
        default=None,
        description="Deprecated alias for HLS output path.",
    )

    @model_validator(mode="after")
    def validate_keys(self) -> "StreamStartRequest":
        if not self.input_stream_key and not self.stream_key:
            raise ValueError("input_stream_key or stream_key is required.")
        return self

    model_config = ConfigDict(
        json_schema_extra={
            "example": {
                "broadcast_id": "bc_20260227_001",
                "input_stream_key": "live_101_stream_key",
                "avatar_id": "avatar-a",
                "output_mode": "CHZZK_RTMP",
                "output_url": "rtmp://live.example/app/live-key",
                "watch_url": "https://chzzk.naver.com/channel-id",
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
    input_stream_key: str
    stream_key: str
    state: StreamState
    processed_frames: int = Field(default=0, ge=0)
    dropped_frames: int = Field(default=0, ge=0)
    last_pts_us: int | None = Field(default=None, ge=0)
    output_mode: OutputMode
    input_url: str
    output_path: str
    hls_url: str
    output_url: str
    watch_url: str | None = None
    detail: str | None = None

    model_config = ConfigDict(
        json_schema_extra={
            "example": {
                "broadcast_id": "bc_20260227_001",
                "input_stream_key": "live_101_stream_key",
                "stream_key": "live_101_stream_key",
                "state": "running",
                "processed_frames": 1842,
                "dropped_frames": 17,
                "last_pts_us": 61400000,
                "output_mode": "CHZZK_RTMP",
                "input_url": "rtsp://localhost:8554/live/live_101_stream_key",
                "output_path": "rtmp://live.example/app/live-key",
                "hls_url": "https://chzzk.naver.com/channel-id",
                "output_url": "rtmp://live.example/app/live-key",
                "watch_url": "https://chzzk.naver.com/channel-id",
                "detail": None,
            }
        }
    )
