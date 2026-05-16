import asyncio
import logging
import shutil
import tempfile
import zipfile
from pathlib import Path
from urllib.parse import urlparse

from core.config import Settings

try:
    import boto3
except ImportError:  # pragma: no cover
    boto3 = None

logger = logging.getLogger(__name__)


class AvatarAssetResolver:
    """Materializes a Spring/RDB avatar object_key into a local avatar bank folder."""

    def __init__(self, settings: Settings) -> None:
        self._settings = settings

    async def prepare_avatar_bank(self, avatar_id: str | None, avatar_asset_key: str | None) -> str | None:
        if not avatar_id:
            return self._settings.avatar_bank_dir
        if not avatar_asset_key:
            return self._settings.avatar_bank_dir

        target_dir = Path(self._settings.avatar_cache_dir) / self._safe_path_part(avatar_id)
        if (target_dir / "profile.json").exists():
            return str(target_dir)

        await asyncio.to_thread(self._materialize_avatar, avatar_id, avatar_asset_key, target_dir)
        return str(target_dir)

    def _materialize_avatar(self, avatar_id: str, avatar_asset_key: str, target_dir: Path) -> None:
        logger.info(
            "avatar_asset_materialize_started avatar_id=%s asset_key=%s target=%s",
            avatar_id,
            avatar_asset_key,
            target_dir,
        )
        self._reset_dir(target_dir)

        if avatar_asset_key.startswith("file://"):
            self._materialize_local_path(Path(urlparse(avatar_asset_key).path), target_dir)
        elif avatar_asset_key.endswith(".zip"):
            self._download_and_extract_zip(avatar_asset_key, target_dir)
        else:
            self._download_s3_prefix(avatar_asset_key, target_dir)

        self._flatten_single_profile_dir(target_dir)
        if not (target_dir / "profile.json").exists():
            raise RuntimeError(
                "Avatar asset bundle must contain profile.json at its root or in a single nested avatar directory."
            )
        logger.info("avatar_asset_materialized avatar_id=%s target=%s", avatar_id, target_dir)

    def _materialize_local_path(self, source_path: Path, target_dir: Path) -> None:
        source = source_path.expanduser().resolve()
        if not source.exists():
            raise RuntimeError(f"Local avatar asset path was not found: {source}")
        if source.is_dir():
            shutil.copytree(source, target_dir, dirs_exist_ok=True)
            return
        if source.suffix.lower() == ".zip":
            self._extract_zip(source, target_dir)
            return
        raise RuntimeError(f"Unsupported local avatar asset path: {source}")

    def _download_and_extract_zip(self, avatar_asset_key: str, target_dir: Path) -> None:
        bucket, key = self._resolve_s3_location(avatar_asset_key)
        with tempfile.NamedTemporaryFile(suffix=".zip") as tmp:
            self._s3_client().download_file(bucket, key, tmp.name)
            self._extract_zip(Path(tmp.name), target_dir)

    def _download_s3_prefix(self, avatar_asset_key: str, target_dir: Path) -> None:
        bucket, key = self._resolve_s3_location(avatar_asset_key)
        prefix = key if key.endswith("/") else f"{key}/"
        client = self._s3_client()
        paginator = client.get_paginator("list_objects_v2")
        downloaded = 0

        for page in paginator.paginate(Bucket=bucket, Prefix=prefix):
            for item in page.get("Contents", []):
                object_key = item.get("Key")
                if not isinstance(object_key, str) or object_key.endswith("/"):
                    continue
                relative = object_key.removeprefix(prefix)
                if not relative:
                    continue
                destination = self._safe_join(target_dir, relative)
                destination.parent.mkdir(parents=True, exist_ok=True)
                client.download_file(bucket, object_key, str(destination))
                downloaded += 1

        if downloaded == 0:
            raise RuntimeError(f"No avatar asset objects were found under s3://{bucket}/{prefix}")

    def _resolve_s3_location(self, avatar_asset_key: str) -> tuple[str, str]:
        parsed = urlparse(avatar_asset_key)
        if parsed.scheme == "s3":
            bucket = parsed.netloc
            key = parsed.path.lstrip("/")
        else:
            bucket = self._settings.avatar_s3_bucket or self._settings.s3_bucket
            key = avatar_asset_key.lstrip("/")

        if not bucket:
            raise RuntimeError("AVATAR_S3_BUCKET or S3_BUCKET is required for S3 avatar assets.")
        if not key:
            raise RuntimeError("Avatar asset object_key is empty.")
        return bucket, key

    def _s3_client(self):
        if boto3 is None:
            raise RuntimeError("boto3 is not installed.")
        region = self._settings.avatar_s3_region or self._settings.s3_region
        return boto3.client("s3", region_name=region)

    def _extract_zip(self, zip_path: Path, target_dir: Path) -> None:
        with zipfile.ZipFile(zip_path) as archive:
            for member in archive.infolist():
                destination = self._safe_join(target_dir, member.filename)
                if member.is_dir():
                    destination.mkdir(parents=True, exist_ok=True)
                    continue
                destination.parent.mkdir(parents=True, exist_ok=True)
                with archive.open(member) as source, destination.open("wb") as output:
                    shutil.copyfileobj(source, output)

    def _flatten_single_profile_dir(self, target_dir: Path) -> None:
        if (target_dir / "profile.json").exists():
            return

        profile_paths = sorted(target_dir.rglob("profile.json"))
        if len(profile_paths) != 1:
            return

        profile_dir = profile_paths[0].parent
        tmp_dir = target_dir.parent / f".{target_dir.name}.flat"
        self._reset_dir(tmp_dir)
        shutil.copytree(profile_dir, tmp_dir, dirs_exist_ok=True)
        shutil.rmtree(target_dir)
        tmp_dir.rename(target_dir)

    def _reset_dir(self, path: Path) -> None:
        if path.exists():
            shutil.rmtree(path)
        path.mkdir(parents=True, exist_ok=True)

    def _safe_join(self, root: Path, relative_path: str) -> Path:
        destination = (root / relative_path).resolve()
        root_resolved = root.resolve()
        if root_resolved != destination and root_resolved not in destination.parents:
            raise RuntimeError(f"Unsafe avatar asset path: {relative_path}")
        return destination

    def _safe_path_part(self, raw_value: str) -> str:
        safe = "".join(char if char.isalnum() or char in "._-" else "_" for char in raw_value)
        return safe or "avatar"
