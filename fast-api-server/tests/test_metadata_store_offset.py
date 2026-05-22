import json
import unittest

from adapters.metadata_store import RedisMetadataStore


class _RedisClientStub:
    """Minimal in-memory stub for the redis-py async interface used by RedisMetadataStore."""

    def __init__(self, values: dict[str, str], zsets: dict[str, list[tuple[str, float]]] | None = None) -> None:
        self.values = dict(values)
        self.zsets: dict[str, list[tuple[str, float]]] = {
            key: sorted(items, key=lambda item: item[1]) for key, items in (zsets or {}).items()
        }
        self.closed = False

    async def mget(self, keys: list[str]) -> list[str | None]:
        return [self.values.get(key) for key in keys]

    async def get(self, key: str) -> str | None:
        return self.values.get(key)

    async def zrange(self, key: str, start: int, end: int, withscores: bool = False):
        items = list(self.zsets.get(key, []))
        size = len(items)
        if size == 0:
            return []
        if end < 0:
            end = size + end
        if start < 0:
            start = size + start
        end = min(end, size - 1)
        if start > end:
            return []
        chunk = items[start : end + 1]
        if not withscores:
            return [member for member, _ in chunk]
        return chunk

    async def zrangebyscore(
        self,
        key: str,
        min_score,
        max_score,
        start: int | None = None,
        num: int | None = None,
        withscores: bool = False,
    ):
        min_value = self._parse_score(min_score, default=float("-inf"))
        max_value = self._parse_score(max_score, default=float("inf"))
        min_inclusive = not (isinstance(min_score, str) and min_score.startswith("("))
        max_inclusive = not (isinstance(max_score, str) and max_score.startswith("("))
        items = self.zsets.get(key, [])
        filtered: list[tuple[str, float]] = []
        for member, score in items:
            if min_inclusive and score < min_value:
                continue
            if not min_inclusive and score <= min_value:
                continue
            if max_inclusive and score > max_value:
                continue
            if not max_inclusive and score >= max_value:
                continue
            filtered.append((member, score))
        if start is not None or num is not None:
            offset = start or 0
            limit = num if num is not None else len(filtered)
            filtered = filtered[offset : offset + limit]
        if withscores:
            return filtered
        return [member for member, _ in filtered]

    async def zremrangebyscore(self, key: str, min_score, max_score) -> int:
        min_value = self._parse_score(min_score, default=float("-inf"))
        max_value = self._parse_score(max_score, default=float("inf"))
        min_inclusive = not (isinstance(min_score, str) and min_score.startswith("("))
        max_inclusive = not (isinstance(max_score, str) and max_score.startswith("("))
        items = self.zsets.get(key)
        if not items:
            return 0
        survivors: list[tuple[str, float]] = []
        removed = 0
        for member, score in items:
            should_remove = True
            if min_inclusive:
                if score < min_value:
                    should_remove = False
            else:
                if score <= min_value:
                    should_remove = False
            if max_inclusive:
                if score > max_value:
                    should_remove = False
            else:
                if score >= max_value:
                    should_remove = False
            if should_remove:
                removed += 1
            else:
                survivors.append((member, score))
        self.zsets[key] = survivors
        return removed

    async def aclose(self) -> None:
        self.closed = True

    @staticmethod
    def _parse_score(score, default: float) -> float:
        if score is None:
            return default
        if isinstance(score, (int, float)):
            return float(score)
        text = str(score)
        if text == "-inf":
            return float("-inf")
        if text == "+inf" or text == "inf":
            return float("inf")
        if text.startswith("("):
            text = text[1:]
        return float(text)


