"""
Testes de integração para:
  POST /generate/carousel
  GET  /jobs/{job_id}
  GET  /jobs/{job_id}/result

Todos os testes mocam chamadas externas (redis, auth-service, Celery).
"""
import json
from unittest.mock import AsyncMock, MagicMock, patch
from uuid import uuid4

import pytest
from fastapi.testclient import TestClient

from tests.conftest import encode_test_token

_CONTEXT_ID = str(uuid4())
_JOB_ID = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
_VALID_KEY = "sk-test-key-abcde"
_CONTEXT_PAYLOAD = {
    "id": _CONTEXT_ID,
    "name": "Test Brand",
    "tone": "professional",
    "colorPalette": ["#000000", "#ffffff"],
}
_CAROUSEL_PATH = "app.api.routes.generate"
_REDIS_PATH = "app.core.redis.redis_client"

_SLIDES_DONE = [
    {
        "slide_order": 1,
        "image_url": "https://cdn.example.com/slide1.png",
        "caption": "Slide 1",
        "prompt_used": "A prompt",
    }
]


@pytest.fixture
def auth_headers(authorization_header) -> dict[str, str]:
    token = encode_test_token()
    return {
        **authorization_header(token),
        "X-OpenAI-Key": _VALID_KEY,
    }


@pytest.fixture
def auth_headers_no_key(authorization_header) -> dict[str, str]:
    token = encode_test_token()
    return authorization_header(token)


# ---------------------------------------------------------------------------
# POST /generate/carousel
# ---------------------------------------------------------------------------


@patch(f"{_CAROUSEL_PATH}.generate_carousel")
@patch(f"{_CAROUSEL_PATH}.save_redis_status")
@patch(
    f"{_CAROUSEL_PATH}.fetch_brand_context",
    new_callable=AsyncMock,
)
def test_generate_carousel_success(
    mock_fetch,
    mock_save,
    mock_task,
    client: TestClient,
    auth_headers: dict[str, str],
) -> None:
    mock_fetch.return_value = _CONTEXT_PAYLOAD
    mock_task.apply_async = MagicMock()

    response = client.post(
        "/generate/carousel",
        json={
            "context_id": _CONTEXT_ID,
            "prompt": "Post sobre lancamento",
            "style": "minimalista",
        },
        headers=auth_headers,
    )

    assert response.status_code == 202
    body = response.json()
    assert body["status"] == "pending"
    assert "job_id" in body
    mock_fetch.assert_awaited_once()
    mock_save.assert_called_once()
    mock_task.apply_async.assert_called_once()


@patch(
    f"{_CAROUSEL_PATH}.fetch_brand_context",
    new_callable=AsyncMock,
)
def test_generate_carousel_auth_service_error(
    mock_fetch,
    client: TestClient,
    auth_headers: dict[str, str],
) -> None:
    from app.services.auth_client import AuthClientError
    mock_fetch.side_effect = AuthClientError("service down")

    response = client.post(
        "/generate/carousel",
        json={
            "context_id": _CONTEXT_ID,
            "prompt": "Post sobre lancamento",
            "style": "minimalista",
        },
        headers=auth_headers,
    )
    assert response.status_code == 502


@patch(
    f"{_CAROUSEL_PATH}.fetch_brand_context",
    new_callable=AsyncMock,
)
def test_generate_carousel_context_not_found(
    mock_fetch,
    client: TestClient,
    auth_headers: dict[str, str],
) -> None:
    from app.services.auth_client import ContextNotFoundError
    mock_fetch.side_effect = ContextNotFoundError("not found")

    response = client.post(
        "/generate/carousel",
        json={
            "context_id": _CONTEXT_ID,
            "prompt": "Post sobre lancamento",
            "style": "minimalista",
        },
        headers=auth_headers,
    )
    assert response.status_code == 404


def test_generate_carousel_missing_jwt(
    client: TestClient,
    auth_headers: dict[str, str],
) -> None:
    response = client.post(
        "/generate/carousel",
        json={
            "context_id": _CONTEXT_ID,
            "prompt": "Post",
            "style": "minimalista",
        },
        headers={"X-OpenAI-Key": _VALID_KEY},
    )
    assert response.status_code == 401


