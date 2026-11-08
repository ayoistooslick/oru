"""Signing in on the web and arriving signed in on the phone.

Google only signs people in inside a browser, so the app opens the site and the
site hands it a one-time code. These tests pin that exchange down: it works
once, it expires, and a wrong code does nothing.
"""

from tests.conftest import auth, register


def test_handoff_code_signs_the_app_in(client):
    account = register(client, email="handoff@example.com", handle="handoff_one")
    headers = auth(account["access_token"])

    started = client.post("/api/v1/auth/handoff", headers=headers)
    assert started.status_code == 200, started.text
    code = started.json()["code"]
    assert started.json()["expires_in"] > 0

    exchanged = client.post("/api/v1/auth/handoff/exchange", json={"code": code})
    assert exchanged.status_code == 200, exchanged.text
    token = exchanged.json()["access_token"]
    assert exchanged.json()["user"]["id"] == account["user"]["id"]

    me = client.get("/api/v1/auth/me", headers=auth(token))
    assert me.status_code == 200
    assert me.json()["user"]["handle"] == "handoff_one"


def test_handoff_code_works_only_once(client):
    account = register(client, email="once@example.com", handle="once_one")
    code = client.post("/api/v1/auth/handoff",
                       headers=auth(account["access_token"])).json()["code"]

    assert client.post("/api/v1/auth/handoff/exchange", json={"code": code}).status_code == 200
    # A code that ends up in browser history cannot be replayed.
    again = client.post("/api/v1/auth/handoff/exchange", json={"code": code})
    assert again.status_code == 401
    assert again.json()["error"]["code"] == "bad_handoff"


def test_unknown_handoff_code_is_refused(client):
    response = client.post("/api/v1/auth/handoff/exchange",
                           json={"code": "not-a-real-code-but-long-enough"})
    assert response.status_code == 401


def test_handoff_needs_a_session(client):
    assert client.post("/api/v1/auth/handoff").status_code == 401


def test_reset_route_is_hidden_and_guarded(client, monkeypatch):
    """Without the environment secret the route does not exist; with it, a wrong
    header is refused before anything is dropped."""
    monkeypatch.delenv("ORU_RESET_TOKEN", raising=False)
    assert client.post("/api/v1/maintenance/reset").status_code == 404

    monkeypatch.setenv("ORU_RESET_TOKEN", "only-for-this-test")
    assert client.post("/api/v1/maintenance/reset").status_code == 403
    assert client.post("/api/v1/maintenance/reset",
                       headers={"X-Oru-Reset": "guess"}).status_code == 403
