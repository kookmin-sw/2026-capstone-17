import unittest

from schemas.stream import OutputMode
from workers.pipeline import StreamPipeline


class _MetadataStoreStub:
    async def close(self) -> None:
        pass

    async def get_face_metadata(self, *_args):
        return None


class StreamPipelineAvatarPersonSlotsTest(unittest.TestCase):
    def _pipeline(self) -> StreamPipeline:
        return StreamPipeline(
            broadcast_id="broadcast-a",
            input_stream_key="stream-a",
            input_url="rtsp://example/live/stream-a",
            output_mode=OutputMode.HLS,
            output_url="/tmp/out.m3u8",
            watch_url=None,
            avatar_id="global-avatar",
            fps=15,
            max_frame_lag_ms=100,
            metadata_store=_MetadataStoreStub(),
            avatar_max_faces_per_frame=2,
        )

    def test_multi_person_mode_preserves_face_avatar_assignments(self) -> None:
        pipeline = self._pipeline()
        metadata = {
            "pts_us": 1_000_000,
            "faces": [
                {
                    "tracking_id": 0,
                    "bbox": {"x": 0, "y": 0, "width": 100, "height": 100},
                    "tdmm_raw": {"coeffs": [1.0]},
                    "avatar_id": "avatar-a",
                    "avatar_asset_key": "avatars/a/",
                },
                {
                    "tracking_id": 1,
                    "bbox": {"x": 180, "y": 0, "width": 90, "height": 100},
                    "tdmm_raw": {"coeffs": [1.0]},
                    "avatar_id": "avatar-b",
                    "avatar_asset_key": "avatars/b/",
                },
            ],
        }

        prepared = pipeline._prepare_metadata_for_live_render(metadata)

        self.assertEqual([face.get("avatar_id") for face in prepared["faces"]], ["avatar-a", "avatar-b"])
        self.assertIsNone(pipeline._resolve_renderer_avatar_id(prepared))

    def test_new_tracking_id_near_recent_slot_reuses_previous_avatar(self) -> None:
        pipeline = self._pipeline()
        first_metadata = {
            "pts_us": 1_000_000,
            "faces": [
                {
                    "tracking_id": 0,
                    "bbox": {"x": 0, "y": 0, "width": 100, "height": 100},
                    "tdmm_raw": {"coeffs": [1.0]},
                    "avatar_id": "avatar-a",
                    "avatar_asset_key": "avatars/a/",
                }
            ],
        }
        relink_metadata = {
            "pts_us": 1_200_000,
            "faces": [
                {
                    "tracking_id": 10,
                    "bbox": {"x": 5, "y": 3, "width": 100, "height": 100},
                    "tdmm_raw": {"coeffs": [1.0]},
                    "avatar_id": "avatar-new",
                    "avatar_asset_key": "avatars/new/",
                }
            ],
        }

        pipeline._prepare_metadata_for_live_render(first_metadata)
        prepared = pipeline._prepare_metadata_for_live_render(relink_metadata)

        self.assertEqual(prepared["faces"][0].get("avatar_id"), "avatar-a")
        self.assertEqual(prepared["faces"][0].get("avatar_asset_key"), "avatars/a/")


if __name__ == "__main__":
    unittest.main()
