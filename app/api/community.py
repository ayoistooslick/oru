from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.database import get_db
from app.dependencies import current_user, optional_current_user
from app.models import ArtistPost, ArtistProfile, Follow, Like, Playlist, PlaylistSong, Song, User
from app.schemas import ArtistPostCreate, PlaylistCreate, PlaylistUpdate
from app.api.music import song_data

router = APIRouter(tags=["community"])


def owned_playlist(playlist_id: int, user: User, db: Session) -> Playlist:
    playlist = db.get(Playlist, playlist_id)
    if playlist is None or playlist.owner_id != user.id:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Playlist not found"})
    return playlist


@router.post("/artists/{artist_id}/follow", status_code=201)
def follow_artist(artist_id: int, user: User = Depends(current_user), db: Session = Depends(get_db)):
    artist = db.get(User, artist_id)
    if artist is None or artist.artist_profile is None or artist.id == user.id:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Artist not found"})
    db.merge(Follow(listener_id=user.id, artist_id=artist.id))
    db.commit()
    return {"artist_id": artist.id, "following": True}


@router.delete("/artists/{artist_id}/follow")
def unfollow_artist(artist_id: int, user: User = Depends(current_user), db: Session = Depends(get_db)):
    relation = db.get(Follow, (user.id, artist_id))
    if relation:
        db.delete(relation)
        db.commit()
    return {"artist_id": artist_id, "following": False}


@router.post("/songs/{song_id}/likes", status_code=201)
def like_song(song_id: int, user: User = Depends(current_user), db: Session = Depends(get_db)):
    song = db.get(Song, song_id)
    if song is None or song.status != "published":
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Song not found"})
    db.merge(Like(user_id=user.id, song_id=song_id))
    db.commit()
    return {"song_id": song_id, "liked": True}


@router.delete("/songs/{song_id}/likes")
def unlike_song(song_id: int, user: User = Depends(current_user), db: Session = Depends(get_db)):
    relation = db.get(Like, (user.id, song_id))
    if relation:
        db.delete(relation)
        db.commit()
    return {"song_id": song_id, "liked": False}


@router.post("/playlists", status_code=201)
def create_playlist(payload: PlaylistCreate, user: User = Depends(current_user), db: Session = Depends(get_db)):
    playlist = Playlist(owner_id=user.id, title=payload.title.strip(), is_public=payload.is_public)
    db.add(playlist)
    db.commit()
    db.refresh(playlist)
    return {"playlist": {"id": playlist.id, "title": playlist.title, "is_public": playlist.is_public, "owner_id": user.id, "song_ids": []}}


@router.get("/playlists")
def list_own_playlists(user: User = Depends(current_user), db: Session = Depends(get_db)):
    playlists = db.scalars(select(Playlist).where(Playlist.owner_id == user.id).order_by(Playlist.created_at.desc())).all()
    items = []
    for playlist in playlists:
        count = db.scalar(select(func.count(PlaylistSong.song_id)).where(PlaylistSong.playlist_id == playlist.id)) or 0
        items.append({"id": playlist.id, "title": playlist.title, "is_public": playlist.is_public,
                      "owner_id": playlist.owner_id, "song_count": count, "created_at": playlist.created_at})
    return {"items": items, "limit": len(items), "offset": 0}


@router.get("/listeners/me/likes")
def liked_songs(limit: int = 50, offset: int = 0, user: User = Depends(current_user), db: Session = Depends(get_db)):
    safe_limit = min(max(limit, 1), 50)
    query = (
        select(Song).join(Like, Like.song_id == Song.id)
        .where(Like.user_id == user.id, Song.status == "published")
        .order_by(Like.created_at.desc())
        .offset(max(offset, 0)).limit(safe_limit)
    )
    songs = db.scalars(query).unique().all()
    return {"items": [song_data(song, db, user) for song in songs], "limit": safe_limit, "offset": max(offset, 0),
            "liked_song_ids": db.scalars(select(Like.song_id).where(Like.user_id == user.id)).all()}


@router.get("/listeners/me/following")
def followed_artists(limit: int = 50, offset: int = 0, user: User = Depends(current_user), db: Session = Depends(get_db)):
    safe_limit = min(max(limit, 1), 50)
    query = (
        select(User).join(Follow, Follow.artist_id == User.id)
        .where(Follow.listener_id == user.id, User.is_active.is_(True))
        .order_by(Follow.created_at.desc())
        .offset(max(offset, 0)).limit(safe_limit)
    )
    artists = db.scalars(query).all()
    return {"items": [{"user_id": artist.id, "handle": artist.handle,
                       "stage_name": artist.artist_profile.stage_name if artist.artist_profile else artist.display_name,
                       "bio": artist.bio} for artist in artists],
            "limit": safe_limit, "offset": max(offset, 0),
            "followed_artist_ids": db.scalars(select(Follow.artist_id).where(Follow.listener_id == user.id)).all()}


