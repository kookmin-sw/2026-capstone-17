import asyncio
import json
import logging
import time
from collections.abc import Iterable
from typing import Any

from core.config import Settings
from schemas.analysis import GeminiAnalysisResult, SpringAnalysisContext

try:
    from google import genai
    from google.genai import types
except ImportError:  # pragma: no cover
    genai = None
    types = None

logger = logging.getLogger(__name__)


ANALYSIS_PROMPT = """
당신은 IRL 라이브 방송 분석가입니다. 첨부된 video/mp4 방송 아카이브를 보고
방송 종료 리포트에 사용할 JSON만 생성하세요.

분석 목표:
- 오늘 방송 요약
- strengths / weaknesses / actionItems
- Spring 컨텍스트의 occurredAt 시각대에 해당하는 장면 설명(sceneDescription)
- 타인 얼굴, 군중, 아바타 치환으로 보이는 장면의 통계 추정

Spring 컨텍스트가 함께 제공되면 peakViewerCount, occurredAt, contentRatios는
Spring 서버가 치지직 API polling으로 계산한 값입니다.
수치 데이터와 카테고리 비율은 임의로 변경하지 말고 전달된 값을 유지하세요.
viewerPeakInsight가 null이면 peak 데이터가 없는 정상 케이스입니다.
이 경우 peakViewerCount와 occurredAt을 추정하지 말고, 일반 장면 요약 수준으로 sceneDescription을 작성하거나 null로 두세요.
viewerPeakInsight가 객체로 제공된 경우 Gemini는 특히 viewerPeakInsight.sceneDescription 생성에 집중하세요.

반드시 아래 JSON 형태만 반환하세요. Markdown 코드블록, 설명 문장, 주석은 금지합니다.
모르는 값은 합리적으로 추정하되, 숫자는 음수가 되면 안 됩니다.
Spring 컨텍스트가 없을 때만 peakViewerCount, occurredAt, contentRatios를 영상 기반으로 추정하세요.

{
  "summary": "string",
  "strengths": ["string"],
  "weaknesses": ["string"],
  "actionItems": ["string"],
  "viewerPeakInsight": {
    "peakViewerCount": 0,
    "occurredAt": null,
    "sceneDescription": "string"
  },
  "faceStatistics": {
    "totalReplacedFaceCount": 0,
    "maxSimultaneousCrowdCount": 0
  },
  "contentRatios": [
    {
      "contentType": "string",
      "percentage": 0.0,
      "durationSec": 0
    }
  ]
}
"""


KOREAN_ANALYSIS_PROMPT = """
당신은 IRL 라이브 방송 분석가입니다. 첨부된 video/mp4 방송 아카이브를 보고
방송 종료 리포트에 사용할 JSON만 생성하세요.

모든 자연어 결과는 반드시 한국어로 작성하세요.
summary, strengths, weaknesses, actionItems, viewerPeakInsight.sceneDescription,
contentRatios.contentType 등 사용자가 읽는 모든 문자열은 한국어여야 합니다.

분석 목표:
- 오늘 방송 요약
- strengths / weaknesses / actionItems
- Spring context의 occurredAt 시각대에 해당하는 장면 설명(sceneDescription)
- 타인 얼굴, 군중, 아바타 치환으로 보이는 장면의 통계 추정

얼굴 마스킹, 얼굴 치환, 얼굴 필터, 아바타 기반 얼굴 대체, 얼굴 변형은
이 서비스의 의도된 핵심 기능일 수 있습니다.
이러한 얼굴 처리 효과만으로 방송 품질 저하나 기술적 결함이라고 판단하지 마세요.
단순히 얼굴이 가려져 있거나 변형되어 있다는 사실만으로 weaknesses에 넣지 마세요.
weaknesses로 분류하려면 화면 전체 해석을 심각하게 방해하거나
시청 경험을 명확히 저해하는 구체적인 근거가 있어야 합니다.
summary에서도 얼굴 처리를 문제 상황처럼 단정하지 말고,
필요하면 프라이버시 보호, 콘셉트 연출, 얼굴 치환 기반 서비스 기능 활용의 맥락으로 설명하세요.
strengths에는 적절한 경우 프라이버시 보호나 얼굴 치환 기반 연출이 자연스럽다는 점을 반영할 수 있습니다.
actionItems에서도 얼굴 처리 기능 자체를 끄라고 제안하지 말고,
실제 송출 품질 저하나 장면 이해 방해가 있을 때만 개선 제안을 작성하세요.

Spring context가 함께 제공되면 peakViewerCount, occurredAt, contentRatios는
Spring 서버가 치지직 API polling으로 계산한 값입니다.
수치 데이터와 카테고리 비율은 임의로 변경하지 말고 전달된 값을 유지하세요.
viewerPeakInsight가 null이면 peak 데이터가 없는 정상 케이스입니다.
이 경우 peakViewerCount와 occurredAt을 추정하지 말고, 일반 장면 요약 수준으로
sceneDescription을 작성하거나 null로 두세요.
viewerPeakInsight가 객체로 제공된 경우에는 occurredAt 주변 장면을 찾아
viewerPeakInsight.sceneDescription 작성에 집중하세요.

반드시 아래 JSON 형태만 반환하세요. Markdown 코드블록, 설명 문장, 주석은 금지입니다.
모르는 값은 합리적으로 추정하되, 숫자에 확신이 없으면 0 또는 null을 사용하세요.
Spring context가 없을 때만 peakViewerCount, occurredAt, contentRatios를 영상 기반으로 추정하세요.

{
  "summary": "string",
  "strengths": ["string"],
  "weaknesses": ["string"],
  "actionItems": ["string"],
  "viewerPeakInsight": {
    "peakViewerCount": 0,
    "occurredAt": null,
    "sceneDescription": "string"
  },
  "faceStatistics": {
    "totalReplacedFaceCount": 0,
    "maxSimultaneousCrowdCount": 0
  },
  "contentRatios": [
    {
      "contentType": "string",
      "percentage": 0.0,
      "durationSec": 0
    }
  ]
}
"""


