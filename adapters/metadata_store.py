import json
import logging
from typing import Any, Protocol

logger = logging.getLogger(__name__)

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
        auto_offset_max_us: int = 0,
    ) -> None:
        self._redis_url = redis_url
        self._key_template = key_template
        self._latest_key_template = latest_key_template
        self._lookup_tolerance_us = max(int(lookup_tolerance_us), 0)
        self._latest_tolerance_us = max(int(latest_tolerance_us), 0)
        self._fine_tolerance_us = max(int(fine_tolerance_us), 0)
        self._coarse_step_us = max(int(coarse_step_us), 1)
        self._auto_offset_max_us = max(int(auto_offset_max_us), 0)
        self._offset_us_by_broadcast: dict[str, int] = {}
        self._last_logged_offset_us_by_broadcast: dict[str, int] = {}
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

        learned_offset_us = self._offset_us_by_broadcast.get(broadcast_id)
        if learned_offset_us is not None:
            offset_payload = await self._lookup_payload(client, broadcast_id, int(pts_us) + learned_offset_us)
            if offset_payload is not None:
                return offset_payload

        exact_payload = await self._lookup_payload(client, broadcast_id, pts_us)
        if exact_payload is not None:
            return exact_payload

        latest_payload = await client.get(self._build_latest_key(broadcast_id))
        decoded_latest_payload = self._decode_payload(latest_payload)
        latest_pts_us = self._extract_payload_pts_us(decoded_latest_payload) if decoded_latest_payload else None
        if decoded_latest_payload is not None and latest_pts_us is not None:
            offset_payload = await self._try_auto_offset_lookup(
                client,
                broadcast_id=broadcast_id,
                frame_pts_us=int(pts_us),
                latest_pts_us=latest_pts_us,
                latest_payload=decoded_latest_payload,
            )
            if offset_payload is not None:
                return offset_payload

        return self._decode_latest_payload_and_log(latest_payload, pts_us, broadcast_id)

    async def _lookup_payload(self, client, broadcast_id: str, pts_us: int) -> dict[str, Any] | None:
        candidate_keys = [
            self._build_key(broadcast_id=broadcast_id, pts_us=candidate_pts_us)
            for candidate_pts_us in self._candidate_pts_values(pts_us)
        ]
        payloads = await client.mget(candidate_keys)
        _, payload = self._first_matched_payload(candidate_keys, payloads)
        return self._decode_payload(payload)

    async def _try_auto_offset_lookup(
        self,
        client,
        *,
        broadcast_id: str,
        frame_pts_us: int,
        latest_pts_us: int,
        latest_payload: dict[str, Any],
    ) -> dict[str, Any] | None:
        if self._auto_offset_max_us <= 0:
            return None
        offset_candidate_us = latest_pts_us - frame_pts_us
        if offset_candidate_us <= 0 or offset_candidate_us > self._auto_offset_max_us:
            return None

        learned_offset_us = self._learn_offset(broadcast_id, offset_candidate_us)
        adjusted_pts_us = frame_pts_us + learned_offset_us
        offset_payload = await self._lookup_payload(client, broadcast_id, adjusted_pts_us)
        if offset_payload is not None:
            return offset_payload
        return latest_payload

    def _learn_offset(self, broadcast_id: str, offset_candidate_us: int) -> int:
        previous_offset_us = self._offset_us_by_broadcast.get(broadcast_id)
        if previous_offset_us is None:
            learned_offset_us = offset_candidate_us
        else:
            learned_offset_us = int((previous_offset_us * 0.85) + (offset_candidate_us * 0.15))

        self._offset_us_by_broadcast[broadcast_id] = learned_offset_us
        last_logged_offset_us = self._last_logged_offset_us_by_broadcast.get(broadcast_id)
        if last_logged_offset_us is None or abs(learned_offset_us - last_logged_offset_us) >= 250000:
            logger.info(
                "metadata_auto_offset_learned broadcast_id=%s offset_us=%s candidate_us=%s previous_us=%s",
                broadcast_id,
                learned_offset_us,
                offset_candidate_us,
                previous_offset_us,
            )
            self._last_logged_offset_us_by_broadcast[broadcast_id] = learned_offset_us
        return learned_offset_us

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

    def _decode_latest_payload_and_log(self, payload: str | None, pts_us: int, broadcast_id: str) -> dict[str, Any] | None:
        decoded_payload = self._decode_payload(payload)
        if decoded_payload is None:
            logger.info("metadata_miss broadcast_id=%s frame_pts_us=%s redis_latest_pts_us=None diff_us=None miss_reason=no_latest", broadcast_id, pts_us)
            return None

        payload_pts_us = self._extract_payload_pts_us(decoded_payload)
        if payload_pts_us is None:
            logger.info("metadata_miss broadcast_id=%s frame_pts_us=%s redis_latest_pts_us=None diff_us=None miss_reason=invalid_latest", broadcast_id, pts_us)
            return None

        diff_us = int(pts_us) - payload_pts_us
        if self._latest_tolerance_us <= 0:
            logger.info("metadata_miss broadcast_id=%s frame_pts_us=%s redis_latest_pts_us=%s diff_us=%s miss_reason=tolerance_zero", broadcast_id, pts_us, payload_pts_us, diff_us)
            return None

        if abs(diff_us) > self._latest_tolerance_us:
            miss_reason = "latest_too_old" if diff_us > 0 else "latest_too_new"
            logger.info("metadata_miss broadcast_id=%s frame_pts_us=%s redis_latest_pts_us=%s diff_us=%s miss_reason=%s", broadcast_id, pts_us, payload_pts_us, diff_us, miss_reason)
            return None

        return decoded_payload

    def _decode_latest_payload(self, payload: str | None, pts_us: int) -> dict[str, Any] | None:
        return self._decode_latest_payload_and_log(payload, pts_us, "unknown")

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
