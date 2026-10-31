from fastapi import APIRouter, Depends, File, HTTPException, Request, UploadFile
from sqlalchemy import func, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.api.uploads import IMAGE_TYPES, guess_image_type, media_response, save_upload
from app.config import settings
from app.database import get_db
from app.dependencies import current_user
from app.models import ArtistProfile, Follow, Song, User
from app.schemas import ArtistProfileCreate, LoginRequest, ProfileUpdate, RegisterRequest
from app.security import create_access_token, hash_password, verify_password
from app.storage import storage

router = APIRouter(tags=["accounts"])


def user_data(user: User) -> dict:
    return {"id": user.id, "email": user.email, "handle": user.handle, "display_name": user.display_name,
            "bio": user.bio, "role": user.role, "created_at": user.created_at,
            "avatar_url": f"/api/v1/users/{user.id}/avatar" if user.avatar_key else None}


def artist_data(artist: User) -> dict:
    profile = artist.artist_profile
    return {"user_id": artist.id, "handle": artist.handle,
            "stage_name": profile.stage_name if profile else artist.display_name,
            "bio": artist.bio,
            "avatar_url": f"/api/v1/users/{artist.id}/avatar" if artist.avatar_key else None,
            "banner_url": f"/api/v1/artists/{artist.id}/banner" if profile and profile.banner_key else None}


def _replace_image(kind: str, upload: UploadFile, previous: str | None) -> str:
    if not upload.filename:
        raise HTTPException(status_code=422, detail={"code": "missing_file", "message": f"Choose a {kind} picture"})
    key, _ = save_upload(upload, IMAGE_TYPES, settings.max_cover_bytes)
    if previous:
        storage.delete(previous)
    return key


@router.post("/auth/register", status_code=201)
def register(payload: RegisterRequest, db: Session = Depends(get_db)):
    user = User(email=payload.email.lower(), handle=payload.handle.lower(), display_name=payload.display_name.strip(),
                password_hash=hash_password(payload.password), role="admin" if settings.is_admin_email(payload.email) else "listener")
    db.add(user)
    try:
        db.commit()
    except IntegrityError:
        db.rollback()
        raise HTTPException(status_code=409, detail={"code": "account_exists", "message": "That email or handle is already in use"})
    db.refresh(user)
    return {"user": user_data(user), "access_token": create_access_token(user.id), "token_type": "bearer"}


@router.post("/auth/login")
def login(payload: LoginRequest, db: Session = Depends(get_db)):
    user = db.scalar(select(User).where(User.email == payload.email.lower()))
    if user is None or not verify_password(payload.password, user.password_hash) or not user.is_active:
        raise HTTPException(status_code=401, detail={"code": "bad_credentials", "message": "Email or password is incorrect"}, headers={"WWW-Authenticate": "Bearer"})
    return {"user": user_data(user), "access_token": create_access_token(user.id), "token_type": "bearer"}


@router.get("/auth/me")
def me(user: User = Depends(current_user)):
    return {"user": user_data(user)}


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
    db.commit()
    db.refresh(user)
    return {"user": user_data(user)}


@router.delete("/listeners/me/avatar")
def clear_avatar(user: User = Depends(current_user), db: Session = Depends(get_db)):
    if user.avatar_key:
        storage.delete(user.avatar_key)
        user.avatar_key = None
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
    return {"artist": artist_data(user)}


@router.post("/artists/me/banner")
def set_banner(file: UploadFile = File(...), user: User = Depends(current_user), db: Session = Depends(get_db)):
    if user.artist_profile is None:
        raise HTTPException(status_code=403, detail={"code": "artist_profile_required", "message": "Open your artist profile first"})
    user.artist_profile.banner_key = _replace_image("banner", file, user.artist_profile.banner_key)
    db.commit()
    db.refresh(user)
    return {"artist": artist_data(user)}


@router.delete("/artists/me/banner")
def clear_banner(user: User = Depends(current_user), db: Session = Depends(get_db)):
    if user.artist_profile and user.artist_profile.banner_key:
        storage.delete(user.artist_profile.banner_key)
        user.artist_profile.banner_key = None
        db.commit()
    return {"artist": artist_data(user)}


@router.get("/artists/{artist_id}/banner")
def get_banner(artist_id: int, request: Request, db: Session = Depends(get_db)):
    artist = db.get(User, artist_id)
    if artist is None or artist.artist_profile is None or not artist.artist_profile.banner_key:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "No banner picture"})
    key = artist.artist_profile.banner_key
    return media_response(key, guess_image_type(key), request, cache_control="public, max-age=86400")


@router.get("/artists")
def list_artists(q: str | None = None, limit: int = 20, offset: int = 0, db: Session = Depends(get_db)):
    limit = min(max(limit, 1), 50)
    query = select(User).join(ArtistProfile).where(User.is_active.is_(True)).order_by(ArtistProfile.stage_name).offset(max(offset, 0)).limit(limit)
    if q:
        query = query.where(ArtistProfile.stage_name.ilike(f"%{q[:80]}%"))
    artists = db.scalars(query).all()
    return {"items": [artist_data(artist) | {"bio": artist.bio} for artist in artists], "limit": limit, "offset": max(offset, 0)}


@router.get("/artists/{artist_id}")
def get_artist(artist_id: int, db: Session = Depends(get_db)):
    artist = db.get(User, artist_id)
    if artist is None or artist.artist_profile is None or not artist.is_active:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Artist not found"})
    songs = db.scalar(select(func.count(Song.id)).where(Song.artist_id == artist.id, Song.status == "published")) or 0
    followers = db.scalar(select(func.count(Follow.listener_id)).where(Follow.artist_id == artist.id)) or 0
    return artist_data(artist) | {"bio": artist.bio, "published_song_count": songs, "follower_count": followers}
