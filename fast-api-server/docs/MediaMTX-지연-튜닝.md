# MediaMTX 지연 튜닝

라이브 방송에서 client capture → viewer 사이의 4초 안팎 지연이 관측될 때 점검할 항목.

## 1. 지연의 출처

```
[client RTMP publish] ─► [MediaMTX 큐] ─► [RTSP/PyAV] ─► [render+sink] ─► [RTMP push] ─► [CDN] ─► [viewer]
        ~200ms              ~?s            ~70ms            ~100ms         ~1s        ~1-2s
```

가장 큰 변수는 MediaMTX 큐 깊이. FastAPI가 PyAV로 RTSP read를 시작하는 시점이 client RTMP publish 시작보다 늦으면, MediaMTX는 그 동안 들어온 모든 RTMP 프레임을 큐에 쌓아두고, PyAV는 오래된 프레임부터 차례대로 받게 됨. 결과적으로 PyAV는 항상 그 큐 깊이만큼 실시간보다 늦은 프레임을 처리.

PyAV 로그 `pyav_pts_sample raw_us=1250144 base_us=1250144 resolved_us=0` 처럼 첫 프레임 raw pts가 0보다 크면 그만큼 MediaMTX 버퍼가 쌓여있다는 뜻 (단위: 마이크로초, RTMP publish 시작 기준).

## 2. 점검 순서

### 2-1. FastAPI 워커 시작 타이밍 앞당기기

Spring이 `POST /api/stream/start` 호출하는 시점을 client RTMP publish 시작 전후로 정렬. Spring → FastAPI start latency가 1-2초여도 그 동안 RTMP가 publish 중이면 큐가 쌓임.

권장: client가 publish 시작 직후 Spring API 호출 → Spring이 곧장 FastAPI start. FastAPI는 PyAV로 RTSP open을 최대한 빨리 시도.

### 2-2. MediaMTX 큐 크기 제한 (적용됨)

`deploy/mediamtx/mediamtx.yml` 에 다음 설정 추가하고 `deploy/docker-compose.ec2.yaml` 에서 `:/mediamtx.yml:ro` 로 마운트.

```yaml
writeQueueSize: 64           # RTSP reader 당 큐 한계 (≈ 2초@30fps)
rtspTransports: [tcp]        # PyAV 와 동일하게 TCP 강제
hls: no                      # 본 게이트웨이는 RTMP→RTSP 만 사용
webrtc: no
paths:
  '~^live/.+$':
    source: publisher
    sourceOnDemand: no       # publisher 재연결 대비 path 유지
```

`writeQueueSize` 가 한계에 닿으면 RTSP reader (PyAV) 가 끊겨서 FastAPI 측에서 재연결. 클라가 30fps 로 publish 하지만 PyAV 가 PyAV 자체에서 frame drop 하므로 정상 상태에선 큐가 거의 비어 있어야 함. 누적되면 PyAV decode 속도가 부족하다는 신호.

### 2-3. PyAV RTSP open 옵션 (이미 적용됨)

`adapters/media_source.py:_build_open_options` 가 이미 low-latency 옵션 설정:
- `rtsp_transport=tcp`
- `fflags=nobuffer`
- `flags=low_delay`
- `analyzeduration=0`
- `probesize=32768`
- `max_delay=0`

추가 검토 가능:
- `stimeout=5000000` — RTSP 소켓 타임아웃 (5초)
- `rtsp_flags=prefer_tcp` — UDP 거치지 않고 TCP only

### 2-4. 큐 잔량 모니터링

MediaMTX `/v3/paths/list` API (포트 9997, `api: yes` 설정 필요) 로 path 별 현재 reader/publisher 상태 확인. RTMP ingest rate vs RTSP read rate 차이가 누적 큐.

### 2-5. 백로그 검출

PyAV 첫 프레임 raw_pts 가 Redis `latest_pts` 보다 크게 작으면 (예: > 2초 차이) 백로그 누적. 로그 기반 알림:

```
pyav_pts_sample raw_us=4400000
metadata_offset_seeded offset_us=4400000  ← 큐 4.4초
```

`offset_us` 값이 곧 큐 깊이. 이 값을 모니터 메트릭으로 노출하면 운영에서 추적 가능.

## 3. 아바타-비디오 sync vs e2e 지연 구분

아바타가 face 위치보다 늦게 따라오면 **metadata 매칭 어긋남** (코드 버그). PyAV `_base_pts_us` seed 로 해결됨.

전체 영상이 실시간보다 늦게 viewer 에게 도달하면 **e2e latency** (인프라). 위 2-1 ~ 2-4 튜닝으로 줄임. 아바타-비디오 sync 자체는 정상.

## 4. 권장 최종 구성

| 항목 | 값 |
|------|-----|
| MediaMTX writeQueueSize | 64 |
| Spring → FastAPI start latency | < 500ms |
| PyAV low-latency 옵션 | 적용됨 |
| HLS hls_time | 1s |
| FFmpeg sink `tune zerolatency` | 적용됨 |
| RTMP push to CHZZK GOP | 1s |

이 조합으로 e2e ≈ 2-3초 수준 기대. 4초 이상이면 위 항목 재점검.
