"""Start ORU over: every song and every artist gone, replaced by demo content.

    python -m scripts.seed_demo --yes

The demo artists are made up, and their songs are generated here (a WAV per
track, a PNG per cover), so the library plays and looks finished on a phone
without downloading anything from the internet.

Listener accounts are kept, so the owner can still sign in. Artist accounts
(and everything that belonged to them) are removed with the songs.
"""

from __future__ import annotations

import argparse
import math
import secrets
import struct
import wave
import zlib
from array import array
from datetime import datetime, timedelta, timezone
from io import BytesIO

from sqlalchemy import delete, func, or_, select, update
from sqlalchemy.orm import Session

from app.database import Base, SessionLocal, engine
from app.models import (
    ArtistPost,
    ArtistProfile,
    ExternalShare,
    Follow,
    Genre,
    Like,
    ListeningEvent,
    ModerationAction,
    PlaylistSong,
    Release,
    ReleaseSong,
    Report,
    Song,
    SongFeature,
    SongGenre,
    User,
)
from app.security import hash_password
from app.storage import storage

RATE = 22050
TABLE_SIZE = 4096
SINE = [math.sin(2 * math.pi * index / TABLE_SIZE) for index in range(TABLE_SIZE)]

ARTISTS = [
    {
        "handle": "adaezesun",
        "name": "Adaeze Sun",
        "stage": "Adaeze Sun",
        "bio": "Afro-fusion from Lagos by way of Enugu. Soft drums, loud feelings.",
        "palette": ("#E8552E", "#2B1B12"),
        "genres": ["Afrofusion", "Soul"],
        "songs": [
            ("Slow Morning", "Written on a Sunday, recorded on a Monday.", 88, "Am"),
            ("Palm Wine Sunday", "For the table that never empties.", 102, "C"),
            ("Lantern", "A small light, carried carefully.", 76, "F"),
        ],
    },
    {
        "handle": "kolarivers",
        "name": "Kola Rivers",
        "stage": "Kola Rivers",
        "bio": "Alté and R&B. Guitars with the reverb left on.",
        "palette": ("#F2A65A", "#3A1D12"),
        "genres": ["Alté", "R&B"],
        "songs": [
            ("Neon Rain", "The city at 2am, in one take.", 94, "Em"),
            ("Blue Hour", "Between the streetlights.", 84, "Dm"),
            ("Gold Teeth", "A smile with a little shine.", 100, "G"),
        ],
    },
    {
        "handle": "paperowls",
        "name": "The Paper Owls",
        "stage": "The Paper Owls",
        "bio": "Four friends, one rehearsal room, a lot of tea.",
        "palette": ("#7FB3A3", "#12211E"),
        "genres": ["Indie", "Folk"],
        "songs": [
            ("Open Window", "Recorded with the window open on purpose.", 118, "G"),
            ("Little Things", "The list keeps getting longer.", 92, "C"),
        ],
    },
    {
        "handle": "ifymoss",
        "name": "Ify Moss",
        "stage": "Ify Moss",
        "bio": "Soul singer. Writes at the piano, finishes in the kitchen.",
        "palette": ("#D9A5B3", "#2A1520"),
        "genres": ["Soul", "R&B"],
        "songs": [
            ("Quiet Part of Town", "Where the noise stops.", 72, "Bb"),
            ("Soft Landing", "For anyone who needed one.", 80, "Eb"),
        ],
    },
    {
        "handle": "tundewaves",
        "name": "Tunde Waves",
        "stage": "Tunde Waves",
        "bio": "Afrobeats with a log drum problem.",
        "palette": ("#8C6CE0", "#161129"),
        "genres": ["Afrobeats", "Amapiano"],
        "songs": [
            ("Midnight Drive", "Third mainland, empty road.", 106, "Fm"),
            ("After the Rain", "Harmattan mornings.", 98, "Ab"),
        ],
    },
    {
        "handle": "lumenchoir",
        "name": "Lumen Choir",
        "stage": "Lumen Choir",
        "bio": "Ambient hymns for people who sing in the car.",
        "palette": ("#E4C05C", "#241B0E"),
        "genres": ["Ambient", "Gospel"],
        "songs": [
            ("Blue Hour II", "The same hour, a different year.", 68, "D"),
            ("Still Water", "Nothing happens. Beautifully.", 64, "A"),
        ],
    },
]

