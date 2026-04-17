from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = "focus-fast-api"
    app_version: str = "0.1.0"
    app_env: str = "local"
    log_level: str = "INFO"
    api_docs_url: str = "/swagger"
    api_redoc_url: str = "/redoc"
    api_openapi_url: str = "/openapi.json"

    redis_url: str = "redis://localhost:6379/0"
    redis_metadata_key_template: str = "broadcast:{broadcast_id}:meta:{pts_us}"

    pipeline_fps: int = 30
    max_frame_lag_ms: int = 250
    ffmpeg_log_level: str = "warning"
    pipeline_gop_seconds: int = 1
    pipeline_video_bitrate: str = "2500k"
    pipeline_maxrate: str = "2500k"
    pipeline_bufsize: str = "5000k"
    hls_time: float = 1.0
    hls_list_size: int = 6
    hls_flags: str = "delete_segments+independent_segments+append_list+omit_endlist"

    mediamtx_rtsp_read_base_url: str = "rtsp://localhost:8554"
    mediamtx_path_prefix: str = "live"
    hls_output_root: str = "/tmp/hls"
    hls_public_base_url: str = "http://localhost:8000/hls"

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )


@lru_cache
def get_settings() -> Settings:
    return Settings()
