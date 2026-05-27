from unittest.mock import AsyncMock, patch
from uuid import uuid4

import pytest
from fastapi.testclient import TestClient

from tests.conftest import encode_test_token

_CONTEXT_ID = str(uuid4())
_CONTEXT_PAYLOAD = {
    "id": _CONTEXT_ID,
    "name": "Test Brand",
    "tone": "professional",
    "colorPalette": ["#000000", "#ffffff"],
}
_PROMPTS_PATH = "app.api.routes.prompts"


@pytest.fixture
def auth_headers(authorization_header) -> dict[str, str]:
    token = encode_test_token()
    return authorization_header(token)


@patch(
    f"{_PROMPTS_PATH}.fetch_brand_context",
    new_callable=AsyncMock,
)
def test_build_prompt_success(
    mock_fetch,
    client: TestClient,
    auth_headers: dict[str, str],
) -> None:
    mock_fetch.return_value = _CONTEXT_PAYLOAD

    response = client.post(
        "/prompts/build",
        json={
            "context_id": _CONTEXT_ID,
            "prompt": "Como funciona o Docker?",
            "style": "moderno",
            "slide_count": 5,
        },
        headers=auth_headers,
    )

    assert response.status_code == 200
    body = response.json()
    assert "prompt" in body
    prompt_text = body["prompt"]
    assert "Como funciona o Docker?" in prompt_text
    assert "Test Brand" in prompt_text
    assert "moderno" in prompt_text
    assert "professional" in prompt_text
    assert "#000000, #ffffff" in prompt_text
    mock_fetch.assert_awaited_once()


@patch(
    f"{_PROMPTS_PATH}.fetch_brand_context",
    new_callable=AsyncMock,
)
def test_build_prompt_context_not_found(
    mock_fetch,
    client: TestClient,
    auth_headers: dict[str, str],
) -> None:
    from app.services.auth_client import ContextNotFoundError

    mock_fetch.side_effect = ContextNotFoundError("not found")

    response = client.post(
        "/prompts/build",
        json={
            "context_id": _CONTEXT_ID,
            "prompt": "Como funciona o Docker?",
            "style": "moderno",
            "slide_count": 5,
        },
        headers=auth_headers,
    )

    assert response.status_code == 404
    mock_fetch.assert_awaited_once()


@patch(
    f"{_PROMPTS_PATH}.fetch_brand_context",
    new_callable=AsyncMock,
)
def test_build_prompt_auth_service_error(
    mock_fetch,
    client: TestClient,
    auth_headers: dict[str, str],
) -> None:
    from app.services.auth_client import AuthClientError

    mock_fetch.side_effect = AuthClientError("service down")

    response = client.post(
        "/prompts/build",
        json={
            "context_id": _CONTEXT_ID,
            "prompt": "Como funciona o Docker?",
            "style": "moderno",
            "slide_count": 5,
        },
        headers=auth_headers,
    )

    assert response.status_code == 502
    mock_fetch.assert_awaited_once()


def test_build_prompt_unauthorized(client: TestClient) -> None:
    response = client.post(
        "/prompts/build",
        json={
            "context_id": _CONTEXT_ID,
            "prompt": "Como funciona o Docker?",
            "style": "moderno",
            "slide_count": 5,
        },
    )

    assert response.status_code == 401
