from fastapi import APIRouter, Depends

from app.core.security import TokenPayload, get_current_user

router = APIRouter()


@router.get("/me")
def read_current_user(
    current_user: TokenPayload = Depends(get_current_user),
) -> dict[str, str]:
    return {
        "userId": str(current_user.user_id),
        "email": current_user.email,
    }
