from app.config import settings
from tests.conftest import auth, register


def make_artist(client, email="artist@example.com", handle="artist_one"):
    account = register(client, email=email, handle=handle)
    token = account["access_token"]
    response = client.post("/api/v1/artists/me", json={"stage_name": f"Stage {handle}"}, headers=auth(token))
    assert response.status_code == 201, response.text
    return account


def upload_track(client, token, title="New track", content=b"fake mp3 bytes"):
    response = client.post("/api/v1/artists/me/songs", headers=auth(token), data={"title": title, "genres": "ambient"}, files={"audio": ("song.mp3", content, "audio/mpeg")})
    assert response.status_code == 201, response.text
    return response.json()["song"]["id"]


def test_register_login_and_profile(client):
    account = register(client)
    token = account["access_token"]
    assert account["user"]["role"] == "listener"
    assert client.get("/api/v1/auth/me", headers=auth(token)).status_code == 200
    updated = client.patch("/api/v1/listeners/me", headers=auth(token), json={"bio": "I find new music"})
    assert updated.status_code == 200
    assert updated.json()["user"]["bio"] == "I find new music"
    login = client.post("/api/v1/auth/login", json={"email": "listener@example.com", "password": "a-long-test-password"})
    assert login.status_code == 200
    assert client.post("/api/v1/auth/login", json={"email": "listener@example.com", "password": "wrong-password"}).status_code == 401


def test_duplicate_account_returns_conflict(client):
    register(client)
    duplicate = client.post("/api/v1/auth/register", json={"email": "listener@example.com", "handle": "another_handle", "display_name": "Second", "password": "a-long-test-password"})
    assert duplicate.status_code == 409
    assert duplicate.json()["error"]["code"] == "account_exists"


def test_artist_permissions_upload_publish_and_stream(client):
    listener = register(client)
    refused = client.post("/api/v1/artists/me/songs", headers=auth(listener["access_token"]), data={"title": "Nope"}, files={"audio": ("song.mp3", b"audio", "audio/mpeg")})
    assert refused.status_code == 403

    artist = make_artist(client)
    song_id = upload_track(client, artist["access_token"])
    assert client.get(f"/api/v1/songs/{song_id}").status_code == 404
    assert client.get(f"/api/v1/songs/{song_id}/audio", headers=auth(artist["access_token"])).status_code == 200

    other_artist = make_artist(client, "other@example.com", "artist_two")
    forbidden = client.post(f"/api/v1/artists/me/songs/{song_id}/publish", headers=auth(other_artist["access_token"]))
    assert forbidden.status_code == 404
    assert client.post(f"/api/v1/artists/me/songs/{song_id}/publish", headers=auth(artist["access_token"])).status_code == 200
    assert client.get(f"/api/v1/songs/{song_id}").status_code == 200
    assert client.get(f"/api/v1/songs/{song_id}/audio").status_code == 200


def test_upload_rejects_wrong_file_type(client):
    artist = make_artist(client)
    response = client.post("/api/v1/artists/me/songs", headers=auth(artist["access_token"]), data={"title": "Bad file"}, files={"audio": ("payload.exe", b"not audio", "application/octet-stream")})
    assert response.status_code == 415
    assert response.json()["error"]["code"] == "unsupported_file"


def test_discovery_and_listen_counting(client):
    artist = make_artist(client)
    song_id = upload_track(client, artist["access_token"])
    client.post(f"/api/v1/artists/me/songs/{song_id}/publish", headers=auth(artist["access_token"]))
    listener = register(client, "fan@example.com", "fan_listener")
    results = client.get("/api/v1/discover?genre=ambient")
    assert results.status_code == 200
    assert results.json()["items"][0]["id"] == song_id
    first = client.post(f"/api/v1/songs/{song_id}/listen", headers=auth(listener["access_token"]), json={"listened_ms": 45000})
    second = client.post(f"/api/v1/songs/{song_id}/listen", headers=auth(listener["access_token"]), json={"listened_ms": 45000})
    assert first.json()["counted_for_discovery"] is True
    assert second.json()["counted_for_discovery"] is False


def test_reports_require_moderator_and_record_decision(client, monkeypatch):
    artist = make_artist(client)
    song_id = upload_track(client, artist["access_token"])
    client.post(f"/api/v1/artists/me/songs/{song_id}/publish", headers=auth(artist["access_token"]))
    reporter = register(client, "reporter@example.com", "reporter")
    report = client.post("/api/v1/reports", headers=auth(reporter["access_token"]), json={"target_type": "song", "target_id": song_id, "reason": "copyright", "details": "I own this work"})
    assert report.status_code == 201
    assert client.get("/api/v1/moderation/reports").status_code == 401
    monkeypatch.setattr(settings, "admin_emails", "moderator@example.com")
    moderator = register(client, "moderator@example.com", "moderator")
    assert moderator["user"]["role"] == "admin"
    decision = client.post(f"/api/v1/moderation/reports/{report.json()['report']['id']}/decision", headers=auth(moderator["access_token"]), json={"action": "hide", "reason": "Pending review"})
    assert decision.status_code == 200
    assert client.get(f"/api/v1/songs/{song_id}").status_code == 404


