# Local E2E Runbook

이 문서는 `focus-server` + `focus-fast-api` + `MediaMTX` + `Redis` + `Postgres`를 로컬에서 함께 검증하는 절차입니다.

## 1. 인프라 실행

```bash
cd /Users/jjlee/Desktop/github/focus-fast-api
docker compose up -d
docker compose ps
```

## 2. 테스트용 회원 생성

```bash
docker exec focus-postgres psql -U focus_user -d focus_avatar \
  -c "insert into member (member_id, kakao_id, email, nickname, role) values ('01TESTMEMBER00000000000001', 1001, 'e2e@test.local', 'e2e-user', 'USER') on conflict (member_id) do nothing;"
```

## 3. FastAPI 실행

```bash
cd /Users/jjlee/Desktop/github/focus-fast-api
.venv/bin/pip install -r requirements.txt
.venv/bin/pip install -r requirements.media.txt
.venv/bin/python main.py
```

## 4. Spring Boot 실행

```bash
cd /Users/jjlee/Desktop/github/focus-server
export JWT_SECRET='YOUR_BASE64_SECRET'
export KAKAO_CLIENT_ID=dummy
export KAKAO_CLIENT_SECRET=dummy
export FASTAPI_BASE_URL='http://127.0.0.1:8000'
bash ./gradlew bootRun
```

## 5. 로컬 JWT 생성

```bash
cd /Users/jjlee/Desktop/github/focus-server
python3 scripts/local_dev_jwt.py \
  --secret-b64 "$JWT_SECRET" \
  --member-id 01TESTMEMBER00000000000001 \
  --name e2e-user
```

반환된 토큰을 이후 `Authorization: Bearer ...` 헤더에 사용합니다.

## 6. 방송 생성

```bash
curl -s -X POST http://127.0.0.1:8080/api/v1/broadcasts \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"e2e-test"}'
```

응답에서 `broadcastId`, `streamKey`를 확인합니다.

## 7. SRT publish

```bash
ffmpeg -re -f lavfi -i testsrc=size=640x360:rate=30 \
  -t 20 \
  -c:v libx264 -preset veryfast -tune zerolatency \
  -f mpegts "srt://127.0.0.1:8890?streamid=publish:live/$STREAM_KEY"
```

## 8. 방송 시작

```bash
curl -s -X POST http://127.0.0.1:8080/api/v1/broadcasts/$BROADCAST_ID/start \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"avatarId":"avatar-a"}'
```

성공하면 Spring 응답의 `data.hlsUrl` 에 FastAPI가 생성한 HLS 주소가 들어갑니다.

## 9. 검증

FastAPI 워커 상태:

```bash
curl -s http://127.0.0.1:8000/api/stream/$BROADCAST_ID/status
```

HLS playlist:

```bash
curl -s http://127.0.0.1:8000/hls/$BROADCAST_ID/index.m3u8
```

## 10. 방송 종료

```bash
curl -s -X POST http://127.0.0.1:8080/api/v1/broadcasts/$BROADCAST_ID/stop \
  -H "Authorization: Bearer $TOKEN"
```

## 참고

- 클라이언트 -> MediaMTX 는 SRT publish
- FastAPI -> MediaMTX 는 RTSP pull
- 로컬 E2E에서는 S3가 필요하지 않습니다. HLS는 FastAPI가 `/hls/...`로 직접 서빙합니다.
