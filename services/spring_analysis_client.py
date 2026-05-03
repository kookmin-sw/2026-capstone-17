import logging
from typing import Any

import httpx

from core.config import Settings
from schemas.analysis import SpringAnalysisCompletePayload

logger = logging.getLogger(__name__)


class SpringAnalysisClient:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings

    async def fetch_latest_job_id(self, broadcast_id: str) -> str:
        if not self._settings.spring_internal_base_url:
            raise RuntimeError("SPRING_INTERNAL_BASE_URL is required.")
        payload = await self._request(
            "GET",
            f"/internal/broadcasts/{broadcast_id}/analysis-jobs/latest",
        )
        job_id = self._extract_job_id(payload)
        if not job_id:
            raise RuntimeError(f"analysis job id not found in Spring response. payload={payload}")
        return job_id

    async def complete_job(
        self,
        broadcast_id: str,
        analysis_job_id: str,
        payload: SpringAnalysisCompletePayload,
    ) -> None:
        await self._request(
            "POST",
            f"/internal/broadcasts/{broadcast_id}/analysis-jobs/{analysis_job_id}/complete",
            json=payload.model_dump(),
        )
        logger.info(
            "spring_analysis_complete_sent broadcast_id=%s analysis_job_id=%s",
            broadcast_id,
            analysis_job_id,
        )

    async def _request(self, method: str, path: str, **kwargs) -> dict[str, Any]:
        headers = {"X-Internal-Api-Key": self._settings.internal_api_key or ""}
        base_url = (self._settings.spring_internal_base_url or "").rstrip("/")
        async with httpx.AsyncClient(
            base_url=base_url,
            timeout=self._settings.spring_internal_timeout_sec,
            headers=headers,
        ) as client:
            response = await client.request(method, path, **kwargs)
            response.raise_for_status()
            if not response.content:
                return {}
            return response.json()

    def _extract_job_id(self, payload: dict[str, Any]) -> str | None:
        candidates = [
            payload,
            payload.get("data") if isinstance(payload.get("data"), dict) else None,
            payload.get("result") if isinstance(payload.get("result"), dict) else None,
        ]
        for candidate in candidates:
            if not candidate:
                continue
            for key in ("analysisJobId", "analysis_job_id", "id", "jobId"):
                value = candidate.get(key)
                if value is not None:
                    return str(value)
        return None
