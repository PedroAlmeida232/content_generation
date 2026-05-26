"""
Testes de integracao para POST /generate/slide-image.

Todos os testes mocam generate_slide_image para garantir
zero chamadas reais a API da OpenAI.
"""
from unittest.mock import patch

import pytest
from fastapi.testclient import TestClient

from app.services.openai_client import OpenAIClientError
from tests.conftest import encode_test_token

_VALID_KEY = "sk-test-key-abcde"
_VALID_PROMPT = "A futuristic city skyline at dusk, minimalist style"
_FAKE_URL = "https://oaidalleapiprodscus.blob.core.windows.net/slide.png"

_GENERATE_PATH = "app.api.routes.generate.generate_slide_image"


@pytest.fixture
def auth_headers(authorization_header) -> dict[str, str]:
    token = encode_test_token()
    return {
        **authorization_header(token),
        "X-OpenAI-Key": _VALID_KEY,
    }


# ---------------------------------------------------------------------------
# Happy path
# ---------------------------------------------------------------------------


@patch(_GENERATE_PATH, return_value=_FAKE_URL)
def test_generate_slide_image_success(
    mock_gen,
    client: TestClient,
    auth_headers: dict[str, str],
) -> None:
    response = client.post(
        "/generate/slide-image",
        json={"image_prompt": _VALID_PROMPT},
        headers=auth_headers,
    )

    assert response.status_code == 200
    assert response.json()["image_url"] == _FAKE_URL
    mock_gen.assert_called_once_with(
        openai_api_key=_VALID_KEY,
        image_prompt=_VALID_PROMPT,
        model="dall-e-3",
        size="1024x1024",
        quality="standard",
    )


@patch(_GENERATE_PATH, return_value=_FAKE_URL)
def test_generate_slide_image_custom_params(
    mock_gen,
    client: TestClient,
    auth_headers: dict[str, str],
) -> None:
    response = client.post(
        "/generate/slide-image",
        json={
            "image_prompt": _VALID_PROMPT,
            "model": "dall-e-3",
            "size": "1792x1024",
            "quality": "hd",
        },
        headers=auth_headers,
    )

    assert response.status_code == 200
    mock_gen.assert_called_once_with(
        openai_api_key=_VALID_KEY,
        image_prompt=_VALID_PROMPT,
        model="dall-e-3",
        size="1792x1024",
        quality="hd",
    )


# ---------------------------------------------------------------------------
# Authentication / authorization errors
# ---------------------------------------------------------------------------


def test_generate_rejects_missing_jwt(client: TestClient) -> None:
    response = client.post(
        "/generate/slide-image",
        json={"image_prompt": _VALID_PROMPT},
        headers={"X-OpenAI-Key": _VALID_KEY},
    )

    assert response.status_code == 401


def test_generate_rejects_invalid_jwt(
    client: TestClient,
    authorization_header,
) -> None:
    response = client.post(
        "/generate/slide-image",
        json={"image_prompt": _VALID_PROMPT},
        headers={
            **authorization_header("invalid.jwt.token"),
            "X-OpenAI-Key": _VALID_KEY,
        },
    )

    assert response.status_code == 401


# ---------------------------------------------------------------------------
# OpenAI key header errors
# ---------------------------------------------------------------------------


def test_generate_rejects_missing_openai_key(
    client: TestClient,
    authorization_header,
) -> None:
    token = encode_test_token()
    response = client.post(
        "/generate/slide-image",
        json={"image_prompt": _VALID_PROMPT},
        headers=authorization_header(token),
    )

    assert response.status_code == 400
    assert "X-OpenAI-Key" in response.json()["detail"]


def test_generate_rejects_blank_openai_key(
    client: TestClient,
    authorization_header,
) -> None:
    token = encode_test_token()
    response = client.post(
        "/generate/slide-image",
        json={"image_prompt": _VALID_PROMPT},
        headers={
            **authorization_header(token),
            "X-OpenAI-Key": "   ",
        },
    )

    assert response.status_code == 400
    assert "X-OpenAI-Key" in response.json()["detail"]


# ---------------------------------------------------------------------------
# Service layer errors
# ---------------------------------------------------------------------------


@patch(_GENERATE_PATH, side_effect=OpenAIClientError("API down"))
def test_generate_returns_502_on_openai_error(
    mock_gen,
    client: TestClient,
    auth_headers: dict[str, str],
) -> None:
    response = client.post(
        "/generate/slide-image",
        json={"image_prompt": _VALID_PROMPT},
        headers=auth_headers,
    )

    assert response.status_code == 502
    assert "DALL-E 3" in response.json()["detail"]


@patch(
    _GENERATE_PATH,
    side_effect=ValueError("image_prompt must be a non-empty string"),
)
def test_generate_returns_400_on_value_error(
    mock_gen,
    client: TestClient,
    auth_headers: dict[str, str],
) -> None:
    response = client.post(
        "/generate/slide-image",
        json={"image_prompt": _VALID_PROMPT},
        headers=auth_headers,
    )

    assert response.status_code == 400


# ---------------------------------------------------------------------------
# Request body validation
# ---------------------------------------------------------------------------


def test_generate_rejects_blank_image_prompt(
    client: TestClient,
    auth_headers: dict[str, str],
) -> None:
    response = client.post(
        "/generate/slide-image",
        json={"image_prompt": "   "},
        headers=auth_headers,
    )

    # Pydantic v2 strips before min_length check — blank -> empty -> 422
    assert response.status_code == 422


def test_generate_rejects_missing_image_prompt(
    client: TestClient,
    auth_headers: dict[str, str],
) -> None:
    response = client.post(
        "/generate/slide-image",
        json={},
        headers=auth_headers,
    )

    assert response.status_code == 422
