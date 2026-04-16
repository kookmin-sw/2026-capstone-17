from pydantic import BaseModel, Field


class ApiFailureResponse(BaseModel):
    success: bool = Field(default=False, description="성공 여부")
    message: str = Field(description="에러 메시지")
    errorTitle: str = Field(description="에러 타이틀")
    errorCode: int = Field(description="에러 코드(HTTP status)")
