# Broadcast lifecycle entry points

## Primary files

- `src/main/kotlin/com/capstone/focus/api/broadcast/controller/BroadcastController.kt`
- `src/main/kotlin/com/capstone/focus/api/broadcast/service/BroadcastService.kt`
- `src/main/kotlin/com/capstone/focus/api/broadcast/dto/request/CreateBroadcastRequest.kt`
- `src/main/kotlin/com/capstone/focus/api/broadcast/dto/request/StartBroadcastRequest.kt`
- `src/main/kotlin/com/capstone/focus/api/broadcast/dto/request/UpdateBroadcastRequest.kt`
- `src/main/kotlin/com/capstone/focus/api/broadcast/dto/response/BroadcastResponse.kt`
- `src/main/kotlin/com/capstone/focus/domain/entity/Broadcast.kt`
- `src/main/kotlin/com/capstone/focus/domain/repository/BroadcastRepository.kt`
- `src/main/kotlin/com/capstone/focus/common/external/fastapi/FastApiStreamClient.kt`
- `src/main/kotlin/com/capstone/focus/common/external/fastapi/dto/FastApiStreamDtos.kt`

## Related docs

- `docs/local-e2e-runbook.md`

## Useful checks

- `./gradlew test`
- Manual broadcast create, start, stop flow via curl from `docs/local-e2e-runbook.md`

## Behavioral notes

- Start currently targets `BroadcastOutputMode.CHZZK_RTMP`.
- Ownership is enforced in the service before mutating broadcast state.
- FastAPI failures are surfaced as `ApiException(ErrorTitle.FeignClientError, ...)`.