@router.get("/playlists/{playlist_id}")
def get_playlist(playlist_id: int, user: User | None = Depends(optional_current_user), db: Session = Depends(get_db)):
    playlist = db.get(Playlist, playlist_id)
    if playlist is None or (not playlist.is_public and (user is None or user.id != playlist.owner_id)):
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Playlist not found"})
    entries = db.scalars(select(PlaylistSong).where(PlaylistSong.playlist_id == playlist.id).order_by(PlaylistSong.position)).all()
    songs = []
    for entry in entries:
        song = db.get(Song, entry.song_id)
        if song and song.status == "published":
            songs.append({"position": entry.position, **song_data(song, db, user)})
    return {"playlist": {"id": playlist.id, "title": playlist.title, "is_public": playlist.is_public, "owner_id": playlist.owner_id, "songs": songs}}


@router.patch("/playlists/{playlist_id}")
def update_playlist(playlist_id: int, payload: PlaylistUpdate, user: User = Depends(current_user), db: Session = Depends(get_db)):
    playlist = owned_playlist(playlist_id, user, db)
    if payload.title is not None:
        playlist.title = payload.title.strip()
    if payload.is_public is not None:
        playlist.is_public = payload.is_public
    db.commit()
    return {"playlist": {"id": playlist.id, "title": playlist.title, "is_public": playlist.is_public}}


@router.post("/playlists/{playlist_id}/songs/{song_id}", status_code=201)
def add_playlist_song(playlist_id: int, song_id: int, user: User = Depends(current_user), db: Session = Depends(get_db)):
    playlist = owned_playlist(playlist_id, user, db)
    song = db.get(Song, song_id)
    if song is None or song.status != "published":
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Song not found"})
    if db.get(PlaylistSong, (playlist.id, song.id)):
        return {"playlist_id": playlist.id, "song_id": song.id, "added": True}
    position = (db.scalar(select(func.max(PlaylistSong.position)).where(PlaylistSong.playlist_id == playlist.id)) or 0) + 1
    db.add(PlaylistSong(playlist_id=playlist.id, song_id=song.id, position=position))
    db.commit()
    return {"playlist_id": playlist.id, "song_id": song.id, "added": True}


@router.delete("/playlists/{playlist_id}/songs/{song_id}")
def remove_playlist_song(playlist_id: int, song_id: int, user: User = Depends(current_user), db: Session = Depends(get_db)):
    playlist = owned_playlist(playlist_id, user, db)
    entry = db.get(PlaylistSong, (playlist.id, song_id))
    if entry:
        db.delete(entry)
        db.commit()
    return {"playlist_id": playlist.id, "song_id": song_id, "added": False}


@router.post("/artists/me/posts", status_code=201)
def create_post(payload: ArtistPostCreate, user: User = Depends(current_user), db: Session = Depends(get_db)):
    if user.artist_profile is None:
        raise HTTPException(status_code=403, detail={"code": "artist_only", "message": "Create an artist profile first"})
    post = ArtistPost(artist_id=user.id, body=payload.body.strip())
    db.add(post)
    db.commit()
    db.refresh(post)
    return {"post": {"id": post.id, "artist_id": user.id, "body": post.body, "created_at": post.created_at}}


@router.get("/artists/me/posts")
def own_posts(limit: int = 50, offset: int = 0, user: User = Depends(current_user), db: Session = Depends(get_db)):
    if user.artist_profile is None:
        raise HTTPException(status_code=403, detail={"code": "artist_only", "message": "Create an artist profile first"})
    posts = db.scalars(select(ArtistPost).where(ArtistPost.artist_id == user.id).order_by(ArtistPost.created_at.desc()).offset(max(offset, 0)).limit(min(max(limit, 1), 50))).all()
    return {"items": [{"id": post.id, "artist_id": post.artist_id, "body": post.body, "is_hidden": post.is_hidden, "created_at": post.created_at} for post in posts],
            "limit": min(max(limit, 1), 50), "offset": max(offset, 0)}


@router.get("/artists/{artist_id}/posts")
def list_posts(artist_id: int, limit: int = 20, offset: int = 0, db: Session = Depends(get_db)):
    if db.get(ArtistProfile, artist_id) is None:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Artist not found"})
    posts = db.scalars(select(ArtistPost).where(ArtistPost.artist_id == artist_id, ArtistPost.is_hidden.is_(False)).order_by(ArtistPost.created_at.desc()).offset(max(offset, 0)).limit(min(max(limit, 1), 50))).all()
    return {"items": [{"id": post.id, "artist_id": post.artist_id, "body": post.body, "created_at": post.created_at} for post in posts], "limit": min(max(limit, 1), 50), "offset": max(offset, 0)}
