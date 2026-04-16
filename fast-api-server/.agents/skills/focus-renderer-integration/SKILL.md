---
name: focus-renderer-integration
description: Use when replacing or improving this repo's `AvatarRenderer`, integrating a real avatar or face model, handling Redis face metadata, adding GPU or ML dependencies, or designing fallback behavior for render failures. Do not use for generic API contract or deployment-only work.
---

# Focus Renderer Integration

## Read first

- `model/renderer.py`
- `workers/types.py`
- `workers/pipeline.py`
- `README.md`
- `docs/클라이언트-연동.md`
- `requirements.media.txt`
- `Dockerfile`

## Expectations

- `render()` receives a `VideoFrame`, optional Redis metadata, and an optional `avatar_id`.
- The current pipeline expects a `VideoFrame` back that the FFmpeg sink can write immediately.
- `face_metadata` may be `None`, missing fields, or malformed JSON-derived data.

## Non-negotiables

- Keep `emergency_fallback()` cheap, reliable, and safe to call during failures.
- Do not assume metadata is always present or high quality.
- If the model changes output dimensions, pixel format, or payload layout, update the sink path in the same task.
- Avoid heavy synchronous inference directly on the event loop.
- Add new dependencies only when they are truly required, and keep `Dockerfile` plus requirements files in sync.

## Change checklist

1. Define the metadata fields the renderer actually consumes.
2. Preserve a clear fallback path for missing metadata, model load failures, and per-frame inference errors.
3. If GPU or native libraries are needed, update container and deployment assumptions together.
4. Update `README.md` when the renderer contract stops being a simple pass-through stub.

## Validation

- Prefer a targeted smoke test that exercises `render()` and `emergency_fallback()` with representative metadata.
- If a real model cannot run in the current environment, still validate importability and failure handling.
- Call out any unverified GPU, CUDA, or native dependency assumptions in the final answer.
