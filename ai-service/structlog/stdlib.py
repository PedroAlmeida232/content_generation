from __future__ import annotations

import logging


class LoggerFactory:
    def __call__(self, name: str | None = None) -> logging.Logger:
        return logging.getLogger(name)


class BoundLogger:  # compatibility alias for callers that expect it
    pass
