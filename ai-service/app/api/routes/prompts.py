from fastapi import APIRouter, HTTPException, status

from app.api.dependencies import CurrentUser, RawToken
from app.schemas.carousel import CarouselRequest, PromptBuildResponse
from app.services.auth_client import (
    AuthClientError,
    ContextNotFoundError,
    fetch_brand_context,
)
from app.services.prompt_builder import build_carousel_prompt

router = APIRouter(prefix="/prompts", tags=["prompts"])


@router.post(
    "/build",
    response_model=PromptBuildResponse,
    status_code=status.HTTP_200_OK,
    summary="Retorna prompt montado sem chamar OpenAI",
    description=(
        "Valida autenticacao JWT (sem exigir chave OpenAI), busca o "
        "contexto de marca no auth-service e retorna o prompt final "
        "montado sem efetuar chamadas a OpenAI ou Redis."
    ),
)
async def build_prompt_route(
    request: CarouselRequest,
    current_user: CurrentUser,
    raw_token: RawToken,
) -> PromptBuildResponse:
    """Retorna o prompt completo interpolado."""
    try:
        context = await fetch_brand_context(
            context_id=request.context_id,
            token=raw_token,
        )
    except ContextNotFoundError as exc:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=str(exc),
        ) from exc
    except AuthClientError as exc:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail=f"Failed to reach auth-service: {exc}",
        ) from exc

    prompt_text = build_carousel_prompt(
        prompt=request.prompt,
        style=request.style,
        slide_count=request.slide_count,
        tone=context.get("tone"),
        color_palette=context.get("colorPalette"),
        context_name=context.get("name"),
    )

    return PromptBuildResponse(prompt=prompt_text)
