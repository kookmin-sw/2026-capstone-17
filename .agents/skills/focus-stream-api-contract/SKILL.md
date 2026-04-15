---
name: focus-stream-api-contract
description: Use when changing or debugging this repo's FastAPI stream-control API, Pydantic schemas, OpenAPI examples, validation, or exception contract for `/api/stream/start`, `/api/stream/stop`, and `/api/stream/{broadcast_id}/status`. Trigger for API/contract tasks in this internal worker service. Do not use for media pipeline internals, renderer/model work, or deployment-only changes.
---

# Focus Stream API Contract

## Read first

- `README.md`
- `docs/클라이언트-연동.md`
- `api/routes_stream.py`
- `schemas/stream.py`
- `api/exception_handlers.py`
- `core/exceptions.py`
- `services/stream_manager.py`

## Service boundary

- Treat this FastAPI app as an internal worker controlled by Spring Boot.
- Do not turn it into a client-facing API unless the user explicitly requests that architecture change.
- Keep the `/api/stream` contract stable by default because Spring depends on it.

## Non-negotiables

- Preserve the failure payload shape: `success`, `message`, `errorTitle`, `errorCode`.
- Keep `ErrorTitle` names aligned with the Spring-side error contract.
- Prefer `input_stream_key`. Treat `stream_key` as a deprecated compatibility alias unless the user explicitly approves a breaking change.
- When request or response fields change, update both schema examples and route examples together.
- If a status enum changes, keep it consistent with `workers/pipeline.py`.

## Change checklist

1. Update the Pydantic models in `schemas/stream.py`.
2. Update route docs and examples in `api/routes_stream.py`.
3. Confirm `services/stream_manager.py` still builds defaults that match the contract.
4. If URL or output-mode behavior changes, update `README.md` and `docs/클라이언트-연동.md`.
5. Call out any intentional breaking change clearly in the final answer.

## Validation

- Prefer a quick import or app startup smoke check after edits.
- If the app can run locally, inspect `/openapi.json` or `/swagger` to confirm examples and schema changes.
- Mention if validation was limited by missing runtime dependencies or environment setup.
