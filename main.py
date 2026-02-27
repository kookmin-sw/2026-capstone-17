import uvicorn
from fastapi import FastAPI

from api.routes_health import router as health_router
from api.routes_stream import router as stream_router
from core.config import get_settings
from core.logging import configure_logging

API_DESCRIPTION = """
Spring Boot가 제어하는 내부 영상 워커 API입니다.

- 방송 시작 시: `/api/stream/start`
- 방송 종료 시: `/api/stream/stop`
- 운영 모니터링: `/api/stream/{broadcast_id}/status`

FastAPI는 클라이언트 직접 통신이 아닌, 스트림 파이프라인 실행/중지를 담당합니다.
"""

TAGS_METADATA = [
    {
        "name": "health",
        "description": "서비스 프로세스 상태 확인",
    },
    {
        "name": "stream-control",
        "description": "Spring Boot가 호출하는 내부 스트림 제어 API",
    },
]


def create_app() -> FastAPI:
    settings = get_settings()
    configure_logging(settings.log_level)

    app = FastAPI(
        title=settings.app_name,
        version=settings.app_version,
        description=API_DESCRIPTION,
        openapi_tags=TAGS_METADATA,
        docs_url=settings.api_docs_url,
        redoc_url=settings.api_redoc_url,
        openapi_url=settings.api_openapi_url,
    )
    app.include_router(health_router)
    app.include_router(stream_router, prefix="/api")
    return app


app = create_app()


if __name__ == "__main__":
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=False)
