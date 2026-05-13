import asyncio
import logging
from collections.abc import Awaitable, Callable

from core.config import Settings
from schemas.analysis import GeminiAnalysisResult, SpringAnalysisCompletePayload, SpringAnalysisContext
from services.analysis_archive import AnalysisArchiveService
from services.gemini_analyzer import GeminiVideoAnalyzer
from services.s3_storage import S3StorageClient
from services.spring_analysis_client import SpringAnalysisClient
from workers.pipeline import StreamPipeline

logger = logging.getLogger(__name__)


class AnalysisWorkflow:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._archive = AnalysisArchiveService(settings)
        self._storage = S3StorageClient(settings)
        self._gemini = GeminiVideoAnalyzer(settings)
        self._spring = SpringAnalysisClient(settings)

    async def run_for_pipeline(self, pipeline: StreamPipeline) -> SpringAnalysisCompletePayload | None:
        if not self._settings.analysis_enabled:
            logger.info("analysis_workflow_skipped broadcast_id=%s reason=disabled", pipeline.broadcast_id)
            return None

        broadcast_id = pipeline.broadcast_id
        logger.info("analysis_workflow_started broadcast_id=%s", broadcast_id)
        try:
            analysis_path = await self._with_retries(
                "analysis_mp4",
                lambda: self._archive.ensure_analysis_mp4(
                    broadcast_id=broadcast_id,
                    hls_path=pipeline.output_path,
                    analysis_path=pipeline.analysis_output_path,
                ),
            )
            duration_sec = await self._with_retries(
                "analysis_duration",
                lambda: self._archive.probe_duration_sec(analysis_path),
            )
            storage_url = await self._with_retries(
                "s3_upload",
                lambda: self._storage.upload_analysis_mp4(broadcast_id, analysis_path),
            )
            analysis_job_id = await self._with_retries(
                "spring_latest_job",
                lambda: self._spring.fetch_latest_job_id(broadcast_id),
            )
            analysis_context = await self._with_retries(
                "spring_analysis_context",
                lambda: self._spring.fetch_analysis_context(broadcast_id),
            )
            gemini_result = await self._with_retries(
                "gemini_analysis",
                lambda: self._gemini.analyze(analysis_path, duration_sec, analysis_context),
            )
            complete_payload = self._build_complete_payload(
                gemini_result=gemini_result,
                analysis_context=analysis_context,
                storage_url=storage_url,
                duration_sec=duration_sec,
            )
            logger.info(
                "analysis_complete_payload_prepared broadcast_id=%s peak_viewer_count=%s occurred_at=%s scene_description_present=%s content_ratio_count=%s",
                broadcast_id,
                complete_payload.viewerPeakInsight.peakViewerCount,
                complete_payload.viewerPeakInsight.occurredAt,
                bool(complete_payload.viewerPeakInsight.sceneDescription),
                len(complete_payload.contentRatios),
            )
            await self._with_retries(
                "spring_complete_job",
                lambda: self._spring.complete_job(broadcast_id, analysis_job_id, complete_payload),
            )
            logger.info("analysis_workflow_completed broadcast_id=%s", broadcast_id)
            return complete_payload
        except Exception:
            logger.exception("analysis_workflow_failed broadcast_id=%s", broadcast_id)
            return None

    async def _with_retries(self, label: str, fn: Callable[[], Awaitable]):
        attempts = max(self._settings.analysis_retry_attempts, 1)
        for attempt in range(1, attempts + 1):
            try:
                return await fn()
            except Exception:
                if attempt >= attempts:
                    raise
                delay = self._settings.analysis_retry_backoff_sec * attempt
                logger.warning(
                    "analysis_step_retrying step=%s attempt=%s/%s delay=%s",
                    label,
                    attempt,
                    attempts,
                    delay,
                    exc_info=True,
                )
                await asyncio.sleep(delay)

    def _build_complete_payload(
        self,
        gemini_result: GeminiAnalysisResult,
        analysis_context: SpringAnalysisContext,
        storage_url: str,
        duration_sec: int,
    ) -> SpringAnalysisCompletePayload:
        payload_data = gemini_result.model_dump()

        context_peak = analysis_context.viewerPeakInsight
        gemini_peak = payload_data.get("viewerPeakInsight") or {}
        merged_peak = {
            **gemini_peak,
            "sceneDescription": gemini_peak.get("sceneDescription") or context_peak.sceneDescription,
        }
        if context_peak.peakViewerCount is not None:
            merged_peak["peakViewerCount"] = context_peak.peakViewerCount
        if context_peak.occurredAt is not None:
            merged_peak["occurredAt"] = context_peak.occurredAt
        payload_data["viewerPeakInsight"] = merged_peak

        if analysis_context.contentRatios:
            payload_data["contentRatios"] = [
                ratio.model_dump() for ratio in analysis_context.contentRatios
            ]

        return SpringAnalysisCompletePayload(
            **payload_data,
            storageUrl=storage_url,
            durationSec=duration_sec,
        )
