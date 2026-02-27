from pydantic import BaseModel, Field


class ErrorResponse(BaseModel):
    detail: str = Field(description="오류 상세 메시지")
