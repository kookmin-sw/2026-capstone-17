# Gemini Analysis Integration

## Current Server Flow

1. A broadcast ends through `POST /api/v1/broadcasts/{broadcastId}/stop`.
2. The server marks the broadcast as ended.
3. The server automatically creates a `FULL_SUMMARY` analysis job.
4. A default report row is stored immediately.
5. An external worker can later call:
   - `POST /api/v1/broadcasts/{broadcastId}/analysis-jobs/{analysisJobId}/complete`
6. The completion call updates the same job and overwrites the report with final AI results.

## Required Environment Variables

- `GEMINI_API_KEY`
- `GEMINI_MODEL`
- `GEMINI_BASE_URL` (optional, defaults to Google API base URL)

## Recommended Worker Responsibilities

1. Wait until an analysis MP4 is available in S3.
2. Upload or reference the video in Gemini.
3. Generate these outputs:
   - `summary`
   - `strengths`
   - `weaknesses`
   - `actionItems`
   - `viewerPeakInsight`
   - `faceStatistics`
   - `contentRatios`
4. Call the Spring completion endpoint with the final payload.

## Internal API For FastAPI Worker

- Get latest summary job:
  - `GET /internal/broadcasts/{broadcastId}/analysis-jobs/latest`
- Complete a job:
  - `POST /internal/broadcasts/{broadcastId}/analysis-jobs/{analysisJobId}/complete`
- Required header:
  - `X-Internal-Api-Key: {INTERNAL_API_KEY}`

## Completion Payload Example

```json
{
  "storageUrl": "https://bucket.s3.ap-northeast-2.amazonaws.com/broadcasts/01ABC/archive/analysis.mp4",
  "durationSec": 14400,
  "summary": "오늘 방송은 이동형 IRL 중심으로 진행되었고 카페 소통 구간의 체류 시간이 길었습니다.",
  "strengths": [
    "현장감 있는 이동형 구간이 강점이었습니다."
  ],
  "weaknesses": [
    "오프닝 구간이 다소 길었습니다."
  ],
  "actionItems": [
    "최고 반응 장면 직후 3초 정도 더 머물러 보세요."
  ],
  "viewerPeakInsight": {
    "peakViewerCount": 500,
    "occurredAt": "2026-04-27T14:15:00",
    "sceneDescription": "탕후루 먹방을 진행하며 시청 반응이 가장 크게 올라간 시점입니다."
  },
  "faceStatistics": {
    "totalReplacedFaceCount": 342,
    "maxSimultaneousCrowdCount": 12
  },
  "contentRatios": [
    {
      "contentType": "이동",
      "percentage": 45.0,
      "durationSec": 6480
    },
    {
      "contentType": "카페 소통",
      "percentage": 30.0,
      "durationSec": 4320
    },
    {
      "contentType": "식사",
      "percentage": 25.0,
      "durationSec": 3600
    }
  ]
}
```

## Recommended Next Step

- Keep Gemini invocation in a separate worker first.
- Let Spring own:
  - job creation
  - job completion
  - report persistence
- Let the worker own:
  - MP4 readiness check
  - Gemini upload/call
  - structured output transformation
