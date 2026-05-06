# Focus — 클라이언트 연동 가이드

> **Spring API Base URL:** `http://3.35.202.126:8080`
>
> **Media ingest (SRT/RTMP) host:** `13.125.126.120`
>
> **인증:** `Authorization: Bearer <accessToken>` 헤더

---

## 1. 전체 흐름

```
1. 카카오 로그인 → accessToken 획득
2. 치지직 계정 연동 (WebView OAuth)
3. 방송 생성 API → streamKey 획득
4. 카메라 영상을 SRT 또는 RTMP로 서버에 송출
5. 방송 시작 API 호출 → 치지직 라이브 시작
6. 방송 종료 API 호출
7. 카메라 송출 종료
```

### 영상 경로

```
모바일 카메라 ──SRT/RTMP──▶ MediaMTX(FASTAPI_INGEST_IP) ──RTSP──▶ FastAPI ──RTMP──▶ 치지직
```

> 클라이언트는 **카메라 영상 송출 + REST API 호출**만 하면 됩니다.

---

## 2. 인증

### 토큰 갱신

```
POST /api/auth/refresh
```

```json
{ "refreshToken": "550e..." }
```

### 내 정보 조회

```
GET /api/members/me
Authorization: Bearer <accessToken>
```

### 로그아웃

```
POST /api/members/logout
Authorization: Bearer <accessToken>
```

---

## 3. 치지직 계정 연동

> 방송 시작 전 **반드시** 치지직 연동이 필요합니다.

### 연동 URL 조회

```
GET /api/v1/platforms/chzzk/connect
Authorization: Bearer <accessToken>
```

```json
{ "success": true, "data": { "authUrl": "https://chzzk.naver.com/account-interlock?..." } }
```

> WebView에서 `authUrl`을 열면 사용자가 승인합니다.
> 서버 콜백(`/api/v1/platforms/chzzk/callback`)이 자동 처리되므로, 콜백 URL 로드 시 WebView를 닫으면 됩니다.

### 연동 상태 확인

```
GET /api/v1/platforms/chzzk/status
Authorization: Bearer <accessToken>
```

```json
{
  "success": true,
  "data": {
    "connected": true,
    "channelId": "abc123...",
    "channelName": "내채널",
    "watchUrl": "https://chzzk.naver.com/abc123..."
  }
}
```

### 연동 해제

```
DELETE /api/v1/platforms/chzzk/connection
Authorization: Bearer <accessToken>
```

---

## 4. 방송

### 방송 생성

```
POST /api/v1/broadcasts
Authorization: Bearer <accessToken>
```

```json
{ "title": "내 첫 방송" }
```

**응답:**

```json
{
  "success": true,
  "data": {
    "broadcastId": "01JRZH...",
    "title": "내 첫 방송",
    "status": "CREATED",
    "streamKey": "550e8400-e29b-41d4-a716-446655440000"
  }
}
```

> **`streamKey`를 저장하세요.** 카메라 영상 송출 URL에 사용합니다.

### 방송 시작

**사전 조건:** 치지직 연동 완료 + 카메라 영상 송출 중 (5장 참고)

```
POST /api/v1/broadcasts/{broadcastId}/start
Authorization: Bearer <accessToken>
```

```json
{ "avatarId": null }
```

**응답:**

```json
{
  "success": true,
  "data": {
    "broadcastId": "01JRZH...",
    "status": "ON_AIR",
    "watchUrl": "https://chzzk.naver.com/abc123..."
  }
}
```

### 방송 종료

```
POST /api/v1/broadcasts/{broadcastId}/stop
Authorization: Bearer <accessToken>
```

### 방송 목록 / 상세 / 수정 / 삭제

```
GET    /api/v1/broadcasts?page=0&size=10
GET    /api/v1/broadcasts/{broadcastId}
PUT    /api/v1/broadcasts/{broadcastId}    → { "title": "수정된 제목" }
DELETE /api/v1/broadcasts/{broadcastId}
```

---

## 5. 카메라 영상 송출

