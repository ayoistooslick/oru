"""New drops: who hears about them, once, and how they turn them off."""

from tests.conftest import auth, register
from tests.test_api import make_artist, upload_track


def _fan(client, artist, email="fan@example.com", handle="fan_one"):
    account = register(client, email=email, handle=handle)
    followed = client.post(f"/api/v1/artists/{artist['user']['id']}/follow", headers=auth(account["access_token"]))
    assert followed.status_code == 201, followed.text
    return account


def _notifications(client, token):
    response = client.get("/api/v1/notifications", headers=auth(token))
    assert response.status_code == 200, response.text
    return response.json()


def test_followers_hear_a_new_song_once(client):
    artist = make_artist(client)
    artist_token = auth(artist["access_token"])
    fan = _fan(client, artist)
    fan_token = auth(fan["access_token"])

    song_id = upload_track(client, artist["access_token"], title="First light")
    # A draft is not a drop.
    assert _notifications(client, fan["access_token"])["unread_count"] == 0

    published = client.post(f"/api/v1/artists/me/songs/{song_id}/publish", headers=artist_token)
    assert published.status_code == 200, published.text

    inbox = _notifications(client, fan["access_token"])
    assert inbox["unread_count"] == 1
    note = inbox["items"][0]
    assert note["kind"] == "new_song"
    assert note["href"] == f"/song/{song_id}"
    assert note["song"]["title"] == "First light"
    assert note["artist"]["name"] == f"Stage {artist['user']['handle']}"
    assert note["read_at"] is None

    # Publishing the same song again must not ring the bell twice.
    client.post(f"/api/v1/artists/me/songs/{song_id}/publish", headers=artist_token)
    assert _notifications(client, fan["access_token"])["unread_count"] == 1

    # The artist is not their own audience.
    assert _notifications(client, artist["access_token"])["unread_count"] == 0


def test_reading_and_reading_all(client):
    artist = make_artist(client)
    fan = _fan(client, artist)
    fan_token = auth(fan["access_token"])

    for title in ("One", "Two"):
        song_id = upload_track(client, artist["access_token"], title=title)
        client.post(f"/api/v1/artists/me/songs/{song_id}/publish", headers=auth(artist["access_token"]))

    inbox = _notifications(client, fan["access_token"])
    assert inbox["unread_count"] == 2

    # Someone else's notice is not readable.
    stranger = register(client, email="stranger@example.com", handle="stranger_one")
    denied = client.post(f"/api/v1/notifications/{inbox['items'][0]['id']}/read", headers=auth(stranger["access_token"]))
    assert denied.status_code == 404

    read = client.post(f"/api/v1/notifications/{inbox['items'][0]['id']}/read", headers=fan_token)
    assert read.status_code == 200
    assert read.json()["unread_count"] == 1

    marked = client.post("/api/v1/notifications/read-all", headers=fan_token)
    assert marked.json()["unread_count"] == 0
    assert _notifications(client, fan["access_token"])["unread_count"] == 0


def test_the_switch_quiets_future_drops_only(client):
    artist = make_artist(client)
    fan = _fan(client, artist)
    fan_token = auth(fan["access_token"])

    first = upload_track(client, artist["access_token"], title="Before the switch")
    client.post(f"/api/v1/artists/me/songs/{first}/publish", headers=auth(artist["access_token"]))
    assert _notifications(client, fan["access_token"])["unread_count"] == 1

    off = client.patch("/api/v1/notifications/settings", headers=fan_token, json={"enabled": False})
    assert off.status_code == 200, off.text
    assert off.json()["notify_new_drops"] is False
    assert client.get("/api/v1/auth/me", headers=fan_token).json()["user"]["notify_new_drops"] is False

    second = upload_track(client, artist["access_token"], title="After the switch")
    client.post(f"/api/v1/artists/me/songs/{second}/publish", headers=auth(artist["access_token"]))
    inbox = _notifications(client, fan["access_token"])
    assert inbox["unread_count"] == 1
    assert len(inbox["items"]) == 1

    back_on = client.patch("/api/v1/notifications/settings", headers=fan_token, json={"enabled": True})
    assert back_on.json()["notify_new_drops"] is True


def test_unfollowing_stops_the_notices(client):
    artist = make_artist(client)
    fan = _fan(client, artist)
    client.delete(f"/api/v1/artists/{artist['user']['id']}/follow", headers=auth(fan["access_token"]))

    song_id = upload_track(client, artist["access_token"], title="Nobody waiting")
    client.post(f"/api/v1/artists/me/songs/{song_id}/publish", headers=auth(artist["access_token"]))
    assert _notifications(client, fan["access_token"])["unread_count"] == 0


def test_a_release_is_one_notice(client):
    artist = make_artist(client)
    fan = _fan(client, artist)
    artist_token = auth(artist["access_token"])

    song_ids = [upload_track(client, artist["access_token"], title=f"Track {index}") for index in range(2)]
    created = client.post(
        "/api/v1/artists/me/releases",
        headers=artist_token,
        json={"title": "Two songs", "kind": "ep", "song_ids": song_ids},
    )
    assert created.status_code == 201, created.text
    release_id = created.json()["release"]["id"]

    published = client.post(f"/api/v1/artists/me/releases/{release_id}/publish", headers=artist_token)
    assert published.status_code == 200, published.text

    inbox = _notifications(client, fan["access_token"])
    assert inbox["unread_count"] == 1
    note = inbox["items"][0]
    assert note["kind"] == "new_release"
    assert note["release"]["id"] == release_id
    assert note["href"] == f"/release/{release_id}"
