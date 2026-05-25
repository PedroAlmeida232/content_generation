from collections.abc import Iterable

from langchain.prompts import PromptTemplate
from langchain_core.language_models.chat_models import BaseChatModel
from langchain_core.messages import BaseMessage, HumanMessage, SystemMessage
from langchain_openai import ChatOpenAI

from app.schemas.carousel import MAX_SLIDES, MIN_SLIDES
from app.schemas.slide_text import CarouselSlidesText
from app.services.prompt_builder import build_carousel_prompt

DEFAULT_MODEL = "gpt-4"
DEFAULT_TEMPERATURE = 0.7

SLIDE_TEXT_SYSTEM_MESSAGE = (
    "Voce gera textos estruturados para carrosseis de Instagram. "
    "Responda apenas no formato estruturado solicitado."
)

SLIDE_TEXT_PROMPT_TEMPLATE = PromptTemplate(
    input_variables=["carousel_prompt", "slide_count"],
    template="""\
Com base no roteiro abaixo, gere os textos finais do carrossel.

Roteiro do carrossel:
{carousel_prompt}

Requisitos de saida:
- Produza exatamente {slide_count} slides.
- Para cada slide, defina:
  - slide_order: numero do slide (1 a {slide_count})
  - title: titulo curto e impactante para o slide
  - caption: legenda/copy do slide para Instagram
- Evite repetir frases entre slides.
- No ultimo slide, inclua CTA quando fizer sentido ao briefing.
- Use o mesmo idioma do briefing do usuario.
""",
)


class SlideTextChainError(Exception):
    """Erro ao gerar ou validar textos do carrossel."""


def _validate_slide_count(slide_count: int) -> None:
    if slide_count < MIN_SLIDES or slide_count > MAX_SLIDES:
        raise ValueError(
            f"slide_count must be between {MIN_SLIDES} and {MAX_SLIDES}"
        )


def _validate_openai_api_key(openai_api_key: str) -> str:
    normalized = openai_api_key.strip()
    if not normalized:
        raise ValueError("openai_api_key must be a non-empty string")
    return normalized


def build_slide_text_messages(
    *,
    carousel_prompt: str,
    slide_count: int,
) -> list[BaseMessage]:
    _validate_slide_count(slide_count)

    formatted_prompt = SLIDE_TEXT_PROMPT_TEMPLATE.format(
        carousel_prompt=carousel_prompt,
        slide_count=slide_count,
    )

    return [
        SystemMessage(content=SLIDE_TEXT_SYSTEM_MESSAGE),
        HumanMessage(content=formatted_prompt),
    ]


def _validate_chain_result(
    result: CarouselSlidesText,
    slide_count: int,
) -> CarouselSlidesText:
    try:
        return result.validate_slide_count(slide_count)
    except ValueError as error:
        raise SlideTextChainError(
            "Generated slide texts did not match the expected structure"
        ) from error


class SlideTextChain:
    def __init__(self, llm: BaseChatModel) -> None:
        self._llm = llm

    def invoke(
        self,
        *,
        carousel_prompt: str,
        slide_count: int,
    ) -> CarouselSlidesText:
        messages = build_slide_text_messages(
            carousel_prompt=carousel_prompt,
            slide_count=slide_count,
        )
        structured_llm = self._llm.with_structured_output(CarouselSlidesText)

        try:
            result = structured_llm.invoke(messages)
        except Exception as error:
            raise SlideTextChainError(
                "Failed to generate slide texts from the language model"
            ) from error

        if not isinstance(result, CarouselSlidesText):
            result = CarouselSlidesText.model_validate(result)

        return _validate_chain_result(result, slide_count)


def run_slide_text_chain(
    *,
    openai_api_key: str,
    prompt: str,
    style: str,
    slide_count: int,
    tone: str | None = None,
    color_palette: Iterable[str] | None = None,
    context_name: str | None = None,
    model: str = DEFAULT_MODEL,
    temperature: float = DEFAULT_TEMPERATURE,
    llm: BaseChatModel | None = None,
) -> CarouselSlidesText:
    normalized_api_key = _validate_openai_api_key(openai_api_key)
    _validate_slide_count(slide_count)

    carousel_prompt = build_carousel_prompt(
        prompt=prompt,
        style=style,
        slide_count=slide_count,
        tone=tone,
        color_palette=color_palette,
        context_name=context_name,
    )

    chat_model = llm or ChatOpenAI(
        model=model,
        api_key=normalized_api_key,
        temperature=temperature,
    )
    chain = SlideTextChain(chat_model)
    return chain.invoke(
        carousel_prompt=carousel_prompt,
        slide_count=slide_count,
    )