### 송출 URL (중요)

| 프로토콜 | URL | 비고 |
|----------|-----|------|
| **SRT** (기본) | `srt://13.125.126.120:8890?streamid=publish:live/<streamKey>` | UDP, 낮은 지연, 패킷 손실 복구 |
| **RTMP** (폴백) | `rtmp://13.125.126.120:1935/live/<streamKey>` | TCP, UDP 차단 환경용 |

> SRT `streamid`는 반드시 `publish:live/<streamKey>` 형식이어야 합니다.
> `publish:/live/<streamKey>`처럼 `/`로 시작하면 MediaMTX가 연결을 거절합니다.

### 영상 스펙

| 항목 | 값 |
|------|-----|
| 해상도 | 1280×720 |
| FPS | 30 |
| 비디오 | H.264, 2~4 Mbps |
| 오디오 | AAC, 128 kbps, 44100 Hz |

### 권장 라이브러리

| 플랫폼 | 라이브러리 | 비고 |
|--------|-----------|------|
| Android | [RootEncoder](https://github.com/pedroSG94/RootEncoder) | SRT/RTMP 모두 지원, Camera2 API |
| iOS | [HaishinKit](https://github.com/shogo4405/HaishinKit.swift) + [SRTHaishinKit](https://github.com/shogo4405/SRTHaishinKit.swift) | SRT/RTMP 모두 지원 |

### 타이밍 (중요)

```
방송 생성 API (Spring) → streamKey 획득
         ↓
카메라 SRT/RTMP 송출 시작 (FASTAPI_INGEST_IP로 송출)
         ↓
연결 성공 확인 (콜백)
         ↓
방송 시작 API 호출 (Spring) ← 반드시 송출 성공 후!
         ↓
watchUrl 수신 → 시청자 공유
```

> **송출이 성공한 후에 방송 시작 API를 호출해야 합니다.**
> 순서가 바뀌면 서버가 영상 스트림을 찾지 못해 실패합니다.

---

## 6. 멤버 이미지

```
POST   /api/members/images    (multipart/form-data, key: image)
GET    /api/members/images
DELETE /api/members/images/{imageId}
```

> 모두 `Authorization: Bearer <accessToken>` 필요

---

## 7. 전체 API 요약

| 메서드 | 경로 | 인증 | 설명 |
|--------|------|:----:|------|
| `GET` | `/api/auth/kakao/url` | | 카카오 로그인 URL |
| `POST` | `/api/auth/kakao/login` | | 카카오 로그인 |
| `POST` | `/api/auth/refresh` | | 토큰 갱신 |
| `GET` | `/api/members/me` | O | 내 정보 |
| `POST` | `/api/members/logout` | O | 로그아웃 |
| `GET` | `/api/members/images` | O | 이미지 목록 |
| `POST` | `/api/members/images` | O | 이미지 업로드 |
| `DELETE` | `/api/members/images/{imageId}` | O | 이미지 삭제 |
| `POST` | `/api/v1/broadcasts` | O | 방송 생성 |
| `POST` | `/api/v1/broadcasts/{id}/start` | O | 방송 시작 |
| `POST` | `/api/v1/broadcasts/{id}/stop` | O | 방송 종료 |
| `GET` | `/api/v1/broadcasts` | | 방송 목록 |
| `GET` | `/api/v1/broadcasts/{id}` | | 방송 상세 |
| `PUT` | `/api/v1/broadcasts/{id}` | O | 방송 수정 |
| `DELETE` | `/api/v1/broadcasts/{id}` | O | 방송 삭제 |
| `GET` | `/api/v1/platforms/chzzk/connect` | O | 치지직 연동 URL |
| `GET` | `/api/v1/platforms/chzzk/status` | O | 치지직 연동 상태 |
| `DELETE` | `/api/v1/platforms/chzzk/connection` | O | 치지직 연동 해제 |

> Spring Swagger UI: `http://3.35.202.126:8080/swagger-ui.html`
>
> FastAPI health check: `http://13.125.126.120:8000/healthz`
