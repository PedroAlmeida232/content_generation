import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Settings:
    app_name: str = os.getenv("APP_NAME", "ai-service")
    redis_url: str = os.getenv("REDIS_URL", "redis://redis:6379/0")
    jwt_secret: str = os.getenv("JWT_SECRET", "")
    jwt_algorithm: str = "HS256"
    auth_service_url: str = os.getenv(
        "AUTH_SERVICE_URL",
        "http://auth-service:8080",
    )


settings = Settings()


def require_jwt_secret() -> str:
    secret = settings.jwt_secret.strip()
    if not secret:
        raise ValueError("JWT_SECRET must be configured")
    return secret
