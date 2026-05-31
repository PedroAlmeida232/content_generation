from __future__ import annotations

import logging
from typing import Any


class LoggerFactory:
    def __call__(self, name: str | None = None) -> logging.Logger:
        return logging.getLogger(name)


class BoundLogger:  # compatibility alias for callers that expect it
    pass
