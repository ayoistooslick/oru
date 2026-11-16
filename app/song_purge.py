"""Delete every song, and the media behind it, without touching accounts.

Used by two callers that must agree:

- ``POST /maintenance/songs`` in ``app/api/maintenance.py`` (an operator route
  that only exists while ``ORU_SONGS_TOKEN`` is set), and
- ``python -m scripts.purge_songs --yes`` for a local database.

Likes, playlist tracks, release entries, notifications and listening events all
cascade from ``songs.id``, so removing the rows is enough for the database. The
audio and cover files are removed afterwards, individually and tolerantly: one
file that the media service no longer has must not leave the catalogue
half-deleted.
"""

from __future__ import annotations

from sqlalchemy import select

from app.database import SessionLocal
from app.models import Song
from app.storage import storage


def purge_songs() -> dict:
    """Remove every song row, then every file it pointed at."""
    with SessionLocal() as db:
        songs = db.scalars(select(Song)).all()
        keys: list[str] = []
        for song in songs:
            for key in (song.audio_key, song.cover_key):
                if key:
                    keys.append(key)
            db.delete(song)
        db.commit()

    deleted = 0
    failed = 0
    for key in keys:
        try:
            storage.delete(key)
            deleted += 1
        except Exception:  # noqa: BLE001 - a missing file is not a failure
            failed += 1
    return {"songs": len(songs), "media_deleted": deleted,
            "media_missing": failed}
