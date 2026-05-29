from datetime import date, datetime, timezone
from uuid import UUID

from app.core.redis import redis_client

DAILY_GENERATION_LIMIT = 10
DAILY_GENERATION_TTL_SECONDS = 172800
DAILY_GENERATION_LIMIT_EXCEEDED_MESSAGE = (
    "Daily generation limit reached. Try again tomorrow."
)

_RATE_LIMIT_SCRIPT = """
local current = redis.call('GET', KEYS[1])
if current and tonumber(current) >= tonumber(ARGV[1]) then
    return -1
end

local new_count = redis.call('INCR', KEYS[1])
if new_count == 1 then
    redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2]))
end

return new_count
"""


class DailyGenerationLimitExceeded(Exception):
    """Raised when a user reaches the daily generation quota."""


def _daily_generation_key(user_id: UUID, current_day: date) -> str:
    return (
        f"rate_limit:generate_carousel:{user_id}:{current_day.isoformat()}"
    )


def check_daily_generation_limit(
    user_id: UUID,
    *,
    limit: int = DAILY_GENERATION_LIMIT,
    today: date | None = None,
) -> int:
    current_day = today or datetime.now(timezone.utc).date()
    key = _daily_generation_key(user_id, current_day)

    result = redis_client.eval(
        _RATE_LIMIT_SCRIPT,
        1,
        key,
        limit,
        DAILY_GENERATION_TTL_SECONDS,
    )

    if int(result) == -1:
        raise DailyGenerationLimitExceeded(
            DAILY_GENERATION_LIMIT_EXCEEDED_MESSAGE
        )

    return int(result)
