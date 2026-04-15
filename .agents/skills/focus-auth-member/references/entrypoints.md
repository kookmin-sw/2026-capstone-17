# Auth and member entry points

## Primary files

- `src/main/kotlin/com/capstone/focus/auth/controller/AuthController.kt`
- `src/main/kotlin/com/capstone/focus/auth/controller/UserController.kt`
- `src/main/kotlin/com/capstone/focus/auth/service/AuthService.kt`
- `src/main/kotlin/com/capstone/focus/auth/jwt/JwtService.kt`
- `src/main/kotlin/com/capstone/focus/auth/security/config/SecurityConfig.kt`
- `src/main/kotlin/com/capstone/focus/auth/security/filter/JwtAuthenticationFilter.kt`
- `src/main/kotlin/com/capstone/focus/auth/security/filter/ExceptionFilter.kt`
- `src/main/kotlin/com/capstone/focus/auth/security/service/FocusMemberDetails.kt`
- `src/main/kotlin/com/capstone/focus/common/external/redis/RedisService.kt`
- `src/main/kotlin/com/capstone/focus/common/external/kakao/KakaoOAuthClient.kt`
- `src/main/kotlin/com/capstone/focus/domain/MemberService.kt`

## Configuration

- `src/main/resources/application.yaml`
- Required env vars: `JWT_SECRET`, `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET`

## Useful checks

- `./gradlew test`
- `python3 scripts/local_dev_jwt.py --secret-b64 "$JWT_SECRET" --member-id <memberId> --name <nickname>`

## Behavioral notes

- Refresh tokens are stored in Redis with both `user -> token` and `token -> user` mappings.
- `MemberController.logout()` clears refresh token state by member id.
- `AuthService.refresh()` deletes the presented refresh token before issuing a new token pair.
