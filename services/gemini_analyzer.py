import asyncio
import json
import logging
import time
from typing import Any

from core.config import Settings
from schemas.analysis import GeminiAnalysisResult

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
- 시청자 반응이 가장 높았을 것으로 보이는 시점과 당시 장면 설명
- 타인 얼굴, 군중, 아바타 치환으로 보이는 장면의 통계 추정
- 이동, 카페 소통, 식사 등 주요 콘텐츠 유형별 방송 비율

반드시 아래 JSON 형태만 반환하세요. Markdown 코드블록, 설명 문장, 주석은 금지합니다.
모르는 값은 합리적으로 추정하되, 숫자는 음수가 되면 안 됩니다.
occurredAt은 실제 절대 시각을 알 수 없으면 null로 둡니다.

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

    async def analyze(self, video_path: str, duration_sec: int) -> GeminiAnalysisResult:
        if genai is None or types is None:
            raise RuntimeError("google-genai is not installed.")
        if not self._settings.gemini_api_key:
            raise RuntimeError("GEMINI_API_KEY is required.")

        result = await asyncio.to_thread(self._analyze_blocking, video_path, duration_sec)
        logger.info("gemini_analysis_completed video=%s", video_path)
        return result

    def _analyze_blocking(self, video_path: str, duration_sec: int) -> GeminiAnalysisResult:
        client = genai.Client(api_key=self._settings.gemini_api_key)
        uploaded = client.files.upload(
            file=video_path,
            config=types.UploadFileConfig(mime_type="video/mp4"),
        )
        uploaded = self._wait_until_active(client, uploaded)
        prompt = f"{ANALYSIS_PROMPT}\n\n방송 전체 길이(durationSec): {duration_sec}"
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
