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
    redis_metadata_latest_key_template: str = "broadcast:{broadcast_id}:meta:latest"
    redis_metadata_index_key_template: str = "broadcast:{broadcast_id}:meta:index"

    pipeline_fps: int = 15
    max_frame_lag_ms: int = 100
    ffmpeg_log_level: str = "warning"
    pipeline_gop_seconds: int = 1
    pipeline_video_bitrate: str = "1200k"
    pipeline_maxrate: str = "1200k"
    pipeline_bufsize: str = "2400k"
    pipeline_max_frame_width: int = 720
    pipeline_max_frame_height: int = 720
    pipeline_x264_preset: str = "ultrafast"
    pipeline_x264_profile: str = "high"
    hls_time: float = 1.0
    hls_list_size: int = 6
    hls_flags: str = "delete_segments+independent_segments+append_list+omit_endlist"
    default_output_mode: str = "HLS"
    input_open_retry_count: int = 5
    input_open_retry_backoff_ms: int = 1000
    output_audio_bitrate: str = "128k"
    output_audio_sample_rate: int = 48000
    output_audio_channels: int = 2
    avatar_rendering_enabled: bool = True
    avatar_project_dir: str | None = "focus-avatar/project"
    avatar_bank_dir: str | None = "focus-avatar/project/avatar_bank"
    avatar_cache_dir: str = "/tmp/focus-avatar-cache"
    avatar_s3_bucket: str | None = None
    avatar_s3_region: str | None = None
    avatar_random_seed: int = 0
    avatar_max_faces_per_frame: int = 2
    avatar_metadata_grace_ms: int = 3000
    avatar_primary_reselect_grace_ms: int = 250
    avatar_person_slot_grace_ms: int = 3000
    avatar_person_slot_match_iou: float = 0.10
    avatar_mosaic_non_selected_faces: bool = False
    metadata_poll_attempts: int = 2
    metadata_poll_interval_ms: int = 10
    metadata_lookup_tolerance_us: int = 150000
    metadata_latest_tolerance_us: int = 3000000
    metadata_lookup_fine_tolerance_us: int = 200
    metadata_lookup_coarse_step_us: int = 500
    metadata_auto_offset_max_us: int = 8000000
    metadata_index_lookup_window_us: int = 200000
    metadata_latest_fallback_window_us: int = 200000

    mediamtx_rtsp_read_base_url: str = "rtsp://localhost:8554"
    mediamtx_path_prefix: str = "live"
    hls_output_root: str = "/tmp/hls"
    hls_public_base_url: str = "http://localhost:8000/hls"

    analysis_enabled: bool = False
    analysis_skip_s3: bool = False
    analysis_output_filename: str = "analysis.mp4"
    analysis_ffmpeg_timeout_sec: int = 300
    analysis_retry_attempts: int = 3
    analysis_retry_backoff_sec: float = 2.0

    s3_bucket: str | None = None
    s3_region: str = "ap-northeast-2"
    s3_public_base_url: str | None = None

    gemini_api_key: str | None = None
    gemini_model: str = "gemini-2.5-flash"
    gemini_fallback_model: str | None = None
    gemini_file_processing_timeout_sec: int = 600
    gemini_upload_attempts: int = 3
    gemini_upload_backoff_initial_sec: float = 2.0
    gemini_upload_backoff_max_sec: float = 10.0
    gemini_file_poll_interval_sec: float = 5.0
    gemini_file_poll_timeout_sec: int = 120
    gemini_generate_attempts: int = 4
    gemini_generate_backoff_initial_sec: float = 5.0
    gemini_generate_backoff_max_sec: float = 20.0

    spring_internal_base_url: str | None = None
    internal_api_key: str | None = None
    spring_internal_timeout_sec: float = 10.0

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )


@lru_cache
def get_settings() -> Settings:
    return Settings()
