---
name: focus-auth-member
description: Use this skill when working on Kakao login, JWT issuance or refresh, Redis-backed refresh token storage, authenticated member lookup, logout, or Spring Security principal wiring in the focus-server Spring Boot service. Do not use it for CHZZK OAuth connection management, broadcast start and stop orchestration, member image upload, gRPC face metadata ingestion, or multi-service local E2E bring-up unless auth is the primary issue.
---

# Focus Auth & Member

Use this skill for authentication and authenticated member flows in `focus-server`.

## Quick start

1. Read `../../../agent/coding-rules.md`.
2. Read `references/entrypoints.md` when you need the file map or manual verification commands.
3. Start from the controller or service closest to the requested behavior before widening the search.

## Workflow

- Preserve the existing response shape: `ResponseEntity<ApiResponse.Success<...>>` returned through `ResponseUtil.success(...)`.
- Keep authenticated endpoints on `@AuthenticationPrincipal FocusMemberDetails`.
- The current Kakao login flow is:
  1. exchange the code for a Kakao token
  2. fetch Kakao user info
  3. create or update the member
  4. issue the access token
  5. create a refresh token and persist Redis bidirectional mappings
- The current refresh flow invalidates the old refresh token before minting a new one.
- Logout deletes the refresh token mapping by member id.
- Prefer localized fixes in `AuthService`, `JwtService`, `RedisService`, or the security layer before changing shared controller conventions.

## Validation

- Run targeted Gradle tests first when the change is unit-testable.
- For manual checks, verify `/api/auth/*` and `/api/members/*`.
- If downstream API checks need a local token, use `scripts/local_dev_jwt.py`, but use `focus-local-e2e` when the task expands into full-stack orchestration.

## Boundaries

- Use `focus-member-image-storage` for S3-backed member image endpoints.
- Use `focus-broadcast-lifecycle` for broadcast CRUD and FastAPI worker orchestration.
- Use `focus-chzzk-platform` for CHZZK OAuth, token refresh, and channel connection behavior.
