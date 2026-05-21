import asyncio
import logging
from collections.abc import Awaitable, Callable
from dataclasses import dataclass
from typing import Any

from core.config import Settings
from schemas.analysis import GeminiAnalysisResult, SpringAnalysisCompletePayload, SpringAnalysisContext
from schemas.stream import OutputMode
from services.analysis_archive import AnalysisArchiveService
from services.gemini_analyzer import GeminiVideoAnalyzer
from services.s3_storage import S3StorageClient
from services.spring_analysis_client import SpringAnalysisClient
from workers.pipeline import StreamPipeline

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class AnalysisPaths:
    analysis_path: str
    hls_path: str | None


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
        analysis_job_id: str | None = None
        complete_sent = False
        failure_stage = "analysis_start"
        logger.info("analysis_workflow_started broadcast_id=%s", broadcast_id)
        try:
            failure_stage = "spring_latest_job"
            analysis_job_id = await self._with_retries(
                "spring_latest_job",
                lambda: self._spring.fetch_latest_job_id(broadcast_id),
            )
            failure_stage = "spring_analysis_context"
            analysis_context = await self._with_retries(
                "spring_analysis_context",
                lambda: self._spring.fetch_analysis_context(broadcast_id),
            )
            failure_stage = "analysis_paths"
            analysis_paths = self._resolve_analysis_paths(pipeline)
            failure_stage = "analysis_mp4"
            analysis_path = await self._with_retries(
                "analysis_mp4",
                lambda: self._archive.ensure_analysis_mp4(
                    broadcast_id=broadcast_id,
                    hls_path=analysis_paths.hls_path,
                    analysis_path=analysis_paths.analysis_path,
                ),
            )
            failure_stage = "analysis_duration"
            duration_sec = await self._with_retries(
                "analysis_duration",
                lambda: self._archive.probe_duration_sec(analysis_path),
            )
            failure_stage = "s3_upload"
            storage_url = await self._with_retries(
                "s3_upload",
                lambda: self._storage.upload_analysis_mp4(broadcast_id, analysis_path),
            )
            failure_stage = "gemini_analysis"
            gemini_result = await self._gemini.analyze(
                analysis_path,
                duration_sec,
                analysis_context,
            )
            failure_stage = "complete_payload"
            complete_payload = self._build_complete_payload(
                gemini_result=gemini_result,
                analysis_context=analysis_context,
                storage_url=storage_url,
                duration_sec=duration_sec,
            )
            logger.info(
                "analysis_complete_payload_prepared broadcast_id=%s viewer_peak_insight_present=%s peak_viewer_count=%s occurred_at=%s scene_description_present=%s content_ratio_count=%s",
                broadcast_id,
                complete_payload.viewerPeakInsight is not None,
                complete_payload.viewerPeakInsight.peakViewerCount if complete_payload.viewerPeakInsight else None,
                complete_payload.viewerPeakInsight.occurredAt if complete_payload.viewerPeakInsight else None,
                bool(complete_payload.viewerPeakInsight.sceneDescription) if complete_payload.viewerPeakInsight else False,
                len(complete_payload.contentRatios),
            )
            failure_stage = "spring_complete_job"
            await self._with_retries(
                "spring_complete_job",
                lambda: self._spring.complete_job(broadcast_id, analysis_job_id, complete_payload),
            )
            complete_sent = True
            logger.info("analysis_workflow_completed broadcast_id=%s", broadcast_id)
            return complete_payload
        except Exception as exc:
            logger.exception(
                "analysis_workflow_failed broadcast_id=%s stage=%s",
                broadcast_id,
                failure_stage,
            )
            await self._notify_analysis_failed(
                broadcast_id=broadcast_id,
                analysis_job_id=analysis_job_id,
                stage=failure_stage,
                exc=exc,
                complete_sent=complete_sent,
            )
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

    def _resolve_analysis_paths(self, pipeline: StreamPipeline) -> AnalysisPaths:
        analysis_path = getattr(pipeline, "analysis_output_path", None)
        if not analysis_path:
            analysis_path = self._archive.build_analysis_path(pipeline.broadcast_id)

        output_mode = getattr(pipeline, "output_mode", None)
        output_url = getattr(pipeline, "output_url", None)
        hls_path = output_url if output_mode == OutputMode.HLS else None
        logger.info(
            "analysis_paths_resolved broadcast_id=%s output_mode=%s output_url=%s analysis_path=%s hls_path=%s",
            pipeline.broadcast_id,
            getattr(output_mode, "value", output_mode),
            output_url,
            analysis_path,
            hls_path,
        )
        return AnalysisPaths(analysis_path=analysis_path, hls_path=hls_path)

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
        if context_peak is None:
            scene_description = gemini_peak.get("sceneDescription") if isinstance(gemini_peak, dict) else None
            payload_data["viewerPeakInsight"] = (
                {"sceneDescription": scene_description} if scene_description else None
            )
            logger.info("analysis_context_peak_absent_continuing_with_nullable_peak")
        else:
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

    async def _notify_analysis_failed(
        self,
        broadcast_id: str,
        analysis_job_id: str | None,
        stage: str,
        exc: Exception,
        complete_sent: bool,
    ) -> None:
        if complete_sent:
            logger.info(
                "spring_analysis_fail_skipped broadcast_id=%s reason=complete_already_sent stage=%s",
                broadcast_id,
                stage,
            )
            return

        error_message = self._build_failure_error_message(stage, exc)
        if not analysis_job_id:
            logger.warning(
                "spring_analysis_fail_skipped broadcast_id=%s reason=missing_analysis_job_id stage=%s error_message=%s",
                broadcast_id,
                stage,
                error_message,
            )
            return

        try:
            await self._with_retries(
                "spring_fail_job",
                lambda: self._spring.fail_job(broadcast_id, analysis_job_id, error_message),
            )
        except Exception:
            logger.exception(
                "spring_analysis_fail_skipped broadcast_id=%s analysis_job_id=%s reason=fail_api_failed stage=%s error_message=%s",
                broadcast_id,
                analysis_job_id,
                stage,
                error_message,
            )

    def _build_failure_error_message(self, stage: str, exc: Exception) -> str:
        raw_message = self._first_error_line(exc)
        lowered = raw_message.lower()

        if "file processing" in lowered and "timeout" in lowered:
            return "Gemini file processing timeout"
        if "file processing failed" in lowered:
            return self._truncate_error_message(raw_message)

        detail = self._extract_error_code(exc) or raw_message or exc.__class__.__name__
        if stage == "gemini_analysis":
            return self._truncate_error_message(f"Gemini analysis failed: {detail}")
        if stage == "complete_payload":
            return self._truncate_error_message(f"Complete payload build failed: {detail}")
        return self._truncate_error_message(f"Analysis workflow failed at {stage}: {detail}")

    def _first_error_line(self, exc: Exception) -> str:
        message = str(exc).replace("\r", " ").strip()
        if not message:
            return exc.__class__.__name__
        return " ".join(message.splitlines()[0].split())

    def _extract_error_code(self, exc: Exception) -> str | None:
        candidates: list[Any] = [
            getattr(exc, "code", None),
            getattr(exc, "status", None),
            getattr(exc, "reason", None),
        ]
        parts = [str(candidate) for candidate in candidates if candidate]
        if parts:
            return " ".join(parts)
        return None

    def _truncate_error_message(self, message: str) -> str:
        normalized = " ".join(message.split())
        return normalized[:500]
