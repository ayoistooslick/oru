"""Page metadata for link previews.

The React app is a single page, so a crawler that does not run JavaScript sees
only what we put in index.html. Every route therefore gets its own title,
description and share image, rendered into the HTML before it is sent.
"""

from __future__ import annotations

import html
import logging
import re

logger = logging.getLogger("oru")

SITE_NAME = "ORU"
DEFAULT_TITLE = "ORU, music from new artists"
DEFAULT_DESCRIPTION = (
    "ORU is where new artists upload a song, publish it and get heard. "
    "Listen free, follow artists, make playlists and put your own music up."
)
DEFAULT_IMAGE = "/og.png"
DEFAULT_IMAGE_ALT = "The ORU mark and the line, new music from artists who are just starting"
SQUARE_IMAGE = "/og-square.png"
SQUARE_IMAGE_ALT = "The ORU mark on a dark square"

_TITLE_RE = re.compile(r"<title>.*?</title>", re.S)
_BLOCK_RE = re.compile(r"(<!-- social-meta:start -->).*?(<!-- social-meta:end -->)", re.S)

# Route shapes the single page serves. Anything unmatched falls back to the
# site default, which is still a complete preview.
_STATIC_PAGES = {
    "": (DEFAULT_TITLE, DEFAULT_DESCRIPTION),
    "listen": ("Home, ORU", "New songs from artists who are just starting, plus everything you follow."),
    "search": ("Search, ORU", "Find a song, an artist or a playlist on ORU."),
    "library": ("Your library, ORU", "The playlists you made and the songs you saved with the heart."),
    "studio": ("Studio, ORU", "Upload a song, keep it as a draft, publish it when you are ready."),
    "settings": ("Settings, ORU", "Your account, your handle and what other people see."),
    "moderation": ("Reports, ORU", "Songs people asked a moderator to look at."),
    "auth": ("Log in or sign up, ORU", "Make a free account and start listening. It is free."),
    "about": (
        "About the developer, ORU",
        "Ayodele Ayokunle David builds ORU. A teenager writing software and useful ideas.",
    ),
    "share": ("Opening ORU", "A song, artist or playlist shared into ORU."),
}


def _escape(value: object) -> str:
    return html.escape(str(value), quote=True)


def _shorten(text: str, limit: int = 200) -> str:
    text = " ".join(str(text).split())
    if len(text) <= limit:
        return text
    return text[: limit - 1].rstrip() + "…"


def request_origin(request) -> str:
    """Absolute origin of the current request, trusting a front proxy when present."""
    from app.config import settings

    if settings.site_url:
        return settings.site_url.rstrip("/")
    proto = request.headers.get("x-forwarded-proto") or request.url.scheme or "https"
    host = request.headers.get("x-forwarded-host") or request.headers.get("host") or request.url.netloc
    root = request.scope.get("root_path", "") or ""
    return f"{proto}://{host}{root}".rstrip("/")


def _absolute(origin: str, path: str) -> str:
    if path.startswith(("http://", "https://")):
        return path
    if path.startswith("/"):
        return f"{origin}{path}"
    return f"{origin}/{path}"


