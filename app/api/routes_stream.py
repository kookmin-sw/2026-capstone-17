from fastapi import APIRouter, Depends, HTTPException, status

from app.schemas.stream import StreamStartRequest, StreamStatusResponse, StreamStopRequest
from app.services.container import get_stream_manager
from app.services.errors import StreamAlreadyRunningError, StreamNotFoundError
from app.services.stream_manager import StreamManager

router = APIRouter(prefix="/stream", tags=["stream-control"])


@router.post("/start", response_model=StreamStatusResponse, status_code=status.HTTP_202_ACCEPTED)
async def start_stream(
    req: StreamStartRequest,
    manager: StreamManager = Depends(get_stream_manager),
) -> StreamStatusResponse:
    try:
        return await manager.start_stream(req)
    except StreamAlreadyRunningError as exc:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=str(exc)) from exc


@router.post("/stop", response_model=StreamStatusResponse, status_code=status.HTTP_202_ACCEPTED)
async def stop_stream(
    req: StreamStopRequest,
    manager: StreamManager = Depends(get_stream_manager),
) -> StreamStatusResponse:
    try:
        return await manager.stop_stream(req.stream_id)
    except StreamNotFoundError as exc:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(exc)) from exc


@router.get("/{stream_id}/status", response_model=StreamStatusResponse)
async def stream_status(
    stream_id: str,
    manager: StreamManager = Depends(get_stream_manager),
) -> StreamStatusResponse:
    try:
        return await manager.get_status(stream_id)
    except StreamNotFoundError as exc:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(exc)) from exc
