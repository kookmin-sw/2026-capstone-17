---
name: focus-local-e2e
description: Use this skill when a task requires local bring-up or debugging across focus-server plus companion services such as Postgres, Redis, focus-fast-api, MediaMTX, generated JWTs, curl or grpcurl verification, or reproducing bugs end to end. Do not use it for isolated code-only changes when a narrower domain skill such as auth, broadcast, CHZZK, member image, or gRPC metadata is sufficient.
---

# Focus Local E2E

Use this skill when you need the full local runtime, not just isolated code edits.

## Quick start

1. Read `../../../agent/coding-rules.md` only if you are also changing code.
2. Read `references/entrypoints.md` for the exact runbooks and environment prerequisites.
3. Choose the narrowest scenario first: auth-only, broadcast-only, or gRPC-only manual checks before bringing up every service.

## Workflow

- Local E2E in this project often spans `focus-server`, `focus-fast-api`, MediaMTX, Redis, and Postgres.
- The usual broadcast path is:
  1. start infra
  2. run FastAPI
  3. run Spring Boot with required env vars
  4. mint a local JWT
  5. create a broadcast
  6. publish SRT input
  7. start the broadcast
  8. verify FastAPI status and HLS output
  9. stop the broadcast
- The usual gRPC path is:
  1. start infra
  2. run Spring Boot
  3. inspect services with `grpcurl`
  4. stream sample metadata
  5. verify Redis writes

## Validation

- Prefer the existing runbooks instead of inventing new shell flows.
- When a task fails only in the integrated stack, capture the exact failing step and then move back to the narrower domain skill for the code fix.
- Keep environment-variable assumptions explicit in your notes and commands.

## Boundaries

- Use `focus-auth-member`, `focus-member-image-storage`, `focus-broadcast-lifecycle`, `focus-chzzk-platform`, or `focus-grpc-face-metadata` once the root cause has been narrowed to one subsystem.
