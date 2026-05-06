# Local E2E entry points

## Primary runbooks

- `docs/local-e2e-runbook.md`
- `docs/grpc-server-local-runbook.md`
- `docs/grpc-face-metadata-api.md`

## Supporting files

- `docker-compose.yaml`
- `docker-compose.local-db.yaml`
- `scripts/local_dev_jwt.py`
- `src/main/resources/application.yaml`
- `build.gradle.kts`

## Typical commands

- `docker compose up -d`
- `bash ./gradlew bootRun`
- `python3 scripts/local_dev_jwt.py --secret-b64 "$JWT_SECRET" --member-id <memberId> --name <nickname>`
- `grpcurl -plaintext 127.0.0.1:8080 list`

## Behavioral notes

- Broadcast E2E also depends on the sibling `focus-fast-api` repository referenced in the runbook.
- Local gRPC runs in servlet mode on port `8080`.
- Local HLS serving comes from FastAPI in the current documented flow.
