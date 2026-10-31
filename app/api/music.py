from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, File, Form, HTTPException, Request, UploadFile
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.api.uploads import AUDIO_TYPES, IMAGE_TYPES, guess_image_type, media_response, save_upload
from app.config import settings
from app.database import get_db
from app.dependencies import artist_user, current_user, optional_current_user
from app.models import ArtistProfile, Follow, Genre, Like, ListeningEvent, PlaylistSong, Release, ReleaseSong, Song, SongFeature, SongGenre, User
from app.schemas import ListeningCreate, ReleaseCreate, ShareDecision
from app.services.discovery import discover_songs
from app.storage import storage

router = APIRouter(tags=["music"])
MAX_FEATURES = 5


def is_insider(song: Song, viewer: User | None) -> bool:
    """The credited artist, the listener who shared the song, and moderators."""
    if viewer is None:
        return False
    if viewer.role == "admin" or viewer.id == song.artist_id:
        return True
    return song.shared_by_user_id is not None and viewer.id == song.shared_by_user_id


def can_access(song: Song, viewer: User | None) -> bool:
    """Published tracks are public; drafts and shares stay with the people involved."""
    if song.status == "published":
        return True
    return is_insider(song, viewer)


def featuring(song: Song, db: Session) -> list[dict]:
    rows = db.execute(
        select(User.id, User.handle, ArtistProfile.stage_name)
        .select_from(SongFeature)
        .join(User, User.id == SongFeature.artist_id)
        .outerjoin(ArtistProfile, ArtistProfile.user_id == User.id)
        .where(SongFeature.song_id == song.id)
        .order_by(SongFeature.artist_id)
    ).all()
    return [{"artist_id": row[0], "handle": row[1], "name": row[2] or row[1]} for row in rows]


def song_data(song: Song, db: Session, viewer: User | None = None) -> dict:
    genres = db.scalars(select(Genre.name).join(SongGenre).where(SongGenre.song_id == song.id).order_by(Genre.name)).all()
    readable = can_access(song, viewer)
    return {"id": song.id, "artist_id": song.artist_id, "artist_handle": song.artist.handle,
            "artist_name": song.artist.artist_profile.stage_name if song.artist.artist_profile else song.artist.display_name,
            "artist_avatar_url": f"/api/v1/users/{song.artist_id}/avatar" if song.artist.avatar_key else None,
            "title": song.title, "description": song.description, "genres": genres, "status": song.status,
            "created_at": song.created_at, "published_at": song.published_at,
            "audio_url": f"/api/v1/songs/{song.id}/audio" if readable else None,
            "cover_url": f"/api/v1/songs/{song.id}/cover" if song.cover_key and readable else None,
            "source_url": song.source_url,
            "featuring": featuring(song, db),
            "shared_by": ({"handle": song.shared_by.handle, "display_name": song.shared_by.display_name}
                          if song.shared_by is not None else None)}


def visible_song(song_id: int, db: Session, viewer: User | None = None) -> Song:
    song = db.get(Song, song_id)
    if song is None or song.status == "hidden":
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Song not found"})
    if song.status != "published" and not is_insider(song, viewer):
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Song not found"})
    return song


def _save_upload(file: UploadFile, allowed: dict[str, set[str]], max_bytes: int) -> str:
    key, _ = save_upload(file, allowed, max_bytes)
    return key


def _attach_features(db: Session, song: Song, artist_ids: list[int], owner: User) -> None:
    """Credit other artists on a song. They keep their own profile; this only links them."""
    wanted = [value for value in dict.fromkeys(artist_ids) if value != owner.id]
    if len(wanted) > MAX_FEATURES:
        raise HTTPException(status_code=422, detail={"code": "too_many_features", "message": f"A song can credit up to {MAX_FEATURES} other artists"})
    if not wanted:
        return
    guests = db.scalars(select(User).where(User.id.in_(wanted), User.is_active.is_(True))).all()
    if len(guests) != len(wanted):
        raise HTTPException(status_code=404, detail={"code": "artist_not_found", "message": "One of the featured artists was not found"})
    if any(guest.artist_profile is None for guest in guests):
        raise HTTPException(status_code=422, detail={"code": "artist_profile_required", "message": "Featured artists need an artist profile first"})
    db.add_all([SongFeature(song=song, artist_id=guest.id) for guest in guests])


