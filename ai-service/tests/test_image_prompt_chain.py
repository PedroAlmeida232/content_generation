from unittest.mock import MagicMock, patch

import pytest
from pydantic import ValidationError

from app.schemas.image_prompt import CarouselImagePrompts, SlideImagePrompt
from app.schemas.slide_text import CarouselSlidesText, SlideText
from app.services.image_prompt_chain import (
    ImagePromptChain,
    ImagePromptChainError,
    build_image_prompt_messages,
    format_slides_for_image_prompt,
    run_image_prompt_chain,
)


def _sample_slides(slide_count: int) -> list[SlideText]:
    return [
        SlideText(
            slide_order=index,
            title=f"Titulo {index}",
            caption=f"Legenda {index}",
        )
        for index in range(1, slide_count + 1)
    ]


def _sample_prompts(slide_count: int) -> list[SlideImagePrompt]:
    return [
        SlideImagePrompt(
            slide_order=index,
            image_prompt=f"Visual prompt {index}",
        )
        for index in range(1, slide_count + 1)
    ]


def test_format_slides_for_image_prompt() -> None:
    slides = _sample_slides(2)
    formatted = format_slides_for_image_prompt(slides)
    assert "Slide 1 | Titulo: Titulo 1 | Legenda: Legenda 1" in formatted
    assert "Slide 2 | Titulo: Titulo 2 | Legenda: Legenda 2" in formatted


def test_build_image_prompt_messages_success() -> None:
    slides_text = CarouselSlidesText(slides=_sample_slides(3))
    messages = build_image_prompt_messages(
        slides_text=slides_text,
        style="minimalista",
        color_palette=["#FFFFFF", "#000000"],
        context_name="Acme",
    )

    assert len(messages) == 2
    assert messages[0].content.startswith("Voce e um especialista")

    human_content = messages[1].content
    assert "minimalista" in human_content
    assert "#FFFFFF, #000000" in human_content
    assert "3 slides" in human_content
    assert "Acme" in human_content


def test_build_image_prompt_messages_invalid_style() -> None:
    slides_text = CarouselSlidesText(slides=_sample_slides(2))
    with pytest.raises(ValueError, match="style must be one of"):
        build_image_prompt_messages(
            slides_text=slides_text,
            style="invalid_style",
        )


def test_carousel_image_prompts_validate_slide_count_success() -> None:
    payload = CarouselImagePrompts(slides=_sample_prompts(3))
    validated = payload.validate_slide_count(3)
    assert len(validated.slides) == 3


def test_carousel_image_prompts_validate_slide_count_wrong_length() -> None:
    payload = CarouselImagePrompts(slides=_sample_prompts(2))
    with pytest.raises(ValueError, match="expected 3 slides"):
        payload.validate_slide_count(3)


def test_carousel_image_prompts_validate_slide_count_non_seq() -> None:
    payload = CarouselImagePrompts(
        slides=[
            SlideImagePrompt(slide_order=1, image_prompt="Prompt A"),
            SlideImagePrompt(slide_order=3, image_prompt="Prompt B"),
        ]
    )
    with pytest.raises(ValueError, match="slide_order values must be unique"):
        payload.validate_slide_count(2)


def test_image_prompt_strips_prompt() -> None:
    prompt = SlideImagePrompt(
        slide_order=1,
        image_prompt="  Clean and crisp design  ",
    )
    assert prompt.image_prompt == "Clean and crisp design"


def test_image_prompt_rejects_blank_prompt() -> None:
    with pytest.raises(ValidationError):
        SlideImagePrompt(slide_order=1, image_prompt="   ")


@patch("app.services.image_prompt_chain.ChatOpenAI")
def test_run_image_prompt_chain_success(mock_chat_openai: MagicMock) -> None:
    slide_count = 3
    expected = CarouselImagePrompts(slides=_sample_prompts(slide_count))

    mock_structured = MagicMock()
    mock_structured.invoke.return_value = expected

    mock_llm = MagicMock()
    mock_llm.with_structured_output.return_value = mock_structured
    mock_chat_openai.return_value = mock_llm

    slides_text = CarouselSlidesText(slides=_sample_slides(slide_count))

    result = run_image_prompt_chain(
        openai_api_key="sk-test-key",
        slides_text=slides_text,
        style="minimalista",
        color_palette=["#112233"],
        context_name="TestCorp",
    )

    mock_chat_openai.assert_called_once_with(
        model="gpt-4",
        api_key="sk-test-key",
        temperature=0.7,
    )
    mock_llm.with_structured_output.assert_called_once_with(
        CarouselImagePrompts
    )
    assert len(result.slides) == slide_count


def test_run_image_prompt_chain_rejects_blank_api_key() -> None:
    slides_text = CarouselSlidesText(slides=_sample_slides(3))
    with pytest.raises(ValueError, match="openai_api_key"):
        run_image_prompt_chain(
            openai_api_key="   ",
            slides_text=slides_text,
            style="minimalista",
        )


def test_run_image_prompt_chain_rejects_invalid_style() -> None:
    slides_text = CarouselSlidesText(slides=_sample_slides(3))
    with pytest.raises(ValueError, match="style must be one of"):
        run_image_prompt_chain(
            openai_api_key="sk-test-key",
            slides_text=slides_text,
            style="invalid-style",
        )


def test_run_image_prompt_chain_rejects_inconsistent_input() -> None:
    slides_text = CarouselSlidesText(slides=_sample_slides(3))
    # Modify slides_text order to make it inconsistent
    slides_text.slides[1].slide_order = 5

    with pytest.raises(ValueError):
        run_image_prompt_chain(
            openai_api_key="sk-test-key",
            slides_text=slides_text,
            style="minimalista",
        )


def test_image_prompt_chain_raises_error_on_invalid_structure() -> None:
    mock_structured = MagicMock()
    # Return 2 slides instead of expected 3
    mock_structured.invoke.return_value = CarouselImagePrompts(
        slides=_sample_prompts(2)
    )

    mock_llm = MagicMock()
    mock_llm.with_structured_output.return_value = mock_structured

    chain = ImagePromptChain(mock_llm)
    slides_text = CarouselSlidesText(slides=_sample_slides(3))

    with pytest.raises(ImagePromptChainError):
        chain.invoke(
            slides_text=slides_text,
            style="minimalista",
        )


def test_image_prompt_chain_raises_error_when_llm_fails() -> None:
    mock_structured = MagicMock()
    mock_structured.invoke.side_effect = RuntimeError("upstream failure")

    mock_llm = MagicMock()
    mock_llm.with_structured_output.return_value = mock_structured

    chain = ImagePromptChain(mock_llm)
    slides_text = CarouselSlidesText(slides=_sample_slides(2))

    with pytest.raises(ImagePromptChainError):
        chain.invoke(
            slides_text=slides_text,
            style="minimalista",
        )


def test_run_image_prompt_chain_accepts_injected_llm() -> None:
    slide_count = 2
    expected = CarouselImagePrompts(slides=_sample_prompts(slide_count))

    mock_structured = MagicMock()
    mock_structured.invoke.return_value = expected

    mock_llm = MagicMock()
    mock_llm.with_structured_output.return_value = mock_structured

    slides_text = CarouselSlidesText(slides=_sample_slides(slide_count))

    result = run_image_prompt_chain(
        openai_api_key="sk-test-key",
        slides_text=slides_text,
        style="minimalista",
        llm=mock_llm,
    )

    mock_llm.with_structured_output.assert_called_once_with(
        CarouselImagePrompts
    )
    assert len(result.slides) == slide_count
