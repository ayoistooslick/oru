import jwt
from fastapi import Depends, HTTPException
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy.orm import Session

from app.database import get_db
from app.models import User
from app.security import read_access_token

bearer = HTTPBearer(auto_error=False)


def current_user(credentials: HTTPAuthorizationCredentials | None = Depends(bearer), db: Session = Depends(get_db)) -> User:
    if credentials is None:
        raise HTTPException(status_code=401, detail={"code": "authentication_required", "message": "Sign in to do that"}, headers={"WWW-Authenticate": "Bearer"})
    try:
        user_id = read_access_token(credentials.credentials)
    except (jwt.InvalidTokenError, ValueError, KeyError):
        raise HTTPException(status_code=401, detail={"code": "invalid_token", "message": "Your sign-in token is invalid or expired"}, headers={"WWW-Authenticate": "Bearer"})
    user = db.get(User, user_id)
    if user is None or not user.is_active:
        raise HTTPException(status_code=401, detail={"code": "account_unavailable", "message": "This account is unavailable"})
    return user


def artist_user(user: User = Depends(current_user)) -> User:
    if user.role != "artist" or user.artist_profile is None:
        raise HTTPException(status_code=403, detail={"code": "artist_only", "message": "Create an artist profile first"})
    return user


def admin_user(user: User = Depends(current_user)) -> User:
    if user.role != "admin":
        raise HTTPException(status_code=403, detail={"code": "admin_only", "message": "This action is only available to moderators"})
    return user


def optional_current_user(credentials: HTTPAuthorizationCredentials | None = Depends(bearer), db: Session = Depends(get_db)) -> User | None:
    if credentials is None:
        return None
    try:
        user_id = read_access_token(credentials.credentials)
    except (jwt.InvalidTokenError, ValueError, KeyError):
        return None
    user = db.get(User, user_id)
    return user if user is not None and user.is_active else None
