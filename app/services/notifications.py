"""Fan a new drop out to the people who follow the artist.

Written as a service because publishing happens in three places — a single
song, a release, and a shared song being approved — and all three should ring
the same bell. Nothing is committed here; the caller owns the transaction, so a
notification can never outlive a failed publish.
"""

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models import Follow, Notification, Release, Song, User


def followers_to_notify(db: Session, artist_id: int) -> list[int]:
    """Listeners who follow this artist and have not turned notices off."""
    return list(
        db.scalars(
            select(Follow.listener_id)
            .join(User, User.id == Follow.listener_id)
            .where(
                Follow.artist_id == artist_id,
                User.notify_new_drops.is_(True),
                User.is_active.is_(True),
            )
        ).all()
    )


def _add(
    db: Session,
    recipients: list[int],
    *,
    dedupe_key: str,
    kind: str,
    artist_id: int,
    song_id: int | None = None,
    release_id: int | None = None,
) -> int:
    if not recipients:
        return 0
    already = set(
        db.scalars(
            select(Notification.user_id).where(
                Notification.user_id.in_(recipients),
                Notification.dedupe_key == dedupe_key,
            )
        ).all()
    )
    fresh = [user_id for user_id in recipients if user_id not in already]
    db.add_all(
        Notification(
            user_id=user_id,
            kind=kind,
            artist_id=artist_id,
            song_id=song_id,
            release_id=release_id,
            dedupe_key=dedupe_key,
        )
        for user_id in fresh
    )
    return len(fresh)


def announce_song(db: Session, song: Song) -> int:
    """One published song, one notice per follower."""
    return _add(
        db,
        followers_to_notify(db, song.artist_id),
        dedupe_key=f"song:{song.id}",
        kind="new_song",
        artist_id=song.artist_id,
        song_id=song.id,
    )


def announce_release(db: Session, release: Release) -> int:
    """A release is announced once as a collection, not once per song in it."""
    return _add(
        db,
        followers_to_notify(db, release.artist_id),
        dedupe_key=f"release:{release.id}",
        kind="new_release",
        artist_id=release.artist_id,
        release_id=release.id,
    )
