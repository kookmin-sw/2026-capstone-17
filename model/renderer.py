from typing import Any

from workers.types import VideoFrame


class AvatarRenderer:
    """Model adapter; replace implementation with real avatar compositing."""

    async def render(
        self,
        frame: VideoFrame,
        face_metadata: dict[str, Any] | None,
        avatar_id: str | None,
    ) -> VideoFrame:
        _ = (face_metadata, avatar_id)
        return frame

    async def emergency_fallback(self, frame: VideoFrame) -> VideoFrame:
        return frame
