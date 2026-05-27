import uuid

from fastapi import APIRouter, HTTPException, status

from app.api.dependencies import CurrentUser, OpenAIKey, RawToken
from app.core.redis import get_redis_status, save_redis_status
from app.schemas.carousel import (
    CarouselRequest,
    CarouselResponse,
    CarouselResultResponse,
    SlideImageRequest,
    SlideImageResponse,
)
from app.services.auth_client import (
    AuthClientError,
    ContextNotFoundError,
    fetch_brand_context,
)
from app.services.openai_client import (
    ContentFilterClientError,
    InvalidAPIKeyClientError,
    OpenAIClientError,
    RateLimitClientError,
    generate_slide_image,
)
from app.tasks.generate_task import generate_carousel

router = APIRouter(prefix="/generate", tags=["generation"])
jobs_router = APIRouter(prefix="/jobs", tags=["jobs"])


# ---------------------------------------------------------------------------
# POST /generate/slide-image
# ---------------------------------------------------------------------------


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
    except RateLimitClientError as exc:
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail=(
                "OpenAI rate limit reached. "
                "Please try again later."
            ),
        ) from exc
    except InvalidAPIKeyClientError as exc:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or expired OpenAI API key.",
        ) from exc
    except ContentFilterClientError as exc:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=(
                "Image prompt was rejected by "
                "OpenAI content filter."
            ),
        ) from exc
    except OpenAIClientError as exc:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail="Failed to generate slide image via DALL-E 3",
        ) from exc

    return SlideImageResponse(image_url=url)  # type: ignore[arg-type]


# ---------------------------------------------------------------------------
# POST /generate/carousel
# ---------------------------------------------------------------------------


@router.post(
    "/carousel",
    response_model=CarouselResponse,
    status_code=status.HTTP_202_ACCEPTED,
    summary="Enfileira geracao assincrona de carrossel",
    description=(
        "Valida JWT e X-OpenAI-Key, busca o contexto de marca no "
        "auth-service, registra o job como 'pending' no Redis e "
        "enfileira a task Celery. Retorna o job_id imediatamente."
    ),
)
async def generate_carousel_route(
    request: CarouselRequest,
    current_user: CurrentUser,
    openai_key: OpenAIKey,
    raw_token: RawToken,
) -> CarouselResponse:
    """Dispara geracao assincrona de carrossel."""
    # 1. Buscar contexto de marca no auth-service
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

    # 2. Gerar job_id e registrar status inicial no Redis
    job_id = str(uuid.uuid4())
    save_redis_status(job_id, "pending")

    # 3. Enfileirar task Celery (task_id = job_id para correlação)
    generate_carousel.apply_async(
        kwargs={
            "openai_api_key": openai_key,
            "prompt": request.prompt,
            "style": request.style,
            "slide_count": request.slide_count,
            "tone": context.get("tone"),
            "color_palette": context.get("colorPalette"),
            "context_name": context.get("name"),
        },
        task_id=job_id,
    )

    return CarouselResponse(job_id=job_id, status="pending")  # type: ignore


# ---------------------------------------------------------------------------
# GET /jobs/{job_id}  &  GET /jobs/{job_id}/result
# ---------------------------------------------------------------------------


def _get_job_or_404(job_id: str) -> dict:
    """Retorna o payload do Redis ou lança 404."""
    payload = get_redis_status(job_id)
    if payload is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Job '{job_id}' not found",
        )
    return payload


@jobs_router.get(
    "/{job_id}",
    response_model=CarouselResponse,
    summary="Status do job de geração",
    description=(
        "Retorna o status atual do job (pending, processing, done "
        "ou failed) com progresso e slides quando aplicável."
    ),
)
def get_job_status(
    job_id: str,
    _current_user: CurrentUser,
) -> CarouselResponse:
    """Polling de status do job Celery via Redis."""
    payload = _get_job_or_404(job_id)
    return CarouselResponse.model_validate(payload)


@jobs_router.get(
    "/{job_id}/result",
    response_model=CarouselResultResponse,
    summary="Resultado final do carrossel gerado",
    description=(
        "Retorna apenas a lista de slides quando o job esta "
        "concluido (status=done). Retorna 400 caso contrario."
    ),
)
def get_job_result(
    job_id: str,
    _current_user: CurrentUser,
) -> CarouselResultResponse:
    """Retorna slides do job concluído ou erro 400."""
    payload = _get_job_or_404(job_id)
    if payload.get("status") != "done":
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=(
                f"Job '{job_id}' is not done yet "
                f"(current status: {payload.get('status')})"
            ),
        )
    return CarouselResultResponse.model_validate(payload)
