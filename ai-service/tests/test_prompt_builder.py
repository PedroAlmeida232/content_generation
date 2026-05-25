from langchain.prompts import PromptTemplate

from app.services import (
    CAROUSEL_PROMPT_TEMPLATE,
    build_carousel_prompt,
    format_color_palette,
)


def test_template_is_langchain_prompt_template() -> None:
    assert isinstance(CAROUSEL_PROMPT_TEMPLATE, PromptTemplate)


def test_build_carousel_prompt_includes_all_main_fields() -> None:
    prompt = build_carousel_prompt(
        prompt="  Carrossel sobre lancamento de um produto SaaS  ",
        style="  minimalista  ",
        slide_count=6,
        tone="  profissional e direto  ",
        color_palette=["#FF5733", " #223344 ", "#F6E7D8"],
        context_name="  Studio Aurora  ",
    )

    assert "Carrossel sobre lancamento de um produto SaaS" in prompt
    assert "minimalista" in prompt
    assert "profissional e direto" in prompt
    assert "#FF5733, #223344, #F6E7D8" in prompt
    assert "Studio Aurora" in prompt
    assert "Numero de slides: 6" in prompt


def test_build_carousel_prompt_uses_fallbacks_for_optional_fields() -> None:
    prompt = build_carousel_prompt(
        prompt="Post de vendas com CTA final",
        style="moderno",
        slide_count=5,
    )

    assert "marca do usuario" in prompt
    assert "neutro e claro" in prompt
    assert "nao especificada" in prompt


def test_format_color_palette_ignores_blank_values() -> None:
    formatted = format_color_palette(["#123456", "  ", "", " #abcdef "])

    assert formatted == "#123456, #abcdef"


def test_format_color_palette_uses_fallback_when_values_are_missing() -> None:
    assert format_color_palette(None) == "nao especificada"
    assert format_color_palette([" ", ""]) == "nao especificada"


def test_build_carousel_prompt_rejects_blank_prompt() -> None:
    try:
        build_carousel_prompt(
            prompt="   ",
            style="editorial",
            slide_count=4,
        )
    except ValueError as error:
        assert str(error) == "prompt must be a non-empty string"
    else:
        raise AssertionError("Expected ValueError for blank prompt")


def test_build_carousel_prompt_rejects_blank_style() -> None:
    try:
        build_carousel_prompt(
            prompt="Post educativo",
            style="   ",
            slide_count=4,
        )
    except ValueError as error:
        assert str(error) == "style must be a non-empty string"
    else:
        raise AssertionError("Expected ValueError for blank style")
