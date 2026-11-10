import hashlib
import re
import secrets
from datetime import timedelta, timezone

import httpx
from fastapi import APIRouter, Depends, File, HTTPException, Request, UploadFile
from sqlalchemy import func, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.api.uploads import IMAGE_TYPES, guess_image_type, media_response, save_upload
from app.config import settings
from app.database import get_db
from app.dependencies import current_user, optional_current_user
from app.models import ArtistProfile, Follow, Like, ListeningEvent, LoginCode, Song, User, now_utc
from app.schemas import (
    ArtistProfileCreate,
    GoogleLoginRequest,
    HandoffRequest,
    LoginRequest,
    ProfileUpdate,
    RegisterRequest,
)
from app.security import create_access_token, hash_password, verify_password
from app.storage import storage

router = APIRouter(tags=["accounts"])

# Google hands back whatever name it has; anything is fine for the login form.
_GOOGLE_INFO_URL = "https://oauth2.googleapis.com/tokeninfo"


def _clean_handle(raw: str) -> str:
    """Reduce a name or email prefix to characters a handle allows."""
    cleaned = re.sub(r"[^a-zA-Z0-9_]", "", raw).lower().strip("_")
    return cleaned


def _unique_handle(db: Session, wanted: str) -> str:
    """Take the wanted handle, or append a number until it is free."""
    base = (wanted or "listener")[:24] or "listener"
    candidate = base[:32]
    suffix = 1
    while db.scalar(select(User.id).where(User.handle == candidate)) is not None:
        suffix += 1
        candidate = f"{base[:32 - len(str(suffix)) - 1]}_{suffix}"
    return candidate


def _derive_handle(db: Session, email: str, display_name: str | None = None) -> str:
    """Build a free handle from the name they gave, else from the email."""
    from_name = _clean_handle(display_name or "") if display_name else ""
    from_email = _clean_handle(email.split("@")[0])
    return _unique_handle(db, from_name or from_email)


def banner_key(user: User) -> str | None:
    """The wide picture for an account.

    Banners started on artist profiles; they now live on the account, so
    listeners can have one too. The artist row stays as a fallback for pictures
    uploaded before the move.
    """
    if user.banner_key:
        return user.banner_key
    profile = user.artist_profile
    return profile.banner_key if profile is not None else None


def user_data(user: User) -> dict:
    version = user.image_version or 0
    return {"id": user.id, "email": user.email, "handle": user.handle, "display_name": user.display_name,
            "bio": user.bio, "role": user.role, "created_at": user.created_at,
            "avatar_url": f"/api/v1/users/{user.id}/avatar" if user.avatar_key else None,
            # Expose the real path so the profile and settings read the picture
            # instead of building a link that misses the API prefix and 404s
            # into its own alt text.
            "banner_url": f"/api/v1/users/{user.id}/banner" if banner_key(user) else None,
            "image_version": version,
            # Settings reads this to draw its switch without a second request.
            "notify_new_drops": bool(user.notify_new_drops)}


def artist_data(artist: User, db: Session | None = None, viewer: User | None = None) -> dict:
    profile = artist.artist_profile
    data = {"user_id": artist.id, "handle": artist.handle,
            "stage_name": profile.stage_name if profile else artist.display_name,
            "bio": artist.bio,
            "avatar_url": f"/api/v1/users/{artist.id}/avatar" if artist.avatar_key else None,
            "banner_url": f"/api/v1/users/{artist.id}/banner" if banner_key(artist) else None,
            "image_version": artist.image_version or 0}
    if db is not None:
        data["published_song_count"] = db.scalar(
            select(func.count(Song.id)).where(Song.artist_id == artist.id, Song.status == "published")
        ) or 0
        data["plays_count"] = db.scalar(
            select(func.count(ListeningEvent.id)).join(Song, Song.id == ListeningEvent.song_id)
            .where(Song.artist_id == artist.id, ListeningEvent.counted.is_(True))
        ) or 0
        data["likes_count"] = db.scalar(
            select(func.count()).select_from(Like).join(Song, Song.id == Like.song_id)
            .where(Song.artist_id == artist.id)
        ) or 0
        data["follower_count"] = db.scalar(
            select(func.count(Follow.listener_id)).where(Follow.artist_id == artist.id)
        ) or 0
        data["is_followed"] = bool(
            viewer is not None and viewer.id != artist.id and db.scalar(
                select(func.count()).select_from(Follow)
                .where(Follow.listener_id == viewer.id, Follow.artist_id == artist.id)
            )
        )
    return data


