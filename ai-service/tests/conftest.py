import os
from collections.abc import Callable
from datetime import datetime, timedelta, timezone
from pathlib import Path
import sys
from uuid import uuid4

PROJECT_ROOT = Path(__file__).resolve().parents[1]

if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

TEST_SECRET = "12345678901234567890123456789012"
OTHER_SECRET = "abcdefghijklmnopqrstuvwxyz123456"

# JWT must be set before app modules load settings (overwrite empty env).
os.environ["JWT_SECRET"] = TEST_SECRET

import pytest  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402
from jose import jwt  # noqa: E402

from app.core.config import settings  # noqa: E402
from app.main import app  # noqa: E402


@pytest.fixture
def client() -> TestClient:
    return TestClient(app)


@pytest.fixture
def authorization_header() -> Callable[[str], dict[str, str]]:
    def _header(token: str) -> dict[str, str]:
        return {"Authorization": f"Bearer {token}"}

    return _header


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
