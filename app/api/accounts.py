from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import func, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.config import settings
from app.database import get_db
from app.dependencies import current_user
from app.models import ArtistProfile, Follow, Song, User
from app.schemas import ArtistProfileCreate, LoginRequest, ProfileUpdate, RegisterRequest
from app.security import create_access_token, hash_password, verify_password

router = APIRouter(tags=["accounts"])


def user_data(user: User) -> dict:
    return {"id": user.id, "email": user.email, "handle": user.handle, "display_name": user.display_name,
            "bio": user.bio, "role": user.role, "created_at": user.created_at}


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
    return {"artist": {"user_id": user.id, "stage_name": profile.stage_name, "handle": user.handle, "bio": user.bio}}


@router.get("/artists")
def list_artists(q: str | None = None, limit: int = 20, offset: int = 0, db: Session = Depends(get_db)):
    limit = min(max(limit, 1), 50)
    query = select(User).join(ArtistProfile).where(User.is_active.is_(True)).order_by(ArtistProfile.stage_name).offset(max(offset, 0)).limit(limit)
    if q:
        query = query.where(ArtistProfile.stage_name.ilike(f"%{q[:80]}%"))
    artists = db.scalars(query).all()
    return {"items": [{"user_id": artist.id, "handle": artist.handle, "stage_name": artist.artist_profile.stage_name, "bio": artist.bio} for artist in artists], "limit": limit, "offset": max(offset, 0)}


@router.get("/artists/{artist_id}")
def get_artist(artist_id: int, db: Session = Depends(get_db)):
    artist = db.get(User, artist_id)
    if artist is None or artist.artist_profile is None or not artist.is_active:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Artist not found"})
    songs = db.scalar(select(func.count(Song.id)).where(Song.artist_id == artist.id, Song.status == "published")) or 0
    followers = db.scalar(select(func.count(Follow.listener_id)).where(Follow.artist_id == artist.id)) or 0
    return {"user_id": artist.id, "handle": artist.handle, "stage_name": artist.artist_profile.stage_name, "bio": artist.bio, "published_song_count": songs, "follower_count": followers}
