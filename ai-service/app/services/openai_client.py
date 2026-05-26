import openai

DEFAULT_MODEL = "dall-e-3"
DEFAULT_SIZE = "1024x1024"
DEFAULT_QUALITY = "standard"

_MAX_PROMPT_LENGTH = 4000


class OpenAIClientError(Exception):
    """Erro ao chamar a API da OpenAI (DALL-E 3)."""


def _validate_api_key(openai_api_key: str) -> str:
    if not isinstance(openai_api_key, str):
        raise ValueError("openai_api_key must be a non-empty string")
    normalized = openai_api_key.strip()
    if not normalized:
        raise ValueError("openai_api_key must be a non-empty string")
    return normalized


def _validate_image_prompt(image_prompt: str) -> str:
    if not isinstance(image_prompt, str):
        raise ValueError("image_prompt must be a non-empty string")
    normalized = image_prompt.strip()
    if not normalized:
        raise ValueError("image_prompt must be a non-empty string")
    if len(normalized) > _MAX_PROMPT_LENGTH:
        raise ValueError(
            f"image_prompt must not exceed {_MAX_PROMPT_LENGTH} characters"
        )
    return normalized


def generate_slide_image(
    *,
    openai_api_key: str,
    image_prompt: str,
    model: str = DEFAULT_MODEL,
    size: str = DEFAULT_SIZE,
    quality: str = DEFAULT_QUALITY,
) -> str:
    """
    Envia o prompt de imagem ao DALL-E 3 e retorna a URL temporaria.

    Args:
        openai_api_key: Chave da API da OpenAI (obrigatoria, por request).
        image_prompt: Descricao visual otimizada para o DALL-E 3.
        model: Modelo de geracao de imagem (padrao: 'dall-e-3').
        size: Resolucao da imagem (padrao: '1024x1024').
        quality: Qualidade da imagem (padrao: 'standard').

    Returns:
        URL temporaria da imagem gerada pela OpenAI.

    Raises:
        ValueError: Se os parametros de entrada forem invalidos.
        OpenAIClientError: Se a chamada a API da OpenAI falhar.
    """
    normalized_key = _validate_api_key(openai_api_key)
    normalized_prompt = _validate_image_prompt(image_prompt)

    client = openai.OpenAI(api_key=normalized_key)

    try:
        response = client.images.generate(
            model=model,
            prompt=normalized_prompt,
            size=size,
            quality=quality,
            n=1,
        )
    except Exception as error:
        raise OpenAIClientError(
            "Failed to generate slide image via DALL-E 3"
        ) from error

    try:
        url = response.data[0].url
    except (IndexError, AttributeError) as error:
        raise OpenAIClientError(
            "Unexpected response format from DALL-E 3 API"
        ) from error

    if not url:
        raise OpenAIClientError(
            "DALL-E 3 returned an empty image URL"
        )

    return url
