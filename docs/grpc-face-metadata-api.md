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
| `faces` | `repeated FaceFrame` | N | 프레임 내 얼굴 목록 |

`FaceFrame`

| Field | Type |
|---|---|
| `tracking_id` | `int64` |
| `bbox` | `BoundingBox` |
| `tdmm_raw` | `TdmmRaw` |

`BoundingBox`

| Field | Type |
|---|---|
| `x` | `int32` |
| `y` | `int32` |
| `width` | `int32` |
| `height` | `int32` |

`TdmmRaw`

| Field | Type |
|---|---|
| `coeffs` | `repeated float` |

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
- `pts_us`가 `0` 미만이면 해당 프레임은 드롭됩니다.

## 5) Request Example
```json
{
  "sessionId": "bc_20260227_001",
  "ptsUs": 133333,
  "faces": [
    {
      "trackingId": 0,
      "bbox": {
        "x": 659,
        "y": 177,
        "width": 49,
        "height": 64
      },
      "tdmmRaw": {
        "coeffs": [0.12, -0.05, 0.0, 0.03, 0.01, 0.02]
      }
    }
  ]
}
```

## 6) Proto Source of Truth
- `src/main/proto/focus/metadata/v1/face_metadata.proto`
