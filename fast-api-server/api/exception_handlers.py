import logging

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

from core.exceptions import ApiException, ErrorTitle
from schemas.common import ApiFailureResponse

logger = logging.getLogger(__name__)


def register_exception_handlers(app: FastAPI) -> None:
    @app.exception_handler(ApiException)
    async def handle_api_exception(_: Request, exc: ApiException) -> JSONResponse:
        return JSONResponse(
            status_code=exc.error_title.status_code,
            content=_failure_payload(error_title=exc.error_title, message=exc.message),
        )

    @app.exception_handler(RequestValidationError)
    async def handle_validation_exception(_: Request, exc: RequestValidationError) -> JSONResponse:
        logger.warning("request_validation_failed errors=%s", exc.errors())
        title = ErrorTitle.InvalidInputValue
        return JSONResponse(
            status_code=title.status_code,
            content=_failure_payload(error_title=title),
        )

    @app.exception_handler(StarletteHTTPException)
    async def handle_http_exception(_: Request, exc: StarletteHTTPException) -> JSONResponse:
        title = _map_http_status_to_error_title(exc.status_code)
        message = exc.detail if isinstance(exc.detail, str) and exc.detail else None
        return JSONResponse(
            status_code=title.status_code,
            content=_failure_payload(error_title=title, message=message),
        )

    @app.exception_handler(Exception)
    async def handle_unexpected_exception(_: Request, exc: Exception) -> JSONResponse:
        logger.exception("unexpected_server_error", exc_info=exc)
        title = ErrorTitle.InternalServerError
        return JSONResponse(
            status_code=title.status_code,
            content=_failure_payload(error_title=title),
        )


def _failure_payload(error_title: ErrorTitle, message: str | None = None) -> dict:
    return ApiFailureResponse(
        message=message or error_title.message,
        errorTitle=error_title.error_name,
        errorCode=error_title.status_code,
    ).model_dump()


def _map_http_status_to_error_title(status_code: int) -> ErrorTitle:
    mapping = {
        400: ErrorTitle.BadRequest,
        401: ErrorTitle.Unauthorized,
        403: ErrorTitle.Forbidden,
        404: ErrorTitle.NotFoundEndpoint,
        405: ErrorTitle.MethodNotAllowed,
        422: ErrorTitle.InvalidInputValue,
    }
    return mapping.get(status_code, ErrorTitle.InternalServerError)
