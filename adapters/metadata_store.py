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
        latest_key_template: str = "broadcast:{broadcast_id}:meta:latest",
        lookup_tolerance_us: int = 0,
        latest_tolerance_us: int = 0,
        fine_tolerance_us: int = 0,
        coarse_step_us: int = 500,
    ) -> None:
        self._redis_url = redis_url
        self._key_template = key_template
        self._latest_key_template = latest_key_template
        self._lookup_tolerance_us = max(int(lookup_tolerance_us), 0)
        self._latest_tolerance_us = max(int(latest_tolerance_us), 0)
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

    def _build_latest_key(self, broadcast_id: str) -> str:
        try:
            return self._latest_key_template.format(broadcast_id=broadcast_id)
        except KeyError:
            return self._latest_key_template.format(stream_id=broadcast_id)

    async def get_face_metadata(self, broadcast_id: str, pts_us: int) -> dict[str, Any] | None:
        client = await self._ensure_client()
        if client is None:
            return None

        candidate_keys = [
            self._build_key(broadcast_id=broadcast_id, pts_us=candidate_pts_us)
            for candidate_pts_us in self._candidate_pts_values(pts_us)
        ]
        payloads = await client.mget(candidate_keys)
        matched_key, payload = self._first_matched_payload(candidate_keys, payloads)
        if payload and matched_key:
            await client.delete(matched_key)
            return self._decode_payload(payload)

        latest_payload = await client.get(self._build_latest_key(broadcast_id))
        return self._decode_latest_payload(latest_payload, pts_us)

    def _first_matched_payload(
        self,
        candidate_keys: list[str],
        payloads: list[str | None],
    ) -> tuple[str | None, str | None]:
        for key, payload in zip(candidate_keys, payloads):
            if payload:
                return key, payload
        return None, None

    def _decode_payload(self, payload: str | None) -> dict[str, Any] | None:
        if not payload:
            return None
        try:
            return json.loads(payload)
        except json.JSONDecodeError:
            return None

    def _decode_latest_payload(self, payload: str | None, pts_us: int) -> dict[str, Any] | None:
        decoded_payload = self._decode_payload(payload)
        if decoded_payload is None or self._latest_tolerance_us <= 0:
            return None
        payload_pts_us = self._extract_payload_pts_us(decoded_payload)
        if payload_pts_us is None:
            return None
        if abs(payload_pts_us - int(pts_us)) > self._latest_tolerance_us:
            return None
        return decoded_payload

    def _extract_payload_pts_us(self, payload: dict[str, Any]) -> int | None:
        raw_pts_us = payload.get("pts_us", payload.get("ptsUs"))
        try:
            return int(raw_pts_us)
        except (TypeError, ValueError):
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
