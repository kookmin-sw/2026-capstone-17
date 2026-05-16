import asyncio
import logging
from urllib.parse import quote

from adapters.metadata_store import RedisMetadataStore
from core.config import Settings
from core.exceptions import ApiException, ErrorTitle
from schemas.stream import OutputMode, StreamStartRequest, StreamStatusResponse
from services.analysis_archive import AnalysisArchiveService
from services.analysis_workflow import AnalysisWorkflow
from services.avatar_assets import AvatarAssetResolver
from workers.pipeline import PipelineState, StreamPipeline

logger = logging.getLogger(__name__)


class StreamManager:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._pipelines: dict[str, StreamPipeline] = {}
        self._analysis_tasks: dict[str, asyncio.Task[None]] = {}
        self._analysis_archive = AnalysisArchiveService(settings)
        self._analysis_workflow = AnalysisWorkflow(settings)
        self._avatar_assets = AvatarAssetResolver(settings)
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
                lookup_tolerance_us=self._settings.metadata_lookup_tolerance_us,
                fine_tolerance_us=self._settings.metadata_lookup_fine_tolerance_us,
                coarse_step_us=self._settings.metadata_lookup_coarse_step_us,
            )
            input_stream_key = req.input_stream_key or req.stream_key
            if input_stream_key is None:
                raise ApiException(ErrorTitle.BadRequest, "input_stream_key 또는 stream_key 가 필요합니다.")

            output_mode = req.output_mode or self._default_output_mode()
            input_url = req.input_url or self._build_input_url(input_stream_key)
            output_url = req.output_url or req.output_path or self._build_output_url(
                broadcast_id=req.broadcast_id,
                output_mode=output_mode,
            )
            watch_url = req.watch_url or (
                self._build_hls_url(req.broadcast_id) if output_mode == OutputMode.HLS else None
            )
            analysis_output_path = self._analysis_archive.build_analysis_path(req.broadcast_id)
            avatar_bank_dirs = await self._prepare_avatar_bank_dirs(req)

            try:
                pipeline = StreamPipeline(
                    broadcast_id=req.broadcast_id,
                    input_stream_key=input_stream_key,
                    input_url=input_url,
                    output_mode=output_mode,
                    output_url=output_url,
                    watch_url=watch_url,
                    avatar_id=req.avatar_id,
                    fps=self._settings.pipeline_fps,
                    max_frame_lag_ms=self._settings.max_frame_lag_ms,
                    metadata_store=metadata_store,
                    avatar_rendering_enabled=self._settings.avatar_rendering_enabled,
                    avatar_project_dir=self._settings.avatar_project_dir,
                    avatar_bank_dir=avatar_bank_dirs,
                    avatar_asset_resolver=self._avatar_assets,
                    avatar_random_seed=self._settings.avatar_random_seed,
                    metadata_poll_attempts=self._settings.metadata_poll_attempts,
                    metadata_poll_interval_ms=self._settings.metadata_poll_interval_ms,
                    ffmpeg_log_level=self._settings.ffmpeg_log_level,
                    gop_seconds=self._settings.pipeline_gop_seconds,
                    video_bitrate=self._settings.pipeline_video_bitrate,
                    maxrate=self._settings.pipeline_maxrate,
                    bufsize=self._settings.pipeline_bufsize,
                    hls_time=self._settings.hls_time,
                    hls_list_size=self._settings.hls_list_size,
                    hls_flags=self._settings.hls_flags,
                    analysis_output_path=analysis_output_path,
                    input_open_retry_count=self._settings.input_open_retry_count,
                    input_open_retry_backoff_ms=self._settings.input_open_retry_backoff_ms,
                    output_audio_bitrate=self._settings.output_audio_bitrate,
                    output_audio_sample_rate=self._settings.output_audio_sample_rate,
                    output_audio_channels=self._settings.output_audio_channels,
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
        self._schedule_analysis(pipeline)
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

    def _build_input_url(self, input_stream_key: str) -> str:
        sanitized_stream_key = quote(input_stream_key, safe="")
        prefix = self._settings.mediamtx_path_prefix.strip("/")
        return f"{self._settings.mediamtx_rtsp_read_base_url.rstrip('/')}/{prefix}/{sanitized_stream_key}"

    def _build_output_url(self, broadcast_id: str, output_mode: OutputMode) -> str:
        if output_mode == OutputMode.HLS:
            return f"{self._settings.hls_output_root.rstrip('/')}/{broadcast_id}/index.m3u8"
        raise ApiException(
            ErrorTitle.BadRequest,
            f"output_url is required when output_mode={output_mode.value}",
        )

    def _build_hls_url(self, broadcast_id: str) -> str:
        return f"{self._settings.hls_public_base_url.rstrip('/')}/{broadcast_id}/index.m3u8"

    def _default_output_mode(self) -> OutputMode:
        try:
            return OutputMode(self._settings.default_output_mode)
        except ValueError:
            return OutputMode.HLS

    async def _prepare_avatar_bank_dirs(self, req: StreamStartRequest) -> list[str]:
        try:
            prepared_bank_dir = await self._avatar_assets.prepare_avatar_bank(req.avatar_id, req.avatar_asset_key)
        except RuntimeError as exc:
            raise ApiException(ErrorTitle.BadRequest, str(exc)) from exc

        bank_dirs = [
            prepared_bank_dir,
            self._settings.avatar_cache_dir,
            self._settings.avatar_bank_dir,
        ]
        unique_bank_dirs: list[str] = []
        for bank_dir in bank_dirs:
            if bank_dir and bank_dir not in unique_bank_dirs:
                unique_bank_dirs.append(bank_dir)
        return unique_bank_dirs

    def _schedule_analysis(self, pipeline: StreamPipeline) -> None:
        if not self._settings.analysis_enabled:
            return

        existing = self._analysis_tasks.get(pipeline.broadcast_id)
        if existing and not existing.done():
            logger.info("analysis_task_already_running broadcast_id=%s", pipeline.broadcast_id)
            return

        task = asyncio.create_task(
            self._analysis_workflow.run_for_pipeline(pipeline),
            name=f"analysis:{pipeline.broadcast_id}",
        )
        self._analysis_tasks[pipeline.broadcast_id] = task
        task.add_done_callback(lambda _: self._analysis_tasks.pop(pipeline.broadcast_id, None))
