import asyncio
import logging
from collections.abc import Mapping, Sequence
from pathlib import Path
from typing import Any

from model.avatar_reenactor import LiveAvatarReenactor
from workers.types import VideoFrame

logger = logging.getLogger(__name__)


class AvatarRenderer:
    """Avatar compositing adapter backed by the vendored focus-avatar runtime."""

    def __init__(
        self,
        *,
        avatar_project_dir: str | Path | None = None,
        avatar_bank_dir: str | Path | Sequence[str | Path] | None = None,
        avatar_random_seed: int = 0,
    ) -> None:
        self._avatar_project_dir = avatar_project_dir
        self._avatar_bank_dir = avatar_bank_dir
        self._avatar_random_seed = int(avatar_random_seed)
        self._reenactor: LiveAvatarReenactor | None = None
        self._setup_failure: str | None = None

    async def render(
        self,
        frame: VideoFrame,
        face_metadata: dict[str, Any] | None,
        avatar_id: str | None,
    ) -> VideoFrame:
        if not face_metadata:
            return frame
        if self._setup_failure is not None:
            return await self._mosaic_only(frame, face_metadata)
        if frame.pixel_format not in (None, "rgb24"):
            logger.warning("avatar_render_unsupported_pixel_format format=%s", frame.pixel_format)
            return frame
        if frame.width is None or frame.height is None:
            logger.warning("avatar_render_missing_dimensions")
            return frame

        try:
            return await asyncio.to_thread(
                self._render_sync,
                frame,
                face_metadata,
                avatar_id,
            )
        except (ImportError, ModuleNotFoundError, RuntimeError) as exc:
            setup_error_markers = (
                "No avatar profiles",
                "Avatar project directory",
                "No module named",
            )
            if isinstance(exc, (ImportError, ModuleNotFoundError)) or any(
                marker in str(exc) for marker in setup_error_markers
            ):
                self._setup_failure = str(exc)
            logger.warning("avatar_render_fallback detail=%s", exc)
            return await self._mosaic_only(frame, face_metadata)
        except Exception as exc:
            logger.exception("avatar_render_frame_failed detail=%s", exc)
            return await self._mosaic_only(frame, face_metadata)

    async def emergency_fallback(self, frame: VideoFrame) -> VideoFrame:
        # 렌더링 실패 시 원본 프레임을 그대로 반환하는 안전 장치
        return frame

    def _render_sync(
        self,
        frame: VideoFrame,
        face_metadata: dict[str, Any],
        avatar_id: str | None,
    ) -> VideoFrame:
        import cv2
        import numpy as np

        expected_size = int(frame.width or 0) * int(frame.height or 0) * 3
        if len(frame.payload) != expected_size:
            raise RuntimeError(
                f"Unexpected rgb24 payload size: got={len(frame.payload)} expected={expected_size}"
            )
        face_metadata = self._prepare_metadata_for_frame(frame, face_metadata)

        rgb_frame = np.frombuffer(frame.payload, dtype=np.uint8).reshape(
            int(frame.height),
            int(frame.width),
            3,
        )
        bgr_frame = cv2.cvtColor(rgb_frame, cv2.COLOR_RGB2BGR)
        rendered_bgr = bgr_frame.copy()
        if avatar_id or self._has_avatar_faces(face_metadata):
            reenactor = self._ensure_reenactor()
            rendered_bgr = reenactor.render_frame(rendered_bgr, face_metadata, avatar_id)
        rendered_bgr = self._apply_mosaic_faces(rendered_bgr, face_metadata)
        rendered_rgb = cv2.cvtColor(rendered_bgr, cv2.COLOR_BGR2RGB)
        return VideoFrame(
            pts_us=frame.pts_us,
            payload=rendered_rgb.tobytes(),
            width=frame.width,
            height=frame.height,
            pixel_format="rgb24",
            source_width=frame.source_width,
            source_height=frame.source_height,
        )

    async def _mosaic_only(self, frame: VideoFrame, face_metadata: dict[str, Any]) -> VideoFrame:
        try:
            return await asyncio.to_thread(self._render_mosaic_sync, frame, face_metadata)
        except Exception as exc:
            logger.warning("mosaic_fallback_failed detail=%s", exc)
            return await self.emergency_fallback(frame)

    def _render_mosaic_sync(self, frame: VideoFrame, face_metadata: dict[str, Any]) -> VideoFrame:
        import cv2
        import numpy as np

        expected_size = int(frame.width or 0) * int(frame.height or 0) * 3
        if len(frame.payload) != expected_size:
            raise RuntimeError(
                f"Unexpected rgb24 payload size: got={len(frame.payload)} expected={expected_size}"
            )
        face_metadata = self._prepare_metadata_for_frame(frame, face_metadata)
        rgb_frame = np.frombuffer(frame.payload, dtype=np.uint8).reshape(
            int(frame.height),
            int(frame.width),
            3,
        )
        bgr_frame = cv2.cvtColor(rgb_frame, cv2.COLOR_RGB2BGR)
        rendered_bgr = self._apply_mosaic_faces(bgr_frame.copy(), face_metadata)
        rendered_rgb = cv2.cvtColor(rendered_bgr, cv2.COLOR_BGR2RGB)
        return VideoFrame(
            pts_us=frame.pts_us,
            payload=rendered_rgb.tobytes(),
            width=frame.width,
            height=frame.height,
            pixel_format="rgb24",
            source_width=frame.source_width,
            source_height=frame.source_height,
        )

    def _prepare_metadata_for_frame(
        self,
        frame: VideoFrame,
        face_metadata: dict[str, Any],
    ) -> dict[str, Any]:
        target_width = int(frame.width or 0)
        target_height = int(frame.height or 0)
        if target_width <= 0 or target_height <= 0:
            return face_metadata

        source_size = self._resolve_metadata_source_size(face_metadata, frame)
        if source_size is None:
            return face_metadata

        source_width, source_height = source_size
        if source_width <= 0 or source_height <= 0:
            return face_metadata
        if source_width == target_width and source_height == target_height:
            return face_metadata

        scale_x = target_width / source_width
        scale_y = target_height / source_height
        return self._remap_metadata_bboxes(face_metadata, scale_x=scale_x, scale_y=scale_y)

    def _resolve_metadata_source_size(
        self,
        face_metadata: dict[str, Any],
        frame: VideoFrame,
    ) -> tuple[int, int] | None:
        metadata_width = self._extract_int(
            face_metadata,
            (
                "frame_width",
                "frameWidth",
                "source_width",
                "sourceWidth",
                "video_width",
                "videoWidth",
            ),
        )
        metadata_height = self._extract_int(
            face_metadata,
            (
                "frame_height",
                "frameHeight",
                "source_height",
                "sourceHeight",
                "video_height",
                "videoHeight",
            ),
        )
        if metadata_width is not None and metadata_height is not None:
            return metadata_width, metadata_height
        if frame.source_width is not None and frame.source_height is not None:
            return int(frame.source_width), int(frame.source_height)
        return None

    def _extract_int(self, payload: Mapping[str, Any], keys: Sequence[str]) -> int | None:
        for key in keys:
            value = payload.get(key)
            try:
                if value is not None:
                    return int(value)
            except (TypeError, ValueError):
                continue
        return None

    def _remap_metadata_bboxes(
        self,
        face_metadata: dict[str, Any],
        *,
        scale_x: float,
        scale_y: float,
    ) -> dict[str, Any]:
        remapped = dict(face_metadata)
        if remapped.get("bbox") is not None:
            remapped["bbox"] = self._scale_bbox(remapped["bbox"], scale_x=scale_x, scale_y=scale_y)

        raw_faces = face_metadata.get("faces")
        if isinstance(raw_faces, Sequence) and not isinstance(raw_faces, (str, bytes)):
            faces: list[Any] = []
            for raw_face in raw_faces:
                if not isinstance(raw_face, Mapping):
                    faces.append(raw_face)
                    continue

                face = dict(raw_face)
                raw_bbox = face.get("bbox")
                if raw_bbox is None:
                    raw_bbox = face.get("bounding_box", face.get("boundingBox"))
                if raw_bbox is not None:
                    face["bbox"] = self._scale_bbox(raw_bbox, scale_x=scale_x, scale_y=scale_y)
                faces.append(face)
            remapped["faces"] = faces
        return remapped

    def _scale_bbox(self, raw_bbox: Any, *, scale_x: float, scale_y: float) -> Any:
        bbox = self._normalize_bbox(raw_bbox)
        if bbox is None:
            return raw_bbox
        return {
            "x": bbox["x"] * scale_x,
            "y": bbox["y"] * scale_y,
            "width": bbox["width"] * scale_x,
            "height": bbox["height"] * scale_y,
        }

    def _has_avatar_faces(self, face_metadata: dict[str, Any]) -> bool:
        for face in self._iter_faces(face_metadata):
            if face.get("avatar_id") and self._extract_coeffs(face):
                return True
        return False

    def _apply_mosaic_faces(self, frame_bgr: Any, face_metadata: dict[str, Any]) -> Any:
        import cv2

        frame_height, frame_width = frame_bgr.shape[:2]
        for face in self._iter_faces(face_metadata):
            if not self._should_mosaic_face(face):
                continue
            bbox = self._normalize_bbox(face.get("bbox"))
            if bbox is None:
                continue
            x1 = max(int(bbox["x"]), 0)
            y1 = max(int(bbox["y"]), 0)
            x2 = min(int(bbox["x"] + bbox["width"]), frame_width)
            y2 = min(int(bbox["y"] + bbox["height"]), frame_height)
            if x2 <= x1 or y2 <= y1:
                continue
            face_region = frame_bgr[y1:y2, x1:x2]
            block_width = max((x2 - x1) // 12, 1)
            block_height = max((y2 - y1) // 12, 1)
            tiny = cv2.resize(face_region, (block_width, block_height), interpolation=cv2.INTER_LINEAR)
            frame_bgr[y1:y2, x1:x2] = cv2.resize(tiny, (x2 - x1, y2 - y1), interpolation=cv2.INTER_NEAREST)
        return frame_bgr

    def _should_mosaic_face(self, face: dict[str, Any]) -> bool:
        render_mode = str(face.get("render_mode", face.get("renderMode", ""))).upper()
        if render_mode in {"MOSAIC", "PIXELATE", "BLUR"}:
            return True
        return self._normalize_bbox(face.get("bbox")) is not None and not self._extract_coeffs(face)

    def _iter_faces(self, face_metadata: dict[str, Any]) -> list[dict[str, Any]]:
        raw_faces = face_metadata.get("faces")
        if raw_faces is None and face_metadata.get("bbox") is not None:
            raw_faces = [face_metadata]
        if not isinstance(raw_faces, Sequence) or isinstance(raw_faces, (str, bytes)):
            return []
        return [dict(face) for face in raw_faces if isinstance(face, Mapping)]

    def _normalize_bbox(self, raw_bbox: Any) -> dict[str, float] | None:
        if isinstance(raw_bbox, Mapping) and {"x", "y", "width", "height"}.issubset(raw_bbox.keys()):
            return {
                "x": float(raw_bbox["x"]),
                "y": float(raw_bbox["y"]),
                "width": float(raw_bbox["width"]),
                "height": float(raw_bbox["height"]),
            }
        if isinstance(raw_bbox, Mapping) and {"left", "top", "right", "bottom"}.issubset(raw_bbox.keys()):
            left = float(raw_bbox["left"])
            top = float(raw_bbox["top"])
            return {
                "x": left,
                "y": top,
                "width": float(raw_bbox["right"]) - left,
                "height": float(raw_bbox["bottom"]) - top,
            }
        if isinstance(raw_bbox, Sequence) and not isinstance(raw_bbox, (str, bytes)):
            values = list(raw_bbox)
            if len(values) >= 4:
                return {
                    "x": float(values[0]),
                    "y": float(values[1]),
                    "width": float(values[2]),
                    "height": float(values[3]),
                }
        return None

    def _extract_coeffs(self, face: dict[str, Any]) -> Sequence[float] | None:
        tdmm = face.get("tdmm_raw")
        if not isinstance(tdmm, Mapping):
            tdmm = face.get("tdmmRaw")
        if isinstance(tdmm, Mapping):
            coeffs = tdmm.get("coeffs")
            if coeffs:
                return coeffs
        coeffs = face.get("coeffs")
        if coeffs:
            return coeffs
        return None

    def _ensure_reenactor(self) -> LiveAvatarReenactor:
        if self._reenactor is None:
            self._reenactor = LiveAvatarReenactor(
                avatar_project_dir=self._avatar_project_dir,
                avatar_bank_dir=self._avatar_bank_dir,
                random_seed=self._avatar_random_seed,
            )
        return self._reenactor
