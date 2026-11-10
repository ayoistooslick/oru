from datetime import datetime, timezone

from sqlalchemy import Boolean, CheckConstraint, DateTime, ForeignKey, Index, Integer, String, Text, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


def now_utc():
    return datetime.now(timezone.utc)


class User(Base):
    __tablename__ = "users"
    id: Mapped[int] = mapped_column(primary_key=True)
    email: Mapped[str] = mapped_column(String(320), unique=True, index=True)
    handle: Mapped[str] = mapped_column(String(32), unique=True, index=True)
    password_hash: Mapped[str] = mapped_column(String(255))
    role: Mapped[str] = mapped_column(String(16), default="listener")
    display_name: Mapped[str] = mapped_column(String(80))
    bio: Mapped[str] = mapped_column(Text, default="")
    # Everyone, listeners included, can put a face and a wide banner on their
    # profile. Bumping image_version changes the picture URL, so a replaced
    # image is never served from an old cache.
    avatar_key: Mapped[str | None] = mapped_column(String(500), nullable=True)
    banner_key: Mapped[str | None] = mapped_column(String(500), nullable=True)
    image_version: Mapped[int] = mapped_column(Integer, default=0)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    # On unless the listener turns it off. Only songs from artists they follow
    # are ever announced, so following the artist is the real opt-in.
    notify_new_drops: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now_utc)
    artist_profile: Mapped["ArtistProfile"] = relationship(back_populates="user", uselist=False)


class LoginCode(Base):
    """A short-lived ticket that carries a browser sign-in back to the app.

    Google will only sign a person in inside a browser, so the app opens the
    site, the site signs in, and the phone is handed a single-use code instead
    of the token itself: a URL that ends up in browser history cannot be
    replayed as a session.
    """
    __tablename__ = "login_codes"
    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    code_hash: Mapped[str] = mapped_column(String(64), unique=True, index=True)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    used_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now_utc)


class ArtistProfile(Base):
    __tablename__ = "artist_profiles"
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), primary_key=True)
    stage_name: Mapped[str] = mapped_column(String(100))
    # A wide picture behind the artist page header. Artists only.
    banner_key: Mapped[str | None] = mapped_column(String(500), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now_utc)
    user: Mapped[User] = relationship(back_populates="artist_profile")


class Genre(Base):
    __tablename__ = "genres"
    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(String(48), unique=True, index=True)


class Song(Base):
    __tablename__ = "songs"
    __table_args__ = (Index("ix_songs_status_created", "status", "created_at"),)
    id: Mapped[int] = mapped_column(primary_key=True)
    artist_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    title: Mapped[str] = mapped_column(String(120))
    description: Mapped[str] = mapped_column(Text, default="")
    audio_key: Mapped[str] = mapped_column(String(500))
    cover_key: Mapped[str | None] = mapped_column(String(500), nullable=True)
    audio_type: Mapped[str] = mapped_column(String(80))
    # Sent by whichever client uploaded the audio, which is the only place that
    # knows how long the file is; older rows predate the field.
    duration_ms: Mapped[int | None] = mapped_column(Integer, nullable=True)
    status: Mapped[str] = mapped_column(String(16), default="draft", index=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now_utc)
    published_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    # Set when a listener shares someone else's song: the artist keeps the credit,
    # the uploader is recorded for transparency and can withdraw it before approval.
    shared_by_user_id: Mapped[int | None] = mapped_column(ForeignKey("users.id", ondelete="SET NULL"), nullable=True, index=True)
    source_url: Mapped[str | None] = mapped_column(String(500), nullable=True)
    artist: Mapped[User] = relationship(foreign_keys=[artist_id])
    shared_by: Mapped[User | None] = relationship(foreign_keys=[shared_by_user_id])
    genres: Mapped[list["SongGenre"]] = relationship(back_populates="song", cascade="all, delete-orphan")
    features: Mapped[list["SongFeature"]] = relationship(back_populates="song", cascade="all, delete-orphan")


class SongFeature(Base):
    """Another artist credited on a song, shown as "feat." everywhere it plays."""
    __tablename__ = "song_features"
    song_id: Mapped[int] = mapped_column(ForeignKey("songs.id", ondelete="CASCADE"), primary_key=True)
    artist_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), primary_key=True, index=True)
    song: Mapped[Song] = relationship(back_populates="features")
    artist: Mapped[User] = relationship()


class SongGenre(Base):
    __tablename__ = "song_genres"
    song_id: Mapped[int] = mapped_column(ForeignKey("songs.id", ondelete="CASCADE"), primary_key=True)
    genre_id: Mapped[int] = mapped_column(ForeignKey("genres.id", ondelete="CASCADE"), primary_key=True)
    song: Mapped[Song] = relationship(back_populates="genres")
    genre: Mapped[Genre] = relationship()


class Release(Base):
    __tablename__ = "releases"
    id: Mapped[int] = mapped_column(primary_key=True)
    artist_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    title: Mapped[str] = mapped_column(String(120))
    kind: Mapped[str] = mapped_column(String(16))
    status: Mapped[str] = mapped_column(String(16), default="draft", index=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now_utc)
    published_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)


