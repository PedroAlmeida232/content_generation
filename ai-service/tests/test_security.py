from datetime import datetime, timedelta, timezone
from uuid import uuid4

import pytest
from fastapi import HTTPException
from fastapi.testclient import TestClient
from jose import jwt

from app.core.config import settings
from app.core.security import decode_token, extract_bearer_token
from app.main import app

TEST_SECRET = "12345678901234567890123456789012"
OTHER_SECRET = "abcdefghijklmnopqrstuvwxyz123456"


@pytest.fixture
def client() -> TestClient:
    return TestClient(app)


def encode_test_token(
    secret: str = TEST_SECRET,
    *,
    user_id: str | None = None,
    email: str = "user@example.com",
    exp_delta: timedelta = timedelta(hours=1),
) -> str:
    now = datetime.now(timezone.utc)
    claims = {
        "sub": email,
        "userId": user_id or str(uuid4()),
        "iat": now,
        "exp": now + exp_delta,
    }

    return jwt.encode(claims, secret, algorithm=settings.jwt_algorithm)


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


def test_me_endpoint_returns_current_user(client: TestClient) -> None:
    user_id = uuid4()
    token = encode_test_token(user_id=str(user_id), email="user@example.com")

    response = client.get("/me", headers={"Authorization": f"Bearer {token}"})

    assert response.status_code == 200
    assert response.json() == {
        "userId": str(user_id),
        "email": "user@example.com",
    }


def test_me_endpoint_rejects_missing_authorization(client: TestClient) -> None:
    response = client.get("/me")

    assert response.status_code == 401
    assert response.json()["detail"] == "Authorization header required"


def test_me_endpoint_rejects_invalid_token(client: TestClient) -> None:
    response = client.get(
        "/me",
        headers={"Authorization": "Bearer invalid.jwt.token"},
    )

    assert response.status_code == 401
    assert response.json()["detail"] == "Invalid or expired token"


def test_health_and_styles_remain_public(client: TestClient) -> None:
    assert client.get("/health").status_code == 200
    assert client.get("/styles").status_code == 200