def test_generate_carousel_missing_openai_key(
    client: TestClient,
    auth_headers_no_key: dict[str, str],
) -> None:
    response = client.post(
        "/generate/carousel",
        json={
            "context_id": _CONTEXT_ID,
            "prompt": "Post",
            "style": "minimalista",
        },
        headers=auth_headers_no_key,
    )
    assert response.status_code == 400


# ---------------------------------------------------------------------------
# GET /jobs/{job_id}
# ---------------------------------------------------------------------------


@patch(_REDIS_PATH)
def test_get_job_status_processing(
    mock_redis,
    client: TestClient,
    auth_headers: dict[str, str],
) -> None:
    mock_redis.get.return_value = json.dumps(
        {
            "job_id": _JOB_ID,
            "status": "processing",
            "progress": 30,
            "slides": None,
            "error": None,
        }
    )
    response = client.get(
        f"/jobs/{_JOB_ID}",
        headers=auth_headers,
    )
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "processing"
    assert body["progress"] == 30


@patch(_REDIS_PATH)
def test_get_job_status_not_found(
    mock_redis,
    client: TestClient,
    auth_headers: dict[str, str],
) -> None:
    mock_redis.get.return_value = None

    response = client.get(
        "/jobs/nonexistent-job",
        headers=auth_headers,
    )
    assert response.status_code == 404


@patch(_REDIS_PATH)
def test_get_job_status_done(
    mock_redis,
    client: TestClient,
    auth_headers: dict[str, str],
) -> None:
    mock_redis.get.return_value = json.dumps(
        {
            "job_id": _JOB_ID,
            "status": "done",
            "progress": None,
            "slides": _SLIDES_DONE,
            "error": None,
        }
    )

    response = client.get(
        f"/jobs/{_JOB_ID}",
        headers=auth_headers,
    )
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "done"
    assert len(body["slides"]) == 1


@patch(_REDIS_PATH)
def test_get_job_status_failed(
    mock_redis,
    client: TestClient,
    auth_headers: dict[str, str],
) -> None:
    mock_redis.get.return_value = json.dumps(
        {
            "job_id": _JOB_ID,
            "status": "failed",
            "progress": None,
            "slides": None,
            "error": "Something went wrong",
        }
    )

    response = client.get(
        f"/jobs/{_JOB_ID}",
        headers=auth_headers,
    )
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "failed"
    assert body["error"] == "Something went wrong"


# ---------------------------------------------------------------------------
# GET /jobs/{job_id}/result
# ---------------------------------------------------------------------------


@patch(_REDIS_PATH)
def test_get_job_result_done(
    mock_redis,
    client: TestClient,
    auth_headers: dict[str, str],
) -> None:
    mock_redis.get.return_value = json.dumps(
        {
            "job_id": _JOB_ID,
            "status": "done",
            "progress": None,
            "slides": _SLIDES_DONE,
            "error": None,
        }
    )

    response = client.get(
        f"/jobs/{_JOB_ID}/result",
        headers=auth_headers,
    )
    assert response.status_code == 200
    body = response.json()
    assert "slides" in body
    assert len(body["slides"]) == 1


@patch(_REDIS_PATH)
def test_get_job_result_not_done_returns_400(
    mock_redis,
    client: TestClient,
    auth_headers: dict[str, str],
) -> None:
    mock_redis.get.return_value = json.dumps(
        {
            "job_id": _JOB_ID,
            "status": "processing",
            "progress": 50,
            "slides": None,
            "error": None,
        }
    )

    response = client.get(
        f"/jobs/{_JOB_ID}/result",
        headers=auth_headers,
    )
    assert response.status_code == 400


@patch(_REDIS_PATH)
def test_get_job_result_not_found(
    mock_redis,
    client: TestClient,
    auth_headers: dict[str, str],
) -> None:
    mock_redis.get.return_value = None

    response = client.get(
        "/jobs/nonexistent-job/result",
        headers=auth_headers,
    )
    assert response.status_code == 404