def _attach_genres(db: Session, song: Song, genres: list[str]) -> None:
    for value in dict.fromkeys(name.strip().lower() for name in genres if name.strip()):
        if len(value) > 48:
            raise HTTPException(status_code=422, detail={"code": "invalid_genre", "message": "Genre names can be up to 48 characters"})
        genre = db.scalar(select(Genre).where(Genre.name == value))
        if genre is None:
            genre = Genre(name=value)
            db.add(genre)
            db.flush()
        db.add(SongGenre(song=song, genre=genre))


@router.get("/discover")
def discover(genre: str | None = None, limit: int = 20, offset: int = 0, user: User | None = Depends(optional_current_user), db: Session = Depends(get_db)):
    # Anonymous browsing is public. Personalized following boosts are currently optional.
    safe_limit = min(max(limit, 1), 50)
    songs = discover_songs(db, user.id if user else None, genre, safe_limit, max(offset, 0))
    return {"items": [song_data(song, db, user) for song in songs], "limit": safe_limit, "offset": max(offset, 0), "ranking": "explainable-v1"}


@router.get("/genres")
def list_genres(db: Session = Depends(get_db)):
    rows = db.execute(
        select(Genre.name, func.count(SongGenre.song_id))
        .join(SongGenre, SongGenre.genre_id == Genre.id)
        .join(Song, Song.id == SongGenre.song_id)
        .where(Song.status == "published")
        .group_by(Genre.name)
        .order_by(func.count(SongGenre.song_id).desc(), Genre.name)
    ).all()
    return {"items": [{"name": name, "song_count": count} for name, count in rows]}


@router.get("/songs")
def list_songs(q: str | None = None, genre: str | None = None, limit: int = 20, offset: int = 0,
               user: User | None = Depends(optional_current_user), db: Session = Depends(get_db)):
    limit = min(max(limit, 1), 50)
    query = select(Song).where(Song.status == "published").order_by(Song.published_at.desc(), Song.id.desc())
    if q:
        safe_q = q.strip()[:100]
        query = query.join(User, Song.artist_id == User.id).outerjoin(ArtistProfile, ArtistProfile.user_id == User.id).where(
            Song.title.ilike(f"%{safe_q}%") | User.display_name.ilike(f"%{safe_q}%") | ArtistProfile.stage_name.ilike(f"%{safe_q}%")
        )
    if genre:
        query = query.join(SongGenre, SongGenre.song_id == Song.id).join(Genre, Genre.id == SongGenre.genre_id).where(func.lower(Genre.name) == genre.strip().lower())
    songs = db.scalars(query.offset(max(offset, 0)).limit(limit)).unique().all()
    return {"items": [song_data(song, db, user) for song in songs], "limit": limit, "offset": max(offset, 0)}


@router.post("/artists/me/songs", status_code=201)
def upload_song(title: str = Form(min_length=1, max_length=120), description: str = Form(default="", max_length=2000),
                genres: list[str] = Form(default=[]), featured: list[int] = Form(default=[]),
                audio: UploadFile = File(...), cover: UploadFile | None = File(default=None),
                artist: User = Depends(artist_user), db: Session = Depends(get_db)):
    audio_key = _save_upload(audio, AUDIO_TYPES, settings.max_audio_bytes)
    cover_key = None
    try:
        if cover is not None and cover.filename:
            cover_key = _save_upload(cover, IMAGE_TYPES, settings.max_cover_bytes)
        song = Song(artist_id=artist.id, title=title.strip(), description=description.strip(), audio_key=audio_key,
                    cover_key=cover_key, audio_type=audio.content_type or "application/octet-stream", status="draft")
        db.add(song)
        _attach_genres(db, song, genres)
        _attach_features(db, song, featured, artist)
        db.commit()
        db.refresh(song)
        return {"song": song_data(song, db, artist)}
    except Exception:
        db.rollback()
        storage.delete(audio_key)
        if cover_key:
            storage.delete(cover_key)
        raise


