from collections.abc import Iterable

from langchain.prompts import PromptTemplate
from langchain_core.language_models.chat_models import BaseChatModel
from langchain_core.messages import BaseMessage, HumanMessage, SystemMessage
from langchain_openai import ChatOpenAI

from app.api.routes.styles import VISUAL_STYLES
from app.schemas.carousel import MAX_SLIDES, MIN_SLIDES
from app.schemas.image_prompt import CarouselImagePrompts
from app.schemas.slide_text import CarouselSlidesText, SlideText
from app.services.prompt_builder import (
    format_color_palette,
    normalize_optional_text,
)

IMAGE_PROMPT_SYSTEM_MESSAGE = (
    "Voce e um especialista em criar prompts visuais altamente otimizados "
    "para o DALL-E 3. Gerar descricoes visuais detalhadas e coesas. "
    "Responda apenas no formato estruturado solicitado."
)

IMAGE_PROMPT_TEMPLATE = PromptTemplate(
    input_variables=[
        "slides_description",
        "style",
        "color_palette",
        "slide_count",
        "context_name",
    ],
    template=(
        "Você é um especialista em direção de arte e DALL-E 3.\n"
        "Sua tarefa é gerar descrições visuais (image_prompt) em inglês "
        "para os {slide_count} slides da marca '{context_name}'.\n\n"
        "Estilo visual: {style}\n"
        "Paleta de cores: {color_palette}\n\n"
        "Descrições dos slides:\n"
        "{slides_description}\n\n"
        "Diretrizes obrigatórias:\n"
        "1. Escreva em inglês.\n"
        "2. Descreva cena, composição, iluminação e mood. Não copie texto.\n"
        "3. Mantenha consistência visual entre os slides.\n"
        "4. Sem texto ou tipografia dentro da imagem.\n"
        "5. Use a paleta {color_palette} e estilo {style}.\n"
        "6. Sem metainstruções (ex: 'generate an image...').\n"
        "7. Produza exatamente {slide_count} entradas.\n"
    ),
)


class ImagePromptChainError(Exception):
    """Erro ao gerar ou validar prompts de imagem do carrossel."""


def _validate_openai_api_key(openai_api_key: str) -> str:
    if not isinstance(openai_api_key, str):
        raise ValueError("openai_api_key must be a non-empty string")
    normalized = openai_api_key.strip()
    if not normalized:
        raise ValueError("openai_api_key must be a non-empty string")
    return normalized


def format_slides_for_image_prompt(slides: list[SlideText]) -> str:
    parts = []
    for s in slides:
        parts.append(
            f"Slide {s.slide_order} | "
            f"Titulo: {s.title} | "
            f"Legenda: {s.caption}"
        )
    return "\n".join(parts)


def build_image_prompt_messages(
    *,
    slides_text: CarouselSlidesText,
    style: str,
    color_palette: Iterable[str] | None = None,
    context_name: str | None = None,
) -> list[BaseMessage]:
    slide_count = len(slides_text.slides)
    if slide_count < MIN_SLIDES or slide_count > MAX_SLIDES:
        raise ValueError(
            f"slide_count must be between {MIN_SLIDES} and {MAX_SLIDES}"
        )

    normalized_style = normalize_optional_text(style, "")
    if not normalized_style:
        raise ValueError("style must be a non-empty string")

    if normalized_style not in VISUAL_STYLES:
        allowed = ", ".join(VISUAL_STYLES)
        raise ValueError(f"style must be one of: {allowed}")

    slides_description = format_slides_for_image_prompt(slides_text.slides)
    palette_str = format_color_palette(color_palette)
    normalized_context = normalize_optional_text(
        context_name,
        "marca do usuario",
    )

    formatted_prompt = IMAGE_PROMPT_TEMPLATE.format(
        slides_description=slides_description,
        style=normalized_style,
        color_palette=palette_str,
        slide_count=slide_count,
        context_name=normalized_context,
    )

    return [
        SystemMessage(content=IMAGE_PROMPT_SYSTEM_MESSAGE),
        HumanMessage(content=formatted_prompt),
    ]


class ImagePromptChain:
    def __init__(self, llm: BaseChatModel) -> None:
        self._llm = llm

    def invoke(
        self,
        *,
        slides_text: CarouselSlidesText,
        style: str,
        color_palette: Iterable[str] | None = None,
        context_name: str | None = None,
    ) -> CarouselImagePrompts:
        try:
            messages = build_image_prompt_messages(
                slides_text=slides_text,
                style=style,
                color_palette=color_palette,
                context_name=context_name,
            )
        except ValueError as error:
            raise ImagePromptChainError(
                "Invalid input parameters for visual prompts chain"
            ) from error

        structured_llm = self._llm.with_structured_output(
            CarouselImagePrompts
        )

        try:
            result = structured_llm.invoke(messages)
        except Exception as error:
            raise ImagePromptChainError(
                "Failed to generate visual prompts from the language model"
            ) from error

        if not isinstance(result, CarouselImagePrompts):
            try:
                result = CarouselImagePrompts.model_validate(result)
            except Exception as error:
                raise ImagePromptChainError(
                    "Invalid schema returned by language model"
                ) from error

        # Validações pós-parse
        slide_count = len(slides_text.slides)
        try:
            result.validate_slide_count(slide_count)
        except ValueError as error:
            raise ImagePromptChainError(
                "Generated visual prompts did not match expected structure"
            ) from error

        return result


def run_image_prompt_chain(
    *,
    openai_api_key: str,
    slides_text: CarouselSlidesText,
    style: str,
    color_palette: Iterable[str] | None = None,
    context_name: str | None = None,
    model: str = "gpt-4",
    temperature: float = 0.7,
    llm: BaseChatModel | None = None,
) -> CarouselImagePrompts:
    normalized_api_key = _validate_openai_api_key(openai_api_key)

    if not isinstance(slides_text, CarouselSlidesText):
        raise ValueError("slides_text must be a CarouselSlidesText instance")

    slide_count = len(slides_text.slides)
    if slide_count < MIN_SLIDES or slide_count > MAX_SLIDES:
        raise ValueError(
            f"slide_count must be between {MIN_SLIDES} and {MAX_SLIDES}"
        )

    # Validamos localmente a consistência do CarouselSlidesText de entrada
    slides_text.validate_slide_count(slide_count)

    normalized_style = normalize_optional_text(style, "")
    if not normalized_style:
        raise ValueError("style must be a non-empty string")

    if normalized_style not in VISUAL_STYLES:
        allowed = ", ".join(VISUAL_STYLES)
        raise ValueError(f"style must be one of: {allowed}")

    chat_model = llm or ChatOpenAI(
        model=model,
        api_key=normalized_api_key,
        temperature=temperature,
    )
    chain = ImagePromptChain(chat_model)
    return chain.invoke(
        slides_text=slides_text,
        style=style,
        color_palette=color_palette,
        context_name=context_name,
    )
