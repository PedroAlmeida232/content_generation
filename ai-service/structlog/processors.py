from __future__ import annotations

import json
from datetime import datetime, timezone
from typing import Any


def add_log_level(
    _logger: Any,
    method_name: str,
    event_dict: dict[str, Any],
) -> dict[str, Any]:
    event_dict.setdefault("level", method_name)
    return event_dict


class TimeStamper:
    def __init__(self, fmt: str = "iso", key: str = "timestamp") -> None:
        self.fmt = fmt
        self.key = key

    def __call__(
        self,
        _logger: Any,
        _method_name: str,
        event_dict: dict[str, Any],
    ) -> dict[str, Any]:
        if self.fmt == "iso":
            value = datetime.now(timezone.utc).isoformat()
        else:
            value = datetime.now(timezone.utc).timestamp()
        event_dict[self.key] = value
        return event_dict


class JSONRenderer:
    def __init__(self, sort_keys: bool = True) -> None:
        self.sort_keys = sort_keys

    def __call__(
        self,
        _logger: Any,
        _method_name: str,
        event_dict: dict[str, Any],
    ) -> str:
        return json.dumps(
            event_dict,
            ensure_ascii=False,
            default=str,
            sort_keys=self.sort_keys,
        )