CHORDS = {
    "Am": [57, 60, 64],
    "C": [48, 52, 55],
    "Dm": [50, 53, 57],
    "Em": [52, 55, 59],
    "F": [53, 57, 60],
    "G": [55, 59, 62],
    "A": [57, 61, 64],
    "Bb": [58, 62, 65],
    "Eb": [51, 55, 58],
    "Fm": [53, 56, 60],
    "Ab": [56, 60, 63],
    "D": [50, 54, 57],
}

PROGRESSIONS = {
    "Am": ["Am", "F", "C", "G"],
    "C": ["C", "G", "Am", "F"],
    "F": ["F", "C", "Dm", "Bb"],
    "Em": ["Em", "C", "G", "D"],
    "Dm": ["Dm", "Bb", "F", "C"],
    "G": ["G", "Em", "C", "D"],
    "Bb": ["Bb", "Eb", "F", "G"],
    "Eb": ["Eb", "Bb", "F", "G"],
    "Fm": ["Fm", "Ab", "Eb", "Bb"],
    "Ab": ["Ab", "Eb", "Fm", "Bb"],
    "D": ["D", "A", "G", "Em"],
    "A": ["A", "D", "F", "Em"],
}


def midi_hz(note: int) -> float:
    return 440.0 * (2 ** ((note - 69) / 12))


def png(width: int, height: int, pixels: bytearray) -> bytes:
    """A plain RGB PNG, written without any image library."""
    raw = bytearray()
    stride = width * 3
    for row in range(height):
        raw.append(0)
        raw.extend(pixels[row * stride:(row + 1) * stride])

    def chunk(tag: bytes, data: bytes) -> bytes:
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    header = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header)
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))


def _rgb(value: str) -> tuple[int, int, int]:
    value = value.lstrip("#")
    return int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16)