def _replace_image(kind: str, upload: UploadFile, previous: str | None) -> str:
    if not upload.filename:
        raise HTTPException(status_code=422, detail={"code": "missing_file", "message": f"Choose a {kind} picture"})
    key, _ = save_upload(upload, IMAGE_TYPES, settings.max_cover_bytes)
    if previous:
        storage.delete(previous)
    return key


@router.post("/auth/register", status_code=201)
def register(payload: RegisterRequest, db: Session = Depends(get_db)):
    # Name falls back to the handle, so the form only has to ask for the two
    # things that cannot be guessed: an email and a password.
    handle = payload.handle.lower() if payload.handle else _derive_handle(db, payload.email, payload.display_name)
    display_name = (payload.display_name or handle).strip()
    user = User(email=payload.email.lower(), handle=handle, display_name=display_name,
                password_hash=hash_password(payload.password), role="admin" if settings.is_admin_email(payload.email) else "listener")
    db.add(user)
    try:
        db.commit()
    except IntegrityError:
        db.rollback()
        raise HTTPException(status_code=409, detail={"code": "account_exists", "message": "That email or handle is already in use"})
    db.refresh(user)
    return {"user": user_data(user), "access_token": create_access_token(user.id), "token_type": "bearer"}


def _finish_google(db: Session, email: str, name: str | None) -> dict:
    """Sign in with Google, creating the account the first time."""
    email = email.lower()
    user = db.scalar(select(User).where(User.email == email))
    if user is None:
        handle = _derive_handle(db, email, name)
        # No password for a Google account. A hash of a secret nobody knows
        # keeps the column valid while making a password login impossible.
        user = User(email=email, handle=handle, display_name=(name or handle).strip(),
                    password_hash=hash_password(secrets.token_urlsafe(32)),
                    role="admin" if settings.is_admin_email(email) else "listener")
        db.add(user)
        try:
            db.commit()
        except IntegrityError:
            db.rollback()
            user = db.scalar(select(User).where(User.email == email))
            if user is None:
                raise HTTPException(status_code=409, detail={"code": "account_exists", "message": "That email is already in use"})
    db.refresh(user)
    return {"user": user_data(user), "access_token": create_access_token(user.id), "token_type": "bearer"}


@router.get("/auth/config")
def auth_config():
    """What sign-in methods are live. The client id is public by design."""
    return {"google_enabled": bool(settings.google_client_id), "google_client_id": settings.google_client_id or None}


@router.post("/auth/google")
def google_login(payload: GoogleLoginRequest, db: Session = Depends(get_db)):
    """Verify the token Google gave the browser, then sign the account in."""
    if not settings.google_client_id:
        raise HTTPException(status_code=503, detail={"code": "google_disabled", "message": "Google sign-in is not set up yet"})
    try:
        response = httpx.get(_GOOGLE_INFO_URL, params={"id_token": payload.id_token}, timeout=6.0)
        info = response.json() if response.status_code == 200 else {}
    except (httpx.HTTPError, ValueError):
        info = {}
    # The audience check is what stops a token minted for another app being
    # replayed here, so it is not optional.
    if info.get("aud") != settings.google_client_id or not info.get("email") or info.get("email_verified") is False:
        raise HTTPException(status_code=401, detail={"code": "bad_google_token", "message": "Google could not confirm that sign-in"})
    return _finish_google(db, info["email"], info.get("name"))


@router.post("/auth/login")
def login(payload: LoginRequest, db: Session = Depends(get_db)):
    user = db.scalar(select(User).where(User.email == payload.email.lower()))
    if user is None or not verify_password(payload.password, user.password_hash) or not user.is_active:
        raise HTTPException(status_code=401, detail={"code": "bad_credentials", "message": "Email or password is incorrect"}, headers={"WWW-Authenticate": "Bearer"})
    return {"user": user_data(user), "access_token": create_access_token(user.id), "token_type": "bearer"}