@router.post("/songs/share", status_code=201)
def share_song(title: str = Form(min_length=1, max_length=120), description: str = Form(default="", max_length=2000),
               genres: list[str] = Form(default=[]), artist_id: int = Form(...),
               source_url: str = Form(min_length=4, max_length=500),
               audio: UploadFile = File(...), cover: UploadFile | None = File(default=None),
               user: User = Depends(current_user), db: Session = Depends(get_db)):
    """A listener shares a song by an artist who is on ORU. The artist keeps the
    credit, the uploader is recorded as the sharer, and the song waits for approval."""
    target = db.get(User, artist_id)
    if target is None or target.artist_profile is None or not target.is_active:
        raise HTTPException(status_code=404, detail={"code": "artist_not_found", "message": "That artist is not on ORU yet"})
    url = source_url.strip()
    if not url.startswith(("http://", "https://")):
        raise HTTPException(status_code=422, detail={"code": "invalid_source", "message": "Add a link that starts with http:// or https://"})
    audio_key = _save_upload(audio, AUDIO_TYPES, settings.max_audio_bytes)
    cover_key = None
    try:
        if cover is not None and cover.filename:
            cover_key = _save_upload(cover, IMAGE_TYPES, settings.max_cover_bytes)
        song = Song(artist_id=target.id, title=title.strip(), description=description.strip(), audio_key=audio_key,
                    cover_key=cover_key, audio_type=audio.content_type or "application/octet-stream", status="pending",
                    shared_by_user_id=user.id, source_url=url)
        db.add(song)
        _attach_genres(db, song, genres)
        db.commit()
        db.refresh(song)
        return {"song": song_data(song, db, user)}
    except Exception:
        db.rollback()
        storage.delete(audio_key)
        if cover_key:
            storage.delete(cover_key)
        raise


@router.get("/shared/songs")
def own_shared_songs(limit: int = 50, offset: int = 0, user: User = Depends(current_user), db: Session = Depends(get_db)):
    """Songs the signed-in listener shared, with their review status."""
    limit = min(max(limit, 1), 50)
    songs = db.scalars(
        select(Song).where(Song.shared_by_user_id == user.id)
        .order_by(Song.created_at.desc(), Song.id.desc()).offset(max(offset, 0)).limit(limit)
    ).all()
    return {"items": [song_data(song, db, user) for song in songs], "limit": limit, "offset": max(offset, 0)}


@router.delete("/shared/songs/{song_id}")
def withdraw_share(song_id: int, user: User = Depends(current_user), db: Session = Depends(get_db)):
    """A sharer can take a request back while it is still waiting for approval."""
    song = db.get(Song, song_id)
    if song is None or song.shared_by_user_id != user.id or song.status != "pending":
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Share request not found"})
    db.delete(song)
    db.commit()
    storage.delete(song.audio_key)
    if song.cover_key:
        storage.delete(song.cover_key)
    return {"song_id": song_id, "deleted": True}


@router.get("/songs/pending-shares")
def pending_shares(user: User = Depends(current_user), db: Session = Depends(get_db)):
    """Requests waiting on me: songs shared with my artist profile, or all of them for moderators."""
    query = select(Song).where(Song.status == "pending", Song.shared_by_user_id.is_not(None)).order_by(Song.created_at, Song.id)
    if user.role != "admin":
        query = query.where(Song.artist_id == user.id)
    songs = db.scalars(query.limit(50)).all()
    return {"items": [song_data(song, db, user) for song in songs]}


@router.post("/songs/{song_id}/share-decision")
def decide_share(song_id: int, payload: ShareDecision, user: User = Depends(current_user), db: Session = Depends(get_db)):
    """Only the credited artist or a moderator can approve or reject a share."""
    song = db.get(Song, song_id)
    if song is None or song.status != "pending" or song.shared_by_user_id is None:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Share request not found"})
    if user.role != "admin" and song.artist_id != user.id:
        raise HTTPException(status_code=403, detail={"code": "not_allowed", "message": "Only the artist or a moderator can decide on this"})
    if payload.action == "approve":
        song.status = "published"
        song.published_at = datetime.now(timezone.utc)
        db.commit()
        db.refresh(song)
        return {"song": song_data(song, db, user)}
    db.delete(song)
    db.commit()
    storage.delete(song.audio_key)
    if song.cover_key:
        storage.delete(song.cover_key)
    return {"song_id": song_id, "action": "reject", "deleted": True}


@router.get("/songs/{song_id}")
def get_song(song_id: int, user: User | None = Depends(optional_current_user), db: Session = Depends(get_db)):
    return {"song": song_data(visible_song(song_id, db, user), db, user)}


@router.post("/artists/me/songs/{song_id}/publish")
def publish_song(song_id: int, artist: User = Depends(artist_user), db: Session = Depends(get_db)):
    song = db.get(Song, song_id)
    if song is None or song.artist_id != artist.id:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Song not found"})
    song.status = "published"
    song.published_at = datetime.now(timezone.utc)
    db.commit()
    db.refresh(song)
    return {"song": song_data(song, db, artist)}


