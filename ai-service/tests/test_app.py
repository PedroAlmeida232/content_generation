from app.api.routes.health import healthcheck
from app.api.routes.styles import VISUAL_STYLES, list_styles
from app.main import app


def test_app_title() -> None:
    assert app.title == "ai-service"


def test_healthcheck_returns_ok() -> None:
    assert healthcheck() == {"status": "ok"}


def test_list_styles_returns_hardcoded_values() -> None:
    assert list_styles() == VISUAL_STYLES
    assert "minimalista" in VISUAL_STYLES
