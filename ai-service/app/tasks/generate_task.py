from app.tasks.celery_app import celery_app


@celery_app.task(name="ai.generate_placeholder")
def generate_placeholder() -> dict[str, str]:
    return {"status": "queued"}
