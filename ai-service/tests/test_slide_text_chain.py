from unittest.mock import MagicMock, patch

import pytest
from langchain.prompts import PromptTemplate
from pydantic import ValidationError

from app.schemas.slide_text import CarouselSlidesText, SlideText
from app.services.slide_text_chain import (
    SLIDE_TEXT_PROMPT_TEMPLATE,
    SlideTextChain,
    SlideTextChainError,
    build_slide_text_messages,
    run_slide_text_chain,
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


def test_slide_text_prompt_template_is_langchain_prompt_template() -> None:
    assert isinstance(SLIDE_TEXT_PROMPT_TEMPLATE, PromptTemplate)


def test_slide_text_messages_include_carousel_prompt_and_count() -> None:
    carousel_prompt = "Roteiro sobre lancamento de produto"
    slide_count = 4

    messages = build_slide_text_messages(
        carousel_prompt=carousel_prompt,
        slide_count=slide_count,
    )

    human_content = messages[1].content
    assert carousel_prompt in human_content
    assert "exatamente 4 slides" in human_content
    assert messages[0].content.startswith("Voce gera textos estruturados")


def test_build_slide_text_messages_rejects_invalid_slide_count() -> None:
    with pytest.raises(ValueError, match="slide_count must be between"):
        build_slide_text_messages(
            carousel_prompt="Briefing",
            slide_count=0,
        )


def test_carousel_slides_text_validate_slide_count_success() -> None:
    payload = CarouselSlidesText(slides=_sample_slides(3))

    validated = payload.validate_slide_count(3)

    assert len(validated.slides) == 3


def test_carousel_slides_text_validate_slide_count_wrong_length() -> None:
    payload = CarouselSlidesText(slides=_sample_slides(2))

    with pytest.raises(ValueError, match="expected 3 slides"):
        payload.validate_slide_count(3)


def test_carousel_slides_text_validate_slide_count_non_sequential() -> None:
    payload = CarouselSlidesText(
        slides=[
            SlideText(slide_order=1, title="A", caption="Legenda A"),
            SlideText(slide_order=3, title="B", caption="Legenda B"),
        ]
    )

    with pytest.raises(ValueError, match="slide_order values must be unique"):
        payload.validate_slide_count(2)


def test_slide_text_strips_title_and_caption() -> None:
    slide = SlideText(
        slide_order=1,
        title="  Titulo  ",
        caption="  Legenda  ",
    )

    assert slide.title == "Titulo"
    assert slide.caption == "Legenda"


@patch("app.services.slide_text_chain.ChatOpenAI")
def test_run_slide_text_chain_success(mock_chat_openai: MagicMock) -> None:
    slide_count = 3
    expected = CarouselSlidesText(slides=_sample_slides(slide_count))

    mock_structured = MagicMock()
    mock_structured.invoke.return_value = expected

    mock_llm = MagicMock()
    mock_llm.with_structured_output.return_value = mock_structured
    mock_chat_openai.return_value = mock_llm

    result = run_slide_text_chain(
        openai_api_key="sk-test-key",
        prompt="Carrossel sobre economia de energia",
        style="minimalista",
        slide_count=slide_count,
        tone="tecnico e acessivel",
        color_palette=["#112233", "#AABBCC"],
        context_name="EcoHome",
    )

    mock_chat_openai.assert_called_once_with(
        model="gpt-4",
        api_key="sk-test-key",
        temperature=0.7,
    )
    mock_llm.with_structured_output.assert_called_once_with(
        CarouselSlidesText
    )

    invoked_messages = mock_structured.invoke.call_args[0][0]
    human_content = invoked_messages[1].content
    assert "Carrossel sobre economia de energia" in human_content
    assert "minimalista" in human_content
    assert "tecnico e acessivel" in human_content
    assert "#112233, #AABBCC" in human_content
    assert "EcoHome" in human_content
    assert len(result.slides) == slide_count


def test_run_slide_text_chain_rejects_blank_api_key() -> None:
    with pytest.raises(ValueError, match="openai_api_key must be a non-empty"):
        run_slide_text_chain(
            openai_api_key="   ",
            prompt="Briefing",
            style="moderno",
            slide_count=3,
        )


def test_run_slide_text_chain_rejects_invalid_slide_count() -> None:
    with pytest.raises(ValueError, match="slide_count must be between"):
        run_slide_text_chain(
            openai_api_key="sk-test-key",
            prompt="Briefing",
            style="moderno",
            slide_count=11,
        )


def test_slide_text_chain_raises_domain_error_on_invalid_structure() -> None:
    mock_structured = MagicMock()
    mock_structured.invoke.return_value = CarouselSlidesText(
        slides=_sample_slides(2)
    )

    mock_llm = MagicMock()
    mock_llm.with_structured_output.return_value = mock_structured

    chain = SlideTextChain(mock_llm)

    with pytest.raises(SlideTextChainError):
        chain.invoke(
            carousel_prompt="Roteiro",
            slide_count=3,
        )


def test_slide_text_chain_raises_domain_error_when_llm_fails() -> None:
    mock_structured = MagicMock()
    mock_structured.invoke.side_effect = RuntimeError("upstream failure")

    mock_llm = MagicMock()
    mock_llm.with_structured_output.return_value = mock_structured

    chain = SlideTextChain(mock_llm)

    with pytest.raises(SlideTextChainError):
        chain.invoke(
            carousel_prompt="Roteiro",
            slide_count=2,
        )


def test_run_slide_text_chain_accepts_injected_llm() -> None:
    slide_count = 2
    expected = CarouselSlidesText(slides=_sample_slides(slide_count))

    mock_structured = MagicMock()
    mock_structured.invoke.return_value = expected

    mock_llm = MagicMock()
    mock_llm.with_structured_output.return_value = mock_structured

    result = run_slide_text_chain(
        openai_api_key="sk-test-key",
        prompt="Briefing",
        style="editorial",
        slide_count=slide_count,
        llm=mock_llm,
    )

    mock_llm.with_structured_output.assert_called_once_with(CarouselSlidesText)
    assert len(result.slides) == slide_count


def test_slide_text_rejects_blank_title() -> None:
    with pytest.raises(ValidationError):
        SlideText(slide_order=1, title="   ", caption="Legenda valida")