@router.post("/artists/me/songs/{song_id}/unpublish")
def unpublish_song(song_id: int, artist: User = Depends(artist_user), db: Session = Depends(get_db)):
    song = db.get(Song, song_id)
    if song is None or song.artist_id != artist.id:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Song not found"})
    song.status = "draft"
    song.published_at = None
    db.commit()
    return {"song": song_data(song, db, artist)}


@router.get("/artists/me/songs")
def own_songs(status: str | None = None, limit: int = 50, offset: int = 0,
              artist: User = Depends(artist_user), db: Session = Depends(get_db)):
    """The signed-in artist's own catalogue, drafts included."""
    if status is not None and status not in {"draft", "published", "hidden"}:
        raise HTTPException(status_code=422, detail={"code": "invalid_status", "message": "Status must be draft, published or hidden"})
    query = select(Song).where(Song.artist_id == artist.id).order_by(Song.created_at.desc(), Song.id.desc())
    if status:
        query = query.where(Song.status == status)
    songs = db.scalars(query.offset(max(offset, 0)).limit(min(max(limit, 1), 50))).all()
    return {"items": [song_data(song, db, artist) for song in songs], "limit": min(max(limit, 1), 50), "offset": max(offset, 0)}


@router.delete("/artists/me/songs/{song_id}")
def delete_song(song_id: int, artist: User = Depends(artist_user), db: Session = Depends(get_db)):
    song = db.get(Song, song_id)
    if song is None or song.artist_id != artist.id:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Song not found"})
    db.delete(song)
    db.commit()
    storage.delete(song.audio_key)
    if song.cover_key:
        storage.delete(song.cover_key)
    return {"song_id": song_id, "deleted": True}


@router.get("/songs/{song_id}/audio")
def stream_audio(song_id: int, request: Request, viewer: User | None = Depends(optional_current_user), db: Session = Depends(get_db)):
    song = visible_song(song_id, db, viewer)
    # Range requests are what let the player jump to a point in the track.
    return media_response(song.audio_key, song.audio_type, request, filename=f"{song.title}.audio")


@router.get("/songs/{song_id}/cover")
def get_cover(song_id: int, request: Request, viewer: User | None = Depends(optional_current_user), db: Session = Depends(get_db)):
    song = visible_song(song_id, db, viewer)
    if not song.cover_key:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Cover artwork not found"})
    return media_response(song.cover_key, guess_image_type(song.cover_key), request, cache_control="public, max-age=86400")


@router.post("/songs/{song_id}/listen", status_code=201)
def record_listen(song_id: int, payload: ListeningCreate, user: User = Depends(current_user), db: Session = Depends(get_db)):
    song = visible_song(song_id, db, user)
    recent_cutoff = datetime.now(timezone.utc).timestamp() - 1800
    previous = db.scalar(select(ListeningEvent).where(ListeningEvent.listener_id == user.id, ListeningEvent.song_id == song.id, ListeningEvent.counted.is_(True)).order_by(ListeningEvent.created_at.desc()).limit(1))
    was_recent = previous is not None and previous.created_at.timestamp() >= recent_cutoff
    counted = payload.listened_ms >= 30_000 and not was_recent
    event = ListeningEvent(listener_id=user.id, song_id=song.id, listened_ms=payload.listened_ms, counted=counted)
    db.add(event)
    db.commit()
    return {"recorded": True, "counted_for_discovery": counted}


@router.post("/artists/me/releases", status_code=201)
def create_release(payload: ReleaseCreate, artist: User = Depends(artist_user), db: Session = Depends(get_db)):
    songs = db.scalars(select(Song).where(Song.id.in_(payload.song_ids), Song.artist_id == artist.id)).all()
    if len(songs) != len(payload.song_ids):
        raise HTTPException(status_code=404, detail={"code": "song_not_found", "message": "One or more songs were not found in your catalog"})
    if payload.kind == "single" and len(songs) != 1:
        raise HTTPException(status_code=422, detail={"code": "invalid_release_size", "message": "A single release must contain exactly one song"})
    if payload.kind == "ep" and len(songs) > 6:
        raise HTTPException(status_code=422, detail={"code": "invalid_release_size", "message": "An EP can contain up to six songs"})
    release = Release(artist_id=artist.id, title=payload.title.strip(), kind=payload.kind, status="draft")
    db.add(release)
    db.flush()
    db.add_all([ReleaseSong(release_id=release.id, song_id=song_id, position=index) for index, song_id in enumerate(payload.song_ids)])
    db.commit()
    return {"release": {"id": release.id, "title": release.title, "kind": release.kind, "status": release.status, "song_ids": payload.song_ids}}


