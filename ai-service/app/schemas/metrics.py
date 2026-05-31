from pydantic import BaseModel, Field


class UsageMetricsResponse(BaseModel):
    average_generation_time_ms: float = Field(ge=0)
    error_rate: float = Field(ge=0, le=1)
    successful_generations: int = Field(ge=0)
    failed_generations: int = Field(ge=0)
    cancelled_generations: int = Field(ge=0)
    total_recorded_generations: int = Field(ge=0)
