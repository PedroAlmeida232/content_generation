from unittest.mock import MagicMock, patch

import openai
import pytest

from app.services.openai_client import (
    ContentFilterClientError,
    InvalidAPIKeyClientError,
    OpenAIClientError,
    RateLimitClientError,
    _MAX_PROMPT_LENGTH,
    generate_slide_image,
)

_VALID_KEY = "sk-test-key-1234"
_VALID_PROMPT = "A minimalist product shot on white background"
_FAKE_URL = "https://oaidalleapiprodscus.blob.core.windows.net/fake.png"


@pytest.fixture(autouse=True)
def disable_retry_sleep(monkeypatch):
    monkeypatch.setattr("app.core.retry._sleep", lambda seconds: None)


def _make_mock_response(url: str = _FAKE_URL) -> MagicMock:
    mock_image = MagicMock()
    mock_image.url = url
    mock_response = MagicMock()
    mock_response.data = [mock_image]
    return mock_response


@patch("app.services.openai_client.openai.OpenAI")
def test_generate_slide_image_success(mock_openai_cls: MagicMock) -> None:
    mock_client = MagicMock()
    mock_client.images.generate.return_value = _make_mock_response()
    mock_openai_cls.return_value = mock_client

    result = generate_slide_image(
        openai_api_key=_VALID_KEY,
        image_prompt=_VALID_PROMPT,
    )

    mock_openai_cls.assert_called_once_with(api_key=_VALID_KEY)
    mock_client.images.generate.assert_called_once_with(
        model="dall-e-3",
        prompt=_VALID_PROMPT,
        size="1024x1024",
        quality="standard",
        n=1,
    )
    assert result == _FAKE_URL


@patch("app.services.openai_client.openai.OpenAI")
def test_generate_slide_image_custom_params(
    mock_openai_cls: MagicMock,
) -> None:
    mock_client = MagicMock()
    mock_client.images.generate.return_value = _make_mock_response()
    mock_openai_cls.return_value = mock_client

    generate_slide_image(
        openai_api_key=_VALID_KEY,
        image_prompt=_VALID_PROMPT,
        model="dall-e-3",
        size="1792x1024",
        quality="hd",
    )

    mock_client.images.generate.assert_called_once_with(
        model="dall-e-3",
        prompt=_VALID_PROMPT,
        size="1792x1024",
        quality="hd",
        n=1,
    )


def test_generate_slide_image_rejects_blank_api_key() -> None:
    with pytest.raises(ValueError, match="openai_api_key"):
        generate_slide_image(
            openai_api_key="   ",
            image_prompt=_VALID_PROMPT,
        )


def test_generate_slide_image_rejects_blank_prompt() -> None:
    with pytest.raises(ValueError, match="image_prompt"):
        generate_slide_image(
            openai_api_key=_VALID_KEY,
            image_prompt="   ",
        )


def test_generate_slide_image_rejects_prompt_too_long() -> None:
    long_prompt = "a" * (_MAX_PROMPT_LENGTH + 1)
    with pytest.raises(ValueError, match="must not exceed"):
        generate_slide_image(
            openai_api_key=_VALID_KEY,
            image_prompt=long_prompt,
        )


@patch("app.services.openai_client.openai.OpenAI")
def test_generate_slide_image_wraps_api_error(
    mock_openai_cls: MagicMock,
) -> None:
    mock_client = MagicMock()
    mock_client.images.generate.side_effect = RuntimeError("API down")
    mock_openai_cls.return_value = mock_client

    with pytest.raises(OpenAIClientError, match="Failed to generate"):
        generate_slide_image(
            openai_api_key=_VALID_KEY,
            image_prompt=_VALID_PROMPT,
        )


@patch("app.services.openai_client.openai.OpenAI")
def test_generate_slide_image_raises_on_empty_data(
    mock_openai_cls: MagicMock,
) -> None:
    mock_client = MagicMock()
    mock_response = MagicMock()
    mock_response.data = []
    mock_client.images.generate.return_value = mock_response
    mock_openai_cls.return_value = mock_client

    with pytest.raises(OpenAIClientError, match="Unexpected response format"):
        generate_slide_image(
            openai_api_key=_VALID_KEY,
            image_prompt=_VALID_PROMPT,
        )


