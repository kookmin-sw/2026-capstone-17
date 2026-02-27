# focus-fast-api

캡스톤 영상 처리 파이프라인에서 FastAPI 워커가 담당하는 역할과 현재 구현 상태를 정리한 문서입니다.

## 1. 현재 구현 요약

현재 레포는 "운영 연동 직전 단계의 FastAPI 스켈레톤"까지 구현되어 있습니다.

- FastAPI 앱 구조 분리 완료 (`app/` 패키지)
- 내부 제어 API 구현 완료
  - `GET /healthz`
  - `POST /api/stream/start`
  - `POST /api/stream/stop`
  - `GET /api/stream/{stream_id}/status`
- 스트림 단위 백그라운드 워커 관리 구현 완료
  - 중복 시작 방지
  - 상태 추적 (`starting`, `running`, `stopping`, `stopped`, `failed`)
- 파이프라인 루프 뼈대 구현 완료
  - 프레임 수신 -> Redis 메타데이터 조회 -> 모델 렌더링 -> 출력
- 지연 프레임 드랍 정책 구현 완료
  - `MAX_FRAME_LAG_MS` 초과 시 과거 프레임 drop
- 장애 내성 기본 처리 구현
  - Redis 조회 실패 시 `face_metadata=None` 처리
  - 렌더링 예외 시 `emergency_fallback()` 호출

아직 실제 미디어 처리(PyAV/FFmpeg)와 실제 아바타 합성 로직은 더미 구현입니다.

## 2. FastAPI의 역할 (R&R)

FastAPI는 "클라이언트-facing API 서버"가 아니라, Spring Boot가 제어하는 "내부 영상 워커"입니다.

- Spring Boot의 시작/중지 명령을 받아 스트림 작업 실행
- MediaMTX 입력(RTMP/SRT)을 프레임 단위 처리
- Redis의 좌표/메타데이터를 `session/stream + pts_us` 기준으로 조회
- 모델러가 제공한 함수로 프레임 합성
- 결과를 HLS(.m3u8/.ts)로 생성/저장
- 필요 시 후속 스토리지(S3) 업로드용 출력 경로 제공

## 3. Spring Boot 서버와의 상호작용

원칙: 외부 클라이언트와의 직접 통신은 Spring Boot가 담당하고, FastAPI는 내부 제어 API만 노출합니다.

### 3-1. 호출 흐름

1. 방송 시작 시 Spring Boot -> `POST /api/stream/start`
2. FastAPI가 해당 `stream_id` 워커 생성/실행
3. Spring Boot는 필요 시 `GET /api/stream/{stream_id}/status`로 상태/처리량 조회
4. 방송 종료 시 Spring Boot -> `POST /api/stream/stop`
5. FastAPI가 워커를 graceful stop

### 3-2. 요청 예시

`POST /api/stream/start`

```json
{
  "stream_id": "live-101",
  "input_url": "srt://mediamtx:8890/live/101",
  "output_path": "/var/www/hls/live-101",
  "avatar_id": "avatar-a"
}
```

`POST /api/stream/stop`

```json
{
  "stream_id": "live-101"
}
```

## 4. 모델러 함수와의 상호작용

현재 `AvatarRenderer` 인터페이스를 통해 모델 함수를 연결하도록 분리되어 있습니다.

- 입력
  - `frame` (원본 프레임)
  - `face_metadata` (Redis JSON, 없을 수 있음)
  - `avatar_id`
- 출력
  - 합성 완료 프레임 (`VideoFrame`)

### 4-1. fallback 규칙

- Redis에 메타데이터가 없거나 손상된 경우: `face_metadata=None`
- 렌더링 중 예외 발생 시: `emergency_fallback(frame)` 호출
- 실제 운영에서는 fallback 구현을 아래 정책으로 교체 권장
  - 강제 블러
  - 원본 반환
  - 마지막 정상 프레임 재사용

## 5. 파이프라인 내부 단계

현재 구현된 워커 루프는 아래 순서로 동작합니다.

1. `MediaSource.read_frame()`으로 프레임 수신 (`pts_us` 포함)
2. 지연 기준 초과 여부 검사 후 drop
3. `MetadataStore.get_face_metadata(stream_id, pts_us)` 조회
4. `AvatarRenderer.render()`로 프레임 합성
5. `FrameSink.write_frame()`로 출력 저장

## 6. 현재 더미 구현 / 교체 필요 포인트

운영 연결을 위해 아래 더미 컴포넌트를 실제 구현으로 바꿔야 합니다.

- `DummyMediaSource` -> PyAV 기반 MediaMTX 입력 디코더
- `DummyHlsSink` -> FFmpeg 기반 HLS muxer (Nginx 디렉터리 또는 S3 업로드 경로)
- `AvatarRenderer` -> 실제 아바타 합성 함수

## 7. 설정 값

`.env`로 제어 가능합니다.

- `REDIS_URL`
- `REDIS_METADATA_KEY_TEMPLATE` (기본: `stream:{stream_id}:meta:{pts_us}`)
- `PIPELINE_FPS`
- `MAX_FRAME_LAG_MS`
- `MEDIAMTX_INPUT_BASE_URL`
- `HLS_OUTPUT_ROOT`

## 8. 실행 방법

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
uvicorn main:app --reload
```

## 9. Swagger 문서

- Swagger UI: `http://localhost:8000/swagger`
- ReDoc: `http://localhost:8000/redoc`
- OpenAPI JSON: `http://localhost:8000/openapi.json`

Swagger에는 아래 내용이 반영되어 있습니다.

- 스트림 시작/중지 요청 예시(SRT, RTMP fallback)
- 상태 조회 응답 스키마 및 예시
- 404/409 오류 응답 스키마 예시
- 내부 제어 API 목적/설명(태그 기반)

## 10. 다음 구현 우선순위 제안

1. PyAV로 RTMP/SRT 입력 디코더 연결 (`pts_us` 정규화)
2. Redis 조회 키를 Spring 적재 포맷과 정확히 일치
3. 모델러 함수 연결 및 블러 fallback 정책 확정
4. FFmpeg HLS 출력 + 저장소(Nginx/S3) 업로드 전략 확정
5. 부하 테스트(30fps, 다중 stream_id) 및 드랍 정책 튜닝
