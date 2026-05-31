import json

import redis

from app.core.config import settings
from app.schemas.carousel import JobStatus

redis_client = redis.Redis.from_url(
    settings.redis_url,
    decode_responses=True,
)

_JOB_TTL_SECONDS = 86400  # 24 horas


def save_redis_status(
    job_id: str,
    status: str,
    progress: int | None = None,
    slides: list[dict] | None = None,
    error: str | None = None,
) -> None:
    """Persiste o estado do job no Redis com TTL de 24 horas."""
    payload = {
        "job_id": job_id,
        "status": status,
        "progress": progress,
        "slides": slides,
        "error": error,
    }
    redis_client.set(
        f"job:{job_id}",
        json.dumps(payload),
        ex=_JOB_TTL_SECONDS,
    )


def get_redis_status(job_id: str) -> dict | None:
    """Retorna o payload do job salvo no Redis ou None se nao existir."""
    raw = redis_client.get(f"job:{job_id}")
    if not isinstance(raw, str):
        return None
    return json.loads(raw)


def cancel_redis_status(job_id: str) -> None:
    """Marca um job como cancelado preservando o TTL de 24 horas."""
    save_redis_status(job_id, JobStatus.CANCELLED.value)


def is_job_cancelled(job_id: str) -> bool:
    """Indica se o job atual foi cancelado."""
    payload = get_redis_status(job_id)
    return payload is not None and payload.get("status") == JobStatus.CANCELLED.value
