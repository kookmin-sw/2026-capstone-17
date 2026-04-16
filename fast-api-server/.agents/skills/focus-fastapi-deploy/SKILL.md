---
name: focus-fastapi-deploy
description: Use when changing or debugging this repo's Dockerfile, docker-compose files, environment variables, EC2 deployment setup, HLS serving path, or 운영 문서 for local or AWS deployment. Trigger for infra and deployment tasks around FastAPI, MediaMTX, Redis connectivity, and HLS publishing. Do not use for pure API/schema or renderer-only changes.
---

# Focus FastAPI Deploy

## Read first

- `README.md`
- `docs/배포-시크릿-정리.md`
- `docker-compose.yaml`
- `deploy/docker-compose.ec2.yaml`
- `Dockerfile`
- `core/config.py`
- `작업현황.md`

## Deployment model

- Local development uses `docker-compose.yaml` for Redis and MediaMTX, while FastAPI usually runs with `uvicorn`.
- AWS deployment uses a separate EC2 for FastAPI plus MediaMTX.
- Redis lives on the Spring side and should be reached over private networking in AWS.
- HLS is currently file-system based, not S3 or CDN first.

## Non-negotiables

- Keep environment variable names aligned with `core/config.py`.
- Preserve the distinction between internal connectivity values and public watch URLs.
- `REDIS_URL` on AWS should remain compatible with the Spring and Redis private network layout unless the user requests an architecture change.
- `HLS_PUBLIC_BASE_URL` represents what viewers can open, not where files are stored on disk.
- When you change runtime configuration, update the matching docs in the same task.

## Change checklist

1. If you add or rename settings, update `core/config.py`, compose files, and docs together.
2. If container dependencies change, keep `Dockerfile`, requirements files, and runtime assumptions consistent.
3. If HLS storage or serving changes, update both the local and EC2 flow descriptions.
4. If the architecture moves toward S3 or CDN, call out which existing file-system assumptions no longer hold.

## Validation

- Prefer configuration and startup checks after deploy-related edits.
- Use the documented local commands first before assuming the AWS path is broken.
- Mention clearly whether validation covered local compose only, container build only, or a full deployment path.
