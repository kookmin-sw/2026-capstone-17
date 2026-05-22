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
    """Looks up face metadata Redis records produced by the Spring writer.

    Spring writes three records per detected frame:

    - ``broadcast:{id}:meta:{pts_us}`` — full payload keyed by the client's pts.
    - ``broadcast:{id}:meta:latest`` — most-recently written payload.
    - ``broadcast:{id}:meta:index`` — sorted set whose scores are the same pts values.

    PyAV rebases the first observed frame's pts to zero, so the FastAPI ``frame_pts_us``
    timeline drifts from the client's by a stable offset (publish-to-PyAV start delay).
    The sorted-set index is used to find the metadata record whose pts most closely
    corresponds to ``frame_pts_us + offset``, and the offset is refined from actual
    hits rather than from the always-future ``latest`` pointer.
    """

    OFFSET_RESYNC_THRESHOLD_US = 1_000_000
    OFFSET_EWMA_PREV_WEIGHT = 0.85
    OFFSET_EWMA_NEW_WEIGHT = 0.15
    OFFSET_LOG_DELTA_THRESHOLD_US = 250_000
    OFFSET_REFINE_DELTA_CAP_US = 100_000

    def __init__(
        self,
        redis_url: str,
        key_template: str,
        latest_key_template: str = "broadcast:{broadcast_id}:meta:latest",
        index_key_template: str | None = "broadcast:{broadcast_id}:meta:index",
        lookup_tolerance_us: int = 0,
        latest_tolerance_us: int = 0,
        fine_tolerance_us: int = 0,
        coarse_step_us: int = 500,
        auto_offset_max_us: int = 0,
        index_lookup_window_us: int = 200_000,
        latest_fallback_window_us: int = 200_000,
    ) -> None:
        self._redis_url = redis_url
        self._key_template = key_template
        self._latest_key_template = latest_key_template
        self._index_key_template = index_key_template or None
        self._lookup_tolerance_us = max(int(lookup_tolerance_us), 0)
        self._latest_tolerance_us = max(int(latest_tolerance_us), 0)
        self._fine_tolerance_us = max(int(fine_tolerance_us), 0)
        self._coarse_step_us = max(int(coarse_step_us), 1)
        self._auto_offset_max_us = max(int(auto_offset_max_us), 0)
        self._index_lookup_window_us = max(int(index_lookup_window_us), 0)
        self._latest_fallback_window_us = max(int(latest_fallback_window_us), 0)
        self._offset_us_by_broadcast: dict[str, int] = {}
        self._last_logged_offset_us_by_broadcast: dict[str, int] = {}
        self._index_initialized_broadcasts: set[str] = set()
        self._client = None

    async def _ensure_client(self):
        if self._client is not None:
            return self._client
        if redis is None:
            return None
        self._client = redis.from_url(self._redis_url, decode_responses=True)
        return self._client

    def set_initial_offset(self, broadcast_id: str, offset_us: int) -> None:
        """Seed the metadata offset before the first lookup.

        Used by the pipeline to pass the PyAV `_base_pts_us` (the raw pts of
        the first observed frame). When MediaMTX preserves RTMP timestamps,
        this is the true delta between PyAV-rebased pts and client metadata pts,
        so we can hit the right Redis keys from frame zero without bootstrapping
        from a stale `meta:0` or from a biased latest-diff sample.
        """
        if broadcast_id in self._offset_us_by_broadcast:
            return
        seeded_offset_us = int(offset_us)
        self._offset_us_by_broadcast[broadcast_id] = seeded_offset_us
        self._last_logged_offset_us_by_broadcast[broadcast_id] = seeded_offset_us
        logger.info(
            "metadata_initial_offset_seeded broadcast_id=%s offset_us=%s",
            broadcast_id,
            seeded_offset_us,
        )

    async def get_face_metadata(self, broadcast_id: str, pts_us: int) -> dict[str, Any] | None:
        client = await self._ensure_client()
        if client is None:
            return None

        frame_pts_us = int(pts_us)
        learned_offset_us = self._offset_us_by_broadcast.get(broadcast_id)

        # Exact lookup is only safe once we know the offset is near zero — otherwise it can
        # accidentally hit an old `meta:0` from a previous frame and learn the wrong offset.
        # When no offset has been learned yet, skip exact and let the ZSET index drive learning.
        if learned_offset_us is not None and abs(learned_offset_us) <= self._index_lookup_window_us:
            exact_payload = await self._lookup_exact_payload(client, broadcast_id, frame_pts_us)
            if exact_payload is not None:
                self._refine_offset(broadcast_id, 0)
                return self._normalize_payload_pts_us(exact_payload, frame_pts_us)

        if self._index_key_template:
            index_payload = await self._lookup_via_index(client, broadcast_id, frame_pts_us)
            if index_payload is not None:
                return index_payload

        return await self._lookup_via_latest_fallback(client, broadcast_id, frame_pts_us)

    async def _lookup_exact_payload(
        self,
        client,
        broadcast_id: str,
        frame_pts_us: int,
    ) -> dict[str, Any] | None:
        candidate_keys = [
            self._build_key(broadcast_id=broadcast_id, pts_us=candidate_pts_us)
            for candidate_pts_us in self._candidate_pts_values(frame_pts_us)
        ]
        if not candidate_keys:
            return None
        payloads = await client.mget(candidate_keys)
        _, payload = self._first_matched_payload(candidate_keys, payloads)
        return self._decode_payload(payload)

    async def _lookup_via_index(
        self,
        client,
        broadcast_id: str,
        frame_pts_us: int,
    ) -> dict[str, Any] | None:
        index_key = self._build_index_key(broadcast_id)
        await self._ensure_index_bootstrapped(client, broadcast_id, index_key)

        selection = await self._select_index_pts(client, broadcast_id, index_key, frame_pts_us)
        if selection is None:
            return None
        matched_pts_us, from_narrow_window = selection

        payload_str = await client.get(self._build_key(broadcast_id, matched_pts_us))
        decoded = self._decode_payload(payload_str)
        if decoded is None:
            # Stale index entry — clean it up so FIFO advances next frame.
            await client.zremrangebyscore(index_key, "-inf", matched_pts_us)
            return None

        if from_narrow_window or broadcast_id not in self._offset_us_by_broadcast:
            # Only refine from narrow-window matches. Wide-search hits return the
            # ZSET min, which races ahead of frame_pts when client metadata write
            # rate exceeds PyAV consume rate — feeding that back into the EWMA
            # caused runaway offset drift in production logs. Bootstrap once when
            # offset is still unknown so the first sample lands somewhere sane.
            self._refine_offset(broadcast_id, matched_pts_us - frame_pts_us)
        await client.zremrangebyscore(index_key, "-inf", matched_pts_us)
        return self._normalize_payload_pts_us(decoded, frame_pts_us)

    async def _select_index_pts(
        self,
        client,
        broadcast_id: str,
        index_key: str,
        frame_pts_us: int,
    ) -> tuple[int, bool] | None:
        offset_us = self._offset_us_by_broadcast.get(broadcast_id)
        if offset_us is not None and self._index_lookup_window_us > 0:
            target_pts_us = frame_pts_us + offset_us
            min_score = max(target_pts_us - self._index_lookup_window_us, 0)
            max_score = target_pts_us + self._index_lookup_window_us
            candidates = await client.zrangebyscore(
                index_key, min_score, max_score, withscores=True
            )
            chosen = self._closest_score_to(candidates, target_pts_us)
            if chosen is not None:
                return chosen, True

        wide_max = frame_pts_us + max(self._auto_offset_max_us, self._index_lookup_window_us, 1)
        wide_candidates = await client.zrangebyscore(
            index_key,
            frame_pts_us,
            wide_max,
            start=0,
            num=1,
            withscores=True,
        )
        if wide_candidates:
            _, score = wide_candidates[0]
            return int(float(score)), False
        return None

    async def _ensure_index_bootstrapped(self, client, broadcast_id: str, index_key: str) -> None:
        if broadcast_id in self._index_initialized_broadcasts:
            return
        self._index_initialized_broadcasts.add(broadcast_id)

        if self._auto_offset_max_us <= 0:
            return

        latest_items = await client.zrange(index_key, -1, -1, withscores=True)
        if not latest_items:
            return
        _, latest_score = latest_items[0]
        latest_pts_us = int(float(latest_score))
        cutoff_pts_us = latest_pts_us - self._auto_offset_max_us
        if cutoff_pts_us <= 0:
            return
        removed = await client.zremrangebyscore(index_key, "-inf", f"({cutoff_pts_us}")
        if removed:
            logger.info(
                "metadata_index_bootstrap_trimmed broadcast_id=%s latest_pts_us=%s cutoff_pts_us=%s removed=%s",
                broadcast_id,
                latest_pts_us,
                cutoff_pts_us,
                removed,
            )

    async def _lookup_via_latest_fallback(
        self,
        client,
        broadcast_id: str,
        frame_pts_us: int,
    ) -> dict[str, Any] | None:
        latest_payload_str = await client.get(self._build_latest_key(broadcast_id))
        decoded_latest = self._decode_payload(latest_payload_str)
        if decoded_latest is None:
            logger.info(
                "metadata_miss broadcast_id=%s frame_pts_us=%s redis_latest_pts_us=None diff_us=None miss_reason=no_latest",
                broadcast_id,
                frame_pts_us,
            )
            return None

        latest_pts_us = self._extract_payload_pts_us(decoded_latest)
        if latest_pts_us is None:
            logger.info(
                "metadata_miss broadcast_id=%s frame_pts_us=%s redis_latest_pts_us=None diff_us=None miss_reason=invalid_latest",
                broadcast_id,
                frame_pts_us,
            )
            return None

        offset_us = self._offset_us_by_broadcast.get(broadcast_id)

        if offset_us is None and self._auto_offset_max_us > 0:
            offset_candidate_us = latest_pts_us - frame_pts_us
            if 0 < offset_candidate_us <= self._auto_offset_max_us:
                auto_payload = await self._lookup_exact_payload(client, broadcast_id, latest_pts_us)
                if auto_payload is not None:
                    self._refine_offset(broadcast_id, offset_candidate_us)
                    return self._normalize_payload_pts_us(auto_payload, frame_pts_us)

        diff_us = frame_pts_us - latest_pts_us
        if self._latest_tolerance_us <= 0:
            logger.info(
                "metadata_miss broadcast_id=%s frame_pts_us=%s redis_latest_pts_us=%s diff_us=%s miss_reason=tolerance_zero",
                broadcast_id,
                frame_pts_us,
                latest_pts_us,
                diff_us,
            )
            return None

        if abs(diff_us) > self._latest_tolerance_us:
            miss_reason = "latest_too_old" if diff_us > 0 else "latest_too_new"
            logger.info(
                "metadata_miss broadcast_id=%s frame_pts_us=%s redis_latest_pts_us=%s diff_us=%s miss_reason=%s",
                broadcast_id,
                frame_pts_us,
                latest_pts_us,
                diff_us,
                miss_reason,
            )
            return None

        if offset_us is None:
            offset_candidate_us = latest_pts_us - frame_pts_us
            if 0 < offset_candidate_us <= max(self._auto_offset_max_us, 1):
                self._refine_offset(broadcast_id, offset_candidate_us)
            return self._normalize_payload_pts_us(decoded_latest, frame_pts_us)

        target_pts_us = frame_pts_us + offset_us
        if self._latest_fallback_window_us > 0 and abs(latest_pts_us - target_pts_us) > self._latest_fallback_window_us:
            logger.info(
                "metadata_miss broadcast_id=%s frame_pts_us=%s redis_latest_pts_us=%s diff_us=%s miss_reason=latest_out_of_offset_window offset_us=%s",
                broadcast_id,
                frame_pts_us,
                latest_pts_us,
                diff_us,
                offset_us,
            )
            return None

        return self._normalize_payload_pts_us(decoded_latest, frame_pts_us)

    def _refine_offset(self, broadcast_id: str, offset_candidate_us: int) -> None:
        previous_offset_us = self._offset_us_by_broadcast.get(broadcast_id)
        if previous_offset_us is None:
            learned_offset_us = int(offset_candidate_us)
        elif abs(offset_candidate_us - previous_offset_us) > self.OFFSET_RESYNC_THRESHOLD_US:
            # Large jumps almost always come from misleading samples (ZSET pileup,
            # late metadata bursts). Cap the movement to one delta step instead of
            # snap-resyncing to the noisy candidate.
            direction = 1 if offset_candidate_us > previous_offset_us else -1
            learned_offset_us = previous_offset_us + direction * self.OFFSET_REFINE_DELTA_CAP_US
        else:
            clipped_candidate_us = max(
                previous_offset_us - self.OFFSET_REFINE_DELTA_CAP_US,
                min(previous_offset_us + self.OFFSET_REFINE_DELTA_CAP_US, int(offset_candidate_us)),
            )
            learned_offset_us = int(
                previous_offset_us * self.OFFSET_EWMA_PREV_WEIGHT
                + clipped_candidate_us * self.OFFSET_EWMA_NEW_WEIGHT
            )

        self._offset_us_by_broadcast[broadcast_id] = learned_offset_us

        last_logged_offset_us = self._last_logged_offset_us_by_broadcast.get(broadcast_id)
        if last_logged_offset_us is None or abs(learned_offset_us - last_logged_offset_us) >= self.OFFSET_LOG_DELTA_THRESHOLD_US:
            logger.info(
                "metadata_offset_learned broadcast_id=%s offset_us=%s candidate_us=%s previous_us=%s",
                broadcast_id,
                learned_offset_us,
                offset_candidate_us,
                previous_offset_us,
            )
            self._last_logged_offset_us_by_broadcast[broadcast_id] = learned_offset_us

    def _closest_score_to(
        self,
        candidates: list[tuple[str, float]],
        target_pts_us: int,
    ) -> int | None:
        best_score: float | None = None
        best_distance: float | None = None
        for _, score in candidates:
            distance = abs(score - target_pts_us)
            if best_distance is None or distance < best_distance:
                best_distance = distance
                best_score = score
        if best_score is None:
            return None
        return int(best_score)

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

    def _extract_payload_pts_us(self, payload: dict[str, Any]) -> int | None:
        raw_pts_us = payload.get("pts_us", payload.get("ptsUs"))
        try:
            return int(raw_pts_us)
        except (TypeError, ValueError):
            return None

    def _normalize_payload_pts_us(self, payload: dict[str, Any], pts_us: int) -> dict[str, Any]:
        normalized = dict(payload)
        normalized_pts_us = int(pts_us)
        normalized["pts_us"] = normalized_pts_us
        normalized["ptsUs"] = normalized_pts_us
        return normalized

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

    def _build_index_key(self, broadcast_id: str) -> str:
        assert self._index_key_template is not None
        try:
            return self._index_key_template.format(broadcast_id=broadcast_id)
        except KeyError:
            return self._index_key_template.format(stream_id=broadcast_id)

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