def _page(path: str) -> tuple[str, str, str, str, str] | None:
    """Resolve a route to (title, description, image, image_alt, canonical path).

    Database trouble must never take the page down, so anything unexpected
    returns None and the default metadata is used instead.
    """
    parts = [part for part in path.strip("/").split("/") if part]
    if not parts:
        return None

    route = parts[0]
    if len(parts) == 1 and route in _STATIC_PAGES:
        title, description = _STATIC_PAGES[route]
        return title, description, DEFAULT_IMAGE, DEFAULT_IMAGE_ALT, f"/{route}"

    if len(parts) != 2 or not parts[1].isdigit():
        return None
    record_id = int(parts[1])

    try:
        from app.database import SessionLocal
        from app.models import ArtistProfile, Playlist, Release, ReleaseSong, Song, User

        db = SessionLocal()
        try:
            if route == "release":
                release = db.get(Release, record_id)
                if release is None or release.status != "published":
                    return None
                owner = db.get(User, release.artist_id)
                stage = owner.artist_profile.stage_name if owner and owner.artist_profile else (owner.display_name if owner else "an ORU artist")
                kind = {"ep": "EP", "album": "Album"}.get(release.kind, "Single")
                songs = db.query(Song).join(ReleaseSong).filter(ReleaseSong.release_id == release.id).order_by(ReleaseSong.position).all()
                cover = next((song for song in songs if song.cover_key and song.status == "published"), None)
                title = f"{release.title}, {kind} by {stage}"
                description = f"Listen to {release.title} by {stage} on ORU. Free, no subscription."
                image = f"/api/v1/songs/{cover.id}/cover" if cover else DEFAULT_IMAGE
                image_alt = f"Cover art for {release.title} by {stage}" if cover else DEFAULT_IMAGE_ALT
                return title, description, image, image_alt, f"/release/{release.id}"

            if route == "artist":
                profile = db.get(ArtistProfile, record_id)
                if profile is None:
                    owner = db.get(User, record_id)
                    if owner is None or owner.artist_profile is None:
                        return None
                    profile = owner.artist_profile
                stage = profile.stage_name
                title = f"{stage}, on ORU"
                description = f"Every song {stage} put up on ORU. Listen free and follow to hear new music first."
                return title, description, DEFAULT_IMAGE, DEFAULT_IMAGE_ALT, f"/artist/{record_id}"

            if route == "library":
                playlist = db.get(Playlist, record_id)
                if playlist is None:
                    return None
                title = f"{playlist.title}, a playlist on ORU"
                description = f"A playlist called {playlist.title}. Play it free on ORU."
                return title, description, DEFAULT_IMAGE, DEFAULT_IMAGE_ALT, f"/library/{playlist.id}"
        finally:
            db.close()
    except Exception:
        logger.exception("Could not build page metadata for %s", path)

    return None


def render_document(source: str, origin: str, path: str) -> str:
    """Return index.html with the share tags rewritten for this route."""
    resolved = _page(path)
    title, description, image, image_alt, canonical = (
        resolved if resolved else (DEFAULT_TITLE, DEFAULT_DESCRIPTION, DEFAULT_IMAGE, DEFAULT_IMAGE_ALT, path)
    )
    title = _escape(title)
    description = _escape(_shorten(description))
    url = _escape(_absolute(origin, canonical))
    image_url = _escape(_absolute(origin, image))
    square_url = _escape(_absolute(origin, SQUARE_IMAGE))
    image_alt = _escape(image_alt)

    block = f"""<!-- social-meta:start -->
    <link rel="canonical" href="{url}" />
    <meta property="og:site_name" content="{SITE_NAME}" />
    <meta property="og:type" content="website" />
    <meta property="og:locale" content="en_US" />
    <meta property="og:title" content="{title}" />
    <meta property="og:description" content="{description}" />
    <meta property="og:url" content="{url}" />
    <meta property="og:image" content="{image_url}" />
    <meta property="og:image:width" content="1200" />
    <meta property="og:image:height" content="630" />
    <meta property="og:image:type" content="image/png" />
    <meta property="og:image:alt" content="{image_alt}" />
    <meta property="og:image" content="{square_url}" />
    <meta property="og:image:width" content="512" />
    <meta property="og:image:height" content="512" />
    <meta property="og:image:type" content="image/png" />
    <meta property="og:image:alt" content="{SQUARE_IMAGE_ALT}" />
    <meta name="twitter:card" content="summary_large_image" />
    <meta name="twitter:title" content="{title}" />
    <meta name="twitter:description" content="{description}" />
    <meta name="twitter:image" content="{image_url}" />
    <meta name="twitter:image:alt" content="{image_alt}" />
    <!-- social-meta:end -->"""

    document = _BLOCK_RE.sub(lambda match: match.group(1) + block[len("<!-- social-meta:start -->") :], source)
    document = _TITLE_RE.sub(f"<title>{title}</title>", document, count=1)
    document = document.replace('"/icons/icon-512.png"', f'"{_absolute(origin, "/icons/icon-512.png")}"')
    document = document.replace('"/screenshots/home-phone.png"', f'"{_absolute(origin, "/screenshots/home-phone.png")}"')
    return document
