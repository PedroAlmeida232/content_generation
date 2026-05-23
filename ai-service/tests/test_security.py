from datetime import datetime, timedelta, timezone
from uuid import uuid4

import pytest
from fastapi import HTTPException
from jose import jwt

from app.core.config import settings
from app.core.security import decode_token, extract_bearer_token
from tests.conftest import OTHER_SECRET, TEST_SECRET, encode_test_token


def test_decode_token_returns_payload() -> None:
    user_id = uuid4()
    token = encode_test_token(user_id=str(user_id), email="user@example.com")

    payload = decode_token(token)

    assert payload.user_id == user_id
    assert payload.email == "user@example.com"


def test_decode_token_rejects_expired_token() -> None:
    token = encode_test_token(exp_delta=timedelta(seconds=-10))

    with pytest.raises(HTTPException) as exc_info:
        decode_token(token)

    assert exc_info.value.status_code == 401


def test_decode_token_rejects_wrong_signature() -> None:
    token = encode_test_token(secret=OTHER_SECRET)

    with pytest.raises(HTTPException) as exc_info:
        decode_token(token)

    assert exc_info.value.status_code == 401


def test_decode_token_rejects_missing_user_id_claim() -> None:
    now = datetime.now(timezone.utc)
    token = jwt.encode(
        {
            "sub": "user@example.com",
            "iat": now,
            "exp": now + timedelta(hours=1),
        },
        TEST_SECRET,
        algorithm=settings.jwt_algorithm,
    )

    with pytest.raises(HTTPException) as exc_info:
        decode_token(token)

    assert exc_info.value.status_code == 401


def test_extract_bearer_token_requires_header() -> None:
    with pytest.raises(HTTPException) as exc_info:
        extract_bearer_token(None)

    assert exc_info.value.status_code == 401
    assert exc_info.value.detail == "Authorization header required"


def test_extract_bearer_token_requires_bearer_prefix() -> None:
    with pytest.raises(HTTPException) as exc_info:
        extract_bearer_token("Token abc")

    assert exc_info.value.status_code == 401
    assert exc_info.value.detail == "Invalid authorization header"
