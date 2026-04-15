---
name: focus-member-image-storage
description: Use this skill when working on member reference image upload, list, or delete behavior, S3-backed image storage integration, multipart request handling, or member-owned image metadata persistence in the focus-server Spring Boot service. Do not use it for Kakao or JWT auth flows, broadcast lifecycle orchestration, CHZZK OAuth, gRPC metadata ingestion, or general local E2E setup unless image storage is the primary issue.
---

# Focus Member Image Storage

Use this skill for member-owned image upload and storage workflows.

## Quick start

1. Read `../../../agent/coding-rules.md`.
2. Read `references/entrypoints.md` for the file map and configuration keys.
3. Check controller contract first, then service, persistence, and storage integration.

## Workflow

- Keep image endpoints authenticated with `FocusMemberDetails`.
- Preserve the current REST contract at `/api/members/images`.
- The service flow is:
  1. validate the member
  2. upload the object through `ImageStorageService`
  3. persist `MemberImage` metadata
  4. return `MemberImageResponse`
- Delete flow removes the backing object and then deletes the database row.
- Prefer changing storage abstractions or S3 configuration in place rather than adding parallel upload paths.

## Validation

- Run targeted tests if the change is covered.
- For manual verification, test multipart upload, list, and delete with an authenticated member.
- Confirm both object storage behavior and metadata persistence when changing delete or upload logic.

## Boundaries

- Use `focus-auth-member` when the problem is token issuance, principal extraction, or member identity.
- Use `focus-local-e2e` when the issue only reproduces in a broader end-to-end environment.
