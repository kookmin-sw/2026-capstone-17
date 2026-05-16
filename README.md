# focus-fast-api

캡스톤 영상 처리 파이프라인에서 FastAPI 워커가 담당하는 역할과 현재 구현 상태를 정리한 문서입니다.

## 1. 현재 구현 요약

현재 레포는 운영 연동 직전 단계의 FastAPI 스켈레톤입니다.

- 내부 제어 API 구현
  - `GET /healthz`
  - `POST /api/stream/start`
  - `POST /api/stream/stop`
  - `GET /api/stream/{broadcast_id}/status`
- 방송 단위 백그라운드 워커 관리
  - 중복 시작 방지
  - 상태 추적 (`starting`, `running`, `stopping`, `stopped`, `failed`)
- 파이프라인 루프 핵심 로직
  - **기본 릴레이:** `avatar_id`가 없거나 `AVATAR_RENDERING_ENABLED=false`이면 FFmpeg가 RTSP 입력을 바로 HLS/RTMP로 중계
  - **아바타 렌더링:** `avatar_id`가 있으면 PyAV로 프레임을 디코딩하고, Redis 메타데이터를 읽어 `AvatarRenderer`로 합성 후 FFmpeg stdin으로 송출
  - **메타데이터 대기:** 네트워크 지연을 고려해 기본 10ms 단위로 최대 3회(30ms) 메타데이터를 기다림
  - **FFmpeg 송출:** 합성된 원시(Raw) RGB 프레임을 FFmpeg 프로세스(stdin)로 파이프라이닝하여 HLS 파일 생성 또는 RTMP로 실시간 재송출 (`zerolatency` 적용)
  - **메모리 최적화:** Redis에서 읽어온 메타데이터는 즉각 삭제 처리
- 지연 프레임 드랍 정책
  - `MAX_FRAME_LAG_MS` 초과 시 과거 프레임 drop
- 장애 내성 기본 처리
  - Redis 조회 실패/타임아웃 시 `face_metadata=None`으로 진행
  - 렌더링 예외 시 `emergency_fallback()` (블러 또는 원본 통과)

기본 FFmpeg 릴레이 경로와 아바타 합성 경로가 분리되어 있으며, 아바타 합성은 vendored `focus-avatar` 런타임을 `model/renderer.py`에서 호출합니다.

## 2. FastAPI 역할 (R&R)

FastAPI는 외부 클라이언트 API 서버가 아니라 Spring Boot가 제어하는 내부 영상 워커입니다.

- Spring Boot의 시작/중지 명령 수신
- MediaMTX 입력(SRT publish, RTSP pull)을 프레임 단위 처리
- Redis에서 `broadcast_id + pts_us` 기준 메타데이터 조회
- 모델러 함수로 프레임 합성
- HLS(.m3u8/.ts) 생성/저장

## 3. Spring Boot와 상호작용

원칙: 외부 클라이언트와의 통신은 Spring Boot가 담당하고, FastAPI는 내부 제어 API만 노출합니다.

1. 방송 시작: Spring Boot -> `POST /api/stream/start`
2. FastAPI가 `broadcast_id` 워커 생성/실행
3. 상태 확인: Spring Boot -> `GET /api/stream/{broadcast_id}/status`
4. 방송 종료: Spring Boot -> `POST /api/stream/stop`
5. FastAPI가 워커 종료

Spring이 넘겨야 하는 최소 정보:

- `broadcast_id`
- `stream_key`
- `avatar_id` (선택)
- `avatar_asset_key` (선택, Spring RDB `avatar.object_key`에서 조회한 S3 prefix 또는 zip key)

### 시작 요청 예시

```json
{
  "broadcast_id": "bc_20260227_001",
  "stream_key": "live_101_stream_key",
  "avatar_id": "01KP_AVATAR_ID",
  "avatar_asset_key": "avatars/01KP_AVATAR_ID/"
}
```

기본 동작:

- 입력 URL: `rtsp://localhost:8554/live/{stream_key}`
- HLS 출력 파일: `/tmp/hls/{broadcast_id}/index.m3u8`
- HLS 접근 URL: `http://localhost:8000/hls/{broadcast_id}/index.m3u8`

