import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Settings:
    app_name: str = os.getenv("APP_NAME", "ai-service")
    redis_url: str = os.getenv("REDIS_URL", "redis://redis:6379/0")


settings = Settings()
