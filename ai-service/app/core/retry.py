from time import sleep as _sleep


DEFAULT_RETRY_ATTEMPTS = 3
DEFAULT_RETRY_WAIT_MULTIPLIER = 1
DEFAULT_RETRY_WAIT_MAX = 8


def retry_sleep(seconds: float) -> None:
    _sleep(seconds)


async def retry_async_sleep(seconds: float) -> None:
    _sleep(seconds)
