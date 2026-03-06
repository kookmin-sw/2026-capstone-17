# gRPC Local Runbook (Server Developers)

## 1) Prerequisites
- Docker
- JDK 25
- `grpcurl` (for manual gRPC checks)
- Redis/Postgres local containers

## 2) Start Local Infra
```bash
cd /Users/lio.jj/Desktop/개인-github/focus-server
docker compose up -d
```

## 3) Run Spring Server
```bash
export KAKAO_CLIENT_ID=dummy
export KAKAO_CLIENT_SECRET=dummy
export JWT_SECRET=$(printf '0123456789012345678901234567890123456789012345678901234567890123' | base64)
bash ./gradlew bootRun
```

## 4) gRPC Port Notes
- Current mode is servlet-based gRPC.
- gRPC is served on the same HTTP port as the web server (default `8080`).
- `9090` is not used unless native gRPC server mode is explicitly enabled.

## 5) Reflection / Discovery
Reflection is enabled via application config:

```yaml
spring:
  grpc:
    server:
      reflection:
        enabled: true
```

Commands:
```bash
grpcurl -plaintext 127.0.0.1:8080 list
grpcurl -plaintext 127.0.0.1:8080 describe focus.metadata.v1.FaceMetadataIngestService
```

## 6) Streaming Test
```bash
cat <<'EOF' | grpcurl -plaintext -d @ 127.0.0.1:8080 focus.metadata.v1.FaceMetadataIngestService/PushFaceMetadata
{"sessionId":"bc_20260227_001","ptsUs":61400000,"avatarUrl":"s3://avatars/a.vrm","faceData":{"AU12":0.63,"yaw":0.10},"trackingId":"t-102","isReentry":false,"confidence":0.94,"bbox":[120,80,220,200]}
{"sessionId":"bc_20260227_001","ptsUs":61433333,"avatarUrl":"s3://avatars/a.vrm","faceData":{"AU12":0.61,"yaw":0.12},"trackingId":"t-102","isReentry":false,"confidence":0.93,"bbox":[121,80,221,200]}
EOF
```

## 7) Redis Verification
```bash
redis-cli GET "broadcast:bc_20260227_001:meta:61433333"
redis-cli TTL "broadcast:bc_20260227_001:meta:61433333"
```

## 8) Troubleshooting
- `connection refused` on `9090`:
  - Use `127.0.0.1:8080` in servlet mode.
- Boot fails due to port conflict:
  - check listeners with `lsof -nP -iTCP:8080 -sTCP:LISTEN`
- No gRPC service in `grpcurl list`:
  - confirm app logs include `Registering gRPC service: focus.metadata.v1.FaceMetadataIngestService`
