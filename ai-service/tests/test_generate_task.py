import json
from unittest.mock import MagicMock, call, patch

import pytest

from app.schemas.image_prompt import CarouselImagePrompts, SlideImagePrompt
from app.schemas.slide_text import CarouselSlidesText, SlideText
from app.tasks.generate_task import JobCancelledError, generate_carousel

_JOB_ID = "test-job-uuid-1234"
_KEY = f"job:{_JOB_ID}"
_TTL = 86400

# O redis_client agora vive em app.core.redis — o patch deve apontar para lá.
_REDIS_PATCH = "app.core.redis.redis_client"


@pytest.fixture
def mock_self() -> MagicMock:
    task = MagicMock()
    task.request.id = _JOB_ID
    return task


@pytest.fixture
def mock_slides_text() -> CarouselSlidesText:
    return CarouselSlidesText(
        slides=[
            SlideText(slide_order=1, title="Title 1", caption="Caption 1"),
            SlideText(slide_order=2, title="Title 2", caption="Caption 2"),
        ]
    )


@pytest.fixture
def mock_image_prompts() -> CarouselImagePrompts:
    return CarouselImagePrompts(
        slides=[
            SlideImagePrompt(slide_order=1, image_prompt="Visual Prompt 1"),
            SlideImagePrompt(slide_order=2, image_prompt="Visual Prompt 2"),
        ]
    )


def _redis_call(
    status: str,
    progress: int | None = None,
    slides: list[dict] | None = None,
    error: str | None = None,
) -> call:
    """Gera um call() esperado para redis_client.set."""
    payload = json.dumps(
        {
            "job_id": _JOB_ID,
            "status": status,
            "progress": progress,
            "slides": slides,
            "error": error,
        }
    )
    return call(_KEY, payload, ex=_TTL)


# ---------------------------------------------------------------------------
# Happy path
# ---------------------------------------------------------------------------


@patch(_REDIS_PATCH)
@patch("app.tasks.generate_task.generate_slide_image")
@patch("app.tasks.generate_task.run_image_prompt_chain")
@patch("app.tasks.generate_task.run_slide_text_chain")
def test_generate_carousel_success(
    mock_run_text,
    mock_run_image,
    mock_gen_image,
    mock_redis,
    mock_self,
    mock_slides_text,
    mock_image_prompts,
    caplog,
) -> None:
    mock_run_text.return_value = mock_slides_text
    mock_run_image.return_value = mock_image_prompts
    mock_gen_image.side_effect = ["http://cdn/1.png", "http://cdn/2.png"]

    with caplog.at_level("INFO"):
        result = generate_carousel.run.__func__(
            mock_self,
            openai_api_key="sk-test-key",
            prompt="Test Prompt",
            style="minimalista",
            slide_count=2,
            tone="professional",
            color_palette=["#000000"],
            context_name="Test Brand",
            user_id="user-123",
        )

    # --- Chain / service calls ---
    mock_run_text.assert_called_once_with(
        openai_api_key="sk-test-key",
        prompt="Test Prompt",
        style="minimalista",
        slide_count=2,
        tone="professional",
        color_palette=["#000000"],
        context_name="Test Brand",
    )
    mock_run_image.assert_called_once_with(
        openai_api_key="sk-test-key",
        slides_text=mock_slides_text,
        style="minimalista",
        color_palette=["#000000"],
        context_name="Test Brand",
    )
    assert mock_gen_image.call_count == 2

    # --- Celery update_state calls ---
    mock_self.update_state.assert_any_call(
        state="processing", meta={"progress": 0}
    )
    mock_self.update_state.assert_any_call(
        state="processing", meta={"progress": 30}
    )
    mock_self.update_state.assert_any_call(
        state="processing", meta={"progress": 50}
    )
    # i=1 -> 50 + int((1/2)*45) = 72
    mock_self.update_state.assert_any_call(
        state="processing", meta={"progress": 72}
    )
    # i=2 -> 50 + int((2/2)*45) = 95
    mock_self.update_state.assert_any_call(
        state="processing", meta={"progress": 95}
    )

    # --- Redis set calls (in order) ---
    expected_slides_done = [
        {
            "slide_order": 1,
            "image_url": "http://cdn/1.png",
            "caption": "Caption 1",
            "prompt_used": "Visual Prompt 1",
        },
        {
            "slide_order": 2,
            "image_url": "http://cdn/2.png",
            "caption": "Caption 2",
            "prompt_used": "Visual Prompt 2",
        },
    ]
    mock_redis.set.assert_has_calls(
        [
            _redis_call("processing", progress=0),
            _redis_call("processing", progress=30),
            _redis_call("processing", progress=50),
            _redis_call("processing", progress=72),
            _redis_call("processing", progress=95),
            _redis_call("done", slides=expected_slides_done),
        ],
        any_order=False,
    )
    assert mock_redis.set.call_count == 6

    # --- Return value ---
    assert result == {"slides": expected_slides_done}

    log_entries = [
        json.loads(record.message)
        for record in caplog.records
        if record.name == "app.tasks.generate_task"
    ]
    completed_log = next(
        entry
        for entry in log_entries
        if entry.get("event") == "carousel_generation_completed"
    )
    assert completed_log["job_id"] == _JOB_ID
    assert completed_log["user_id"] == "user-123"
    assert completed_log["status"] == "done"
    assert completed_log["duration_ms"] >= 0


# ---------------------------------------------------------------------------
# Failure / error handling
# ---------------------------------------------------------------------------


@patch(_REDIS_PATCH)
@patch("app.tasks.generate_task.run_slide_text_chain")
def test_generate_carousel_saves_failed_status_on_exception(
    mock_run_text,
    mock_redis,
    mock_self,
) -> None:
    mock_run_text.side_effect = ValueError("invalid count")

    with pytest.raises(ValueError, match="invalid count"):
        generate_carousel.run.__func__(
            mock_self,
            openai_api_key="sk-test-key",
            prompt="Test Prompt",
            style="minimalista",
            slide_count=2,
        )

    # Initial processing state was persisted
    mock_redis.set.assert_any_call(
        _KEY,
        json.dumps(
            {
                "job_id": _JOB_ID,
                "status": "processing",
                "progress": 0,
                "slides": None,
                "error": None,
            }
        ),
        ex=_TTL,
    )
    # Failed state was persisted with the error message
    mock_redis.set.assert_any_call(
        _KEY,
        json.dumps(
            {
                "job_id": _JOB_ID,
                "status": "failed",
                "progress": None,
                "slides": None,
                "error": "invalid count",
            }
        ),
        ex=_TTL,
    )
    # Exactly 2 redis writes: initial + failed
    assert mock_redis.set.call_count == 2


@patch(_REDIS_PATCH)
@patch("app.tasks.generate_task.is_job_cancelled")
def test_generate_carousel_stops_when_job_is_cancelled(
    mock_is_cancelled,
    mock_redis,
    mock_self,
) -> None:
    mock_is_cancelled.return_value = True

    with pytest.raises(JobCancelledError, match="was cancelled"):
        generate_carousel.run.__func__(
            mock_self,
            openai_api_key="sk-test-key",
            prompt="Test Prompt",
            style="minimalista",
            slide_count=2,
        )

    mock_redis.set.assert_not_called()
