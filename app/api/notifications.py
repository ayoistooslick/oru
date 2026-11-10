from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import func, select, update
from sqlalchemy.orm import Session

from app.database import get_db
from app.dependencies import current_user
from app.models import Notification, Release, ReleaseSong, Song, User, now_utc
from app.schemas import NotificationPreference

router = APIRouter(tags=["notifications"])


def _unread(db: Session, user_id: int) -> int:
    return db.scalar(
        select(func.count(Notification.id)).where(
            Notification.user_id == user_id, Notification.read_at.is_(None)
        )
    ) or 0


def _cover_for(db: Session, song: Song | None, release: Release | None) -> str | None:
    if song is not None:
        return f"/api/v1/songs/{song.id}/cover" if song.cover_key else None
    if release is None:
        return None
    # A release borrows the artwork of its first song that has one.
    first = db.scalar(
        select(ReleaseSong.song_id)
        .join(Song, Song.id == ReleaseSong.song_id)
        .where(ReleaseSong.release_id == release.id, Song.cover_key.is_not(None))
        .order_by(ReleaseSong.position)
        .limit(1)
    )
    return f"/api/v1/songs/{first}/cover" if first else None


def notification_data(note: Notification, db: Session) -> dict:
    """Everything a row needs to draw itself, without a second request."""
    artist = db.get(User, note.artist_id) if note.artist_id else None
    song = db.get(Song, note.song_id) if note.song_id else None
    release = db.get(Release, note.release_id) if note.release_id else None
    profile = artist.artist_profile if artist is not None else None
    href = None
    if song is not None and song.status == "published":
        href = f"/song/{song.id}"
    elif release is not None and release.status == "published":
        href = f"/release/{release.id}"
    return {
        "id": note.id,
        "kind": note.kind,
        "created_at": note.created_at,
        "read_at": note.read_at,
        "artist": None if artist is None else {
            "id": artist.id,
            "name": profile.stage_name if profile else artist.display_name,
            "handle": artist.handle,
            "avatar_url": f"/api/v1/users/{artist.id}/avatar" if artist.avatar_key else None,
        },
        "song": None if song is None else {
            "id": song.id,
            "title": song.title,
            "cover_url": _cover_for(db, song, None),
            "is_available": song.status == "published",
        },
        "release": None if release is None else {
            "id": release.id,
            "title": release.title,
            "kind": release.kind,
            "cover_url": _cover_for(db, None, release),
            "is_available": release.status == "published",
        },
        "href": href,
    }


@router.get("/notifications")
def list_notifications(limit: int = 50, offset: int = 0,
                       user: User = Depends(current_user), db: Session = Depends(get_db)):
    """Newest first. The unread count rides along so the bell needs one call."""
    safe_limit = min(max(limit, 1), 50)
    notes = db.scalars(
        select(Notification)
        .where(Notification.user_id == user.id)
        .order_by(Notification.created_at.desc(), Notification.id.desc())
        .offset(max(offset, 0))
        .limit(safe_limit)
    ).all()
    return {
        "items": [notification_data(note, db) for note in notes],
        "unread_count": _unread(db, user.id),
        "limit": safe_limit,
        "offset": max(offset, 0),
    }


@router.get("/notifications/unread")
def unread_notifications(user: User = Depends(current_user), db: Session = Depends(get_db)):
    """The number behind the bell dot, small enough to poll."""
    return {"unread": _unread(db, user.id)}


@router.post("/notifications/read-all")
def read_all_notifications(user: User = Depends(current_user), db: Session = Depends(get_db)):
    marked = db.execute(
        update(Notification)
        .where(Notification.user_id == user.id, Notification.read_at.is_(None))
        .values(read_at=now_utc())
    ).rowcount or 0
    db.commit()
    return {"marked": marked, "unread_count": 0}


@router.post("/notifications/{notification_id}/read")
def read_notification(notification_id: int, user: User = Depends(current_user),
                      db: Session = Depends(get_db)):
    note = db.get(Notification, notification_id)
    if note is None or note.user_id != user.id:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Notification not found"})
    if note.read_at is None:
        note.read_at = now_utc()
        db.commit()
        db.refresh(note)
    return {"notification": notification_data(note, db), "unread_count": _unread(db, user.id)}


@router.patch("/notifications/settings")
def update_notification_preference(payload: NotificationPreference,
                                   user: User = Depends(current_user), db: Session = Depends(get_db)):
    """Turn new-drop notices off without unfollowing anyone.

    Existing notices stay where they are; only future drops are silenced.
    """
    user.notify_new_drops = payload.enabled
    db.commit()
    db.refresh(user)
    return {"notify_new_drops": bool(user.notify_new_drops)}
