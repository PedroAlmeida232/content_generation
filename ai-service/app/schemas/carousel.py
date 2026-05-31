from enum import StrEnum
from uuid import UUID

from pydantic import (
    BaseModel,
    Field,
    HttpUrl,
    field_validator,
    model_validator,
)


from app.api.routes.styles import VISUAL_STYLES

# Valores válidos de proporção de imagem para o Instagram.
# Devem estar em sincronia com INSTAGRAM_FORMATS em image_processor.py.
VALID_ASPECT_RATIOS = ("1:1", "4:5", "9:16")

MIN_SLIDES = 1
MAX_SLIDES = 10
DEFAULT_SLIDE_COUNT = 5


class JobStatus(StrEnum):
    PENDING = "pending"
    PROCESSING = "processing"
    DONE = "done"
    FAILED = "failed"
    CANCELLED = "cancelled"


class SlideResult(BaseModel):
    slide_order: int = Field(ge=1)
    image_url: HttpUrl
    caption: str = Field(min_length=1)
    prompt_used: str | None = None


class CarouselRequest(BaseModel):
    context_id: UUID
    prompt: str = Field(min_length=1, max_length=4000)
    style: str = Field(min_length=1, max_length=100)
    aspect_ratio: str = Field(default="1:1")
    slide_count: int = Field(
        default=DEFAULT_SLIDE_COUNT,
        ge=MIN_SLIDES,
        le=MAX_SLIDES,
    )

    @field_validator("prompt", "style", mode="before")
    @classmethod
    def strip_strings(cls, value: object) -> object:
        if isinstance(value, str):
            return value.strip()
        return value

    @field_validator("style")
    @classmethod
    def style_must_be_allowed(cls, value: str) -> str:
        if value not in VISUAL_STYLES:
            allowed = ", ".join(VISUAL_STYLES)
            raise ValueError(f"style must be one of: {allowed}")
        return value

    @field_validator("aspect_ratio")
    @classmethod
    def aspect_ratio_must_be_allowed(
        cls, value: str
    ) -> str:
        if value not in VALID_ASPECT_RATIOS:
            allowed = ", ".join(VALID_ASPECT_RATIOS)
            raise ValueError(
                f"aspect_ratio must be one of: {allowed}"
            )
        return value


class CarouselResponse(BaseModel):
    job_id: str = Field(min_length=1)
    status: JobStatus
    progress: int | None = Field(default=None, ge=0, le=100)
    slides: list[SlideResult] | None = None
    error: str | None = None

    @model_validator(mode="after")
    def validate_status_fields(self) -> "CarouselResponse":
        if self.status == JobStatus.DONE:
            if not self.slides:
                raise ValueError(
                    "slides is required when status is done"
                )
            if self.error is not None:
                raise ValueError(
                    "error must be None when status is done"
                )
        elif self.status == JobStatus.FAILED:
            if self.slides is not None:
                raise ValueError(
                    "slides must be None when status is failed"
                )
        else:
            if self.slides is not None:
                raise ValueError(
                    "slides must be None when status is "
                    "pending or processing"
                )

        if (
            self.progress is not None
            and self.status != JobStatus.PROCESSING
        ):
            raise ValueError(
                "progress is only allowed when status is processing"
            )

        return self

    model_config = {
        "json_schema_extra": {
            "examples": [
                {
                    "job_id": "550e8400-e29b-41d4-a716-446655440000",
                    "status": "pending",
                },
                {
                    "job_id": "550e8400-e29b-41d4-a716-446655440000",
                    "status": "processing",
                    "progress": 40,
                },
                {
                    "job_id": "550e8400-e29b-41d4-a716-446655440000",
                    "status": "done",
                    "slides": [
                        {
                            "slide_order": 1,
                            "image_url": "https://cdn.example/s1.png",
                            "caption": "Primeiro slide",
                        }
                    ],
                },
                {
                    "job_id": "550e8400-e29b-41d4-a716-446655440000",
                    "status": "cancelled",
                },
            ]
        }
    }


class SlideImageRequest(BaseModel):
    """Payload para POST /generate/slide-image."""

    image_prompt: str = Field(min_length=1, max_length=4000)
    model: str = Field(default="dall-e-3")
    size: str = Field(default="1024x1024")
    quality: str = Field(default="standard")

    @field_validator("image_prompt", mode="before")
    @classmethod
    def strip_image_prompt(cls, value: object) -> object:
        if isinstance(value, str):
            return value.strip()
        return value


class SlideImageResponse(BaseModel):
    """Resposta de POST /generate/slide-image."""

    image_url: HttpUrl


class CarouselResultResponse(BaseModel):
    """Resposta de GET /jobs/{job_id}/result quando status=done."""

    slides: list[SlideResult]


class PreviewRequest(BaseModel):
    """Payload para POST /generate/preview."""

    context_id: UUID
    prompt: str = Field(min_length=1, max_length=4000)
    style: str = Field(min_length=1, max_length=100)
    aspect_ratio: str = Field(default="1:1")

    @field_validator("prompt", "style", mode="before")
    @classmethod
    def strip_strings(cls, value: object) -> object:
        if isinstance(value, str):
            return value.strip()
        return value

    @field_validator("style")
    @classmethod
    def style_must_be_allowed(cls, value: str) -> str:
        if value not in VISUAL_STYLES:
            allowed = ", ".join(VISUAL_STYLES)
            raise ValueError(f"style must be one of: {allowed}")
        return value

    @field_validator("aspect_ratio")
    @classmethod
    def aspect_ratio_must_be_allowed(
        cls, value: str
    ) -> str:
        if value not in VALID_ASPECT_RATIOS:
            allowed = ", ".join(VALID_ASPECT_RATIOS)
            raise ValueError(
                f"aspect_ratio must be one of: {allowed}"
            )
        return value


class PromptBuildResponse(BaseModel):
    """Resposta de POST /prompts/build com o prompt final montado."""

    prompt: str
