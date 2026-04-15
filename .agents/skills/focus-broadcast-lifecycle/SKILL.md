---
name: focus-broadcast-lifecycle
description: Use this skill when implementing, debugging, or testing the focus-server broadcast lifecycle: create, start, stop, update, or delete broadcast APIs, broadcast ownership validation, state transitions, FastAPI worker orchestration, stream key issuance, or broadcast response mapping. Do not use it for Kakao or JWT auth, CHZZK OAuth connection management, gRPC face metadata ingestion, or broad local infrastructure bring-up unless the change directly affects broadcast lifecycle behavior.
---

# Focus Broadcast Lifecycle

Use this skill for broadcast CRUD and start or stop orchestration inside `focus-server`.

## Quick start

1. Read `../../../agent/coding-rules.md`.
2. Read `references/entrypoints.md` when the task touches the FastAPI handoff or manual E2E checks.
3. Inspect the smallest relevant path first: controller, service, entity or repository, then external clients.

## Workflow

- Preserve the REST contract under `/api/v1/broadcasts`.
- Keep owner-sensitive actions on authenticated endpoints using `FocusMemberDetails`.
- The current create flow persists a new `Broadcast` and assigns a UUID stream key.
- The current start flow is:
  1. load the broadcast and validate ownership
  2. prepare the CHZZK output target
  3. call `FastApiStreamClient.startBroadcast(...)`
  4. transition the entity to started state
- The current stop flow calls the FastAPI worker stop endpoint before ending the broadcast.
- Keep start failure handling aligned with `broadcast.markStartFailure(...)` when `ApiException` is thrown.

## Validation

- Run targeted tests when they exist.
- For manual verification, use the broadcast steps from `docs/local-e2e-runbook.md`.
- When you change worker handoff payloads, verify both Spring responses and FastAPI-facing request fields.

## Boundaries

- Use `focus-chzzk-platform` when the issue is in CHZZK OAuth, token refresh, stream key retrieval, or connection persistence.
- Use `focus-local-e2e` when reproducing the issue requires MediaMTX, FastAPI, Redis, or Postgres together.
