from __future__ import annotations

import logging
import time
from typing import Any

import structlog
from structlog.stdlib import LoggerFactory

_CONFIGURED = False


def configure_logging() -> None:
    global _CONFIGURED
    if _CONFIGURED:
        return

    structlog.configure(
        processors=[
            structlog.processors.add_log_level,
            structlog.processors.TimeStamper(fmt="iso"),
            structlog.processors.JSONRenderer(),
        ],
        logger_factory=LoggerFactory(),
    )

    root_logger = logging.getLogger()
    if not root_logger.handlers:
        logging.basicConfig(level=logging.INFO, format="%(message)s")

    _CONFIGURED = True


def get_logger(name: str | None = None) -> Any:
    configure_logging()
    return structlog.get_logger(name)


def elapsed_ms(start: float) -> float:
    return round((time.perf_counter() - start) * 1000, 2)
