from fastapi import APIRouter, Body, Depends, HTTPException, status

from app.schemas.common import ErrorResponse
from app.schemas.stream import StreamStartRequest, StreamStatusResponse, StreamStopRequest
from app.services.container import get_stream_manager
from app.services.errors import StreamAlreadyRunningError, StreamNotFoundError
from app.services.stream_manager import StreamManager

router = APIRouter(prefix="/stream", tags=["stream-control"])

START_REQUEST_EXAMPLES = {
    "srt_live": {
        "summary": "SRT 입력 스트림 시작",
        "value": {
            "stream_id": "live-101",
            "input_url": "srt://mediamtx:8890/live/101",
            "output_path": "/var/www/hls/live-101",
            "avatar_id": "avatar-a",
        },
    },
    "rtmp_fallback": {
        "summary": "RTMP 폴백 스트림 시작",
        "value": {
            "stream_id": "live-102",
            "input_url": "rtmp://mediamtx:1935/live/102",
            "output_path": "/var/www/hls/live-102",
            "avatar_id": "avatar-b",
        },
    },
}

STOP_REQUEST_EXAMPLES = {
    "stop_stream": {
        "summary": "스트림 중지",
        "value": {"stream_id": "live-101"},
    }
}


@router.post(
    "/start",
    response_model=StreamStatusResponse,
    status_code=status.HTTP_202_ACCEPTED,
    summary="스트림 워커 시작",
    description="Spring Boot가 방송 시작 시 내부 호출하여 FastAPI 워커를 시작합니다.",
    response_description="요청 접수 직후 스트림 현재 상태를 반환합니다.",
    responses={
        status.HTTP_409_CONFLICT: {
            "model": ErrorResponse,
            "description": "이미 실행 중인 stream_id",
            "content": {
                "application/json": {
                    "example": {"detail": "stream_id 'live-101' is already active"}
                }
            },
        }
    },
)
async def start_stream(
    req: StreamStartRequest = Body(..., openapi_examples=START_REQUEST_EXAMPLES),
    manager: StreamManager = Depends(get_stream_manager),
) -> StreamStatusResponse:
    try:
        return await manager.start_stream(req)
    except StreamAlreadyRunningError as exc:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=str(exc)) from exc


@router.post(
    "/stop",
    response_model=StreamStatusResponse,
    status_code=status.HTTP_202_ACCEPTED,
    summary="스트림 워커 중지",
    description="Spring Boot가 방송 종료 시 내부 호출하여 FastAPI 워커를 중지합니다.",
    response_description="중지 처리 후 최종 상태를 반환합니다.",
    responses={
        status.HTTP_404_NOT_FOUND: {
            "model": ErrorResponse,
            "description": "해당 stream_id 미존재",
            "content": {
                "application/json": {
                    "example": {"detail": "stream_id 'live-101' was not found"}
                }
            },
        }
    },
)
async def stop_stream(
    req: StreamStopRequest = Body(..., openapi_examples=STOP_REQUEST_EXAMPLES),
    manager: StreamManager = Depends(get_stream_manager),
) -> StreamStatusResponse:
    try:
        return await manager.stop_stream(req.stream_id)
    except StreamNotFoundError as exc:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(exc)) from exc


@router.get(
    "/{stream_id}/status",
    response_model=StreamStatusResponse,
    summary="스트림 워커 상태 조회",
    description="Spring Boot가 운영 상태 모니터링 용도로 내부 호출합니다.",
    response_description="현재 스트림 워커 상태와 처리 통계를 반환합니다.",
    responses={
        status.HTTP_404_NOT_FOUND: {
            "model": ErrorResponse,
            "description": "해당 stream_id 미존재",
            "content": {
                "application/json": {
                    "example": {"detail": "stream_id 'live-101' was not found"}
                }
            },
        }
    },
)
async def stream_status(
    stream_id: str,
    manager: StreamManager = Depends(get_stream_manager),
) -> StreamStatusResponse:
    try:
        return await manager.get_status(stream_id)
    except StreamNotFoundError as exc:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(exc)) from exc
