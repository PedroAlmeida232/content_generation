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
from app.core.rate_limit import DailyGenerationLimitExceeded

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


@pytest.fixture(autouse=True)
def mock_daily_generation_limit():
    with patch(f"{_CAROUSEL_PATH}.check_daily_generation_limit") as mock_limit:
        mock_limit.return_value = 1
        yield mock_limit


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
    caplog,
) -> None:
    mock_fetch.return_value = _CONTEXT_PAYLOAD
    mock_task.apply_async = MagicMock()

    with caplog.at_level("INFO"):
        response = client.post(
            "/generate/carousel",
            json={
                "context_id": _CONTEXT_ID,
                "prompt": "Post sobre lancamento",
                "style": "minimalista",
                "aspect_ratio": "4:5",
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
    # Verificar que aspect_ratio foi repassado para a task
    call_kwargs = mock_task.apply_async.call_args
    task_kwargs = call_kwargs.kwargs.get(
        "kwargs", call_kwargs[1].get("kwargs", {})
    )
    assert task_kwargs.get("aspect_ratio") == "4:5"

    log_entries = [
        json.loads(record.message)
        for record in caplog.records
        if record.name == _CAROUSEL_PATH
    ]
    enqueued_log = next(
        entry
        for entry in log_entries
        if entry.get("event") == "carousel_generation_enqueued"
    )
    assert enqueued_log["status"] == "pending"
    assert enqueued_log["job_id"]
    assert enqueued_log["user_id"]
    assert enqueued_log["duration_ms"] >= 0


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


@patch(f"{_CAROUSEL_PATH}.generate_carousel")
@patch(f"{_CAROUSEL_PATH}.save_redis_status")
@patch(
    f"{_CAROUSEL_PATH}.fetch_brand_context",
    new_callable=AsyncMock,
)
def test_generate_carousel_rejects_daily_limit(
    mock_fetch,
    mock_save,
    mock_task,
    client: TestClient,
    auth_headers: dict[str, str],
    mock_daily_generation_limit,
) -> None:
    mock_fetch.return_value = _CONTEXT_PAYLOAD
    mock_daily_generation_limit.side_effect = DailyGenerationLimitExceeded(
        "Daily generation limit reached. Try again tomorrow."
    )
    mock_task.apply_async = MagicMock()

    response = client.post(
        "/generate/carousel",
        json={
            "context_id": _CONTEXT_ID,
            "prompt": "Post sobre lancamento",
            "style": "minimalista",
            "aspect_ratio": "4:5",
        },
        headers=auth_headers,
    )

    assert response.status_code == 429
    assert (
        response.json()["detail"]
        == "Daily generation limit reached. Try again tomorrow."
    )
    mock_fetch.assert_awaited_once()
    mock_save.assert_not_called()
    mock_task.apply_async.assert_not_called()


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


def test_generate_carousel_invalid_aspect_ratio(
    client: TestClient,
    auth_headers: dict[str, str],
) -> None:
    response = client.post(
        "/generate/carousel",
        json={
            "context_id": _CONTEXT_ID,
            "prompt": "Post",
            "style": "minimalista",
            "aspect_ratio": "16:9",
        },
        headers=auth_headers,
    )
    assert response.status_code == 422


# ---------------------------------------------------------------------------
# POST /generate/preview
# ---------------------------------------------------------------------------


@patch(f"{_CAROUSEL_PATH}.generate_slide_image")
@patch(f"{_CAROUSEL_PATH}.run_image_prompt_chain")
@patch(f"{_CAROUSEL_PATH}.run_slide_text_chain")
@patch(
    f"{_CAROUSEL_PATH}.fetch_brand_context",
    new_callable=AsyncMock,
)
def test_generate_preview_success(
    mock_fetch,
    mock_text_chain,
    mock_prompt_chain,
    mock_generate_img,
    client: TestClient,
    auth_headers: dict[str, str],
) -> None:
    mock_fetch.return_value = _CONTEXT_PAYLOAD

    # Mock responses for slide text and image prompt chains
    mock_text_slide = MagicMock()
    mock_text_slide.slide_order = 1
    mock_text_slide.title = "Preview Title"
    mock_text_slide.caption = "Preview Caption"

    mock_text_res = MagicMock()
    mock_text_res.slides = [mock_text_slide]
    mock_text_chain.return_value = mock_text_res

    mock_prompt_slide = MagicMock()
    mock_prompt_slide.slide_order = 1
    mock_prompt_slide.image_prompt = "Preview Prompt"

    mock_prompt_res = MagicMock()
    mock_prompt_res.slides = [mock_prompt_slide]
    mock_prompt_chain.return_value = mock_prompt_res

    mock_generate_img.return_value = "https://cdn.example.com/preview.png"

    response = client.post(
        "/generate/preview",
        json={
            "context_id": _CONTEXT_ID,
            "prompt": "Post sobre Docker",
            "style": "minimalista",
            "aspect_ratio": "9:16",
        },
        headers=auth_headers,
    )

    assert response.status_code == 200
    body = response.json()
    assert body["slide_order"] == 1
    assert body["image_url"] == "https://cdn.example.com/preview.png"
    assert body["caption"] == "Preview Caption"
    assert body["prompt_used"] == "Preview Prompt"

    mock_fetch.assert_awaited_once()
    mock_text_chain.assert_called_once()
    mock_prompt_chain.assert_called_once()
    mock_generate_img.assert_called_once()


@patch(
    f"{_CAROUSEL_PATH}.fetch_brand_context",
    new_callable=AsyncMock,
)
def test_generate_preview_context_not_found(
    mock_fetch,
    client: TestClient,
    auth_headers: dict[str, str],
) -> None:
    from app.services.auth_client import ContextNotFoundError
    mock_fetch.side_effect = ContextNotFoundError("not found")

    response = client.post(
        "/generate/preview",
        json={
            "context_id": _CONTEXT_ID,
            "prompt": "Docker",
            "style": "minimalista",
        },
        headers=auth_headers,
    )
    assert response.status_code == 404


@patch(
    f"{_CAROUSEL_PATH}.fetch_brand_context",
    new_callable=AsyncMock,
)
def test_generate_preview_auth_service_error(
    mock_fetch,
    client: TestClient,
    auth_headers: dict[str, str],
) -> None:
    from app.services.auth_client import AuthClientError
    mock_fetch.side_effect = AuthClientError("service down")

    response = client.post(
        "/generate/preview",
        json={
            "context_id": _CONTEXT_ID,
            "prompt": "Docker",
            "style": "minimalista",
        },
        headers=auth_headers,
    )
    assert response.status_code == 502


def test_generate_preview_missing_jwt(
    client: TestClient,
) -> None:
    response = client.post(
        "/generate/preview",
        json={
            "context_id": _CONTEXT_ID,
            "prompt": "Docker",
            "style": "minimalista",
        },
        headers={"X-OpenAI-Key": _VALID_KEY},
    )
    assert response.status_code == 401


def test_generate_preview_missing_openai_key(
    client: TestClient,
    auth_headers_no_key: dict[str, str],
) -> None:
    response = client.post(
        "/generate/preview",
        json={
            "context_id": _CONTEXT_ID,
            "prompt": "Docker",
            "style": "minimalista",
        },
        headers=auth_headers_no_key,
    )
    assert response.status_code == 400


@patch(f"{_CAROUSEL_PATH}.run_slide_text_chain")
@patch(
    f"{_CAROUSEL_PATH}.fetch_brand_context",
    new_callable=AsyncMock,
)
def test_generate_preview_openai_rate_limit(
    mock_fetch,
    mock_text_chain,
    client: TestClient,
    auth_headers: dict[str, str],
) -> None:
    from app.services.openai_client import RateLimitClientError
    mock_fetch.return_value = _CONTEXT_PAYLOAD
    mock_text_chain.side_effect = RateLimitClientError("rate limit")

    response = client.post(
        "/generate/preview",
        json={
            "context_id": _CONTEXT_ID,
            "prompt": "Docker",
            "style": "minimalista",
        },
        headers=auth_headers,
    )
    assert response.status_code == 429


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
# DELETE /jobs/{job_id}
# ---------------------------------------------------------------------------


@patch(f"{_CAROUSEL_PATH}.celery_app.control.revoke")
@patch(f"{_CAROUSEL_PATH}.cancel_redis_status")
@patch(f"{_CAROUSEL_PATH}.get_redis_status")
def test_cancel_job_processing(
    mock_get,
    mock_cancel,
    mock_revoke,
    client: TestClient,
    auth_headers: dict[str, str],
) -> None:
    mock_get.return_value = {
        "job_id": _JOB_ID,
        "status": "processing",
        "progress": 40,
        "slides": None,
        "error": None,
    }

    response = client.delete(
        f"/jobs/{_JOB_ID}",
        headers=auth_headers,
    )

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "cancelled"
    assert body["job_id"] == _JOB_ID
    mock_cancel.assert_called_once_with(_JOB_ID)
    mock_revoke.assert_called_once_with(
        _JOB_ID,
        terminate=True,
        signal="SIGTERM",
    )


@patch(f"{_CAROUSEL_PATH}.celery_app.control.revoke")
@patch(f"{_CAROUSEL_PATH}.cancel_redis_status")
@patch(f"{_CAROUSEL_PATH}.get_redis_status")
def test_cancel_job_pending(
    mock_get,
    mock_cancel,
    mock_revoke,
    client: TestClient,
    auth_headers: dict[str, str],
) -> None:
    mock_get.return_value = {
        "job_id": _JOB_ID,
        "status": "pending",
        "progress": None,
        "slides": None,
        "error": None,
    }

    response = client.delete(
        f"/jobs/{_JOB_ID}",
        headers=auth_headers,
    )

    assert response.status_code == 200
    assert response.json()["status"] == "cancelled"
    mock_cancel.assert_called_once_with(_JOB_ID)
    mock_revoke.assert_called_once_with(_JOB_ID)


@patch(f"{_CAROUSEL_PATH}.cancel_redis_status")
@patch(f"{_CAROUSEL_PATH}.get_redis_status")
def test_cancel_job_done_returns_409(
    mock_get,
    mock_cancel,
    client: TestClient,
    auth_headers: dict[str, str],
) -> None:
    mock_get.return_value = {
        "job_id": _JOB_ID,
        "status": "done",
        "progress": None,
        "slides": _SLIDES_DONE,
        "error": None,
    }

    response = client.delete(
        f"/jobs/{_JOB_ID}",
        headers=auth_headers,
    )

    assert response.status_code == 409
    mock_cancel.assert_not_called()


@patch(f"{_CAROUSEL_PATH}.cancel_redis_status")
@patch(f"{_CAROUSEL_PATH}.get_redis_status")
def test_cancel_job_already_cancelled_returns_409(
    mock_get,
    mock_cancel,
    client: TestClient,
    auth_headers: dict[str, str],
) -> None:
    mock_get.return_value = {
        "job_id": _JOB_ID,
        "status": "cancelled",
        "progress": None,
        "slides": None,
        "error": None,
    }

    response = client.delete(
        f"/jobs/{_JOB_ID}",
        headers=auth_headers,
    )

    assert response.status_code == 409
    mock_cancel.assert_not_called()


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
