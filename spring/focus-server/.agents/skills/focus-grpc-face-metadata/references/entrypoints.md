# gRPC face metadata entry points

## Primary files

- `src/main/proto/focus/metadata/v1/face_metadata.proto`
- `src/main/kotlin/com/capstone/focus/api/grpc/FaceMetadataIngestGrpcService.kt`
- `src/main/kotlin/com/capstone/focus/api/grpc/interceptor/GrpcAccessLogInterceptor.kt`
- `src/main/kotlin/com/capstone/focus/common/external/redis/StreamMetadataRedisService.kt`
- `src/main/kotlin/com/capstone/focus/common/external/redis/model/FaceMetadataRedisPayload.kt`
- `src/main/resources/application.yaml`
- `build.gradle.kts`

## Related docs

- `docs/grpc-face-metadata-api.md`
- `docs/grpc-server-local-runbook.md`

## Useful checks

- `./gradlew test`
- `grpcurl -plaintext 127.0.0.1:8080 list`
- `grpcurl -plaintext 127.0.0.1:8080 describe focus.metadata.v1.FaceMetadataIngestService`

## Behavioral notes

- This project currently serves gRPC over the same HTTP port as Spring MVC, not a separate `9090` listener.
- Redis keys are derived from `focus.metadata.redis.key-template`.
- Accepted frame payloads are serialized with Jackson and stored through `RedisTemplate<String, String>`.
