import logging
from typing import Any

from app.core.redis import is_job_cancelled, save_redis_status
from app.services.image_prompt_chain import run_image_prompt_chain
from app.services.openai_client import generate_slide_image
from app.services.slide_text_chain import run_slide_text_chain
from app.tasks.celery_app import celery_app

logger = logging.getLogger(__name__)


class JobCancelledError(RuntimeError):
    """Raised when a job is cancelled while it is running."""


def _raise_if_job_cancelled(job_id: str) -> None:
    if is_job_cancelled(job_id):
        raise JobCancelledError(f"Job {job_id} was cancelled")


@celery_app.task(
    bind=True,
    name="ai.generate_carousel",
    ignore_result=False,
)
def generate_carousel(
    self,
    *,
    openai_api_key: str,
    prompt: str,
    style: str,
    slide_count: int,
    aspect_ratio: str = "1:1",
    tone: str | None = None,
    color_palette: list[str] | None = None,
    context_name: str | None = None,
) -> dict[str, Any]:
    job_id = self.request.id
    logger.info(
        f"Starting carousel generation job={job_id} "
        f"aspect_ratio={aspect_ratio} "
        f"for prompt={prompt[:30]}..."
    )

    try:
        _raise_if_job_cancelled(job_id)
        save_redis_status(job_id, "processing", progress=0)
        self.update_state(state="processing", meta={"progress": 0})

        # 1. Gerar os textos dos slides
        _raise_if_job_cancelled(job_id)
        slides_text = run_slide_text_chain(
            openai_api_key=openai_api_key,
            prompt=prompt,
            style=style,
            slide_count=slide_count,
            tone=tone,
            color_palette=color_palette,
            context_name=context_name,
        )
        save_redis_status(job_id, "processing", progress=30)
        self.update_state(state="processing", meta={"progress": 30})

        # 2. Gerar prompts visuais correspondentes
        _raise_if_job_cancelled(job_id)
        image_prompts = run_image_prompt_chain(
            openai_api_key=openai_api_key,
            slides_text=slides_text,
            style=style,
            color_palette=color_palette,
            context_name=context_name,
        )
        save_redis_status(job_id, "processing", progress=50)
        self.update_state(state="processing", meta={"progress": 50})

        # 3. Gerar imagens via DALL-E 3 para cada slide
        slides_results = []
        text_by_order = {s.slide_order: s for s in slides_text.slides}
        prompt_by_order = {
            p.slide_order: p for p in image_prompts.slides
        }

        for i in range(1, slide_count + 1):
            _raise_if_job_cancelled(job_id)
            slide_text = text_by_order[i]
            image_prompt_obj = prompt_by_order[i]

            image_url = generate_slide_image(
                openai_api_key=openai_api_key,
                image_prompt=image_prompt_obj.image_prompt,
            )

            slides_results.append(
                {
                    "slide_order": i,
                    "image_url": image_url,
                    "caption": slide_text.caption,
                    "prompt_used": image_prompt_obj.image_prompt,
                }
            )

            progress = 50 + int((i / slide_count) * 45)
            save_redis_status(job_id, "processing", progress=progress)
            self.update_state(
                state="processing", meta={"progress": progress}
            )

        _raise_if_job_cancelled(job_id)
        save_redis_status(job_id, "done", slides=slides_results)
        logger.info(
            f"Carousel generation job={job_id} completed successfully"
        )
        return {"slides": slides_results}

    except JobCancelledError:
        logger.info(f"Carousel generation job={job_id} was cancelled")
        raise
    except Exception as exc:
        if is_job_cancelled(job_id):
            logger.info(f"Carousel generation job={job_id} was cancelled")
            raise JobCancelledError(
                f"Job {job_id} was cancelled"
            ) from exc
        error_msg = str(exc)
        logger.error(
            f"Carousel generation job={job_id} failed: {error_msg}"
        )
        save_redis_status(job_id, "failed", error=error_msg)
        raise
