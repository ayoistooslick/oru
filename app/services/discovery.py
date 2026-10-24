from datetime import datetime, timedelta, timezone

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.models import Follow, Genre, Like, ListeningEvent, Song, SongGenre


def discover_songs(db: Session, listener_id: int | None, genre: str | None, limit: int, offset: int) -> list[Song]:
    """Rank published tracks with engagement, recency and a small-catalogue opportunity boost."""
    cutoff = datetime.now(timezone.utc) - timedelta(days=30)
    query = select(Song).where(Song.status == "published")
    if genre:
        query = query.join(SongGenre).join(Genre, Genre.id == SongGenre.genre_id).where(func.lower(Genre.name) == genre.strip().lower())
    candidates = db.scalars(query).unique().all()
    if not candidates:
        return []

    ids = [song.id for song in candidates]
    likes = dict(db.execute(select(Like.song_id, func.count()).where(Like.song_id.in_(ids)).group_by(Like.song_id)).all())
    plays = dict(db.execute(select(ListeningEvent.song_id, func.count()).where(ListeningEvent.song_id.in_(ids), ListeningEvent.counted.is_(True), ListeningEvent.created_at >= cutoff).group_by(ListeningEvent.song_id)).all())
    followed = set()
    if listener_id:
        followed = set(db.scalars(select(Follow.artist_id).where(Follow.listener_id == listener_id)).all())

    now = datetime.now(timezone.utc)
    catalog_sizes: dict[int, int] = {}
    for candidate in candidates:
        catalog_sizes[candidate.artist_id] = catalog_sizes.get(candidate.artist_id, 0) + 1

    def score(song: Song) -> tuple[float, int]:
        like_count = likes.get(song.id, 0)
        play_count = plays.get(song.id, 0)
        days_old = max((now - song.created_at.replace(tzinfo=timezone.utc)).total_seconds() / 86400, 0)
        recency = max(0.0, 14.0 - days_old) / 14.0
        follower_bonus = 2.5 if song.artist_id in followed else 0.0
        # Smaller catalogs receive a modest chance bonus, not a guaranteed top spot.
        artist_catalogue = catalog_sizes[song.artist_id]
        opportunity = 2.0 / (1 + artist_catalogue)
        overexposure = min(play_count, 100) * 0.015
        value = 2.0 * like_count + play_count + follower_bonus + recency + opportunity - overexposure
        return value, song.id

    return sorted(candidates, key=score, reverse=True)[max(offset, 0):max(offset, 0) + limit]
