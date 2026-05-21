import json
import unittest

from adapters.metadata_store import RedisMetadataStore


class _RedisClientStub:
    def __init__(self, values: dict[str, str]) -> None:
        self.values = values
        self.closed = False

    async def mget(self, keys: list[str]) -> list[str | None]:
        return [self.values.get(key) for key in keys]

    async def get(self, key: str) -> str | None:
        return self.values.get(key)

    async def aclose(self) -> None:
        self.closed = True


class RedisMetadataStoreOffsetTest(unittest.IsolatedAsyncioTestCase):
    def _store(self, values: dict[str, str]) -> RedisMetadataStore:
        store = RedisMetadataStore(
            redis_url="redis://localhost:6379/0",
            key_template="broadcast:{broadcast_id}:meta:{pts_us}",
            latest_key_template="broadcast:{broadcast_id}:meta:latest",
            lookup_tolerance_us=0,
            latest_tolerance_us=1000,
            auto_offset_max_us=8_000_000,
        )
        store._client = _RedisClientStub(values)
        return store

    async def test_learns_positive_offset_from_latest_metadata(self) -> None:
        payload = {"pts_us": 4_000_000, "faces": [{"tracking_id": 1}]}
        encoded = json.dumps(payload)
        store = self._store(
            {
                "broadcast:broadcast-a:meta:4000000": encoded,
                "broadcast:broadcast-a:meta:latest": encoded,
            }
        )

        result = await store.get_face_metadata("broadcast-a", 0)

        self.assertEqual(result, payload)
        self.assertEqual(store._offset_us_by_broadcast["broadcast-a"], 4_000_000)

    async def test_uses_latest_when_offset_key_is_not_exact(self) -> None:
        payload = {"pts_us": 4_000_123, "faces": [{"tracking_id": 1}]}
        store = self._store(
            {
                "broadcast:broadcast-a:meta:latest": json.dumps(payload),
            }
        )

        result = await store.get_face_metadata("broadcast-a", 123)

        self.assertEqual(result, payload)
        self.assertEqual(store._offset_us_by_broadcast["broadcast-a"], 4_000_000)

    async def test_uses_learned_offset_before_latest_fallback(self) -> None:
        payload = {"pts_us": 4_100_000, "faces": [{"tracking_id": 1}]}
        store = self._store({"broadcast:broadcast-a:meta:4100000": json.dumps(payload)})
        store._offset_us_by_broadcast["broadcast-a"] = 4_000_000

        result = await store.get_face_metadata("broadcast-a", 100_000)

        self.assertEqual(result, payload)


if __name__ == "__main__":
    unittest.main()
