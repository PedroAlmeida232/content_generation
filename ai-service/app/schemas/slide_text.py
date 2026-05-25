from pydantic import BaseModel, Field, field_validator, model_validator


class SlideText(BaseModel):
    slide_order: int = Field(ge=1)
    title: str = Field(min_length=1, max_length=120)
    caption: str = Field(min_length=1, max_length=2200)

    @field_validator("title", "caption", mode="before")
    @classmethod
    def strip_strings(cls, value: object) -> object:
        if isinstance(value, str):
            return value.strip()
        return value


class CarouselSlidesText(BaseModel):
    slides: list[SlideText] = Field(min_length=1)

    def validate_slide_count(self, expected: int) -> "CarouselSlidesText":
        if expected < 1:
            raise ValueError("expected slide_count must be at least 1")

        if len(self.slides) != expected:
            raise ValueError(
                f"expected {expected} slides, got {len(self.slides)}"
            )

        expected_orders = set(range(1, expected + 1))
        actual_orders = {slide.slide_order for slide in self.slides}
        if actual_orders != expected_orders:
            raise ValueError(
                "slide_order values must be unique and sequential from 1 "
                f"to {expected}"
            )

        return self

    @model_validator(mode="after")
    def slides_must_not_be_empty_when_present(self) -> "CarouselSlidesText":
        if not self.slides:
            raise ValueError("slides must contain at least one item")
        return self