def artwork(width: int, height: int, palette: tuple[str, str], seed: int) -> bytes:
    """A gradient with a soft highlight and slow bands: reads as a sleeve."""
    top = _rgb(palette[0])
    bottom = _rgb(palette[1])
    glow = _rgb(palette[0])
    pixels = bytearray(width * height * 3)
    stripe = max(2, int(width / (7 + seed % 4)))
    span = width * 0.85
    centre = width * (0.55 if seed % 2 else 0.45)
    delta_r = bottom[0] - top[0]
    delta_g = bottom[1] - top[1]
    delta_b = bottom[2] - top[2]
    for y in range(height):
        fade = y / max(height - 1, 1)
        base_r = top[0] + delta_r * fade
        base_g = top[1] + delta_g * fade
        base_b = top[2] + delta_b * fade
        row = y * width * 3
        for x in range(width):
            # A diagonal highlight, then two-tone bands at very low contrast.
            light = 1.0 - abs(x + y * 0.55 - centre) / span
            if light < 0.0:
                light = 0.0
            elif light > 1.0:
                light = 1.0
            light *= 0.42
            band = 26 if ((x * 2 + y) // stripe) % 2 else 0
            offset = row + x * 3
            pixels[offset] = min(255, max(0, int(base_r + (glow[0] - base_r) * light + band)))
            pixels[offset + 1] = min(255, max(0, int(base_g + (glow[1] - base_g) * light + band)))
            pixels[offset + 2] = min(255, max(0, int(base_b + (glow[2] - base_b) * light + band)))
    return png(width, height, pixels)


def _add(buffer: list[float], start: int, freq: float, seconds: float,
         amp: float, attack: float = 0.02, decay: float = 0.0,
         harmonic: float = 0.0) -> None:
    count = int(seconds * RATE)
    step = freq * TABLE_SIZE / RATE
    phase = 0.0
    for index in range(count):
        position = start + index
        if position >= len(buffer):
            return
        envelope = min(1.0, (index / RATE) / attack) if attack > 0 else 1.0
        if decay:
            envelope *= math.exp(-decay * index / RATE)
        sample = SINE[int(phase) & (TABLE_SIZE - 1)]
        if harmonic:
            sample += harmonic * SINE[int(phase * 2) & (TABLE_SIZE - 1)]
        buffer[position] += amp * envelope * sample
        phase += step


def track(progressions: dict[str, list[int]], tempo: int, seed: int,
          bars: int = 12) -> bytes:
    """Thirty-odd seconds of pad, bass and a melody, in a key that suits it."""
    beat = 60.0 / tempo
    loop = progressions
    chords = [CHORDS[name] for name in loop]
    beats_per_chord = 8
    total_beats = bars * 4
    length = int(total_beats * beat * RATE) + RATE
    buffer = [0.0] * length

    for chord_index in range(bars * 4 // beats_per_chord):
        chord = chords[chord_index % len(chords)]
        start = int(chord_index * beats_per_chord * beat * RATE)
        span = beats_per_chord * beat
        for voice, note in enumerate(chord):
            _add(buffer, start, midi_hz(note + 12), span, 0.075 / (voice + 1),
                 attack=0.35, harmonic=0.25)
        _add(buffer, start, midi_hz(chord[0] - 12), span, 0.16,
             attack=0.05, harmonic=0.1)
        # A plucked melody, one note per beat, from the chord and its neighbours.
        for step in range(beats_per_chord):
            note = chord[(step + seed) % len(chord)] + (12 if step % 3 else 0)
            if step % 4 == 3:
                continue
            _add(buffer, start + int(step * beat * RATE), midi_hz(note + 12),
                 beat * 1.6, 0.085, attack=0.005, decay=3.2, harmonic=0.15)

    # Fade the ends so the loop does not click when it repeats.
    fade = int(0.6 * RATE)
    for index in range(fade):
        buffer[index] *= index / fade
        buffer[length - 1 - index] *= index / fade

    samples = array("h")
    for value in buffer:
        clipped = max(-1.0, min(1.0, value))
        samples.append(int(clipped * 30000))
    sink = BytesIO()
    with wave.open(sink, "wb") as handle:
        handle.setnchannels(1)
        handle.setsampwidth(2)
        handle.setframerate(RATE)
        handle.writeframes(samples.tobytes())
    return sink.getvalue()


def save_media(data: bytes, suffix: str) -> str:
    key, _ = storage.save(BytesIO(data), suffix, 64 * 1024 * 1024)
    return key


def unique_handle(db: Session, base: str) -> str:
    handle = base[:28]
    attempt = 0
    while db.scalar(select(func.count()).select_from(User).where(User.handle == handle)):
        attempt += 1
        handle = f"{base[:24]}{attempt}"
    return handle


def wipe(db: Session) -> tuple[int, int]:
    """Every song, release, post and artist page.

    Accounts are kept, so nobody loses the login they sign in with: the artist
    pages go, the people stay, and anyone can put a page back up afterwards.
    """
    song_count = db.scalar(select(func.count()).select_from(Song)) or 0
    artist_ids = set(db.scalars(select(ArtistProfile.user_id)).all())
    artist_ids |= set(db.scalars(select(User.id).where(User.role == "artist")).all())
    artist_count = len(artist_ids)

    db.execute(delete(PlaylistSong))
    db.execute(delete(Like))
    db.execute(delete(ListeningEvent))
    db.execute(delete(SongFeature))
    db.execute(delete(SongGenre))
    db.execute(delete(ReleaseSong))
    db.execute(delete(Release))
    db.execute(delete(ArtistPost))
    db.execute(delete(ExternalShare))
    db.execute(delete(ModerationAction))
    db.execute(delete(Report))
    db.execute(delete(Song))
    if artist_ids:
        db.execute(delete(Follow).where(or_(Follow.artist_id.in_(artist_ids),
                                            Follow.listener_id.in_(artist_ids))))
    db.execute(delete(ArtistProfile))
    # Their account stays, but nothing about it says artist until they say so.
    db.execute(update(User).where(User.role == "artist").values(role="listener"))
    return song_count, artist_count


def ensure_genres(db: Session, names: list[str]) -> dict[str, Genre]:
    found = {genre.name.lower(): genre for genre in db.scalars(select(Genre)).all()}
    for name in names:
        if name.lower() not in found:
            genre = Genre(name=name)
            db.add(genre)
            db.flush()
            found[name.lower()] = genre
    return found


def seed(db: Session, quick: bool = False) -> dict[str, int]:
    """Insert the demo artists and their songs.

    quick trades art size and track length for speed, which matters when the
    seed runs inside one request rather than on a laptop.
    """
    all_genres = sorted({g for artist in ARTISTS for g in artist["genres"]})
    genres = ensure_genres(db, all_genres)
    listeners = db.scalars(select(User).where(User.role != "artist").limit(5)).all()

    songs_created = 0
    for index, artist in enumerate(ARTISTS):
        palette = artist["palette"]
        avatar = save_media(artwork(320, 320, palette, index), ".png")
        banner = save_media(artwork(960 if not quick else 720, 320 if not quick else 240,
                                     palette, index + 1), ".png")
        handle = unique_handle(db, artist["handle"])
        user = User(
            email=f"{handle}@demo.oru.invalid",
            handle=handle,
            password_hash=hash_password(secrets.token_urlsafe(32)),
            role="artist",
            display_name=artist["name"],
            bio=artist["bio"],
            avatar_key=avatar,
        )
        db.add(user)
        db.flush()
        db.add(ArtistProfile(user_id=user.id, stage_name=artist["stage"],
                             banner_key=banner))
        db.flush()
        db.add(ArtistPost(artist_id=user.id,
                          body=f"{artist['songs'][0][0]} is out now. Turn it up."))

        release_songs: list[Song] = []
        songs = artist["songs"][:2] if quick else artist["songs"]
        for song_index, (title, description, tempo, key) in enumerate(songs):
            progression = PROGRESSIONS.get(key, PROGRESSIONS["C"])
            audio = track({name: CHORDS[name] for name in progression}, tempo,
                          seed=index + song_index, bars=8 if quick else 12)
            cover = artwork(500 if not quick else 380, 500 if not quick else 380,
                            palette, index + song_index * 3)
            song = Song(
                artist_id=user.id,
                title=title,
                description=description,
                audio_key=save_media(audio, ".wav"),
                cover_key=save_media(cover, ".png"),
                audio_type="audio/wav",
                duration_ms=int(len(audio) / 2 / RATE * 1000),
                status="published",
                published_at=datetime.now(timezone.utc)
                - timedelta(days=index, hours=song_index * 5),
            )
            db.add(song)
            db.flush()
            for name in artist["genres"]:
                genre = genres.get(name.lower())
                if genre is not None:
                    db.add(SongGenre(song_id=song.id, genre_id=genre.id))
            for listener in listeners[:3]:
                db.add(ListeningEvent(listener_id=listener.id, song_id=song.id,
                                      listened_ms=song.duration_ms or 30000,
                                      counted=True,
                                      created_at=datetime.now(timezone.utc)
                                      - timedelta(hours=song_index + 1)))
            songs_created += 1
            release_songs.append(song)

        if len(release_songs) >= 2:
            release = Release(
                artist_id=user.id,
                title=f"{artist['stage']} — First Light",
                kind="EP" if len(release_songs) > 2 else "Single",
                status="published",
                published_at=datetime.now(timezone.utc) - timedelta(days=index + 1),
            )
            db.add(release)
            db.flush()
            for position, song in enumerate(release_songs):
                db.add(ReleaseSong(release_id=release.id, song_id=song.id,
                                   position=position))
        db.flush()

    return {"artists": len(ARTISTS), "songs": songs_created}


def already_seeded(db: Session) -> bool:
    """True once the demo artists exist, so a restart never wipes them again."""
    handles = [artist["handle"] for artist in ARTISTS]
    return (db.scalar(select(func.count()).select_from(User)
                      .where(User.handle.in_(handles))) or 0) > 0


def reset_and_seed(force: bool = False, quick: bool = False) -> dict[str, int]:
    """The same reset, callable from a one-off hook rather than a terminal."""
    Base.metadata.create_all(engine)
    with SessionLocal() as db:
        if not force and already_seeded(db):
            print("demo catalogue already in place; leaving it alone")
            return {"artists": 0, "songs": 0, "removed": 0}
        songs, artists = wipe(db)
        print(f"removed {songs} songs and {artists} artist pages")
        result = seed(db, quick=quick)
        # One commit at the end: a failure half way leaves the old catalogue.
        db.commit()
        print(f"seeded {result['artists']} artists and {result['songs']} songs")
        return {"artists": result["artists"], "songs": result["songs"],
                "removed": songs}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--yes", action="store_true",
                        help="actually delete the current catalogue")
    args = parser.parse_args()
    if not args.yes:
        print("This deletes every song and artist. Re-run with --yes.")
        raise SystemExit(2)

    reset_and_seed(force=True)


if __name__ == "__main__":
    main()
