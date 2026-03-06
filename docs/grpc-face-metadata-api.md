# Face Metadata gRPC API (Client)

이 문서는 모바일 클라이언트(Android/iOS) 연동 관점의 gRPC 계약만 다룹니다.

## 1) Endpoint Contract
- Package: `focus.metadata.v1`
- Service: `FaceMetadataIngestService`
- RPC: `PushFaceMetadata(stream PushFaceMetadataRequest) returns (PushFaceMetadataResponse)`
- Streaming type: Client Streaming

## 2) Request Schema
`PushFaceMetadataRequest`

| Field | Type | Required | Description |
|---|---|---:|---|
| `session_id` | `string` | Y | 방송/세션 식별자 (`broadcast_id`) |
| `pts_us` | `int64` | Y | 프레임 PTS (microseconds) |
| `avatar_url` | `string` | N | 현재 아바타 리소스 URL |
| `face_data` | `map<string, float>` | N | AU, yaw/pitch/roll 등 표정/포즈 값 |
| `tracking_id` | `string` | N | 얼굴 추적 ID |
| `is_reentry` | `bool` | N | 재진입 여부 |
| `confidence` | `float` | N | 신뢰도 |
| `bbox` | `repeated float` | N | 얼굴 바운딩 박스 좌표 |
| `landmarks` | `repeated Landmark` | N | 랜드마크 |

`Landmark`

| Field | Type |
|---|---|
| `x` | `float` |
| `y` | `float` |
| `z` | `float` |

## 3) Response Schema
`PushFaceMetadataResponse`

| Field | Type | Description |
|---|---|---|
| `session_id` | `string` | 마지막 처리 세션 ID |
| `received_frames` | `int64` | 수신 프레임 수 |
| `accepted_frames` | `int64` | 서버 수락 프레임 수 |
| `dropped_frames` | `int64` | 서버 드롭 프레임 수 |
| `last_pts_us` | `int64` | 마지막 처리 PTS |

## 4) Validation Rules
- `session_id`가 비어 있으면 해당 프레임은 드롭됩니다.
- `pts_us`가 `1` 미만이면 해당 프레임은 드롭됩니다.

## 5) Request Example
```json
{
  "sessionId": "bc_20260227_001",
  "ptsUs": 61433333,
  "avatarUrl": "s3://avatars/a.vrm",
  "faceData": {
    "AU12": 0.61,
    "yaw": 0.12
  },
  "trackingId": "t-102",
  "isReentry": false,
  "confidence": 0.93,
  "bbox": [121, 80, 221, 200],
  "landmarks": [
    { "x": 130.1, "y": 99.8, "z": -0.01 }
  ]
}
```

## 6) Proto Source of Truth
- `src/main/proto/focus/metadata/v1/face_metadata.proto`
