import asyncio

from adapters.metadata_store import RedisMetadataStore
from core.config import Settings
from schemas.stream import StreamStartRequest, StreamStatusResponse
from services.errors import StreamAlreadyRunningError, StreamNotFoundError
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
                raise StreamAlreadyRunningError(
                    f"broadcast_id '{req.broadcast_id}' is already active"
                )

            metadata_store = RedisMetadataStore(
                redis_url=self._settings.redis_url,
                key_template=self._settings.redis_metadata_key_template,
            )
            pipeline = StreamPipeline(
                broadcast_id=req.broadcast_id,
                input_url=req.input_url,
                output_path=req.output_path,
                avatar_id=req.avatar_id,
                fps=self._settings.pipeline_fps,
                max_frame_lag_ms=self._settings.max_frame_lag_ms,
                metadata_store=metadata_store,
            )
            self._pipelines[req.broadcast_id] = pipeline

        await pipeline.start()
        return pipeline.snapshot()

    async def stop_stream(self, broadcast_id: str) -> StreamStatusResponse:
        async with self._lock:
            pipeline = self._pipelines.get(broadcast_id)

        if not pipeline:
            raise StreamNotFoundError(f"broadcast_id '{broadcast_id}' was not found")

        await pipeline.stop()
        return pipeline.snapshot()

    async def get_status(self, broadcast_id: str) -> StreamStatusResponse:
        async with self._lock:
            pipeline = self._pipelines.get(broadcast_id)

        if not pipeline:
            raise StreamNotFoundError(f"broadcast_id '{broadcast_id}' was not found")

        return pipeline.snapshot()