class GeminiVideoAnalyzer:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings

    async def analyze(
        self,
        video_path: str,
        duration_sec: int,
        analysis_context: SpringAnalysisContext | None = None,
    ) -> GeminiAnalysisResult:
        if genai is None or types is None:
            raise RuntimeError("google-genai is not installed.")
        if not self._settings.gemini_api_key:
            raise RuntimeError("GEMINI_API_KEY is required.")

        result = await asyncio.to_thread(
            self._analyze_blocking,
            video_path,
            duration_sec,
            analysis_context,
        )
        logger.info("gemini_analysis_completed video=%s", video_path)
        return result

    def _analyze_blocking(
        self,
        video_path: str,
        duration_sec: int,
        analysis_context: SpringAnalysisContext | None,
    ) -> GeminiAnalysisResult:
        client = genai.Client(api_key=self._settings.gemini_api_key)
        uploaded = self._upload_file(client, video_path)
        uploaded = self._wait_until_active(client, uploaded)
        prompt = self._build_korean_prompt(duration_sec=duration_sec, analysis_context=analysis_context)
        response = self._generate_content_with_retries(
            client=client,
            uploaded_file=uploaded,
            prompt=prompt,
        )
        if getattr(response, "parsed", None):
            return GeminiAnalysisResult.model_validate(response.parsed)
        return GeminiAnalysisResult.model_validate(self._parse_json_text(response.text))

    def _upload_file(self, client, video_path: str):
        attempts = max(self._settings.gemini_upload_attempts, 1)
        last_exc: Exception | None = None

        for attempt in range(1, attempts + 1):
            logger.info(
                "gemini_file_upload_start video=%s attempt=%s/%s",
                video_path,
                attempt,
                attempts,
            )
            try:
                uploaded = client.files.upload(
                    file=video_path,
                    config=types.UploadFileConfig(mime_type="video/mp4"),
                )
                logger.info(
                    "gemini_file_uploaded file_name=%s state=%s video=%s attempt=%s/%s",
                    getattr(uploaded, "name", None),
                    self._file_state_name(uploaded),
                    video_path,
                    attempt,
                    attempts,
                )
                return uploaded
            except Exception as exc:
                last_exc = exc
                retryable = self._is_retryable_upload_error(exc)
                if not retryable or attempt >= attempts:
                    logger.error(
                        "gemini_file_upload_failed_final video=%s attempt=%s/%s retryable=%s error_type=%s detail=%s",
                        video_path,
                        attempt,
                        attempts,
                        retryable,
                        type(exc).__name__,
                        self._error_detail(exc),
                        exc_info=True,
                    )
                    break

                delay = self._upload_retry_delay(attempt)
                logger.warning(
                    "gemini_file_upload_retrying video=%s attempt=%s/%s delay_sec=%s error_type=%s detail=%s",
                    video_path,
                    attempt,
                    attempts,
                    delay,
                    type(exc).__name__,
                    self._error_detail(exc),
                )
                time.sleep(delay)

        if last_exc is not None:
            raise last_exc
        raise RuntimeError("Gemini file upload failed without an exception.")

    def _build_prompt(
        self,
        duration_sec: int,
        analysis_context: SpringAnalysisContext | None,
    ) -> str:
        prompt = f"{ANALYSIS_PROMPT}\n\n방송 전체 길이(durationSec): {duration_sec}"
        if analysis_context is not None:
            context_json = json.dumps(
                analysis_context.model_dump(exclude_none=True),
                ensure_ascii=False,
                indent=2,
            )
            prompt += (
                "\n\n아래 Spring 분석 컨텍스트를 우선 신뢰하세요."
                "\npeakViewerCount, occurredAt, contentRatios는 변경하지 마세요."
            )
            if analysis_context.viewerPeakInsight is None:
                prompt += (
                    "\nviewerPeakInsight가 null이므로 peak 시각대 설명을 억지로 만들지 마세요."
                    "\n가능하면 전체 방송 기준의 일반 장면 설명을 sceneDescription에 작성하거나 null로 두세요."
                )
            else:
                prompt += "\noccurredAt 주변 장면을 찾아 sceneDescription을 작성하세요."
            prompt += f"\n\nSpring analysis context:\n{context_json}"
        return prompt

    def _build_korean_prompt(
        self,
        duration_sec: int,
        analysis_context: SpringAnalysisContext | None,
    ) -> str:
        prompt = f"{KOREAN_ANALYSIS_PROMPT}\n\n방송 전체 길이(durationSec): {duration_sec}"
        if analysis_context is not None:
            context_json = json.dumps(
                analysis_context.model_dump(exclude_none=True),
                ensure_ascii=False,
                indent=2,
            )
            prompt += (
                "\n\n아래 Spring 분석 context를 우선 신뢰하세요."
                "\npeakViewerCount, occurredAt, contentRatios는 변경하지 마세요."
            )
            if analysis_context.viewerPeakInsight is None:
                prompt += (
                    "\nviewerPeakInsight가 null이므로 peak 시각과 설명을 억지로 만들지 마세요."
                    "\n가능하면 전체 방송 기준의 일반 장면 설명을 sceneDescription에 작성하거나 null로 두세요."
                )
            else:
                prompt += "\noccurredAt 주변 장면을 찾아 sceneDescription을 한국어로 작성하세요."
            prompt += f"\n\nSpring analysis context:\n{context_json}"
        return prompt

    def _wait_until_active(self, client, uploaded_file):
        timeout_sec = self._settings.gemini_file_poll_timeout_sec or self._settings.gemini_file_processing_timeout_sec
        deadline = time.monotonic() + timeout_sec
        interval_sec = max(float(self._settings.gemini_file_poll_interval_sec), 1.0)
        current = uploaded_file
        while time.monotonic() < deadline:
            state_name = self._file_state_name(current)
            logger.info(
                "gemini_file_state_polled file_name=%s state=%s",
                getattr(current, "name", None),
                state_name,
            )
            if state_name == "ACTIVE":
                return current
            if state_name == "FAILED":
                logger.error(
                    "gemini_file_processing_failed file_name=%s state=%s",
                    getattr(current, "name", None),
                    state_name,
                )
                raise RuntimeError(f"Gemini file processing failed. name={current.name}")
            time.sleep(interval_sec)
            current = client.files.get(name=current.name)
        logger.error(
            "gemini_file_processing_timeout file_name=%s timeout_sec=%s last_state=%s",
            getattr(uploaded_file, "name", None),
            timeout_sec,
            self._file_state_name(current),
        )
        raise RuntimeError(f"Gemini file processing timed out. name={uploaded_file.name}")

    def _generate_content_with_retries(self, client, uploaded_file, prompt: str):
        last_exc: Exception | None = None
        file_name = getattr(uploaded_file, "name", None)
        file_state = self._file_state_name(uploaded_file)

        for model in self._candidate_models():
            model_failed_with_retryable_error = False
            for attempt in range(1, max(self._settings.gemini_generate_attempts, 1) + 1):
                logger.info(
                    "gemini_generate_content_start model=%s file_name=%s file_state=%s attempt=%s/%s",
                    model,
                    file_name,
                    file_state,
                    attempt,
                    max(self._settings.gemini_generate_attempts, 1),
                )
                try:
                    return client.models.generate_content(
                        model=model,
                        contents=[uploaded_file, prompt],
                        config=types.GenerateContentConfig(
                            response_mime_type="application/json",
                            response_schema=GeminiAnalysisResult,
                        ),
                    )
                except Exception as exc:
                    last_exc = exc
                    retryable = self._is_retryable_generate_error(exc)
                    if not retryable or attempt >= max(self._settings.gemini_generate_attempts, 1):
                        model_failed_with_retryable_error = retryable
                        has_fallback = (
                            retryable
                            and self._settings.gemini_fallback_model
                            and model != self._settings.gemini_fallback_model
                        )
                        if has_fallback:
                            logger.warning(
                                "gemini_generate_content_model_exhausted model=%s file_name=%s file_state=%s attempt=%s retryable=%s error_type=%s detail=%s",
                                model,
                                file_name,
                                file_state,
                                attempt,
                                retryable,
                                type(exc).__name__,
                                exc,
                            )
                        else:
                            logger.error(
                                "gemini_generate_content_failed_final model=%s file_name=%s file_state=%s attempt=%s retryable=%s error_type=%s detail=%s",
                                model,
                                file_name,
                                file_state,
                                attempt,
                                retryable,
                                type(exc).__name__,
                                exc,
                                exc_info=True,
                            )
                        break

                    delay = self._generate_retry_delay(attempt)
                    logger.warning(
                        "gemini_generate_content_retrying model=%s file_name=%s file_state=%s attempt=%s delay_sec=%s error_type=%s detail=%s",
                        model,
                        file_name,
                        file_state,
                        attempt,
                        delay,
                        type(exc).__name__,
                        exc,
                    )
                    time.sleep(delay)

            if not model_failed_with_retryable_error:
                break

            if model != self._settings.gemini_fallback_model and self._settings.gemini_fallback_model:
                logger.warning(
                    "gemini_generate_content_switching_fallback primary_model=%s fallback_model=%s file_name=%s file_state=%s",
                    model,
                    self._settings.gemini_fallback_model,
                    file_name,
                    file_state,
                )

        if last_exc is not None:
            raise last_exc
        raise RuntimeError("Gemini generateContent failed without an exception.")

    def _candidate_models(self) -> Iterable[str]:
        yield self._settings.gemini_model
        fallback = self._settings.gemini_fallback_model
        if fallback and fallback != self._settings.gemini_model:
            yield fallback

    def _generate_retry_delay(self, attempt: int) -> float:
        initial = max(float(self._settings.gemini_generate_backoff_initial_sec), 0.0)
        max_delay = max(float(self._settings.gemini_generate_backoff_max_sec), initial)
        return min(initial * (2 ** max(attempt - 1, 0)), max_delay)

    def _upload_retry_delay(self, attempt: int) -> float:
        initial = max(float(self._settings.gemini_upload_backoff_initial_sec), 0.0)
        max_delay = max(float(self._settings.gemini_upload_backoff_max_sec), initial)
        return min(initial * (2.5 ** max(attempt - 1, 0)), max_delay)

    def _is_retryable_upload_error(self, exc: Exception) -> bool:
        code = getattr(exc, "code", None)
        status = str(getattr(exc, "status", "") or "").upper()
        message = str(exc).upper()
        if code in {408, 429, 500, 502, 503, 504}:
            return True
        retryable_markers = (
            "UNAVAILABLE",
            "INTERNAL",
            "SERVICE UNAVAILABLE",
            "UPLOAD HAS ALREADY BEEN TERMINATED",
            "TERMINATED",
            "RESUMABLE",
            "TIMEOUT",
            "TIMED OUT",
            "CONNECTION",
        )
        return any(marker in status or marker in message for marker in retryable_markers)

    def _is_retryable_generate_error(self, exc: Exception) -> bool:
        code = getattr(exc, "code", None)
        status = str(getattr(exc, "status", "") or "").upper()
        message = str(exc).upper()
        if code in {429, 500, 502, 503, 504}:
            return True
        return "UNAVAILABLE" in status or "UNAVAILABLE" in message or "HIGH DEMAND" in message

    def _error_detail(self, exc: Exception) -> str:
        detail = str(exc).replace("\r", " ").strip()
        if not detail:
            detail = exc.__class__.__name__
        return " ".join(detail.splitlines()[0].split())[:500]

    @staticmethod
    def _file_state_name(file_obj) -> str | None:
        state = getattr(file_obj, "state", None)
        return getattr(state, "name", None) or (str(state) if state is not None else None)

    @staticmethod
    def _parse_json_text(text: str | None) -> dict[str, Any]:
        if not text:
            raise RuntimeError("Gemini returned an empty response.")
        cleaned = text.strip()
        if cleaned.startswith("```"):
            cleaned = cleaned.strip("`")
            if cleaned.startswith("json"):
                cleaned = cleaned[4:]
        return json.loads(cleaned)