@router.get("/auth/me")
def me(user: User = Depends(current_user)):
    return {"user": user_data(user)}


# How long a browser has to hand its sign-in to the phone before the code is
# useless. Long enough to switch apps, short enough that a leaked URL is stale.
_HANDOFF_TTL = timedelta(minutes=5)


def _handoff_hash(code: str) -> str:
    return hashlib.sha256(code.encode("utf-8")).hexdigest()


@router.post("/auth/handoff")
def start_handoff(user: User = Depends(current_user), db: Session = Depends(get_db)):
    """Mint a one-time code for the app, after a browser has signed in."""
    now = now_utc()
    db.query(LoginCode).filter(LoginCode.expires_at < now).delete()
    code = secrets.token_urlsafe(32)
    db.add(LoginCode(user_id=user.id, code_hash=_handoff_hash(code),
                     expires_at=now + _HANDOFF_TTL))
    db.commit()
    return {"code": code, "expires_in": int(_HANDOFF_TTL.total_seconds())}


@router.post("/auth/handoff/exchange")
def exchange_handoff(payload: HandoffRequest, db: Session = Depends(get_db)):
    """Trade the code the app received for a session, exactly once."""
    row = db.scalar(select(LoginCode).where(
        LoginCode.code_hash == _handoff_hash(payload.code),
        LoginCode.used_at.is_(None)))
    # SQLite hands datetimes back without a zone; Postgres keeps the offset.
    expires = row.expires_at.replace(tzinfo=timezone.utc) if row is not None \
        and row.expires_at.tzinfo is None else (row.expires_at if row else None)
    if row is None or expires is None or expires < now_utc():
        raise HTTPException(status_code=401, detail={
            "code": "bad_handoff",
            "message": "That sign-in link has expired. Sign in again."})
    row.used_at = now_utc()
    user = db.get(User, row.user_id)
    if user is None or not user.is_active:
        db.commit()
        raise HTTPException(status_code=401, detail={
            "code": "bad_handoff",
            "message": "That account is no longer available."})
    db.commit()
    return {"user": user_data(user), "access_token": create_access_token(user.id),
            "token_type": "bearer"}


@router.patch("/listeners/me")
def update_listener_profile(payload: ProfileUpdate, user: User = Depends(current_user), db: Session = Depends(get_db)):
    if payload.display_name is not None:
        user.display_name = payload.display_name.strip()
    if payload.bio is not None:
        user.bio = payload.bio.strip()
    db.commit()
    db.refresh(user)
    return {"user": user_data(user)}


@router.post("/listeners/me/avatar")
def set_avatar(file: UploadFile = File(...), user: User = Depends(current_user), db: Session = Depends(get_db)):
    """A profile picture. Same storage and limits as cover artwork."""
    user.avatar_key = _replace_image("profile", file, user.avatar_key)
    user.image_version = (user.image_version or 0) + 1
    db.commit()
    db.refresh(user)
    return {"user": user_data(user)}


@router.delete("/listeners/me/avatar")
def clear_avatar(user: User = Depends(current_user), db: Session = Depends(get_db)):
    if user.avatar_key:
        storage.delete(user.avatar_key)
        user.avatar_key = None
        user.image_version = (user.image_version or 0) + 1
        db.commit()
    return {"user": user_data(user)}


@router.get("/users/{user_id}/avatar")
def get_avatar(user_id: int, request: Request, db: Session = Depends(get_db)):
    user = db.get(User, user_id)
    if user is None or not user.avatar_key:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "No profile picture"})
    return media_response(user.avatar_key, guess_image_type(user.avatar_key), request, cache_control="public, max-age=86400")


