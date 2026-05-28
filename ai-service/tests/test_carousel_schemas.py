from uuid import uuid4

import pytest
from pydantic import ValidationError

from app.schemas import (
    CarouselRequest,
    CarouselResponse,
    JobStatus,
    SlideResult,
)

VALID_CONTEXT_ID = uuid4()
JOB_ID = "550e8400-e29b-41d4-a716-446655440000"


def valid_carousel_request_dict(**overrides) -> dict:
    base = {
        "context_id": str(VALID_CONTEXT_ID),
        "prompt": "Post sobre lançamento de produto",
        "style": "minimalista",
    }
    base.update(overrides)
    return base


def test_carousel_request_valid_with_defaults() -> None:
    request = CarouselRequest.model_validate(valid_carousel_request_dict())

    assert request.context_id == VALID_CONTEXT_ID
    assert request.prompt == "Post sobre lançamento de produto"
    assert request.style == "minimalista"
    assert request.slide_count == 5


def test_carousel_request_strips_prompt_and_style() -> None:
    request = CarouselRequest.model_validate(
        valid_carousel_request_dict(
            prompt="  texto  ",
            style="  moderno  ",
        )
    )

    assert request.prompt == "texto"
    assert request.style == "moderno"


def test_carousel_request_rejects_blank_prompt() -> None:
    with pytest.raises(ValidationError):
        CarouselRequest.model_validate(
            valid_carousel_request_dict(prompt="   ")
        )


def test_carousel_request_rejects_blank_style() -> None:
    with pytest.raises(ValidationError):
        CarouselRequest.model_validate(
            valid_carousel_request_dict(style="   ")
        )


def test_carousel_request_rejects_invalid_style() -> None:
    with pytest.raises(ValidationError) as exc_info:
        CarouselRequest.model_validate(
            valid_carousel_request_dict(style="retro")
        )

    assert "style must be one of" in str(exc_info.value)


@pytest.mark.parametrize("slide_count", [0, 11])
def test_rejects_invalid_slide_count(slide_count: int) -> None:
    with pytest.raises(ValidationError):
        CarouselRequest.model_validate(
            valid_carousel_request_dict(slide_count=slide_count)
        )


def test_carousel_request_default_aspect_ratio() -> None:
    request = CarouselRequest.model_validate(
        valid_carousel_request_dict()
    )
    assert request.aspect_ratio == "1:1"


@pytest.mark.parametrize("ratio", ["1:1", "4:5", "9:16"])
def test_carousel_request_valid_aspect_ratios(ratio: str) -> None:
    request = CarouselRequest.model_validate(
        valid_carousel_request_dict(aspect_ratio=ratio)
    )
    assert request.aspect_ratio == ratio


@pytest.mark.parametrize("ratio", ["16:9", "1:2", "square", ""])
def test_carousel_request_rejects_invalid_aspect_ratio(
    ratio: str,
) -> None:
    with pytest.raises(ValidationError) as exc_info:
        CarouselRequest.model_validate(
            valid_carousel_request_dict(aspect_ratio=ratio)
        )
    assert "aspect_ratio must be one of" in str(exc_info.value)


def test_slide_result_rejects_invalid_url() -> None:
    with pytest.raises(ValidationError):
        SlideResult.model_validate(
            {
                "slide_order": 1,
                "image_url": "not-a-url",
                "caption": "Legenda",
            }
        )


def test_carousel_response_done_requires_slides() -> None:
    with pytest.raises(ValidationError) as exc_info:
        CarouselResponse.model_validate(
            {
                "job_id": JOB_ID,
                "status": "done",
                "slides": [],
            }
        )

    assert "slides is required when status is done" in str(exc_info.value)


def test_carousel_response_pending_rejects_slides() -> None:
    with pytest.raises(ValidationError):
        CarouselResponse.model_validate(
            {
                "job_id": JOB_ID,
                "status": "pending",
                "slides": [
                    {
                        "slide_order": 1,
                        "image_url": "https://cdn.example/slide1.png",
                        "caption": "Slide",
                    }
                ],
            }
        )


def test_carousel_response_progress_only_when_processing() -> None:
    with pytest.raises(ValidationError):
        CarouselResponse.model_validate(
            {"job_id": JOB_ID, "status": "pending", "progress": 10}
        )

    response = CarouselResponse.model_validate(
        {"job_id": JOB_ID, "status": "processing", "progress": 40}
    )
    assert response.progress == 40


def test_job_status_serializes_as_string() -> None:
    response = CarouselResponse.model_validate(
        {"job_id": JOB_ID, "status": "pending"}
    )

    assert response.model_dump()["status"] == "pending"


def test_carousel_response_round_trip_json() -> None:
    payload = {
        "job_id": JOB_ID,
        "status": "done",
        "slides": [
            {
                "slide_order": 1,
                "image_url": "https://cdn.example/slide1.png",
                "caption": "Primeiro slide",
            }
        ],
    }

    response = CarouselResponse.model_validate_json(
        '{"job_id": "550e8400-e29b-41d4-a716-446655440000", '
        '"status": "pending"}'
    )
    assert response.status == JobStatus.PENDING

    done = CarouselResponse.model_validate(payload)
    assert done.status == JobStatus.DONE
    assert done.slides is not None
    assert len(done.slides) == 1
    assert done.model_dump_json()
