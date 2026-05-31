"""Compat layer local para a API usada de structlog."""

from __future__ import annotations

import json
import logging
from dataclasses import dataclass, field
from typing import Any

from . import processors


@dataclass
class _Config:
    processors: list[Any] = field(
        default_factory=lambda: [
            processors.add_log_level,
            processors.TimeStamper(fmt="iso"),
            processors.JSONRenderer(),
        ]
    )


_CONFIG = _Config()


def configure(
    *,
    processors: list[Any] | None = None,
    **_kwargs: Any,
) -> None:
    if processors is not None:
        _CONFIG.processors = list(processors)


class BoundLogger:
    def __init__(
        self,
        name: str | None = None,
        context: dict[str, Any] | None = None,
    ) -> None:
        self._logger = logging.getLogger(name)
        self._context = dict(context or {})

    def bind(self, **kwargs: Any) -> "BoundLogger":
        merged = {**self._context, **kwargs}
        return BoundLogger(self._logger.name, merged)

    def _emit(
        self,
        level: int,
        event: str | None = None,
        **kwargs: Any,
    ) -> None:
        log_kwargs: dict[str, Any] = {}
        for key in ("exc_info", "stack_info", "stacklevel", "extra"):
            if key in kwargs:
                log_kwargs[key] = kwargs.pop(key)

        event_dict: dict[str, Any] = {**self._context, **kwargs}
        if event is not None:
            event_dict.setdefault("event", event)

        method_name = logging.getLevelName(level).lower()
        rendered: Any = event_dict
        for processor in _CONFIG.processors:
            rendered = processor(self._logger, method_name, rendered)

        if not isinstance(rendered, str):
            rendered = json.dumps(
                rendered,
                ensure_ascii=False,
                default=str,
                sort_keys=True,
            )

        self._logger.log(level, rendered, **log_kwargs)

    def debug(self, event: str | None = None, **kwargs: Any) -> None:
        self._emit(logging.DEBUG, event, **kwargs)

    def info(self, event: str | None = None, **kwargs: Any) -> None:
        self._emit(logging.INFO, event, **kwargs)

    def warning(self, event: str | None = None, **kwargs: Any) -> None:
        self._emit(logging.WARNING, event, **kwargs)

    def warn(self, event: str | None = None, **kwargs: Any) -> None:
        self.warning(event, **kwargs)

    def error(self, event: str | None = None, **kwargs: Any) -> None:
        self._emit(logging.ERROR, event, **kwargs)

    def exception(self, event: str | None = None, **kwargs: Any) -> None:
        kwargs.setdefault("exc_info", True)
        self._emit(logging.ERROR, event, **kwargs)


def get_logger(
    name: str | None = None,
    **initial_values: Any,
) -> BoundLogger:
    return BoundLogger(name=name, context=initial_values)
