"""Seeking, pictures, features and the weekly chart.

These cover the parts of the product that broke or arrived in this update, so a
regression shows up here rather than in production.
"""

from tests.conftest import auth, register
from tests.test_api import make_artist, upload_track

PNG = b"\x89PNG\r\n\x1a\n" + b"0" * 64
JPEG = b"\xff\xd8\xff" + b"1" * 64


def test_audio_supports_range_requests(client):
    """The player drags to a minute by asking for that byte range."""
    artist = make_artist(client)
    token = auth(artist["access_token"])
    content = bytes(range(256)) * 8
    song_id = upload_track(client, artist["access_token"], content=content)
    client.post(f"/api/v1/artists/me/songs/{song_id}/publish", headers=token)

    full = client.get(f"/api/v1/songs/{song_id}/audio")
    assert full.status_code == 200
    assert full.headers["accept-ranges"] == "bytes"
    assert full.content == content

    part = client.get(f"/api/v1/songs/{song_id}/audio", headers={"Range": "bytes=100-199"})
    assert part.status_code == 206
    assert part.headers["content-range"] == f"bytes 100-199/{len(content)}"
    assert part.content == content[100:200]

    tail = client.get(f"/api/v1/songs/{song_id}/audio", headers={"Range": "bytes=2000-"})
    assert tail.status_code == 206
    assert tail.content == content[2000:]

    suffix = client.get(f"/api/v1/songs/{song_id}/audio", headers={"Range": "bytes=-100"})
    assert suffix.status_code == 206
    assert suffix.content == content[-100:]


def test_profile_picture_and_artist_banner(client):
    artist = make_artist(client)
    token = auth(artist["access_token"])
    assert artist["user"]["avatar_url"] is None

    uploaded = client.post("/api/v1/listeners/me/avatar", headers=token, files={"file": ("me.png", PNG, "image/png")})
    assert uploaded.status_code == 200, uploaded.text
    avatar_url = uploaded.json()["user"]["avatar_url"]
    assert avatar_url
    picture = client.get(avatar_url)
    assert picture.status_code == 200
    assert picture.content == PNG

    banner = client.post("/api/v1/artists/me/banner", headers=token, files={"file": ("banner.jpg", JPEG, "image/jpeg")})
    assert banner.status_code == 200, banner.text
    banner_url = banner.json()["artist"]["banner_url"]
    assert banner_url
    assert client.get(banner_url).status_code == 200
    assert client.get(f"/api/v1/artists/{artist['user']['id']}").json()["banner_url"] == banner_url

    # A listener has no artist page to hang a banner on.
    listener = register(client, "plain@example.com", "plain_listener")
    refused = client.post("/api/v1/artists/me/banner", headers=auth(listener["access_token"]), files={"file": ("top.jpg", JPEG, "image/jpeg")})
    assert refused.status_code == 403
    assert refused.json()["error"]["code"] == "artist_profile_required"

    cleared = client.delete("/api/v1/listeners/me/avatar", headers=token)
    assert cleared.json()["user"]["avatar_url"] is None
    assert client.get(avatar_url).status_code == 404

    # Pictures and audio share the same storage rules.
    wrong = client.post("/api/v1/listeners/me/avatar", headers=token, files={"file": ("me.gif", b"GIF89a", "image/gif")})
    assert wrong.status_code == 415


def test_featured_artists_are_credited_and_listed(client):
    lead = make_artist(client, "lead@example.com", "lead_artist")
    guest = make_artist(client, "guest@example.com", "guest_artist")
    guest_id = guest["user"]["id"]

    created = client.post(
        "/api/v1/artists/me/songs",
        headers=auth(lead["access_token"]),
        data={"title": "Together", "featured": str(guest_id)},
        files={"audio": ("together.mp3", b"audio" * 40, "audio/mpeg")},
    )
    assert created.status_code == 201, created.text
    song = created.json()["song"]
    assert [person["name"] for person in song["featuring"]] == ["Stage guest_artist"]

    client.post(f"/api/v1/artists/me/songs/{song['id']}/publish", headers=auth(lead["access_token"]))
    public = client.get(f"/api/v1/songs/{song['id']}").json()["song"]
    assert public["featuring"][0]["handle"] == "guest_artist"
    assert public["featuring"][0]["artist_id"] == guest_id

    appearances = client.get(f"/api/v1/artists/{guest_id}/appears-on").json()["items"]
    assert [item["id"] for item in appearances] == [song["id"]]

    stranger = client.post(
        "/api/v1/artists/me/songs",
        headers=auth(lead["access_token"]),
        data={"title": "Ghost feature", "featured": "9999"},
        files={"audio": ("ghost.mp3", b"audio" * 40, "audio/mpeg")},
    )
    assert stranger.status_code == 404
    assert stranger.json()["error"]["code"] == "artist_not_found"


def test_weekly_rankings_and_artist_stats(client):
    artist = make_artist(client)
    token = auth(artist["access_token"])
    song_id = upload_track(client, artist["access_token"], title="Chart song")
    client.post(f"/api/v1/artists/me/songs/{song_id}/publish", headers=token)

    listener = register(client, "topfan@example.com", "top_fan")
    client.post(f"/api/v1/songs/{song_id}/listen", headers=auth(listener["access_token"]), json={"listened_ms": 40_000})

    rankings = client.get("/api/v1/rankings/weekly?limit=5").json()
    assert rankings["window_days"] == 7
    assert rankings["items"][0]["rank"] == 1
    assert rankings["items"][0]["song"]["id"] == song_id
    assert rankings["items"][0]["plays"] == 1

    stats = client.get("/api/v1/artists/me/stats", headers=token).json()
    assert stats["weekly_plays"] == 1
    assert stats["weekly_top_song"]["id"] == song_id
    assert stats["window_days"] == 7
