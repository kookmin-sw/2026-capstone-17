---
name: focus-grpc-face-metadata
description: Use this skill when working on the face metadata gRPC API, the `face_metadata.proto` contract, servlet-based gRPC exposure, `FaceMetadataIngestGrpcService`, Redis payload serialization for frame metadata, or grpcurl-based verification in focus-server. Do not use it for REST auth, broadcast CRUD, CHZZK OAuth, or general local E2E streaming tasks unless the issue is specifically in the face metadata ingestion path.
---

# Focus gRPC Face Metadata

Use this skill for the face metadata ingestion pipeline.

## Quick start

1. Read `../../../agent/coding-rules.md`.
2. Read `references/entrypoints.md` before changing the proto, service contract, or manual verification flow.
3. Treat the proto file as the contract source of truth and update server code to match it.

## Workflow

- Keep the package and RPC contract aligned with `focus.metadata.v1`.
- The current service uses servlet-mode gRPC on the same port as the HTTP server.
- Reflection is enabled and should stay usable for manual discovery unless the task explicitly changes that policy.
- `pushFaceMetadata` currently:
  1. counts received frames
  2. drops invalid frames
  3. maps accepted frames into Redis payload objects
  4. writes them through `StreamMetadataRedisService`
  5. returns an ingest summary on stream completion
- Validation rules currently drop blank `session_id` and negative `pts_us`.

## Validation

- Regenerate and compile the project if the proto changes.
- Use `grpcurl` commands from the gRPC runbook for manual verification.
- Verify Redis key shape and TTL whenever payload or key-template behavior changes.

## Boundaries

- Use `focus-broadcast-lifecycle` for REST broadcast work.
- Use `focus-local-e2e` when the task becomes a broader multi-service reproduction effort.
