# Member image storage entry points

## Primary files

- `src/main/kotlin/com/capstone/focus/api/image/controller/MemberImageController.kt`
- `src/main/kotlin/com/capstone/focus/api/image/service/MemberImageService.kt`
- `src/main/kotlin/com/capstone/focus/api/image/dto/response/MemberImageResponse.kt`
- `src/main/kotlin/com/capstone/focus/common/external/s3/S3ImageStorageService.kt`
- `src/main/kotlin/com/capstone/focus/common/config/S3Config.kt`
- `src/main/kotlin/com/capstone/focus/common/config/S3Properties.kt`
- `src/main/kotlin/com/capstone/focus/domain/entity/MemberImage.kt`
- `src/main/kotlin/com/capstone/focus/domain/repository/MemberImageRepository.kt`

## Configuration

- `src/main/resources/application.yaml`
- Required env vars usually include `AWS_S3_BUCKET`, `AWS_REGION`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`
- Optional custom endpoints use `AWS_S3_ENDPOINT` and `AWS_S3_PUBLIC_BASE_URL`

## Useful checks

- `./gradlew test`
- Manual multipart request against `POST /api/members/images`

## Behavioral notes

- Upload stores both object metadata and member ownership.
- Delete removes the S3 object through the storage service before deleting the row.
