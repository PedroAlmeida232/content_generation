from datetime import date
from uuid import UUID
from unittest.mock import patch

import pytest

from app.core.rate_limit import (
    DailyGenerationLimitExceeded,
    check_daily_generation_limit,
)

_USER_ID = UUID("550e8400-e29b-41d4-a716-446655440000")


@patch("app.core.rate_limit.redis_client")
def test_check_daily_generation_limit_increments_daily_counter(mock_redis):
    mock_redis.eval.return_value = 1

    count = check_daily_generation_limit(
        _USER_ID,
        today=date(2026, 5, 29),
    )

    assert count == 1
    mock_redis.eval.assert_called_once()

    script, numkeys, key, limit, ttl = mock_redis.eval.call_args.args
    assert numkeys == 1
    assert key == (
        "rate_limit:generate_carousel:"
        "550e8400-e29b-41d4-a716-446655440000:2026-05-29"
    )
    assert limit == 10
    assert ttl == 172800
    assert "redis.call('INCR'" in script


@patch("app.core.rate_limit.redis_client")
def test_check_daily_generation_limit_raises_when_limit_is_reached(mock_redis):
    mock_redis.eval.return_value = -1

    with pytest.raises(DailyGenerationLimitExceeded):
        check_daily_generation_limit(
            _USER_ID,
            today=date(2026, 5, 29),
        )
