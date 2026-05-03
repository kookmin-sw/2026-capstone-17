import asyncio
import logging
from pathlib import Path

from core.config import Settings

try:
    import boto3
except ImportError:  # pragma: no cover
    boto3 = None

logger = logging.getLogger(__name__)


class S3StorageClient:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings

    async def upload_analysis_mp4(self, broadcast_id: str, file_path: str) -> str:
        if self._settings.analysis_skip_s3:
            logger.info(
                "analysis_mp4_upload_skipped broadcast_id=%s file=%s reason=analysis_skip_s3",
                broadcast_id,
                file_path,
            )
            return f"{self._settings.hls_public_base_url.rstrip('/')}/{broadcast_id}/archive/analysis.mp4"

        if boto3 is None:
            raise RuntimeError("boto3 is not installed.")
        if not self._settings.s3_bucket:
            raise RuntimeError("S3_BUCKET is required for analysis upload.")

        key = f"broadcasts/{broadcast_id}/archive/analysis.mp4"
        await asyncio.to_thread(self._upload_file, file_path, key)
        logger.info(
            "analysis_mp4_uploaded broadcast_id=%s bucket=%s key=%s",
            broadcast_id,
            self._settings.s3_bucket,
            key,
        )
        return self._public_url(key)

    def _upload_file(self, file_path: str, key: str) -> None:
        client = boto3.client("s3", region_name=self._settings.s3_region)
        client.upload_file(
            Filename=str(Path(file_path)),
            Bucket=self._settings.s3_bucket,
            Key=key,
            ExtraArgs={"ContentType": "video/mp4"},
        )

    def _public_url(self, key: str) -> str:
        if self._settings.s3_public_base_url:
            return f"{self._settings.s3_public_base_url.rstrip('/')}/{key}"
        return f"https://{self._settings.s3_bucket}.s3.{self._settings.s3_region}.amazonaws.com/{key}"