디버그용으로만 `input_url`과 `output_path`를 직접 오버라이드할 수 있습니다. `input_url`에 `dummy://stream`을 넣으면 더미 입력 소스를 사용할 수 있습니다.

### 종료 요청 예시

```json
{
  "broadcast_id": "bc_20260227_001"
}
```

## 4. 모델러 함수와 상호작용

`model/renderer.py`의 `AvatarRenderer`가 `focus-avatar/project`의 reenact 런타임을 호출합니다.

실제 운영 플로우에서는 Spring이 PostgreSQL `avatar` 테이블을 조회해 `avatar_id`와 `object_key`를 함께 넘깁니다. FastAPI는 `avatar_asset_key`가 있으면 해당 S3 object/prefix를 `AVATAR_CACHE_DIR/{avatar_id}`로 내려받아 로컬 avatar bank로 사용합니다. 로컬 개발처럼 `avatar_asset_key`가 없으면 `AVATAR_BANK_DIR`에 이미 있는 샘플 bank를 그대로 사용합니다.

- 입력
  - `frame` (원본 프레임)
  - `face_metadata` (Redis JSON, 없을 수 있음)
  - `avatar_id` (Spring RDB의 `avatar.avatar_id`, 또는 로컬 bank 폴더명)
  - `avatar_asset_key` (S3에 올라간 avatar bundle zip 또는 prefix)
- 출력
  - 합성 완료 프레임 (`VideoFrame`)

현재 렌더러가 실제로 소비하는 얼굴 메타데이터 필드:

```json
{
  "faces": [
    {
      "tracking_id": 0,
      "bbox": { "x": 659, "y": 177, "width": 49, "height": 64 },
      "tdmm_raw": { "coeffs": [0.0] }
    }
  ]
}
```

`trackingId`, `tdmmRaw` camelCase 입력도 렌더러에서 정규화합니다. `tdmm_raw.coeffs`는 Qualcomm FaceMap 3DMM 264차원 계수여야 합니다.

fallback 정책:

- Redis 데이터 없음/손상: `face_metadata=None`
- 렌더링 의존성/자산 로딩 실패 또는 프레임별 합성 예외: `emergency_fallback(frame)`으로 원본 프레임 통과
- `avatar_id`가 없거나 `AVATAR_RENDERING_ENABLED=false`: 기존 FFmpeg relay 경로 사용

아바타 렌더링 관련 환경변수:

- `AVATAR_RENDERING_ENABLED` (기본값: `true`)
- `AVATAR_PROJECT_DIR` (기본값: `focus-avatar/project`)
- `AVATAR_BANK_DIR` (기본값: `focus-avatar/project/avatar_bank`)
- `AVATAR_CACHE_DIR` (기본값: `/tmp/focus-avatar-cache`)
- `AVATAR_S3_BUCKET` (선택, 없으면 `S3_BUCKET` 사용)
- `AVATAR_S3_REGION` (선택, 없으면 `S3_REGION` 사용)
- `AVATAR_RANDOM_SEED` (기본값: `0`)
- `METADATA_POLL_ATTEMPTS` (기본값: `3`)
- `METADATA_POLL_INTERVAL_MS` (기본값: `10`)
- `METADATA_LOOKUP_TOLERANCE_US` (기본값: `5000`)
- `METADATA_LOOKUP_FINE_TOLERANCE_US` (기본값: `100`)
- `METADATA_LOOKUP_COARSE_STEP_US` (기본값: `500`)

`METADATA_LOOKUP_*` 설정은 클라이언트 gRPC `pts_us`와 PyAV/RTSP 프레임 PTS가 마이크로초 단위로 아주 조금 어긋나는 경우를 보정합니다. 다만 서로 다른 클럭을 쓰는 큰 오차까지 맞추는 기능은 아니므로 클라이언트는 반드시 영상 프레임 PTS 기준으로 metadata를 보내야 합니다.

## 5. 예외 응답 규칙 (focus-server 통일)

실패 응답은 `focus-server`와 동일한 포맷을 사용합니다.

