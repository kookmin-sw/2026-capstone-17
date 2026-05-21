import asyncio
import logging
from pathlib import Path
from urllib.parse import urlparse

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
            storage_url = f"{self._settings.hls_public_base_url.rstrip('/')}/{broadcast_id}/archive/analysis.mp4"
            logger.info(
                "analysis_mp4_upload_skipped broadcast_id=%s file=%s reason=analysis_skip_s3 final_storage_url=%s",
                broadcast_id,
                file_path,
                storage_url,
            )
            return storage_url

        if boto3 is None:
            raise RuntimeError("boto3 is not installed.")
        if not self._settings.s3_bucket:
            raise RuntimeError("S3_BUCKET is required for analysis upload.")

        key = f"broadcasts/{broadcast_id}/archive/analysis.mp4"
        await asyncio.to_thread(self._upload_file, file_path, key)
        storage_url = self._public_url(key)
        logger.info(
            "analysis_mp4_uploaded broadcast_id=%s bucket=%s key=%s final_storage_url=%s",
            broadcast_id,
            self._settings.s3_bucket,
            key,
            storage_url,
        )
        return storage_url

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
            base_url = self._settings.s3_public_base_url.rstrip("/")
            self._ensure_http_url(base_url, "S3_PUBLIC_BASE_URL")
            return f"{base_url}/{key}"
        url = f"https://{self._settings.s3_bucket}.s3.{self._settings.s3_region}.amazonaws.com/{key}"
        self._ensure_http_url(url, "generated S3 public URL")
        return url

    def _ensure_http_url(self, url: str, label: str) -> None:
        parsed = urlparse(url)
        if parsed.scheme not in {"http", "https"} or not parsed.netloc:
            raise RuntimeError(
                f"{label} must be an absolute http(s) URL when ANALYSIS_SKIP_S3=false. value={url}"
            )
