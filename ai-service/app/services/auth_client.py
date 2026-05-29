"""Cliente HTTP assíncrono para comunicação com o auth-service."""
import logging
from uuid import UUID

import httpx
from tenacity import before_sleep_log, retry, retry_if_exception_type, stop_after_attempt, wait_exponential

from app.core.config import settings
from app.core.retry import (
    DEFAULT_RETRY_ATTEMPTS,
    DEFAULT_RETRY_WAIT_MAX,
    DEFAULT_RETRY_WAIT_MULTIPLIER,
    retry_async_sleep,
)

logger = logging.getLogger(__name__)


class AuthClientError(Exception):
    """Erro genérico ao comunicar com o auth-service."""


class ContextNotFoundError(AuthClientError):
    """O contexto de marca solicitado nao foi encontrado."""


class TransientAuthClientError(AuthClientError):
    """Erro transitório ao comunicar com o auth-service."""


_RETRYABLE_AUTH_ERRORS = (httpx.RequestError, TransientAuthClientError)


@retry(
    retry=retry_if_exception_type(_RETRYABLE_AUTH_ERRORS),
    stop=stop_after_attempt(DEFAULT_RETRY_ATTEMPTS),
    wait=wait_exponential(
        multiplier=DEFAULT_RETRY_WAIT_MULTIPLIER,
        max=DEFAULT_RETRY_WAIT_MAX,
    ),
    sleep=retry_async_sleep,
    before_sleep=before_sleep_log(logger, logging.WARNING),
    reraise=True,
)
async def _fetch_brand_context_with_retry(
    context_id: UUID,
    token: str,
) -> dict:
    url = (
        f"{settings.auth_service_url}"
        f"/contexts/{context_id}"
    )
    headers = {"Authorization": f"Bearer {token}"}

    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            response = await client.get(url, headers=headers)
    except httpx.RequestError as exc:
        raise TransientAuthClientError(
            f"Network error contacting auth-service: {exc}"
        ) from exc

    if response.status_code == 404:
        raise ContextNotFoundError(
            f"Context {context_id} not found"
        )

    if response.status_code in (401, 403):
        raise AuthClientError(
            "Unauthorized to access context in auth-service"
        )

    if response.status_code >= 500:
        raise TransientAuthClientError(
            f"auth-service returned HTTP {response.status_code}"
        )

    if not response.is_success:
        raise AuthClientError(
            f"auth-service returned HTTP {response.status_code}"
        )

    return response.json()


async def fetch_brand_context(
    context_id: UUID,
    token: str,
) -> dict:
    """Busca o contexto de marca no auth-service.

    Args:
        context_id: UUID do contexto de marca do usuário.
        token: JWT Bearer do usuário (propagado do request original).

    Returns:
        Dicionário com os dados do contexto (name, tone, color_palette, ...).

    Raises:
        ContextNotFoundError: Se o contexto nao existir (HTTP 404).
        AuthClientError: Para qualquer outro erro HTTP ou de rede.
    """
    return await _fetch_brand_context_with_retry(context_id, token)
