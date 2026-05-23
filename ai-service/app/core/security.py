from dataclasses import dataclass
from uuid import UUID

from fastapi import HTTPException, status
from jose import JWTError, jwt
from jose.exceptions import ExpiredSignatureError

from app.core.config import require_jwt_secret, settings

BEARER_PREFIX = "Bearer "
USER_ID_CLAIM = "userId"


@dataclass(frozen=True)
class TokenPayload:
    user_id: UUID
    email: str


def extract_bearer_token(authorization: str | None) -> str:
    if authorization is None or not authorization.strip():
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Authorization header required",
        )

    if not authorization.startswith(BEARER_PREFIX):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid authorization header",
        )

    token = authorization[len(BEARER_PREFIX):].strip()
    if not token:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid authorization header",
        )

    return token


def decode_token(token: str) -> TokenPayload:
    try:
        claims = jwt.decode(
            token,
            require_jwt_secret(),
            algorithms=[settings.jwt_algorithm],
            options={"verify_aud": False},
        )
    except ExpiredSignatureError as ex:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or expired token",
        ) from ex
    except JWTError as ex:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or expired token",
        ) from ex

    email = claims.get("sub")
    user_id_raw = claims.get(USER_ID_CLAIM)

    if not email or not user_id_raw:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or expired token",
        )

    try:
        user_id = UUID(str(user_id_raw))
    except ValueError as ex:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or expired token",
        ) from ex

    return TokenPayload(user_id=user_id, email=str(email))
