import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from app.database import Base, get_db
from app.main import app, api
from app.storage import LocalStorage
import app.api.music as music_api
import app.api.uploads as uploads_api
import app.api.accounts as accounts_api
import app.storage as storage_module


@pytest.fixture
def client(tmp_path, monkeypatch):
    engine = create_engine("sqlite://", connect_args={"check_same_thread": False}, poolclass=StaticPool)
    TestingSession = sessionmaker(bind=engine, autoflush=False, expire_on_commit=False)
    Base.metadata.create_all(engine)

    def test_db():
        db = TestingSession()
        try:
            yield db
        finally:
            db.close()

    app.dependency_overrides[get_db] = test_db
    api.dependency_overrides[get_db] = test_db
    # Every module holds its own reference to the storage object, so a test that
    # only patches one of them silently writes to the real bucket.
    local = LocalStorage(str(tmp_path / "media"))
    monkeypatch.setattr(music_api, "storage", local)
    monkeypatch.setattr(uploads_api, "storage", local)
    monkeypatch.setattr(accounts_api, "storage", local)
    monkeypatch.setattr(storage_module, "storage", local)
    with TestClient(app) as test_client:
        stack = app.middleware_stack
        while stack is not None:
            if hasattr(stack, "requests") and isinstance(stack.requests, dict):
                stack.requests.clear()
            stack = getattr(stack, "app", None)
        yield test_client
    app.dependency_overrides.clear()
    api.dependency_overrides.clear()
    Base.metadata.drop_all(engine)
    engine.dispose()


def register(client, email="listener@example.com", handle="listener_one", password="a-long-test-password"):
    response = client.post("/api/v1/auth/register", json={"email": email, "handle": handle, "display_name": handle, "password": password})
    assert response.status_code == 201, response.text
    return response.json()


def auth(token):
    return {"Authorization": f"Bearer {token}"}