class ReleaseSong(Base):
    __tablename__ = "release_songs"
    __table_args__ = (UniqueConstraint("release_id", "position"), UniqueConstraint("release_id", "song_id"))
    release_id: Mapped[int] = mapped_column(ForeignKey("releases.id", ondelete="CASCADE"), primary_key=True)
    song_id: Mapped[int] = mapped_column(ForeignKey("songs.id", ondelete="CASCADE"), primary_key=True)
    position: Mapped[int] = mapped_column(Integer)


class Like(Base):
    __tablename__ = "likes"
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), primary_key=True)
    song_id: Mapped[int] = mapped_column(ForeignKey("songs.id", ondelete="CASCADE"), primary_key=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now_utc)


class Follow(Base):
    __tablename__ = "follows"
    __table_args__ = (CheckConstraint("listener_id != artist_id", name="ck_follow_not_self"),)
    listener_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), primary_key=True)
    artist_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), primary_key=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now_utc)


class Notification(Base):
    """A notice for one listener: an artist they follow put something out.

    `dedupe_key` names the thing announced (`song:12`, `release:4`), so
    re-publishing the same song or approving a share twice cannot ring the
    bell twice. Read state lives here rather than in a separate table: a
    notification is small and always read in one place.
    """
    __tablename__ = "notifications"
    __table_args__ = (
        UniqueConstraint("user_id", "dedupe_key"),
        Index("ix_notifications_user_created", "user_id", "created_at"),
    )
    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    kind: Mapped[str] = mapped_column(String(24))
    artist_id: Mapped[int | None] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), nullable=True)
    song_id: Mapped[int | None] = mapped_column(ForeignKey("songs.id", ondelete="CASCADE"), nullable=True)
    release_id: Mapped[int | None] = mapped_column(ForeignKey("releases.id", ondelete="CASCADE"), nullable=True)
    dedupe_key: Mapped[str] = mapped_column(String(64))
    read_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now_utc)


class Playlist(Base):
    __tablename__ = "playlists"
    id: Mapped[int] = mapped_column(primary_key=True)
    owner_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    title: Mapped[str] = mapped_column(String(100))
    is_public: Mapped[bool] = mapped_column(Boolean, default=True)
    # A cover is the square artwork; the banner is the wide picture behind the
    # playlist page. Without them a playlist borrows its first song's cover.
    cover_key: Mapped[str | None] = mapped_column(String(500), nullable=True)
    banner_key: Mapped[str | None] = mapped_column(String(500), nullable=True)
    image_version: Mapped[int] = mapped_column(Integer, default=0)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now_utc)


class PlaylistSong(Base):
    __tablename__ = "playlist_songs"
    __table_args__ = (UniqueConstraint("playlist_id", "song_id"), UniqueConstraint("playlist_id", "position"))
    playlist_id: Mapped[int] = mapped_column(ForeignKey("playlists.id", ondelete="CASCADE"), primary_key=True)
    song_id: Mapped[int] = mapped_column(ForeignKey("songs.id", ondelete="CASCADE"), primary_key=True)
    position: Mapped[int] = mapped_column(Integer)
    added_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now_utc)


class ListeningEvent(Base):
    __tablename__ = "listening_events"
    __table_args__ = (Index("ix_listening_song_created", "song_id", "created_at"),)
    id: Mapped[int] = mapped_column(primary_key=True)
    listener_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    song_id: Mapped[int] = mapped_column(ForeignKey("songs.id", ondelete="CASCADE"), index=True)
    listened_ms: Mapped[int] = mapped_column(Integer)
    counted: Mapped[bool] = mapped_column(Boolean, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now_utc)


class ArtistPost(Base):
    __tablename__ = "artist_posts"
    id: Mapped[int] = mapped_column(primary_key=True)
    artist_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    body: Mapped[str] = mapped_column(Text)
    is_hidden: Mapped[bool] = mapped_column(Boolean, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now_utc)


class ExternalShare(Base):
    """A listener points at a song that lives somewhere else, because the artist
    is not on ORU yet. There is no audio to upload, only the link and the credit."""
    __tablename__ = "external_shares"
    id: Mapped[int] = mapped_column(primary_key=True)
    sharer_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    title: Mapped[str] = mapped_column(String(120))
    artist_name: Mapped[str] = mapped_column(String(120))
    url: Mapped[str] = mapped_column(String(500))
    note: Mapped[str] = mapped_column(Text, default="")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now_utc)
    sharer: Mapped[User] = relationship(foreign_keys=[sharer_id])


class Report(Base):
    __tablename__ = "reports"
    id: Mapped[int] = mapped_column(primary_key=True)
    reporter_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    target_type: Mapped[str] = mapped_column(String(16))
    target_id: Mapped[int] = mapped_column(Integer)
    reason: Mapped[str] = mapped_column(String(32))
    details: Mapped[str] = mapped_column(Text, default="")
    status: Mapped[str] = mapped_column(String(16), default="open", index=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now_utc)


class ModerationAction(Base):
    __tablename__ = "moderation_actions"
    id: Mapped[int] = mapped_column(primary_key=True)
    moderator_id: Mapped[int] = mapped_column(ForeignKey("users.id"))
    report_id: Mapped[int] = mapped_column(ForeignKey("reports.id", ondelete="CASCADE"), index=True)
    action: Mapped[str] = mapped_column(String(24))
    reason: Mapped[str] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now_utc)
