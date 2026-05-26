from fastapi import APIRouter, HTTPException, status

from app.api.dependencies import CurrentUser, OpenAIKey
from app.schemas.carousel import SlideImageRequest, SlideImageResponse
from app.services.openai_client import OpenAIClientError, generate_slide_image

router = APIRouter(prefix="/generate", tags=["generation"])


@router.post(
    "/slide-image",
    response_model=SlideImageResponse,
    summary="Gera imagem de slide via DALL-E 3",
    description=(
        "Recebe um prompt visual e a chave OpenAI via header "
        "X-OpenAI-Key, invoca o DALL-E 3 e retorna a URL "
        "temporaria da imagem gerada. Requer autenticacao JWT."
    ),
)
def generate_slide_image_route(
    request: SlideImageRequest,
    _current_user: CurrentUser,
    openai_key: OpenAIKey,
) -> SlideImageResponse:
    """Endpoint sincrono de geracao de imagem por slide."""
    try:
        url = generate_slide_image(
            openai_api_key=openai_key,
            image_prompt=request.image_prompt,
            model=request.model,
            size=request.size,
            quality=request.quality,
        )
    except ValueError as exc:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=str(exc),
        ) from exc
    except OpenAIClientError as exc:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail="Failed to generate slide image via DALL-E 3",
        ) from exc

    return SlideImageResponse(image_url=url)  # type: ignore[arg-type]
