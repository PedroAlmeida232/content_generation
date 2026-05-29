import asyncio
from unittest.mock import AsyncMock, MagicMock, patch
from uuid import uuid4

import httpx
import pytest

from app.services.auth_client import (
    ContextNotFoundError,
    TransientAuthClientError,
    fetch_brand_context,
)

_CONTEXT_ID = uuid4()
_TOKEN = "jwt-token"


def _mock_response(status_code: int, body: dict | None = None) -> MagicMock:
    response = MagicMock()
    response.status_code = status_code
    response.is_success = 200 <= status_code < 300
    response.json.return_value = body or {}
    return response


@patch("app.core.retry._sleep", side_effect=lambda seconds: None)
@patch("app.services.auth_client.httpx.AsyncClient")
def test_fetch_brand_context_retries_request_error_then_succeeds(
    mock_async_client_cls,
    mock_sleep,
) -> None:
    request = httpx.Request("GET", "http://auth-service/contexts")
    transient_error = httpx.RequestError("boom", request=request)
    response = _mock_response(
        200,
        {
            "id": str(_CONTEXT_ID),
            "name": "Brand",
            "tone": "professional",
            "colorPalette": ["#000000"],
        },
    )

    mock_client = MagicMock()
    mock_client.__aenter__ = AsyncMock(return_value=mock_client)
    mock_client.__aexit__ = AsyncMock(return_value=None)
    mock_client.get = AsyncMock(side_effect=[transient_error, response])
    mock_async_client_cls.return_value = mock_client

    result = asyncio.run(fetch_brand_context(_CONTEXT_ID, _TOKEN))

    assert result["name"] == "Brand"
    assert mock_client.get.call_count == 2
    mock_sleep.assert_called()


@patch("app.core.retry._sleep", side_effect=lambda seconds: None)
@patch("app.services.auth_client.httpx.AsyncClient")
def test_fetch_brand_context_does_not_retry_404(
    mock_async_client_cls,
    mock_sleep,
) -> None:
    response = _mock_response(404)

    mock_client = MagicMock()
    mock_client.__aenter__ = AsyncMock(return_value=mock_client)
    mock_client.__aexit__ = AsyncMock(return_value=None)
    mock_client.get = AsyncMock(return_value=response)
    mock_async_client_cls.return_value = mock_client

    with pytest.raises(ContextNotFoundError):
        asyncio.run(fetch_brand_context(_CONTEXT_ID, _TOKEN))

    assert mock_client.get.call_count == 1
    mock_sleep.assert_not_called()


@patch("app.core.retry._sleep", side_effect=lambda seconds: None)
@patch("app.services.auth_client.httpx.AsyncClient")
def test_fetch_brand_context_retries_5xx_then_fails(
    mock_async_client_cls,
    mock_sleep,
) -> None:
    first_response = _mock_response(502)

    mock_client = MagicMock()
    mock_client.__aenter__ = AsyncMock(return_value=mock_client)
    mock_client.__aexit__ = AsyncMock(return_value=None)
    mock_client.get = AsyncMock(return_value=first_response)
    mock_async_client_cls.return_value = mock_client

    with pytest.raises(TransientAuthClientError):
        asyncio.run(fetch_brand_context(_CONTEXT_ID, _TOKEN))

    assert mock_client.get.call_count == 3
    mock_sleep.assert_called()
