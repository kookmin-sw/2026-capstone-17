# CHZZK platform entry points

## Primary files

- `src/main/kotlin/com/capstone/focus/api/platform/controller/ChzzkPlatformController.kt`
- `src/main/kotlin/com/capstone/focus/api/platform/service/ChzzkPlatformService.kt`
- `src/main/kotlin/com/capstone/focus/api/platform/dto/response/ChzzkConnectResponse.kt`
- `src/main/kotlin/com/capstone/focus/api/platform/dto/response/ChzzkConnectionStatusResponse.kt`
- `src/main/kotlin/com/capstone/focus/common/external/chzzk/ChzzkClient.kt`
- `src/main/kotlin/com/capstone/focus/common/external/chzzk/ChzzkOpenApiFeignClient.kt`
- `src/main/kotlin/com/capstone/focus/common/config/ChzzkProperties.kt`
- `src/main/kotlin/com/capstone/focus/domain/entity/StreamingPlatformConnection.kt`
- `src/main/kotlin/com/capstone/focus/domain/repository/StreamingPlatformConnectionRepository.kt`
- `src/main/kotlin/com/capstone/focus/common/external/redis/RedisService.kt`

## Configuration

- `src/main/resources/application.yaml`
- Important keys: `naver.chzzk.*`
- `CHZZK_STREAM_PUBLISH_URL_TEMPLATE` must be present for broadcast start output URL generation

## Useful checks

- `./gradlew test`
- Manual connect and callback flow against `/api/v1/platforms/chzzk/*`

## Behavioral notes

- OAuth state is stored as `oauth:state:chzzk:<state>`.
- Token refresh is triggered when the connection is close to expiry using the configured buffer seconds.
- Remote revoke failures are logged, then local connection state is still revoked.
