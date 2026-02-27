from fastapi import APIRouter

router = APIRouter(tags=["health"])


@router.get(
    "/healthz",
    summary="헬스 체크",
    description="FastAPI 서비스 프로세스 상태를 확인합니다.",
    response_description="정상 상태일 경우 ok를 반환합니다.",
)
async def healthz() -> dict[str, str]:
    return {"status": "ok"}