@router.post("/artists/me/releases/{release_id}/publish")
def publish_release(release_id: int, artist: User = Depends(artist_user), db: Session = Depends(get_db)):
    release = db.get(Release, release_id)
    if release is None or release.artist_id != artist.id:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Release not found"})
    now = datetime.now(timezone.utc)
    release.status = "published"
    release.published_at = now
    song_ids = db.scalars(select(ReleaseSong.song_id).where(ReleaseSong.release_id == release.id)).all()
    for song in db.scalars(select(Song).where(Song.id.in_(song_ids), Song.artist_id == artist.id)).all():
        song.status = "published"
        song.published_at = now
    db.commit()
    return {"release": {"id": release.id, "title": release.title, "kind": release.kind, "status": release.status, "song_ids": song_ids}}


@router.get("/artists/me/releases")
def own_releases(limit: int = 50, offset: int = 0,
                 artist: User = Depends(artist_user), db: Session = Depends(get_db)):
    releases = db.scalars(
        select(Release).where(Release.artist_id == artist.id).order_by(Release.created_at.desc())
        .offset(max(offset, 0)).limit(min(max(limit, 1), 50))
    ).all()
    items = []
    for release in releases:
        song_ids = db.scalars(select(ReleaseSong.song_id).where(ReleaseSong.release_id == release.id).order_by(ReleaseSong.position)).all()
        items.append({"id": release.id, "artist_id": release.artist_id, "title": release.title, "kind": release.kind,
                      "status": release.status, "published_at": release.published_at, "song_ids": song_ids})
    return {"items": items, "limit": min(max(limit, 1), 50), "offset": max(offset, 0)}


@router.get("/artists/{artist_id}/songs")
def artist_songs(artist_id: int, limit: int = 50, offset: int = 0,
                 user: User | None = Depends(optional_current_user), db: Session = Depends(get_db)):
    artist = db.get(User, artist_id)
    if artist is None or artist.artist_profile is None or not artist.is_active:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Artist not found"})
    songs = db.scalars(
        select(Song).where(Song.artist_id == artist_id, Song.status == "published")
        .order_by(Song.published_at.desc(), Song.id.desc())
        .offset(max(offset, 0)).limit(min(max(limit, 1), 50))
    ).all()
    return {"items": [song_data(song, db, user) for song in songs], "limit": min(max(limit, 1), 50), "offset": max(offset, 0)}


@router.get("/artists/{artist_id}/releases")
def list_artist_releases(artist_id: int, limit: int = 20, offset: int = 0, db: Session = Depends(get_db)):
    artist = db.get(User, artist_id)
    if artist is None or artist.artist_profile is None or not artist.is_active:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Artist not found"})
    releases = db.scalars(select(Release).where(Release.artist_id == artist_id, Release.status == "published").order_by(Release.published_at.desc()).offset(max(offset, 0)).limit(min(max(limit, 1), 50))).all()
    items = []
    for release in releases:
        song_ids = db.scalars(select(ReleaseSong.song_id).where(ReleaseSong.release_id == release.id).order_by(ReleaseSong.position)).all()
        items.append({"id": release.id, "artist_id": artist_id, "title": release.title, "kind": release.kind, "published_at": release.published_at, "song_ids": song_ids})
    return {"items": items, "limit": min(max(limit, 1), 50), "offset": max(offset, 0)}


@router.get("/releases/{release_id}")
def get_release(release_id: int, user: User | None = Depends(optional_current_user), db: Session = Depends(get_db)):
    release = db.get(Release, release_id)
    if release is None or release.status != "published":
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Release not found"})
    song_ids = db.scalars(select(ReleaseSong.song_id).where(ReleaseSong.release_id == release.id).order_by(ReleaseSong.position)).all()
    songs = [song_data(song, db, user) for song_id in song_ids if (song := db.get(Song, song_id)) is not None and song.status == "published"]
    return {"release": {"id": release.id, "artist_id": release.artist_id, "title": release.title, "kind": release.kind, "published_at": release.published_at, "songs": songs}}


