"""Uploads that live on the media service instead of this server's disk.

Gitty Bank takes one multipart file per request and hands back a public URL,
which becomes the key the database stores. Media responses then redirect to
that URL rather than streaming the file, so the service answers Range requests
(seeking still works) and audio never passes through this API.
"""

import io

import httpx
import pytest
from starlette.requests import Request

import app.storage as storage_module
from app.api.uploads import media_response, save_upload
from app.storage import GittyBankStorage, StorageUnavailable, UploadTooLarge


@pytest.fixture
def bank(monkeypatch):
    monkeypatch.setattr(storage_module.settings, "gitty_bank_api_key", "gbk_test")
    monkeypatch.setattr(storage_module.settings, "gitty_bank_base_url", "https://bank.test")
    return GittyBankStorage()


def _request() -> Request:
    return Request({"type": "http", "method": "GET", "path": "/", "headers": [], "query_string": b""})


def test_upload_sends_the_file_and_keeps_the_url(bank, monkeypatch):
    seen = {}

    def fake_post(url, headers=None, files=None, timeout=None):
        seen.update({"url": url, "headers": headers, "file": files["file"]})
        return httpx.Response(200, json={"id": "med_1", "url": "https://bank.test/f/med_1"})

    monkeypatch.setattr(storage_module.httpx, "post", fake_post)
    key, size = bank.save(io.BytesIO(b"cover-bytes"), ".png", 1024)

    assert key == "https://bank.test/f/med_1"
    assert size == len(b"cover-bytes")
    assert seen["url"] == "https://bank.test/api/v1/upload"
    assert seen["headers"] == {"Authorization": "Bearer gbk_test"}
    name, payload, media_type = seen["file"]
    # The upload is streamed from the file the API handed over, not copied first.
    data = payload.read() if hasattr(payload, "read") else payload
    assert name.endswith(".png") and data == b"cover-bytes" and media_type == "image/png"


def test_upload_falls_back_to_the_file_id_when_no_url_is_returned(bank, monkeypatch):
    monkeypatch.setattr(
        storage_module.httpx, "post",
        lambda *a, **k: httpx.Response(200, json={"id": "med_2"}),
    )
    key, _ = bank.save(io.BytesIO(b"x"), ".mp3", 1024)
    assert key == "https://bank.test/f/med_2"


def test_too_large_is_refused_before_it_leaves(bank, monkeypatch):
    def explode(*args, **kwargs):
        raise AssertionError("nothing should be uploaded")

    monkeypatch.setattr(storage_module.httpx, "post", explode)
    with pytest.raises(UploadTooLarge):
        bank.save(io.BytesIO(b"x" * 32), ".mp3", 16)


def test_service_errors_become_a_readable_failure(bank, monkeypatch):
    monkeypatch.setattr(
        storage_module.httpx, "post",
        lambda *a, **k: httpx.Response(500, text="boom"),
    )
    with pytest.raises(StorageUnavailable):
        bank.save(io.BytesIO(b"x"), ".mp3", 1024)


def test_unreachable_service_is_offered_to_the_client_as_retryable(monkeypatch):
    def down(*args, **kwargs):
        raise StorageUnavailable("connection refused")

    monkeypatch.setattr("app.api.uploads.storage", type("S", (), {"save": staticmethod(down)})())

    class Upload:
        filename = "song.mp3"
        content_type = "audio/mpeg"
        file = io.BytesIO(b"x")

    with pytest.raises(Exception) as caught:
        save_upload(Upload(), {".mp3": {"audio/mpeg"}}, 1024)
    assert getattr(caught.value, "status_code", None) == 503


def test_delete_asks_the_service_to_remove_the_file(bank, monkeypatch):
    seen = {}

    def fake_delete(url, headers=None, timeout=None):
        seen.update({"url": url, "headers": headers})
        return httpx.Response(204)

    monkeypatch.setattr(storage_module.httpx, "delete", fake_delete)
    bank.delete("https://bank.test/f/med_7")

    assert seen["url"] == "https://bank.test/api/v1/files/med_7"
    assert seen["headers"] == {"Authorization": "Bearer gbk_test"}


def test_delete_tolerates_a_refusal_and_an_already_missing_file(bank, monkeypatch):
    codes = iter((403, 404))

    def fake_delete(*args, **kwargs):
        code = next(codes)
        return httpx.Response(code, text='{"detail":"Owner session required for file management"}')

    monkeypatch.setattr(storage_module.httpx, "delete", fake_delete)
    # Neither may raise: the caller has already removed the row.
    bank.delete("https://bank.test/f/med_8")
    bank.delete("https://bank.test/f/med_9")
    # Keys that are not files on the service are left alone, without a request.
    bank.delete("a-local-file.mp3")


def test_a_file_uploaded_on_the_old_host_is_read_back_on_the_new_one(bank):
    # The media service moved hosts; the key stored at upload time still names
    # the host it answered on then.
    old = "https://gitty-bank-api.onrender.com/f/med_1"
    assert bank.url_for(old) == "https://bank.test/f/med_1"
    # A key that is already current, points somewhere else, or is a local file
    # is left exactly as it is.
    assert bank.url_for("https://bank.test/f/med_2") == "https://bank.test/f/med_2"
    assert bank.url_for("https://elsewhere.example/x.jpg") == "https://elsewhere.example/x.jpg"
    assert bank.url_for("a-local-file.mp3") == "a-local-file.mp3"


def test_deleting_an_old_key_asks_the_current_host(bank, monkeypatch):
    seen = {}

    def fake_delete(url, headers=None, timeout=None):
        seen["url"] = url
        return httpx.Response(204)

    monkeypatch.setattr(storage_module.httpx, "delete", fake_delete)
    bank.delete("https://gitty-bank-api.onrender.com/f/med_3")
    assert seen["url"] == "https://bank.test/api/v1/files/med_3"


def test_media_redirects_to_the_file_on_the_service():
    # Whatever the storage this process picked, a key on today's media host is
    # handed to the client unchanged.
    key = f"{storage_module.settings.gitty_bank_base_url.rstrip('/')}/f/med_1"
    response = media_response(key, "audio/mpeg", _request())
    assert response.status_code == 302
    assert response.headers["location"] == key
    assert "max-age" in response.headers["cache-control"]