class RedisMetadataStoreOffsetTest(unittest.IsolatedAsyncioTestCase):
    def _store(
        self,
        values: dict[str, str],
        zsets: dict[str, list[tuple[str, float]]] | None = None,
        *,
        lookup_tolerance_us: int = 0,
        latest_tolerance_us: int = 1000,
        auto_offset_max_us: int = 8_000_000,
        index_lookup_window_us: int = 200_000,
        latest_fallback_window_us: int = 200_000,
    ) -> RedisMetadataStore:
        store = RedisMetadataStore(
            redis_url="redis://localhost:6379/0",
            key_template="broadcast:{broadcast_id}:meta:{pts_us}",
            latest_key_template="broadcast:{broadcast_id}:meta:latest",
            index_key_template="broadcast:{broadcast_id}:meta:index",
            lookup_tolerance_us=lookup_tolerance_us,
            latest_tolerance_us=latest_tolerance_us,
            auto_offset_max_us=auto_offset_max_us,
            index_lookup_window_us=index_lookup_window_us,
            latest_fallback_window_us=latest_fallback_window_us,
        )
        store._client = _RedisClientStub(values=values, zsets=zsets)
        return store

    async def test_index_returns_oldest_metadata_with_biased_latest(self) -> None:
        # Scenario: client_pts is 5s ahead of PyAV frame_pts because PyAV rebased the first observed frame to 0.
        # Latest pointer is at 5.5s (pipeline lag inflates latest above the metadata that actually belongs to frame_pts=0).
        # The biased EWMA-from-latest path would learn offset=5_500_000 and miss every real key.
        # The ZSET FIFO bootstrap must return the OLDEST key >= frame_pts, which corresponds to this frame.
        keys: dict[str, str] = {}
        zindex: list[tuple[str, float]] = []
        for index in range(16):
            pts = 5_000_000 + index * 33_333
            payload = {"pts_us": pts, "faces": [{"tracking_id": index}]}
            keys[f"broadcast:bc:meta:{pts}"] = json.dumps(payload)
            zindex.append((str(pts), float(pts)))
        latest_payload = {"pts_us": 5_500_000, "faces": [{"tracking_id": 99}]}
        keys["broadcast:bc:meta:latest"] = json.dumps(latest_payload)
        store = self._store(values=keys, zsets={"broadcast:bc:meta:index": zindex})

        first = await store.get_face_metadata("bc", 0)
        self.assertIsNotNone(first)
        self.assertEqual(first["pts_us"], 0)
        self.assertEqual(first["faces"][0]["tracking_id"], 0)
        self.assertEqual(store._offset_us_by_broadcast["bc"], 5_000_000)

        second = await store.get_face_metadata("bc", 33_333)
        self.assertIsNotNone(second)
        self.assertEqual(second["faces"][0]["tracking_id"], 1)

        third = await store.get_face_metadata("bc", 66_666)
        self.assertIsNotNone(third)
        self.assertEqual(third["faces"][0]["tracking_id"], 2)

    async def test_index_consumes_entries_to_prevent_replay(self) -> None:
        keys = {
            "broadcast:bc:meta:5000000": json.dumps({"pts_us": 5_000_000, "faces": [{"tracking_id": 0}]}),
            "broadcast:bc:meta:5033333": json.dumps({"pts_us": 5_033_333, "faces": [{"tracking_id": 1}]}),
            "broadcast:bc:meta:latest": json.dumps({"pts_us": 5_033_333, "faces": [{"tracking_id": 1}]}),
        }
        zindex = [("5000000", 5_000_000.0), ("5033333", 5_033_333.0)]
        store = self._store(values=keys, zsets={"broadcast:bc:meta:index": zindex})

        first = await store.get_face_metadata("bc", 0)
        self.assertEqual(first["faces"][0]["tracking_id"], 0)
        # First entry must be drained so subsequent FIFO pulls advance.
        remaining = await store._client.zrange("broadcast:bc:meta:index", 0, -1, withscores=True)
        self.assertEqual([score for _, score in remaining], [5_033_333.0])

    async def test_latest_fallback_gated_when_far_from_offset_target(self) -> None:
        # ZSET is empty (writer hiccup), but latest is 5s ahead of (frame + offset).
        # latest fallback must be gated so it does not contaminate pipeline tracker with future-position bbox.
        latest_payload = {"pts_us": 10_000_000, "faces": [{"tracking_id": 7}]}
        keys = {"broadcast:bc:meta:latest": json.dumps(latest_payload)}
        store = self._store(
            values=keys,
            zsets={"broadcast:bc:meta:index": []},
            latest_fallback_window_us=200_000,
            latest_tolerance_us=20_000_000,
        )
        store._offset_us_by_broadcast["bc"] = 5_000_000

        result = await store.get_face_metadata("bc", 0)
        self.assertIsNone(result)

    async def test_latest_fallback_returns_when_within_window(self) -> None:
        latest_payload = {"pts_us": 5_050_000, "faces": [{"tracking_id": 7}]}
        keys = {"broadcast:bc:meta:latest": json.dumps(latest_payload)}
        store = self._store(
            values=keys,
            zsets={"broadcast:bc:meta:index": []},
            latest_fallback_window_us=200_000,
            latest_tolerance_us=20_000_000,
        )
        store._offset_us_by_broadcast["bc"] = 5_000_000

        result = await store.get_face_metadata("bc", 0)
        self.assertIsNotNone(result)
        self.assertEqual(result["pts_us"], 0)
        self.assertEqual(result["faces"][0]["tracking_id"], 7)

    async def test_seeded_offset_prevents_stale_meta_zero_hit(self) -> None:
        # PyAV rebased its first observed frame from raw_pts=1.25s to 0.
        # Client wrote meta:0 8s ago (still alive under TTL); meta:1250000 is the
        # actual record for this frame. Without seeding, an exact lookup at frame_pts=0
        # would match the stale meta:0 and lock in offset=0, causing every subsequent
        # avatar to render with metadata from 1.25s before the displayed frame.
        keys: dict[str, str] = {
            "broadcast:bc:meta:0": json.dumps({"pts_us": 0, "faces": [{"tracking_id": 99}]}),
        }
        zindex: list[tuple[str, float]] = [("0", 0.0)]
        for index in range(16):
            pts = 1_250_000 + index * 33_333
            payload = {"pts_us": pts, "faces": [{"tracking_id": index}]}
            keys[f"broadcast:bc:meta:{pts}"] = json.dumps(payload)
            zindex.append((str(pts), float(pts)))
        latest_payload = {"pts_us": 1_750_000, "faces": [{"tracking_id": 15}]}
        keys["broadcast:bc:meta:latest"] = json.dumps(latest_payload)
        store = self._store(
            values=keys,
            zsets={"broadcast:bc:meta:index": zindex},
            lookup_tolerance_us=150_000,
        )

        store.set_initial_offset("bc", 1_250_000)

        first = await store.get_face_metadata("bc", 0)
        self.assertIsNotNone(first)
        self.assertEqual(first["faces"][0]["tracking_id"], 0)
        self.assertEqual(store._offset_us_by_broadcast["bc"], 1_250_000)

        second = await store.get_face_metadata("bc", 33_333)
        self.assertIsNotNone(second)
        self.assertEqual(second["faces"][0]["tracking_id"], 1)

    async def test_wide_search_hit_does_not_drift_seeded_offset(self) -> None:
        # Production pathology: PyAV consumes ZSET entries slower than the client
        # writes them, narrow window misses, wide search returns ZSET min, every
        # refine bumps offset upward. 50s log showed offset growing 0.11s → 5.13s.
        # Narrow-only refine + delta cap must keep offset close to the seed value
        # so the avatar stays aligned with the underlying face position.
        keys: dict[str, str] = {}
        zindex: list[tuple[str, float]] = []
        for index in range(60):
            pts = 100_000 + index * 33_333
            payload = {"pts_us": pts, "faces": [{"tracking_id": index}]}
            keys[f"broadcast:bc:meta:{pts}"] = json.dumps(payload)
            zindex.append((str(pts), float(pts)))
        store = self._store(
            values=keys,
            zsets={"broadcast:bc:meta:index": list(zindex)},
            lookup_tolerance_us=0,
            index_lookup_window_us=50_000,
        )
        store.set_initial_offset("bc", 100_000)

        # PyAV steps at the source rate (100ms) while the ZSET advances at 33ms —
        # every lookup falls into the wide-search branch.
        for frame_index in range(25):
            await store.get_face_metadata("bc", frame_index * 100_000)

        learned_offset_us = store._offset_us_by_broadcast["bc"]
        self.assertLessEqual(learned_offset_us, 100_000 + store.OFFSET_REFINE_DELTA_CAP_US)

    async def test_legacy_exact_lookup_still_supported_when_no_index(self) -> None:
        payload = {"pts_us": 4_000_000, "faces": [{"tracking_id": 1}]}
        encoded = json.dumps(payload)
        store = self._store(
            values={
                "broadcast:bc:meta:4000000": encoded,
                "broadcast:bc:meta:latest": encoded,
            },
            zsets=None,
        )

        result = await store.get_face_metadata("bc", 0)
        self.assertIsNotNone(result)
        self.assertEqual(result["pts_us"], 0)
        self.assertEqual(result["faces"], payload["faces"])
        self.assertEqual(store._offset_us_by_broadcast["bc"], 4_000_000)


if __name__ == "__main__":
    unittest.main()
