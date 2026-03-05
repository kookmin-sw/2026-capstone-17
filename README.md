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
- 파이프라인 루프 뼈대
  - 프레임 수신 -> Redis 메타데이터 조회 -> 모델 렌더링 -> 출력
- 지연 프레임 드랍 정책
  - `MAX_FRAME_LAG_MS` 초과 시 과거 프레임 drop
- 장애 내성 기본 처리
  - Redis 조회 실패 시 `face_metadata=None`
  - 렌더링 예외 시 `emergency_fallback()`

RTMP/SRT 입력 디코더(PyAV)는 구현되었고, FFmpeg 출력/실제 아바타 합성은 더미 구현입니다.

## 2. FastAPI 역할 (R&R)

FastAPI는 외부 클라이언트 API 서버가 아니라 Spring Boot가 제어하는 내부 영상 워커입니다.

- Spring Boot의 시작/중지 명령 수신
- MediaMTX 입력(RTMP/SRT)을 프레임 단위 처리
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

### 시작 요청 예시

```json
{
  "broadcast_id": "bc_20260227_001",
  "input_url": "srt://mediamtx:8890/live/101",
  "output_path": "/var/www/hls/bc_20260227_001",
  "avatar_id": "avatar-a"
}
```

테스트용으로 `input_url`에 `dummy://stream`을 넣으면 더미 입력 소스를 사용할 수 있습니다.

### 종료 요청 예시

```json
{
  "broadcast_id": "bc_20260227_001"
}
```

## 4. 모델러 함수와 상호작용

`model/renderer.py`의 `AvatarRenderer` 인터페이스로 모델 함수를 연결합니다.

- 입력
  - `frame` (원본 프레임)
  - `face_metadata` (Redis JSON, 없을 수 있음)
  - `avatar_id`
- 출력
  - 합성 완료 프레임 (`VideoFrame`)

fallback 정책:

- Redis 데이터 없음/손상: `face_metadata=None`
- 렌더링 예외: `emergency_fallback(frame)`
- 운영 단계에서 블러/원본/마지막 정상 프레임 정책으로 교체 권장

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

## 7. Docker Compose (로컬 Redis 전용)

- 파일: `docker-compose.yaml`
- Redis 설정 파일: `infra/redis/redis.conf`

Redis 실행:

```bash
docker compose up -d
```

Redis 확인:

```bash
docker compose ps
docker compose logs -f redis
```

FastAPI는 로컬에서 직접 실행:

```bash
uvicorn main:app --reload
```

라이브 RTMP/SRT 입력(PyAV)까지 테스트할 경우:

```bash
pip install -r requirements.media.txt
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

1. Spring 적재 포맷과 Redis 키/필드 완전 일치
2. 모델러 함수 연결 및 블러 fallback 정책 확정
3. FFmpeg HLS 출력 + Nginx/S3 저장 전략 확정
4. 다중 방송 부하 테스트 및 드랍 정책 튜닝
