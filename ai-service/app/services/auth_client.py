"""Cliente HTTP assíncrono para comunicação com o auth-service."""
import logging
from uuid import UUID

import httpx

from app.core.config import settings

logger = logging.getLogger(__name__)


class AuthClientError(Exception):
    """Erro genérico ao comunicar com o auth-service."""


class ContextNotFoundError(AuthClientError):
    """O contexto de marca solicitado nao foi encontrado."""


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
    url = (
        f"{settings.auth_service_url}"
        f"/contexts/{context_id}"
    )
    headers = {"Authorization": f"Bearer {token}"}

    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            response = await client.get(url, headers=headers)
    except httpx.RequestError as exc:
        raise AuthClientError(
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

    if not response.is_success:
        raise AuthClientError(
            f"auth-service returned HTTP {response.status_code}"
        )

    return response.json()
