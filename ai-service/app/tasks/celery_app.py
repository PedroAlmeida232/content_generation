from celery import Celery

from app.core.config import settings

celery_app = Celery(
    "ai-service",
    broker=settings.redis_url,
    backend=settings.redis_url,
    include=["app.tasks.generate_task"],
)

celery_app.conf.update(
    task_ignore_result=True,
)
