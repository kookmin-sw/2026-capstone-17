import unittest

from model.renderer import AvatarRenderer
from workers.types import VideoFrame


class AvatarRendererMetadataScalingTest(unittest.TestCase):
    def test_remaps_face_bboxes_from_source_frame_to_render_frame(self) -> None:
        renderer = AvatarRenderer()
        frame = VideoFrame(
            pts_us=0,
            payload=b"",
            width=960,
            height=540,
            pixel_format="rgb24",
            source_width=1920,
            source_height=1080,
        )
        metadata = {
            "faces": [
                {
                    "tracking_id": 7,
                    "bbox": {"x": 960, "y": 540, "width": 192, "height": 108},
                    "tdmm_raw": {"coeffs": [0.1] * 264},
                }
            ]
        }

        remapped = renderer._prepare_metadata_for_frame(frame, metadata)

        self.assertIsNot(remapped, metadata)
        self.assertEqual(
            remapped["faces"][0]["bbox"],
            {"x": 480.0, "y": 270.0, "width": 96.0, "height": 54.0},
        )
        self.assertEqual(metadata["faces"][0]["bbox"]["x"], 960)

    def test_metadata_frame_size_overrides_video_source_size(self) -> None:
        renderer = AvatarRenderer()
        frame = VideoFrame(
            pts_us=0,
            payload=b"",
            width=1000,
            height=500,
            pixel_format="rgb24",
            source_width=1920,
            source_height=1080,
        )
        metadata = {
            "frame_width": 2000,
            "frame_height": 1000,
            "faces": [{"bbox": [1000, 500, 200, 100], "render_mode": "MOSAIC"}],
        }

        remapped = renderer._prepare_metadata_for_frame(frame, metadata)

        self.assertEqual(
            remapped["faces"][0]["bbox"],
            {"x": 500.0, "y": 250.0, "width": 100.0, "height": 50.0},
        )

    def test_keeps_metadata_when_no_source_size_is_available(self) -> None:
        renderer = AvatarRenderer()
        frame = VideoFrame(pts_us=0, payload=b"", width=960, height=540, pixel_format="rgb24")
        metadata = {"faces": [{"bbox": {"x": 1, "y": 2, "width": 3, "height": 4}}]}

        self.assertIs(renderer._prepare_metadata_for_frame(frame, metadata), metadata)

    def test_bbox_without_coefficients_does_not_trigger_implicit_mosaic(self) -> None:
        renderer = AvatarRenderer()

        self.assertFalse(renderer._should_mosaic_face({"bbox": {"x": 1, "y": 2, "width": 3, "height": 4}}))
        self.assertTrue(
            renderer._should_mosaic_face(
                {
                    "bbox": {"x": 1, "y": 2, "width": 3, "height": 4},
                    "render_mode": "MOSAIC",
                }
            )
        )


if __name__ == "__main__":
    unittest.main()
