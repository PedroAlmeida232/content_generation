from __future__ import annotations

from typing import Any

from app.core.logging import get_logger
from app.core.redis import redis_client

logger = get_logger(__name__)

_METRICS_KEY = "metrics:usage"
_RECORDED_PREFIX = "metrics:usage:recorded:"
_TOTAL_DURATION_FIELD = "successful_generation_duration_ms"
_SUCCESS_FIELD = "successful_generations"
_FAILURE_FIELD = "failed_generations"
_CANCELLED_FIELD = "cancelled_generations"

_VALID_OUTCOMES = {"done", "failed", "cancelled"}


def _marker_key(job_id: str) -> str:
    return f"{_RECORDED_PREFIX}{job_id}"


def record_generation_outcome(
    job_id: str,
    status: str,
    *,
    duration_ms: float | None = None,
) -> bool:
    if status not in _VALID_OUTCOMES:
        return False

    try:
        recorded = redis_client.set(_marker_key(job_id), status, nx=True)
        if not recorded:
            return False

        pipeline = redis_client.pipeline()
        if status == "done":
            pipeline.hincrby(_METRICS_KEY, _SUCCESS_FIELD, 1)
            if duration_ms is not None:
                pipeline.hincrbyfloat(
                    _METRICS_KEY,
                    _TOTAL_DURATION_FIELD,
                    float(duration_ms),
                )
        elif status == "failed":
            pipeline.hincrby(_METRICS_KEY, _FAILURE_FIELD, 1)
        elif status == "cancelled":
            pipeline.hincrby(_METRICS_KEY, _CANCELLED_FIELD, 1)

        pipeline.execute()
        return True
    except Exception as exc:  # pragma: no cover - defensive guard
        logger.warning("metrics_recording_failed", error=str(exc))
        return False


def get_usage_metrics() -> dict[str, Any]:
    defaults: dict[str, Any] = {
        _SUCCESS_FIELD: 0,
        _FAILURE_FIELD: 0,
        _CANCELLED_FIELD: 0,
        _TOTAL_DURATION_FIELD: 0.0,
    }

    try:
        raw_metrics = redis_client.hgetall(_METRICS_KEY)
    except Exception as exc:  # pragma: no cover - defensive guard
        logger.warning("metrics_read_failed", error=str(exc))
        raw_metrics = {}

    metrics = {**defaults, **raw_metrics}

    successful_generations = int(metrics[_SUCCESS_FIELD])
    failed_generations = int(metrics[_FAILURE_FIELD])
    cancelled_generations = int(metrics[_CANCELLED_FIELD])
    total_duration_ms = float(metrics[_TOTAL_DURATION_FIELD])
    total_recorded_generations = (
        successful_generations
        + failed_generations
        + cancelled_generations
    )
    average_generation_time_ms = (
        round(total_duration_ms / successful_generations, 2)
        if successful_generations
        else 0.0
    )
    error_denominator = successful_generations + failed_generations
    error_rate = (
        round(failed_generations / error_denominator, 4)
        if error_denominator
        else 0.0
    )

    return {
        "average_generation_time_ms": average_generation_time_ms,
        "error_rate": error_rate,
        "successful_generations": successful_generations,
        "failed_generations": failed_generations,
        "cancelled_generations": cancelled_generations,
        "total_recorded_generations": total_recorded_generations,
    }