@patch("app.services.openai_client.openai.OpenAI")
def test_generate_slide_image_raises_on_empty_url(
    mock_openai_cls: MagicMock,
) -> None:
    mock_client = MagicMock()
    mock_client.images.generate.return_value = _make_mock_response(url="")
    mock_openai_cls.return_value = mock_client

    with pytest.raises(OpenAIClientError, match="empty image URL"):
        generate_slide_image(
            openai_api_key=_VALID_KEY,
            image_prompt=_VALID_PROMPT,
        )


def test_generate_slide_image_strips_api_key_whitespace() -> None:
    with pytest.raises(ValueError, match="openai_api_key"):
        generate_slide_image(
            openai_api_key="\t  \n",
            image_prompt=_VALID_PROMPT,
        )


def test_generate_slide_image_strips_prompt_whitespace() -> None:
    with pytest.raises(ValueError, match="image_prompt"):
        generate_slide_image(
            openai_api_key=_VALID_KEY,
            image_prompt="\t  \n",
        )


# ---------------------------------------------------------------------------
# Typed OpenAI SDK error mapping (S2-006)
# ---------------------------------------------------------------------------


@patch("app.services.openai_client.openai.OpenAI")
def test_generate_raises_rate_limit_error(
    mock_openai_cls: MagicMock,
) -> None:
    mock_client = MagicMock()
    mock_client.images.generate.side_effect = openai.RateLimitError(
        message="rate limit exceeded",
        response=MagicMock(status_code=429),
        body={},
    )
    mock_openai_cls.return_value = mock_client

    with pytest.raises(RateLimitClientError):
        generate_slide_image(
            openai_api_key=_VALID_KEY,
            image_prompt=_VALID_PROMPT,
        )


@patch("app.services.openai_client.openai.OpenAI")
def test_generate_retries_rate_limit_then_succeeds(
    mock_openai_cls: MagicMock,
) -> None:
    mock_client = MagicMock()
    mock_client.images.generate.side_effect = [
        openai.RateLimitError(
            message="rate limit exceeded",
            response=MagicMock(status_code=429),
            body={},
        ),
        _make_mock_response(),
    ]
    mock_openai_cls.return_value = mock_client

    result = generate_slide_image(
        openai_api_key=_VALID_KEY,
        image_prompt=_VALID_PROMPT,
    )

    assert result == _FAKE_URL
    assert mock_client.images.generate.call_count == 2


@patch("app.services.openai_client.openai.OpenAI")
def test_generate_raises_invalid_api_key_error(
    mock_openai_cls: MagicMock,
) -> None:
    mock_client = MagicMock()
    mock_client.images.generate.side_effect = openai.AuthenticationError(
        message="invalid api key",
        response=MagicMock(status_code=401),
        body={},
    )
    mock_openai_cls.return_value = mock_client

    with pytest.raises(InvalidAPIKeyClientError):
        generate_slide_image(
            openai_api_key=_VALID_KEY,
            image_prompt=_VALID_PROMPT,
        )


@patch("app.services.openai_client.openai.OpenAI")
def test_generate_raises_content_filter_error(
    mock_openai_cls: MagicMock,
) -> None:
    mock_client = MagicMock()
    bad_req = openai.BadRequestError(
        message="content policy violation",
        response=MagicMock(status_code=400),
        body={},
    )
    bad_req.code = "content_policy_violation"
    mock_client.images.generate.side_effect = bad_req
    mock_openai_cls.return_value = mock_client

    with pytest.raises(ContentFilterClientError):
        generate_slide_image(
            openai_api_key=_VALID_KEY,
            image_prompt=_VALID_PROMPT,
        )


@patch("app.services.openai_client.openai.OpenAI")
def test_generate_raises_generic_error_on_bad_request_no_code(
    mock_openai_cls: MagicMock,
) -> None:
    mock_client = MagicMock()
    bad_req = openai.BadRequestError(
        message="some other bad request",
        response=MagicMock(status_code=400),
        body={},
    )
    bad_req.code = None
    mock_client.images.generate.side_effect = bad_req
    mock_openai_cls.return_value = mock_client

    with pytest.raises(OpenAIClientError) as exc_info:
        generate_slide_image(
            openai_api_key=_VALID_KEY,
            image_prompt=_VALID_PROMPT,
        )
    assert not isinstance(exc_info.value, ContentFilterClientError)
