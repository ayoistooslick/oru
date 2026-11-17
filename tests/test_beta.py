"""The beta line: ask, be read, and only then be handed a build."""

from app.config import settings
from tests.conftest import auth, register


def test_status_is_empty_before_asking(client):
    token = register(client)["access_token"]
    body = client.get("/api/v1/beta/me", headers=auth(token)).json()
    assert body["status"] == "none"
    assert body["download_available"] is False


def test_applying_queues_a_pending_request(client):
    token = register(client)["access_token"]
    response = client.post("/api/v1/beta/apply", headers=auth(token),
                           json={"note": "I test on a Pixel 6"})
    assert response.status_code == 201, response.text
    body = response.json()
    assert body["applied"] is True
    assert body["status"] == "pending"
    assert body["note"] == "I test on a Pixel 6"

    status = client.get("/api/v1/beta/me", headers=auth(token)).json()
    assert status["status"] == "pending"
    assert status["download_available"] is False


def test_applying_twice_keeps_one_request(client):
    token = register(client)["access_token"]
    client.post("/api/v1/beta/apply", headers=auth(token), json={"note": "first"})
    second = client.post("/api/v1/beta/apply", headers=auth(token),
                         json={"note": "second"})
    assert second.status_code == 201
    assert second.json()["note"] == "second"


def test_download_is_refused_until_approved(client, monkeypatch):
    monkeypatch.setattr(settings, "admin_emails", "beta-mod@example.com")
    applicant = register(client, email="tester@example.com", handle="tester")
    token = applicant["access_token"]
    client.post("/api/v1/beta/apply", headers=auth(token), json={"note": "please"})

    refused = client.get("/api/v1/beta/download", headers=auth(token))
    assert refused.status_code == 403
    assert refused.json()["error"]["code"] == "beta_not_approved"


def test_moderator_approves_and_the_build_opens(client, monkeypatch):
    monkeypatch.setattr(settings, "admin_emails", "beta-mod@example.com")
    applicant = register(client, email="tester@example.com", handle="tester")
    moderator = register(client, email="beta-mod@example.com", handle="mod")
    assert moderator["user"]["role"] == "admin"
    token = applicant["access_token"]
    client.post("/api/v1/beta/apply", headers=auth(token), json={"note": "please"})

    queue = client.get("/api/v1/moderation/beta",
                       headers=auth(moderator["access_token"])).json()["items"]
    assert len(queue) == 1
    assert queue[0]["handle"] == "tester"
    # The queue is for a human to read; the note and the address travel with it.
    assert queue[0]["note"] == "please"
    assert queue[0]["email"] == "tester@example.com"

    decision = client.post(
        f"/api/v1/moderation/beta/{queue[0]['id']}/decision",
        headers=auth(moderator["access_token"]),
        json={"approve": True, "note": "Good tester"})
    assert decision.status_code == 200
    assert decision.json()["status"] == "approved"

    status = client.get("/api/v1/beta/me", headers=auth(token)).json()
    assert status["status"] == "approved"
    assert status["download_available"] is True

    download = client.get("/api/v1/beta/download", headers=auth(token),
                          follow_redirects=False)
    assert download.status_code == 302
    assert download.headers["location"].endswith("oru-beta.apk")


def test_declined_request_can_be_made_again(client, monkeypatch):
    monkeypatch.setattr(settings, "admin_emails", "beta-mod@example.com")
    applicant = register(client, email="tester@example.com", handle="tester")
    moderator = register(client, email="beta-mod@example.com", handle="mod")
    token = applicant["access_token"]
    client.post("/api/v1/beta/apply", headers=auth(token), json={"note": "first"})
    queue = client.get("/api/v1/moderation/beta",
                       headers=auth(moderator["access_token"])).json()["items"]
    client.post(f"/api/v1/moderation/beta/{queue[0]['id']}/decision",
                headers=auth(moderator["access_token"]),
                json={"approve": False, "note": "Not yet"})

    declined = client.get("/api/v1/beta/me", headers=auth(token)).json()
    assert declined["status"] == "declined"
    assert declined["download_available"] is False

    again = client.post("/api/v1/beta/apply", headers=auth(token),
                        json={"note": "second try"}).json()
    assert again["status"] == "pending"
    assert again["review_note"] == ""


def test_only_moderators_see_the_queue(client):
    token = register(client)["access_token"]
    assert client.get("/api/v1/moderation/beta",
                      headers=auth(token)).status_code == 403