@router.post("/artists/me", status_code=201)
def create_artist_profile(payload: ArtistProfileCreate, user: User = Depends(current_user), db: Session = Depends(get_db)):
    if user.artist_profile is not None:
        raise HTTPException(status_code=409, detail={"code": "artist_profile_exists", "message": "This account already has an artist profile"})
    # A moderator who starts publishing keeps their moderation access.
    if user.role == "listener":
        user.role = "artist"
    profile = ArtistProfile(user_id=user.id, stage_name=payload.stage_name.strip())
    db.add(profile)
    db.commit()
    db.refresh(user)
    return {"artist": artist_data(user, db, user)}


def _set_banner(user: User, upload: UploadFile, db: Session) -> User:
    previous = banner_key(user)
    user.banner_key = _replace_image("banner", upload, previous)
    user.image_version = (user.image_version or 0) + 1
    if user.artist_profile is not None:
        # One picture, read from both the account and the artist page.
        user.artist_profile.banner_key = user.banner_key
    db.commit()
    db.refresh(user)
    return user


@router.post("/listeners/me/banner")
def set_listener_banner(file: UploadFile = File(...), user: User = Depends(current_user), db: Session = Depends(get_db)):
    """A wide banner for any account, not only artists."""
    return {"user": user_data(_set_banner(user, file, db))}


@router.delete("/listeners/me/banner")
def clear_listener_banner(user: User = Depends(current_user), db: Session = Depends(get_db)):
    previous = banner_key(user)
    if previous:
        storage.delete(previous)
    user.banner_key = None
    if user.artist_profile is not None:
        user.artist_profile.banner_key = None
    user.image_version = (user.image_version or 0) + 1
    db.commit()
    db.refresh(user)
    return {"user": user_data(user)}


@router.get("/users/{user_id}/banner")
def get_user_banner(user_id: int, request: Request, db: Session = Depends(get_db)):
    user = db.get(User, user_id)
    key = banner_key(user) if user is not None else None
    if key is None:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "No banner picture"})
    return media_response(key, guess_image_type(key), request, cache_control="public, max-age=3600")


@router.post("/artists/me/banner")
def set_banner(file: UploadFile = File(...), user: User = Depends(current_user), db: Session = Depends(get_db)):
    """The same picture as /listeners/me/banner, kept for existing clients."""
    if user.artist_profile is None:
        raise HTTPException(status_code=403, detail={"code": "artist_profile_required", "message": "Open your artist profile first"})
    return {"artist": artist_data(_set_banner(user, file, db), db, user)}


@router.delete("/artists/me/banner")
def clear_banner(user: User = Depends(current_user), db: Session = Depends(get_db)):
    if user.artist_profile is not None and user.artist_profile.banner_key:
        storage.delete(user.artist_profile.banner_key)
        user.artist_profile.banner_key = None
    if user.banner_key:
        storage.delete(user.banner_key)
        user.banner_key = None
    user.image_version = (user.image_version or 0) + 1
    db.commit()
    return {"artist": artist_data(user, db, user)}


@router.get("/artists/{artist_id}/banner")
def get_banner(artist_id: int, request: Request, db: Session = Depends(get_db)):
    artist = db.get(User, artist_id)
    key = banner_key(artist) if artist is not None else None
    if key is None:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "No banner picture"})
    return media_response(key, guess_image_type(key), request, cache_control="public, max-age=3600")


@router.get("/artists")
def list_artists(q: str | None = None, limit: int = 20, offset: int = 0,
                viewer: User | None = Depends(optional_current_user),
                db: Session = Depends(get_db)):
    limit = min(max(limit, 1), 50)
    query = select(User).join(ArtistProfile).where(User.is_active.is_(True)).order_by(ArtistProfile.stage_name).offset(max(offset, 0)).limit(limit)
    if q:
        query = query.where(ArtistProfile.stage_name.ilike(f"%{q[:80]}%"))
    artists = db.scalars(query).all()
    return {"items": [artist_data(artist, db, viewer) for artist in artists], "limit": limit, "offset": max(offset, 0)}


@router.get("/artists/{artist_id}")
def get_artist(artist_id: int, viewer: User | None = Depends(optional_current_user),
               db: Session = Depends(get_db)):
    artist = db.get(User, artist_id)
    if artist is None or artist.artist_profile is None or not artist.is_active:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Artist not found"})
    return artist_data(artist, db, viewer)
