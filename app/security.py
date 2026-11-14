from datetime import datetime, timedelta, timezone

import jwt
from pwdlib import PasswordHash

from app.config import settings

password_hash = PasswordHash.recommended()
ALGORITHM = "HS256"


def hash_password(password: str) -> str:
    return password_hash.hash(password)


def verify_password(password: str, stored_hash: str) -> bool:
    return password_hash.verify(password, stored_hash)


def create_access_token(user_id: int) -> str:
    """A session token for this user.

    Sessions last until they are replaced: the token carries no expiry unless
    ``JWT_EXPIRE_MINUTES`` asks for one, so signing in once keeps the app and
    the site signed in instead of logging people out mid-listen.
    """
    payload = {"sub": str(user_id)}
    if settings.jwt_expire_minutes > 0:
        payload["exp"] = (datetime.now(timezone.utc)
                          + timedelta(minutes=settings.jwt_expire_minutes))
    return jwt.encode(payload, settings.jwt_secret, algorithm=ALGORITHM)


def read_access_token(token: str) -> int:
    payload = jwt.decode(token, settings.jwt_secret, algorithms=[ALGORITHM])
    return int(payload["sub"])
