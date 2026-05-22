from fastapi import APIRouter, Body, Depends, status

from schemas.common import ApiFailureResponse
from schemas.stream import StreamStartRequest, StreamStatusResponse, StreamStopRequest
from services.container import get_stream_manager
from services.stream_manager import StreamManager

router = APIRouter(prefix="/stream", tags=["stream-control"])

START_REQUEST_EXAMPLES = {
    "chzzk_live": {
        "summary": "치지직 RTMP 출력 방송 시작",
        "value": {
            "broadcast_id": "bc_20260227_001",
            "input_stream_key": "live_101_stream_key",
            "avatar_id": "avatar-a",
            "output_mode": "CHZZK_RTMP",
            "output_url": "rtmp://live.example/app/live-key",
            "watch_url": "https://chzzk.naver.com/channel-id",
        },
    },
    "youtube_live": {
        "summary": "유튜브 RTMP 출력 방송 시작",
        "value": {
            "broadcast_id": "bc_20260227_003",
            "input_stream_key": "live_103_stream_key",
            "avatar_id": "avatar-a",
            "output_mode": "YOUTUBE_RTMP",
            "output_url": "rtmp://a.rtmp.youtube.com/live2/live-key",
            "watch_url": "https://www.youtube.com/watch?v=video-id",
        },
    },
    "hls_debug": {
        "summary": "로컬 HLS fallback 방송 시작",
        "value": {
            "broadcast_id": "bc_20260227_002",
            "input_stream_key": "live_102_stream_key",
            "avatar_id": "avatar-b",
            "output_mode": "HLS",
        },
    },
    "manual_override": {
        "summary": "디버그용 입력/출력 경로 오버라이드",
        "value": {
            "broadcast_id": "bc_debug_001",
            "input_stream_key": "debug-stream",
            "input_url": "rtsp://127.0.0.1:8554/live/debug-stream",
            "output_path": "/tmp/hls/bc_debug_001/index.m3u8",
            "avatar_id": "avatar-debug",
        },
    },
}

STOP_REQUEST_EXAMPLES = {
    "stop_stream": {
        "summary": "방송 워커 중지",
        "value": {"broadcast_id": "bc_20260227_001"},
    }
}


@router.post(
    "/start",
    response_model=StreamStatusResponse,
    status_code=status.HTTP_202_ACCEPTED,
    summary="방송 워커 시작",
    description="Spring Boot가 방송 시작 시 내부 호출하여 FastAPI 워커를 시작합니다.",
    response_description="요청 접수 직후 방송 현재 상태를 반환합니다.",
    responses={
        status.HTTP_400_BAD_REQUEST: {
            "model": ApiFailureResponse,
            "description": "잘못된 요청 또는 이미 실행 중인 방송",
            "content": {
                "application/json": {
                    "example": {
                        "success": False,
                        "message": "이미 실행 중인 방송입니다. broadcast_id=bc_20260227_001",
                        "errorTitle": "BadRequest",
                        "errorCode": 400,
                    }
                }
            },
        }
    },
)
async def start_stream(
    req: StreamStartRequest = Body(..., openapi_examples=START_REQUEST_EXAMPLES),
    manager: StreamManager = Depends(get_stream_manager),
) -> StreamStatusResponse:
    return await manager.start_stream(req)


@router.post(
    "/stop",
    response_model=StreamStatusResponse,
    status_code=status.HTTP_202_ACCEPTED,
    summary="방송 워커 중지",
    description="Spring Boot가 방송 종료 시 내부 호출하여 FastAPI 워커를 중지합니다.",
    response_description="중지 처리 후 최종 상태를 반환합니다.",
    responses={
        status.HTTP_404_NOT_FOUND: {
            "model": ApiFailureResponse,
            "description": "해당 broadcast_id 미존재",
            "content": {
                "application/json": {
                    "example": {
                        "success": False,
                        "message": "존재하지 않는 방송입니다. broadcast_id=bc_20260227_001",
                        "errorTitle": "NotFoundBroadcast",
                        "errorCode": 404,
                    }
                }
            },
        }
    },
)
async def stop_stream(
    req: StreamStopRequest = Body(..., openapi_examples=STOP_REQUEST_EXAMPLES),
    manager: StreamManager = Depends(get_stream_manager),
) -> StreamStatusResponse:
    return await manager.stop_stream(req.broadcast_id)


@router.get(
    "/{broadcast_id}/status",
    response_model=StreamStatusResponse,
    summary="방송 워커 상태 조회",
    description="broadcast_id 기준으로 현재 워커 상태를 조회합니다.",
)
async def get_stream_status(
    broadcast_id: str,
    manager: StreamManager = Depends(get_stream_manager),
) -> StreamStatusResponse:
    return await manager.get_status(broadcast_id)
