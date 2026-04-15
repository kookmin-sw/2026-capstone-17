---
name: focus-stream-pipeline
description: Use when modifying or debugging this repo's 방송 워커 and media pipeline: StreamManager, StreamPipeline, MediaMTX RTSP pull, Redis metadata lookup, frame drop and jitter behavior, FFmpeg HLS or RTMP output, retry logic, or pipeline state transitions. Do not use for pure API schema work or deployment-only tasks.
---

# Focus Stream Pipeline

## Read first

- `README.md`
- `작업현황.md`
- `services/stream_manager.py`
- `workers/pipeline.py`
- `adapters/media_source.py`
- `adapters/frame_sink.py`
- `adapters/metadata_store.py`
- `workers/types.py`
- `model/renderer.py`

## Current flow

1. Spring calls the FastAPI start endpoint.
2. `StreamManager` resolves input and output URLs and creates a `StreamPipeline`.
3. The pipeline reads frames from MediaMTX RTSP or a dummy source.
4. It waits briefly for Redis metadata for the matching `broadcast_id + pts_us`.
5. It renders through `AvatarRenderer`.
6. It writes frames to FFmpeg for HLS or RTMP output.

## Non-negotiables

- Keep the loop resilient: missing Redis metadata should degrade gracefully, not crash the worker.
- Rendering failures should fall back through `emergency_fallback()` unless the user explicitly wants fail-fast behavior.
- Avoid long blocking work on the event loop. If work is CPU-heavy or blocking, move it behind an async boundary.
- Preserve resource cleanup for media source, frame sink, and metadata store on every exit path.
- Keep frame-drop, retry, and state-transition behavior internally consistent when changing latency or output logic.

## Change checklist

1. If you add or rename an output mode, update `schemas/stream.py`, `services/stream_manager.py`, `adapters/frame_sink.py`, and docs together.
2. If you change metadata timing or lookup keys, re-check the Redis contract in `README.md` and `docs/클라이언트-연동.md`.
3. If you change frame payload shape, confirm `VideoFrame`, renderer output, and FFmpeg sink still agree on width, height, and pixel format.
4. If you touch stop or failure semantics, verify the returned status still matches what Spring expects from `/status`.

## Validation

- Use dummy inputs when live media dependencies are unavailable.
- If live validation is needed, align with the repo's documented local flow: `docker compose up -d`, optional media requirements install, then `uvicorn main:app --reload`.
- Mention clearly whether validation covered dummy mode only or a real MediaMTX and FFmpeg path.
