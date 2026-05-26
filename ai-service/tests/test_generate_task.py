from unittest.mock import MagicMock, patch

import pytest

from app.schemas.image_prompt import CarouselImagePrompts, SlideImagePrompt
from app.schemas.slide_text import CarouselSlidesText, SlideText
from app.tasks.generate_task import generate_carousel


@pytest.fixture
def mock_self() -> MagicMock:
    task = MagicMock()
    task.request.id = "test-job-uuid-1234"
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


@patch("app.tasks.generate_task.generate_slide_image")
@patch("app.tasks.generate_task.run_image_prompt_chain")
@patch("app.tasks.generate_task.run_slide_text_chain")
def test_generate_carousel_success(
    mock_run_text,
    mock_run_image,
    mock_gen_image,
    mock_self,
    mock_slides_text,
    mock_image_prompts,
) -> None:
    mock_run_text.return_value = mock_slides_text
    mock_run_image.return_value = mock_image_prompts
    mock_gen_image.side_effect = ["http://cdn/1.png", "http://cdn/2.png"]

    result = generate_carousel.run.__func__(
        mock_self,
        openai_api_key="sk-test-key",
        prompt="Test Prompt",
        style="minimalista",
        slide_count=2,
        tone="professional",
        color_palette=["#000000"],
        context_name="Test Brand",
    )

    # Asserts
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
    mock_gen_image.assert_any_call(
        openai_api_key="sk-test-key",
        image_prompt="Visual Prompt 1",
    )
    mock_gen_image.assert_any_call(
        openai_api_key="sk-test-key",
        image_prompt="Visual Prompt 2",
    )

    # Verify update_state calls
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

    # Result structure
    assert result == {
        "slides": [
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
    }


@patch("app.tasks.generate_task.run_slide_text_chain")
def test_generate_carousel_bubbles_exceptions(
    mock_run_text,
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

    # Initial state should still be set before the exception is raised
    mock_self.update_state.assert_called_once_with(
        state="processing", meta={"progress": 0}
    )
