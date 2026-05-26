from uuid import uuid4

from fastapi.testclient import TestClient

from tests.conftest import encode_test_token


def test_me_endpoint_returns_current_user(
    client: TestClient,
    authorization_header,
) -> None:
    user_id = uuid4()
    token = encode_test_token(user_id=str(user_id), email="user@example.com")

    response = client.get("/me", headers=authorization_header(token))

    assert response.status_code == 200
    assert response.json() == {
        "userId": str(user_id),
        "email": "user@example.com",
    }


def test_me_endpoint_rejects_missing_authorization(client: TestClient) -> None:
    response = client.get("/me")

    assert response.status_code == 401
    assert response.json()["detail"] == "Authorization header required"
    assert response.headers.get("www-authenticate") == "Bearer"


def test_me_endpoint_rejects_non_bearer_scheme(client: TestClient) -> None:
    response = client.get("/me", headers={"Authorization": "Basic dGVzdA=="})

    assert response.status_code == 401
    assert response.json()["detail"] == "Authorization header required"
    assert response.headers.get("www-authenticate") == "Bearer"


def test_me_endpoint_rejects_invalid_token(
    client: TestClient,
    authorization_header,
) -> None:
    response = client.get(
        "/me",
        headers=authorization_header("invalid.jwt.token"),
    )

    assert response.status_code == 401
    assert response.json()["detail"] == "Invalid or expired token"


def test_health_and_styles_remain_public(client: TestClient) -> None:
    assert client.get("/health").status_code == 200
    assert client.get("/styles").status_code == 200


def test_openapi_documents_bearer_security_on_me(client: TestClient) -> None:
    schema = client.get("/openapi.json").json()
    me_get = schema["paths"]["/me"]["get"]

    assert "security" in me_get
    assert me_get["security"] == [{"HTTPBearer": []}]
    assert "HTTPBearer" in schema["components"]["securitySchemes"]


# ---------------------------------------------------------------------------
# get_openai_api_key dependency
# ---------------------------------------------------------------------------

_VALID_KEY = "sk-test-openai-key-1234"
_VALID_PROMPT = "A minimalist mountain landscape"
_FAKE_URL = "https://oaidalleapiprodscus.blob.core.windows.net/fake.png"


def test_generate_route_rejects_missing_openai_key(
    client: TestClient,
    authorization_header,
) -> None:
    from tests.conftest import encode_test_token

    token = encode_test_token()
    response = client.post(
        "/generate/slide-image",
        json={"image_prompt": _VALID_PROMPT},
        headers=authorization_header(token),
    )

    assert response.status_code == 400
    assert "X-OpenAI-Key" in response.json()["detail"]


def test_generate_route_rejects_empty_openai_key(
    client: TestClient,
    authorization_header,
) -> None:
    from tests.conftest import encode_test_token

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


def test_generate_route_rejects_missing_jwt(client: TestClient) -> None:
    response = client.post(
        "/generate/slide-image",
        json={"image_prompt": _VALID_PROMPT},
        headers={"X-OpenAI-Key": _VALID_KEY},
    )

    assert response.status_code == 401
