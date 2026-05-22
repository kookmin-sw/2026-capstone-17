from pydantic import BaseModel, Field


class ViewerPeakInsight(BaseModel):
    peakViewerCount: int | None = Field(default=None, ge=0)
    occurredAt: str | None = None
    sceneDescription: str | None = None


class FaceStatistics(BaseModel):
    totalReplacedFaceCount: int = Field(default=0, ge=0)
    maxSimultaneousCrowdCount: int = Field(default=0, ge=0)


class ContentRatio(BaseModel):
    contentType: str
    percentage: float = Field(ge=0, le=100)
    durationSec: int = Field(ge=0)


class GeminiAnalysisResult(BaseModel):
    summary: str
    strengths: list[str] = Field(default_factory=list)
    weaknesses: list[str] = Field(default_factory=list)
    actionItems: list[str] = Field(default_factory=list)
    viewerPeakInsight: ViewerPeakInsight | None = None
    faceStatistics: FaceStatistics = Field(default_factory=FaceStatistics)
    contentRatios: list[ContentRatio] = Field(default_factory=list)


class SpringAnalysisContext(BaseModel):
    broadcastId: str | None = None
    viewerPeakInsight: ViewerPeakInsight | None = None
    contentRatios: list[ContentRatio] = Field(default_factory=list)
    sampledSnapshotCount: int | None = Field(default=None, ge=0)
    lastSampledAt: str | None = None


class SpringAnalysisCompletePayload(GeminiAnalysisResult):
    storageUrl: str
    durationSec: int = Field(ge=0)
