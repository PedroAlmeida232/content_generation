from fastapi import FastAPI

from app.api.routes.generate import router as generate_router
from app.api.routes.health import router as health_router
from app.api.routes.me import router as me_router
from app.api.routes.styles import router as styles_router
from app.core.config import settings

app = FastAPI(
    title=settings.app_name,
    swagger_ui_parameters={"persistAuthorization": True},
)
app.include_router(health_router)
app.include_router(me_router)
app.include_router(styles_router)
app.include_router(generate_router)
