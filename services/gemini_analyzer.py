import asyncio
import json
import logging
import time
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

        result = await asyncio.to_thread(self._analyze_blocking, video_path, duration_sec, analysis_context)
        logger.info("gemini_analysis_completed video=%s", video_path)
        return result

    def _analyze_blocking(
        self,
        video_path: str,
        duration_sec: int,
        analysis_context: SpringAnalysisContext | None,
    ) -> GeminiAnalysisResult:
        client = genai.Client(api_key=self._settings.gemini_api_key)
        uploaded = client.files.upload(
            file=video_path,
            config=types.UploadFileConfig(mime_type="video/mp4"),
        )
        uploaded = self._wait_until_active(client, uploaded)
        prompt = self._build_prompt(duration_sec=duration_sec, analysis_context=analysis_context)
        response = client.models.generate_content(
            model=self._settings.gemini_model,
            contents=[uploaded, prompt],
            config=types.GenerateContentConfig(
                response_mime_type="application/json",
                response_schema=GeminiAnalysisResult,
            ),
        )
        if getattr(response, "parsed", None):
            return GeminiAnalysisResult.model_validate(response.parsed)
        return GeminiAnalysisResult.model_validate(self._parse_json_text(response.text))

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

    def _wait_until_active(self, client, uploaded_file):
        deadline = time.monotonic() + self._settings.gemini_file_processing_timeout_sec
        current = uploaded_file
        while time.monotonic() < deadline:
            state_name = getattr(getattr(current, "state", None), "name", None)
            if state_name == "ACTIVE":
                return current
            if state_name == "FAILED":
                raise RuntimeError(f"Gemini file processing failed. name={current.name}")
            time.sleep(5)
            current = client.files.get(name=current.name)
        raise RuntimeError(f"Gemini file processing timed out. name={uploaded_file.name}")

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
