from fastapi import FastAPI

from app.api.routes.generate import jobs_router, router as generate_router
from app.api.routes.health import router as health_router
from app.api.routes.metrics import router as metrics_router
from app.api.routes.me import router as me_router
from app.api.routes.prompts import router as prompts_router
from app.api.routes.styles import router as styles_router
from app.core.logging import configure_logging
from app.core.config import settings

configure_logging()

app = FastAPI(
    title=settings.app_name,
    swagger_ui_parameters={"persistAuthorization": True},
)
app.include_router(health_router)
app.include_router(me_router)
app.include_router(styles_router)
app.include_router(prompts_router)
app.include_router(generate_router)
app.include_router(jobs_router)
app.include_router(metrics_router)
