from typing import Any

from workers.types import VideoFrame


class AvatarRenderer:
    """
    아바타 합성 어댑터 (현재 비활성화 상태)

    아바타 모델이 준비되면 render() 메서드에서 face_metadata를 사용해
    원본 프레임 위에 아바타를 합성하는 로직을 구현합니다.

    현재는 pipeline.py에서 이 클래스를 사용하지 않고,
    카메라 원본 프레임을 그대로 송출(패스스루)합니다.
    """

    async def render(
        self,
        frame: VideoFrame,
        face_metadata: dict[str, Any] | None,
        avatar_id: str | None,
    ) -> VideoFrame:
        # TODO: 아바타 모델 준비 후 여기에 합성 로직 구현
        # face_metadata: gRPC로 수신한 얼굴 랜드마크/바운딩박스 정보
        # avatar_id: 적용할 아바타 에셋 ID
        _ = (face_metadata, avatar_id)
        return frame

    async def emergency_fallback(self, frame: VideoFrame) -> VideoFrame:
        # 렌더링 실패 시 원본 프레임을 그대로 반환하는 안전 장치
        return frame
