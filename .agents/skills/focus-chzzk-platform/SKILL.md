---
name: focus-chzzk-platform
description: Use this skill when working on CHZZK OAuth connect or callback behavior, CHZZK connection status or disconnect flows, token refresh or revoke logic, stream key lookup, live title updates, or persistence of CHZZK platform connections in the focus-server Spring Boot service. Do not use it for general broadcast CRUD, Kakao auth, gRPC metadata ingestion, or local infrastructure bring-up unless the root cause is in the CHZZK integration path.
---

# Focus CHZZK Platform

Use this skill for CHZZK platform integration and channel connection workflows.

## Quick start

1. Read `../../../agent/coding-rules.md`.
2. Read `references/entrypoints.md` for the file map and environment keys.
3. Start at the platform controller or service, then widen into the CHZZK client and connection entity.

## Workflow

- Preserve the REST contract under `/api/v1/platforms/chzzk`.
- `createConnectUrl()` stores an OAuth state in Redis and returns a CHZZK authorization URL.
- `handleCallback()` exchanges the code, fetches the current channel identity, upserts `StreamingPlatformConnection`, and clears the Redis state key.
- `disconnect()` should attempt token revocation, but still revoke the local connection if remote revoke fails.
- `prepareBroadcastTarget()` is the bridge used by broadcast start:
  1. load the active CHZZK connection
  2. refresh tokens if they are near expiry
  3. optionally update the live title
  4. fetch the stream key
  5. build watch and publish URLs

## Validation

- Prefer focused service-level tests when external behavior is mocked.
- For manual checks, validate connect URL generation, callback handling, status, and disconnect semantics.
- When changing token refresh logic, verify both success and refresh-failure paths.

## Boundaries

- Use `focus-broadcast-lifecycle` for broadcast CRUD and worker start or stop orchestration.
- Use `focus-local-e2e` when debugging requires the full streaming stack in motion.
