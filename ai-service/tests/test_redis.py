import json
from unittest.mock import patch

from app.core.redis import get_redis_status, save_redis_status

_JOB_ID = "test-job-uuid-1234"
_KEY = f"job:{_JOB_ID}"
_TTL = 86400


@patch("app.core.redis.redis_client")
def test_save_redis_status_sets_24h_ttl(mock_redis) -> None:
    save_redis_status(
        _JOB_ID,
        "processing",
        progress=30,
    )

    mock_redis.set.assert_called_once_with(
        _KEY,
        json.dumps(
            {
                "job_id": _JOB_ID,
                "status": "processing",
                "progress": 30,
                "slides": None,
                "error": None,
            }
        ),
        ex=_TTL,
    )


@patch("app.core.redis.redis_client")
def test_get_redis_status_returns_parsed_payload(mock_redis) -> None:
    mock_redis.get.return_value = json.dumps(
        {
            "job_id": _JOB_ID,
            "status": "done",
            "progress": None,
            "slides": [],
            "error": None,
        }
    )

    payload = get_redis_status(_JOB_ID)

    assert payload == {
        "job_id": _JOB_ID,
        "status": "done",
        "progress": None,
        "slides": [],
        "error": None,
    }
    mock_redis.get.assert_called_once_with(_KEY)


@patch("app.core.redis.redis_client")
def test_get_redis_status_returns_none_when_missing(mock_redis) -> None:
    mock_redis.get.return_value = None

    payload = get_redis_status(_JOB_ID)

    assert payload is None
    mock_redis.get.assert_called_once_with(_KEY)
