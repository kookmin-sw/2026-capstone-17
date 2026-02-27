from fastapi import FastAPI

from app.api.routes_health import router as health_router
from app.api.routes_stream import router as stream_router
from app.core.config import get_settings
from app.core.logging import configure_logging


def create_app() -> FastAPI:
    settings = get_settings()
    configure_logging(settings.log_level)

    app = FastAPI(
        title=settings.app_name,
        version=settings.app_version,
        description="FastAPI worker service for stream composition pipeline.",
    )
    app.include_router(health_router)
    app.include_router(stream_router, prefix="/api")
    return app


app = create_app()
