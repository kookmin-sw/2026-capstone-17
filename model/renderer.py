import asyncio
import logging
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
        avatar_bank_dir: str | Path | None = None,
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
        if not avatar_id or not face_metadata:
            return frame
        if self._setup_failure is not None:
            return frame
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
            return await self.emergency_fallback(frame)
        except Exception as exc:
            logger.exception("avatar_render_frame_failed detail=%s", exc)
            return await self.emergency_fallback(frame)

    async def emergency_fallback(self, frame: VideoFrame) -> VideoFrame:
        # 렌더링 실패 시 원본 프레임을 그대로 반환하는 안전 장치
        return frame

    def _render_sync(
        self,
        frame: VideoFrame,
        face_metadata: dict[str, Any],
        avatar_id: str,
    ) -> VideoFrame:
        import cv2
        import numpy as np

        reenactor = self._ensure_reenactor()
        expected_size = int(frame.width or 0) * int(frame.height or 0) * 3
        if len(frame.payload) != expected_size:
            raise RuntimeError(
                f"Unexpected rgb24 payload size: got={len(frame.payload)} expected={expected_size}"
            )

        rgb_frame = np.frombuffer(frame.payload, dtype=np.uint8).reshape(
            int(frame.height),
            int(frame.width),
            3,
        )
        bgr_frame = cv2.cvtColor(rgb_frame, cv2.COLOR_RGB2BGR)
        rendered_bgr = reenactor.render_frame(bgr_frame.copy(), face_metadata, avatar_id)
        rendered_rgb = cv2.cvtColor(rendered_bgr, cv2.COLOR_BGR2RGB)
        return VideoFrame(
            pts_us=frame.pts_us,
            payload=rendered_rgb.tobytes(),
            width=frame.width,
            height=frame.height,
            pixel_format="rgb24",
        )

    def _ensure_reenactor(self) -> LiveAvatarReenactor:
        if self._reenactor is None:
            self._reenactor = LiveAvatarReenactor(
                avatar_project_dir=self._avatar_project_dir,
                avatar_bank_dir=self._avatar_bank_dir,
                random_seed=self._avatar_random_seed,
            )
        return self._reenactor
