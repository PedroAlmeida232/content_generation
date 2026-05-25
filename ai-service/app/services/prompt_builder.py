from collections.abc import Iterable

from langchain.prompts import PromptTemplate

DEFAULT_TONE = "neutro e claro"
DEFAULT_CONTEXT_NAME = "marca do usuario"
DEFAULT_COLOR_PALETTE = "nao especificada"

CAROUSEL_PROMPT_TEMPLATE = PromptTemplate(
    input_variables=[
        "context_name",
        "tone",
        "color_palette",
        "style",
        "slide_count",
        "prompt",
    ],
    template="""\
Voce esta criando o roteiro de um carrossel para a {context_name}.

Briefing principal do usuario:
{prompt}

Diretrizes de criacao:
- Estilo visual: {style}
- Tom de voz: {tone}
- Paleta de cores: {color_palette}
- Numero de slides: {slide_count}

Instrucoes obrigatorias:
- Desenvolva um carrossel coeso do primeiro ao ultimo slide.
- Respeite o estilo visual e o tom de voz informados.
- Use a paleta de cores como direcao criativa e referencia visual.
- Distribua o conteudo de forma equilibrada ao longo de {slide_count} slides.
- Garanta uma abertura clara, desenvolvimento consistente e fechamento forte.
- Evite repeticao entre os slides.

Formato esperado:
- Para cada slide, descreva objetivo, sugestao visual e texto principal.
- Mantenha a resposta organizada slide a slide.
""",
)


def normalize_optional_text(value: str | None, fallback: str) -> str:
    if not isinstance(value, str):
        return fallback

    normalized = value.strip()
    return normalized or fallback


def format_color_palette(color_palette: Iterable[str] | None) -> str:
    if color_palette is None:
        return DEFAULT_COLOR_PALETTE

    normalized = [
        color.strip()
        for color in color_palette
        if isinstance(color, str) and color.strip()
    ]
    if not normalized:
        return DEFAULT_COLOR_PALETTE

    return ", ".join(normalized)


def build_carousel_prompt(
    *,
    prompt: str,
    style: str,
    slide_count: int,
    tone: str | None = None,
    color_palette: Iterable[str] | None = None,
    context_name: str | None = None,
) -> str:
    normalized_prompt = normalize_optional_text(prompt, "")
    normalized_style = normalize_optional_text(style, "")

    if not normalized_prompt:
        raise ValueError("prompt must be a non-empty string")

    if not normalized_style:
        raise ValueError("style must be a non-empty string")

    return CAROUSEL_PROMPT_TEMPLATE.format(
        context_name=normalize_optional_text(context_name, DEFAULT_CONTEXT_NAME),
        tone=normalize_optional_text(tone, DEFAULT_TONE),
        color_palette=format_color_palette(color_palette),
        style=normalized_style,
        slide_count=slide_count,
        prompt=normalized_prompt,
    )
