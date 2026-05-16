import json
from typing import Any, Protocol

try:
    import redis.asyncio as redis
except ImportError:  # pragma: no cover
    redis = None


class MetadataStore(Protocol):
    async def get_face_metadata(self, broadcast_id: str, pts_us: int) -> dict[str, Any] | None:
        ...

    async def close(self) -> None:
        ...


class RedisMetadataStore:
    def __init__(
        self,
        redis_url: str,
        key_template: str,
        lookup_tolerance_us: int = 0,
        fine_tolerance_us: int = 0,
        coarse_step_us: int = 500,
    ) -> None:
        self._redis_url = redis_url
        self._key_template = key_template
        self._lookup_tolerance_us = max(int(lookup_tolerance_us), 0)
        self._fine_tolerance_us = max(int(fine_tolerance_us), 0)
        self._coarse_step_us = max(int(coarse_step_us), 1)
        self._client = None

    async def _ensure_client(self):
        if self._client is not None:
            return self._client
        if redis is None:
            return None
        self._client = redis.from_url(self._redis_url, decode_responses=True)
        return self._client

    def _build_key(self, broadcast_id: str, pts_us: int) -> str:
        try:
            return self._key_template.format(broadcast_id=broadcast_id, pts_us=pts_us)
        except KeyError:
            # Backward compatibility with old template: stream:{stream_id}:meta:{pts_us}
            return self._key_template.format(stream_id=broadcast_id, pts_us=pts_us)

    async def get_face_metadata(self, broadcast_id: str, pts_us: int) -> dict[str, Any] | None:
        client = await self._ensure_client()
        if client is None:
            return None

        payload = None
        matched_key = None
        for candidate_pts_us in self._candidate_pts_values(pts_us):
            key = self._build_key(broadcast_id=broadcast_id, pts_us=candidate_pts_us)
            payload = await client.get(key)
            if payload:
                matched_key = key
                break
        if not payload or matched_key is None:
            return None

        # Clean up immediately after reading to prevent Redis memory bloat.
        await client.delete(matched_key)

        try:
            return json.loads(payload)
        except json.JSONDecodeError:
            return None

    def _candidate_pts_values(self, pts_us: int) -> list[int]:
        """Return exact PTS first, then nearby values for encoder clock drift.

        RTSP/H.264 timestamps commonly come from a 90 kHz media clock, while the
        client metadata contract stores microseconds. That conversion can create
        tiny differences, so exact Redis lookup remains first but we also probe a
        narrow fine window and a coarse fallback window.
        """
        exact = int(pts_us)
        if self._lookup_tolerance_us <= 0:
            return [exact]

        candidates: set[int] = {exact}
        fine_window = min(self._fine_tolerance_us, self._lookup_tolerance_us)
        for delta_us in range(-fine_window, fine_window + 1):
            candidates.add(exact + delta_us)

        if self._lookup_tolerance_us > fine_window:
            for delta_us in range(
                -self._lookup_tolerance_us,
                self._lookup_tolerance_us + 1,
                self._coarse_step_us,
            ):
                candidates.add(exact + delta_us)

        return sorted(
            (candidate for candidate in candidates if candidate >= 0),
            key=lambda candidate: (abs(candidate - exact), candidate),
        )

    async def close(self) -> None:
        if self._client is not None:
            await self._client.aclose()
            self._client = None
