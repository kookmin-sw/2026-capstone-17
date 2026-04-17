import asyncio
from urllib.parse import quote

from adapters.metadata_store import RedisMetadataStore
from core.config import Settings
from core.exceptions import ApiException, ErrorTitle
from schemas.stream import StreamStartRequest, StreamStatusResponse
from workers.pipeline import PipelineState, StreamPipeline


class StreamManager:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._pipelines: dict[str, StreamPipeline] = {}
        self._lock = asyncio.Lock()

    async def start_stream(self, req: StreamStartRequest) -> StreamStatusResponse:
        async with self._lock:
            pipeline = self._pipelines.get(req.broadcast_id)
            if pipeline and pipeline.state in {
                PipelineState.STARTING,
                PipelineState.RUNNING,
                PipelineState.STOPPING,
            }:
                raise ApiException(
                    ErrorTitle.BadRequest,
                    f"이미 실행 중인 방송입니다. broadcast_id={req.broadcast_id}",
                )

            metadata_store = RedisMetadataStore(
                redis_url=self._settings.redis_url,
                key_template=self._settings.redis_metadata_key_template,
            )
            input_url = req.input_url or self._build_input_url(req.stream_key)
            output_path = req.output_path or self._build_output_path(req.broadcast_id)
            hls_url = self._build_hls_url(req.broadcast_id)
            try:
                pipeline = StreamPipeline(
                    broadcast_id=req.broadcast_id,
                    stream_key=req.stream_key,
                    input_url=input_url,
                    output_path=output_path,
                    hls_url=hls_url,
                    avatar_id=req.avatar_id,
                    fps=self._settings.pipeline_fps,
                    max_frame_lag_ms=self._settings.max_frame_lag_ms,
                    metadata_store=metadata_store,
                    ffmpeg_log_level=self._settings.ffmpeg_log_level,
                    gop_seconds=self._settings.pipeline_gop_seconds,
                    video_bitrate=self._settings.pipeline_video_bitrate,
                    maxrate=self._settings.pipeline_maxrate,
                    bufsize=self._settings.pipeline_bufsize,
                    hls_time=self._settings.hls_time,
                    hls_list_size=self._settings.hls_list_size,
                    hls_flags=self._settings.hls_flags,
                )
            except RuntimeError as exc:
                raise ApiException(ErrorTitle.BadRequest, str(exc)) from exc
            self._pipelines[req.broadcast_id] = pipeline

        await pipeline.start()
        return pipeline.snapshot()

    async def stop_stream(self, broadcast_id: str) -> StreamStatusResponse:
        async with self._lock:
            pipeline = self._pipelines.get(broadcast_id)

        if not pipeline:
            raise ApiException(
                ErrorTitle.NotFoundBroadcast,
                f"존재하지 않는 방송입니다. broadcast_id={broadcast_id}",
            )

        await pipeline.stop()
        return pipeline.snapshot()

    async def get_status(self, broadcast_id: str) -> StreamStatusResponse:
        async with self._lock:
            pipeline = self._pipelines.get(broadcast_id)

        if not pipeline:
            raise ApiException(
                ErrorTitle.NotFoundBroadcast,
                f"존재하지 않는 방송입니다. broadcast_id={broadcast_id}",
            )

        return pipeline.snapshot()

    def _build_input_url(self, stream_key: str) -> str:
        sanitized_stream_key = quote(stream_key, safe="")
        prefix = self._settings.mediamtx_path_prefix.strip("/")
        return f"{self._settings.mediamtx_rtsp_read_base_url.rstrip('/')}/{prefix}/{sanitized_stream_key}"

    def _build_output_path(self, broadcast_id: str) -> str:
        return f"{self._settings.hls_output_root.rstrip('/')}/{broadcast_id}/index.m3u8"

    def _build_hls_url(self, broadcast_id: str) -> str:
        return f"{self._settings.hls_public_base_url.rstrip('/')}/{broadcast_id}/index.m3u8"
