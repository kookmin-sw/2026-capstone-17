import json
from typing import Any, Protocol

try:
    import redis.asyncio as redis
except ImportError:  # pragma: no cover
    redis = None


class MetadataStore(Protocol):
    async def get_face_metadata(self, stream_id: str, pts_us: int) -> dict[str, Any] | None:
        ...

    async def close(self) -> None:
        ...


class RedisMetadataStore:
    def __init__(self, redis_url: str, key_template: str) -> None:
        self._redis_url = redis_url
        self._key_template = key_template
        self._client = None

    async def _ensure_client(self):
        if self._client is not None:
            return self._client
        if redis is None:
            return None
        self._client = redis.from_url(self._redis_url, decode_responses=True)
        return self._client

    async def get_face_metadata(self, stream_id: str, pts_us: int) -> dict[str, Any] | None:
        client = await self._ensure_client()
        if client is None:
            return None

        key = self._key_template.format(stream_id=stream_id, pts_us=pts_us)
        payload = await client.get(key)
        if not payload:
            return None

        try:
            return json.loads(payload)
        except json.JSONDecodeError:
            return None

    async def close(self) -> None:
        if self._client is not None:
            await self._client.aclose()
            self._client = None
