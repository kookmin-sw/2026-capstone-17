from dataclasses import dataclass
from enum import Enum


@dataclass(frozen=True)
class ErrorSpec:
    status_code: int
    message: str


class ErrorTitle(Enum):
    # 400 Bad Request
    ExternalServerError = ErrorSpec(400, "외부 서버와 통신 과정 중 에러가 발생했습니다.")
    InvalidInputValue = ErrorSpec(400, "잘못된 Request 형식 입니다.")
    InvalidEnumValue = ErrorSpec(400, "잘못된 Enum Value 입니다.")
    BadRequest = ErrorSpec(400, "잘못된 요청 입니다.")
    ModelValidationFail = ErrorSpec(400, "모델 유효성 검사에 실패했습니다.")
    JsonConvertFail = ErrorSpec(400, "Json 변환에 실패했습니다.")
    InvalidJsonType = ErrorSpec(400, "잘못된 Json 형식 입니다.")
    NotSupportedType = ErrorSpec(400, "지원하지 않는 타입입니다.")
    InvalidQueryParameter = ErrorSpec(400, "잘못된 Query Parameter 입니다.")

    # 401 Unauthorized
    LoginRequired = ErrorSpec(401, "인증되지 않은 사용자입니다.")
    InvalidToken = ErrorSpec(401, "유효하지 않은 토큰입니다.")
    ExpiredToken = ErrorSpec(401, "만료된 토큰입니다.")
    Unauthorized = ErrorSpec(401, "인증에 실패했습니다.")

    # 403 Forbidden
    Forbidden = ErrorSpec(403, "권한이 없습니다.")

    # 404 Not Found
    NotFoundEndpoint = ErrorSpec(404, "존재 하지 않는 엔드포인트 입니다.")
    NotFoundUser = ErrorSpec(404, "존재하지 않는 사용자입니다.")
    NotFoundImage = ErrorSpec(404, "존재하지 않는 이미지 파일입니다.")
    NotFoundBroadcast = ErrorSpec(404, "존재하지 않는 방송입니다.")

    # 405 Method Not Allowed
    MethodNotAllowed = ErrorSpec(405, "허용되지 않은 메소드입니다.")

    # 500 Internal Server Error
    InternalServerError = ErrorSpec(500, "서버 에러가 발생했습니다.")
    FeignClientError = ErrorSpec(500, "외부 API 통신 과정에서 에러 발생")

    @property
    def status_code(self) -> int:
        return self.value.status_code

    @property
    def message(self) -> str:
        return self.value.message

    @property
    def error_name(self) -> str:
        return self.name


class ApiException(RuntimeError):
    def __init__(self, error_title: ErrorTitle, message: str | None = None) -> None:
        self.error_title = error_title
        self.message = message or error_title.message
        super().__init__(self.message)