@router.get("/feed/following")
def following_feed(limit: int = 20, offset: int = 0, user: User = Depends(current_user), db: Session = Depends(get_db)):
    query = select(Song).join(Follow, Follow.artist_id == Song.artist_id).where(Follow.listener_id == user.id, Song.status == "published").order_by(Song.published_at.desc(), Song.id.desc())
    songs = db.scalars(query.offset(max(offset, 0)).limit(min(max(limit, 1), 50))).all()
    return {"items": [song_data(song, db, user) for song in songs], "limit": min(max(limit, 1), 50), "offset": max(offset, 0)}


def _week_ago() -> datetime:
    return datetime.now(timezone.utc) - timedelta(days=7)


@router.get("/artists/me/stats")
def artist_stats(artist: User = Depends(artist_user), db: Session = Depends(get_db)):
    song_ids = select(Song.id).where(Song.artist_id == artist.id)
    published_songs = db.scalar(select(func.count(Song.id)).where(Song.artist_id == artist.id, Song.status == "published")) or 0
    likes = db.scalar(select(func.count()).select_from(Like).where(Like.song_id.in_(song_ids))) or 0
    qualified_listens = db.scalar(
        select(func.count(ListeningEvent.id))
        .join(Song, Song.id == ListeningEvent.song_id)
        .where(Song.artist_id == artist.id, ListeningEvent.counted.is_(True))
    ) or 0
    since = _week_ago()
    weekly_plays = db.scalar(
        select(func.count(ListeningEvent.id))
        .join(Song, Song.id == ListeningEvent.song_id)
        .where(Song.artist_id == artist.id, ListeningEvent.counted.is_(True), ListeningEvent.created_at >= since)
    ) or 0
    weekly_likes = db.scalar(
        select(func.count())
        .select_from(Like)
        .join(Song, Song.id == Like.song_id)
        .where(Song.artist_id == artist.id, Like.created_at >= since)
    ) or 0
    top = db.execute(
        select(Song.id, func.count(ListeningEvent.id).label("plays"))
        .join(ListeningEvent, ListeningEvent.song_id == Song.id)
        .where(Song.artist_id == artist.id, ListeningEvent.counted.is_(True), ListeningEvent.created_at >= since)
        .group_by(Song.id)
        .order_by(func.count(ListeningEvent.id).desc())
        .limit(1)
    ).first()
    top_song = None
    if top is not None:
        song = db.get(Song, top[0])
        if song is not None:
            top_song = {"id": song.id, "title": song.title, "plays": top[1]}
    return {"artist_id": artist.id,
            "published_songs": published_songs,
            "likes": likes,
            "qualified_listens": qualified_listens,
            "weekly_plays": weekly_plays,
            "weekly_likes": weekly_likes,
            "weekly_top_song": top_song,
            "window_days": 7}


@router.get("/rankings/weekly")
def weekly_rankings(limit: int = 10, user: User | None = Depends(optional_current_user), db: Session = Depends(get_db)):
    """The songs people actually listened to over the last seven days."""
    limit = min(max(limit, 1), 50)
    since = _week_ago()
    rows = db.execute(
        select(ListeningEvent.song_id, func.count(ListeningEvent.id).label("plays"))
        .join(Song, Song.id == ListeningEvent.song_id)
        .where(Song.status == "published", ListeningEvent.counted.is_(True), ListeningEvent.created_at >= since)
        .group_by(ListeningEvent.song_id)
        .order_by(func.count(ListeningEvent.id).desc(), ListeningEvent.song_id)
        .limit(limit)
    ).all()
    items = []
    for rank, (song_id, plays) in enumerate(rows, start=1):
        song = db.get(Song, song_id)
        if song is not None:
            items.append({"rank": rank, "plays": plays, "song": song_data(song, db, user)})
    return {"items": items, "window_days": 7}


@router.get("/artists/{artist_id}/appears-on")
def appears_on(artist_id: int, limit: int = 20, user: User | None = Depends(optional_current_user), db: Session = Depends(get_db)):
    """Songs by other artists where this artist is credited as a feature."""
    limit = min(max(limit, 1), 50)
    songs = db.scalars(
        select(Song)
        .join(SongFeature, SongFeature.song_id == Song.id)
        .where(SongFeature.artist_id == artist_id, Song.status == "published")
        .order_by(Song.published_at.desc(), Song.id.desc())
        .limit(limit)
    ).all()
    return {"items": [song_data(song, db, user) for song in songs], "limit": limit}