```json
{
  "success": false,
  "message": "존재하지 않는 방송입니다. broadcast_id=bc_20260227_001",
  "errorTitle": "NotFoundBroadcast",
  "errorCode": 404
}
```

- `errorTitle`: Spring `ErrorTitle` enum 이름과 동일
- `errorCode`: HTTP 상태 코드
- 대표 매핑
  - 요청 검증 실패: `InvalidInputValue` (400)
  - 잘못된 요청: `BadRequest` (400)
  - 방송 미존재: `NotFoundBroadcast` (404)
  - 미정의 엔드포인트: `NotFoundEndpoint` (404)
  - 내부 오류: `InternalServerError` (500)

## 6. Redis 계약(현재 기준)

- URL: `REDIS_URL`
- 키 템플릿: `REDIS_METADATA_KEY_TEMPLATE` (기본값: `broadcast:{broadcast_id}:meta:{pts_us}`)

키 예시:

```text
broadcast:bc_20260227_001:meta:61400000
```

값 예시(JSON 문자열):

```json
{
  "tracking_id": "t-102",
  "bbox": [120, 80, 220, 200],
  "landmarks": [[130, 100], [180, 102]],
  "confidence": 0.94,
  "avatar_id": "avatar-a"
}
```

## 7. Docker Compose (로컬 Redis + MediaMTX)

- 파일: `docker-compose.yaml`
- Redis 설정 파일: `infra/redis/redis.conf`

실행:

```bash
docker compose up -d
```

상태 확인:

```bash
docker compose ps
docker compose logs -f redis mediamtx
```

SRT 송출 테스트:

```bash
ffmpeg -re -f lavfi -i testsrc=size=640x360:rate=30 \
-c:v libx264 -preset veryfast -tune zerolatency \
-f mpegts "srt://127.0.0.1:8890?streamid=publish:live/101"
```

참고: macOS Homebrew 기본 `ffmpeg`에는 SRT가 빠질 수 있습니다. 이 경우 `ffmpeg-full`을 사용하세요.

FastAPI는 로컬에서 직접 실행:

```bash
uvicorn main:app --reload
```

라이브 RTMP/SRT 입력(PyAV)까지 테스트할 경우:

```bash
pip install -r requirements.media.txt
```

아바타 합성까지 테스트할 경우:

```bash
pip install -r requirements.media.txt
pip install -r requirements.avatar.txt
```

Swagger:

- `http://localhost:8000/swagger`
- `http://localhost:8000/redoc`
- `http://localhost:8000/openapi.json`

## 8. 로컬 실행

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
# 라이브 입력 테스트 시에만 추가
# pip install -r requirements.media.txt
uvicorn main:app --reload
```

## 9. 다음 구현 우선순위

1. **아바타 합성 검증:** Spring gRPC 메타데이터 + MediaMTX 입력으로 `avatar_id=avatar_001` 형태의 라이브 합성 E2E 확인 및 품질 튜닝
2. **배포 환경 구성:** 생성된 HLS 파일을 Nginx 볼륨 마운트로 서빙하거나, Fluentd/데몬을 통한 AWS S3 동기화 및 CDN 연동 아키텍처 확정
3. **부하 테스트:** 다중 방송 동시 처리 시 프레임 드랍 정책(`max_frame_lag_ms`) 및 지터 버퍼 값 튜닝
4. **로깅 및 모니터링:** Prometheus/Grafana 등과 연동하여 워커 파이프라인의 실시간 FPS 및 지연 메트릭 수집

## 10. 배포 메모

- 로컬/E2E 단계에서는 S3가 필요하지 않습니다.
- 현재 구조는 FastAPI가 생성한 HLS를 `/hls/...`로 직접 서빙합니다.
- EC2 초기 배포도 동일하게 파일시스템 기반 HLS로 시작할 수 있습니다.
- 이후 트래픽이나 보관 요구가 생기면 S3 + CDN 구조로 확장하면 됩니다.

## 11. 추가 문서

- [배포 시크릿 정리](docs/배포-시크릿-정리.md)
- [클라이언트 연동 문서](docs/클라이언트-연동.md)
- [작업 현황](작업현황.md)
