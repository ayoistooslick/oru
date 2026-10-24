# ORU backend

ORU is a music platform for listeners to find music and for newer artists to find an audience. This repository contains its first backend API. The code is a modular monolith: one FastAPI app with a relational database and a separate media-storage adapter. That keeps local development simple without locking the music code to a storage vendor.

## What's here now

The API has account registration and sign-in, listener profile edits, artist profiles, track uploads, draft publishing, releases, discovery, search, likes, playlists, follows, artist posts, listening events, basic artist stats, reports and moderator decisions. The exact route list and example requests are in [the API guide](docs/API.md).

Discovery uses a small, explainable score based on likes, qualified listens, recency, whether the listener follows the artist, and a modest opportunity boost for artists with smaller catalogs. It is a starting point, not a fairness guarantee or a tested anti-fraud system. Listen events count for ranking only after 30 seconds, and another qualifying play from the same account for the same song is ignored for 30 minutes.

## Stack

- Python 3.11 or newer and FastAPI for the HTTP API and generated OpenAPI docs.
- SQLAlchemy and Alembic for relational data and database migrations.
- SQLite by default for a quick local start. Set `DATABASE_URL` to PostgreSQL for a shared or production database.
- Argon2 password hashing and signed bearer tokens for authentication.
- Local media files by default, stored outside the database through a small storage adapter. This makes local uploads real and keeps the storage choice out of the music models.

This version does not yet include a production S3-compatible storage adapter, transcoding, resumable uploads, adaptive bitrate streaming, email verification, or password reset. The API streams the uploaded original file; there is no audio conversion pipeline. Those are separate product and deployment decisions, not features to assume are working.

## Run it locally

```bash
python3 -m venv .venv
source .venv/bin/activate
python -m pip install -r requirements.txt
cp .env.example .env
alembic upgrade head
uvicorn app.main:app --reload
```

The API health check is at `http://127.0.0.1:8000/health`. Versioned routes start at `http://127.0.0.1:8000/api/v1`. Interactive docs are at `http://127.0.0.1:8000/api/v1/docs`, and the OpenAPI JSON is at `http://127.0.0.1:8000/api/v1/openapi.json`.

The default database is `./oru.db`; uploaded media goes into `./data/media`. Both are ignored by Git. To use PostgreSQL, set a SQLAlchemy URL such as `postgresql+psycopg://oru:password@localhost:5432/oru`, then run `alembic upgrade head` again.

## Configuration

Copy `.env.example` to `.env`. Important settings:

- `DATABASE_URL`: SQLite or PostgreSQL connection URL.
- `JWT_SECRET`: set a long, random value outside local development. Never reuse the example value in a deployed environment.
- `JWT_EXPIRE_MINUTES`: access-token lifetime.
- `CORS_ORIGINS`: JSON array of frontend origins allowed to call the API.
- `MEDIA_DIRECTORY`: local media directory.
- `MAX_AUDIO_BYTES` and `MAX_IMAGE_BYTES`: upload limits.
- `ADMIN_EMAILS`: comma-separated addresses that receive moderator access when an account is registered. Keep this private and configure it before creating moderators.

No third-party credentials are needed for local development. A real public launch still needs a production media-storage service, reliable backups, HTTPS, deployment secrets, and an operational process for copyright and safety reports.

## Tests

```bash
pytest -q
```

The test suite uses a separate in-memory SQLite database and temporary media directory. It does not write to your development database.

## Frontend connection

The frontend can call any `/api/v1/...` URL with normal HTTP requests. Register or log in to receive `access_token`, then send `Authorization: Bearer <access_token>` for protected actions. Uploads use `multipart/form-data`; JSON endpoints use `application/json`. The local development base URL is `http://127.0.0.1:8000/api/v1`. Set the frontend URL in `CORS_ORIGINS` when it runs on another origin.

A frontend should treat the API response as the contract, not rely on database tables or backend file names. Errors use `{ "error": { "code", "message", "details?" } }`.

## Database changes

After editing a model, create and review a migration:

```bash
alembic revision --autogenerate -m "describe the change"
alembic upgrade head
```

Do not use `Base.metadata.create_all()` as a replacement for migrations in deployed environments.

## Before public launch

This is a working foundation, not a launch-ready music-hosting service. Review upload scanning and file-signature checks, move media to private object storage, add upload retry/resume and transcoding if needed, move rate limiting to a shared edge service, test with PostgreSQL, set up monitoring and backups, and decide how disputes, takedowns, appeals, privacy requests, and moderation staffing will work. The report endpoints record submissions and moderator actions; they do not establish legal compliance or prove an infringement claim.