def test_releases_following_feed_and_private_playlist_permissions(client):
    artist = make_artist(client)
    song_id = upload_track(client, artist["access_token"], title="A release track")
    release = client.post("/api/v1/artists/me/releases", headers=auth(artist["access_token"]), json={"title": "First EP", "kind": "single", "song_ids": [song_id]})
    assert release.status_code == 201, release.text
    release_id = release.json()["release"]["id"]
    assert client.post(f"/api/v1/artists/me/releases/{release_id}/publish", headers=auth(artist["access_token"])).status_code == 200
    assert client.get(f"/api/v1/releases/{release_id}").json()["release"]["songs"][0]["id"] == song_id

    listener = register(client, "follower@example.com", "follower")
    token = listener["access_token"]
    assert client.post(f"/api/v1/artists/{listener['user']['id']}/follow", headers=auth(token)).status_code == 404
    assert client.post(f"/api/v1/artists/{artist['user']['id']}/follow", headers=auth(token)).status_code == 201
    assert client.get("/api/v1/feed/following", headers=auth(token)).json()["items"][0]["id"] == song_id

    playlist = client.post("/api/v1/playlists", headers=auth(token), json={"title": "Private picks", "is_public": False})
    playlist_id = playlist.json()["playlist"]["id"]
    assert client.post(f"/api/v1/playlists/{playlist_id}/songs/{song_id}", headers=auth(token)).status_code == 201
    assert client.get(f"/api/v1/playlists/{playlist_id}").status_code == 404
    assert client.get(f"/api/v1/playlists/{playlist_id}", headers=auth(token)).status_code == 200
    assert client.patch(f"/api/v1/playlists/{playlist_id}", headers=auth(artist["access_token"]), json={"title": "Not yours"}).status_code == 404


def test_artist_workspace_and_library_endpoints(client):
    artist = make_artist(client)
    token = artist["access_token"]
    song_id = upload_track(client, token, title="Workspace track")
    client.post(f"/api/v1/artists/me/songs/{song_id}/publish", headers=auth(token))

    # /artists/me/... routes must not be swallowed by /artists/{artist_id}/...
    own_songs = client.get("/api/v1/artists/me/songs", headers=auth(token))
    assert own_songs.status_code == 200, own_songs.text
    assert own_songs.json()["items"][0]["audio_url"] is not None
    assert client.get("/api/v1/artists/me/releases", headers=auth(token)).status_code == 200
    assert client.get("/api/v1/artists/me/posts", headers=auth(token)).status_code == 200

    public_tracks = client.get(f"/api/v1/artists/{artist['user']['id']}/songs")
    assert [item["id"] for item in public_tracks.json()["items"]] == [song_id]
    genres = client.get("/api/v1/genres")
    assert "ambient" in [item["name"] for item in genres.json()["items"]]

    listener = register(client, "library@example.com", "library_listener")
    list_token = listener["access_token"]
    client.post(f"/api/v1/songs/{song_id}/likes", headers=auth(list_token))
    client.post(f"/api/v1/artists/{artist['user']['id']}/follow", headers=auth(list_token))
    client.post("/api/v1/playlists", headers=auth(list_token), json={"title": "Night drives", "is_public": True})

    likes = client.get("/api/v1/listeners/me/likes", headers=auth(list_token))
    assert likes.status_code == 200
    assert song_id in likes.json()["liked_song_ids"]
    following = client.get("/api/v1/listeners/me/following", headers=auth(list_token))
    assert artist["user"]["id"] in following.json()["followed_artist_ids"]
    playlists = client.get("/api/v1/playlists", headers=auth(list_token))
    assert playlists.json()["items"][0]["song_count"] == 0

    # The owner keeps streaming access to a draft they have not published yet.
    draft = upload_track(client, token, title="Untouched draft")
    draft_view = client.get(f"/api/v1/songs/{draft}", headers=auth(token))
    assert draft_view.status_code == 200
    assert draft_view.json()["song"]["audio_url"] is not None
    assert client.get(f"/api/v1/songs/{draft}").status_code == 404

    removed = client.delete(f"/api/v1/artists/me/songs/{draft}", headers=auth(token))
    assert removed.status_code == 200
    assert client.get(f"/api/v1/songs/{draft}", headers=auth(token)).status_code == 404


def test_health_check(client):
    assert client.get("/health").json()["status"] == "ok"
