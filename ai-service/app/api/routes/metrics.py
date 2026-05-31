from fastapi import APIRouter

from app.api.dependencies import CurrentUser
from app.core.metrics import get_usage_metrics
from app.schemas.metrics import UsageMetricsResponse

router = APIRouter(prefix="/metrics", tags=["metrics"])


@router.get(
    "/usage",
    response_model=UsageMetricsResponse,
    summary="Métricas básicas de uso",
)
def get_usage_metrics_route(
    _current_user: CurrentUser,
) -> UsageMetricsResponse:
    return UsageMetricsResponse.model_validate(get_usage_metrics())
