from fastapi import APIRouter

from app.api.dependencies import CurrentUser

router = APIRouter()


@router.get("/me")
def read_current_user(current_user: CurrentUser) -> dict[str, str]:
    return {
        "userId": str(current_user.user_id),
        "email": current_user.email,
    }
