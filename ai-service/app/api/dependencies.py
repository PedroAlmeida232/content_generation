from typing import Annotated

from fastapi import Depends, Header, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.core.security import TokenPayload, decode_token

bearer_scheme = HTTPBearer(auto_error=False)


async def get_current_user(
    credentials: HTTPAuthorizationCredentials | None = Depends(
        bearer_scheme
    ),
) -> TokenPayload:
    if credentials is None or credentials.scheme.lower() != "bearer":
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Authorization header required",
            headers={"WWW-Authenticate": "Bearer"},
        )
    return decode_token(credentials.credentials)


async def get_openai_api_key(
    x_openai_key: Annotated[
        str | None,
        Header(alias="X-OpenAI-Key", convert_underscores=False),
    ] = None,
) -> str:
    """Extrai e valida a chave da OpenAI do header X-OpenAI-Key."""
    if x_openai_key is None or not x_openai_key.strip():
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Header 'X-OpenAI-Key' is required",
        )
    return x_openai_key.strip()


CurrentUser = Annotated[TokenPayload, Depends(get_current_user)]
OpenAIKey = Annotated[str, Depends(get_openai_api_key)]


async def get_raw_token(
    credentials: HTTPAuthorizationCredentials | None = Depends(
        bearer_scheme
    ),
) -> str:
    """Extrai o token JWT cru do header Authorization."""
    if credentials is None or credentials.scheme.lower() != "bearer":
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Authorization header required",
            headers={"WWW-Authenticate": "Bearer"},
        )
    return credentials.credentials


RawToken = Annotated[str, Depends(get_raw_token)]
