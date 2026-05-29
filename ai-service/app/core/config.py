import os
from dataclasses import dataclass


def _read_float_env(name: str, default: float) -> float:
    raw_value = os.getenv(name)
    if raw_value is None or not raw_value.strip():
        return default

    try:
        return float(raw_value)
    except ValueError as error:
        raise ValueError(f"{name} must be a valid number") from error


def _read_positive_float_env(name: str, default: float) -> float:
    value = _read_float_env(name, default)
    if value <= 0:
        raise ValueError(f"{name} must be greater than zero")
    return value


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
    openai_timeout_seconds: float = _read_positive_float_env(
        "OPENAI_TIMEOUT_SECONDS",
        30.0,
    )


settings = Settings()


def require_jwt_secret() -> str:
    secret = settings.jwt_secret.strip()
    if not secret:
        raise ValueError("JWT_SECRET must be configured")
    return secret
