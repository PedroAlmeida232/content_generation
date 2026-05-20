from fastapi import APIRouter

router = APIRouter()

VISUAL_STYLES = [
    "minimalista",
    "moderno",
    "corporativo",
    "vibrante",
    "editorial",
]


@router.get("/styles")
def list_styles() -> list[str]:
    return VISUAL_STYLES
