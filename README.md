# ORU

ORU is a music app for artists who are just starting out. Listeners find new songs, artists upload
their own, and the front page is scored so that small artists can still show up there.

[oru.freebuff.app](https://oru.freebuff.app) is the product in a browser, and the same account and
library work in the Android app. The browser is the whole client, not a landing page: an iPhone or a
laptop needs no install. The app adds downloads and background playback.

| | Browser | Android app |
| --- | --- | --- |
| Listen, search, like, playlists, follow | yes | yes |
| Studio: record, upload, publish | yes | yes |
| Downloads that play offline | no | yes |
| Lock-screen and background controls | no | yes |
| Google sign-in | inside the page | through the browser, then back |

Google will only sign people in inside a browser, so the app borrows the site's `/auth` page for
that step.

Tags: `independent-artist-platform` · `music-discovery` · `fastapi` · `android` · `media3` ·
`react` · `vite` · `self-hosted-s3`

## What the product does

- **Listeners** sign up, browse, search, like songs, build playlists, follow artists and play music.
  Playback has a queue, shuffle, repeat, a sleep timer that keeps counting with the screen off, and
  lock-screen controls. Downloads play without a connection.
- **Artists** get a studio page where they record or upload audio, trim it, add cover art, save
  drafts, publish songs and releases, and see plays, likes and follows.
- **Moderators** see reports from listeners and mark them resolved or dismissed.

APK: <https://is.gd/WNgSQY>. Every CI run also attaches `oru.apk` to a `build-N` release.

## Shared song links

A song is shared as `https://oru.freebuff.app/song/<id>`.

- The page is public and plays the song, so a link sent to an iPhone, a laptop or a phone without
  the app still works.
- On Android the page fires an `intent://song/<id>;package=com.oru.app` URL. Chrome opens the app
  when it is installed and stays on the page when it is not, so no digital asset link (and no
  signing-key fingerprint) is needed. `OpenLinkActivity` receives it, asks the listener to sign in
  first if needed, and then plays the song.
- The app also answers `oru://song/<id>` and `oru://artist/<id>` directly, which is what the share
  sheet and other apps can hand it.

## Sign-in across two surfaces

Google refuses to sign a native app in directly, so the app sends the browser to `/auth`:

1. The app opens `https://oru.freebuff.app/auth?client=android` in the browser.
2. The page finishes Google sign-in (Google Identity Services) with the same client id the API
   verifies.
3. The page asks the API for a **one-time code** (`POST /auth/handoff`) and redirects to
   `oru://auth?code=…`.
4. `AuthCallbackActivity` trades that code for the session (`POST /auth/handoff/exchange`). Codes
   live five minutes, are stored hashed, and die on first use, so a URL left in browser history is
   useless.

Email and password still sign in natively, inside the app.

## Stack

- Python 3.11+ with FastAPI, SQLAlchemy and Alembic. SQLite by default, PostgreSQL through
  `DATABASE_URL`.
- React 19, TypeScript, Vite and Tailwind CSS 4 for the download page and the sign-in handoff. GSAP
  for animation.
- Android in Java with native views, Retrofit, and Android Media3 for playback.

## Where things live

```
app/                  FastAPI app. Routes are in app/api/, models in app/models.py
api/index.py          production entrypoint used by the host. The host only routes /api, so
                      calls travel as /api?__path=/api/v1/... and app/main.py puts the path back
src/                  the web client: the whole player, studio, library and settings in React
src/pages/Song.tsx    the public page a shared song link opens
src/pages/Auth.tsx    Google sign-in and the one-time code the Android app trades for a session
public/               icons and share images, plus the service worker that makes the site installable
docs/API.md           every route with example requests
docs/DECISIONS.md     why discovery and ranking work the way they do
tests/                pytest suite
scripts/preview.sh    runs API and built frontend on one port
android/              the Android client (Java, Media3 playback, Retrofit)
.github/workflows/    CI: tests, then the native Android build and release
```

## Run it locally

Backend only:

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
alembic upgrade head
uvicorn app.main:app --reload
```

API is at `http://127.0.0.1:8000/api/v1`, docs at `http://127.0.0.1:8000/api/v1/docs`, health at
`/health`.

Everything on one port, which is how production runs:

```bash
sh ./scripts/preview.sh
```

## Tests

```bash
pytest -q
npm run build        # typechecks, then bundles
```

Both run on every push. The test suite uses its own in-memory database and a temporary media
directory, so it never touches your development data.

## Configuration

Copy `.env.example` to `.env`. The settings that matter:

- `DATABASE_URL` - SQLite file or a PostgreSQL URL.
- `JWT_SECRET` - a long random value. Never reuse the example value in production.
- `GOOGLE_CLIENT_ID` - the web client id. Sign-in is hidden until this is set, and the API checks
  every Google token against it.
- `MEDIA_DIRECTORY` - where uploads land, `./data/media` by default.
- `ADMIN_EMAILS` - addresses that get moderator access on registration. Keep it private.
- `SITE_URL` - the public origin, for example `https://oru.freebuff.app`. Used for share links.

`.env` and the database files are ignored by Git. Never commit them.

## Starting the catalogue over

`POST /api/v1/maintenance/reset` drops every table and builds the schema back. It only exists while
`ORU_RESET_TOKEN` is set in the environment; the caller must send the same value in `X-Oru-Reset`.
Store and remove the secret around the wipe, so the route is not left armed:

```bash
freebuff-deploy env set '{"ORU_RESET_TOKEN":"<random>"}' && freebuff-deploy start
curl -X POST "https://oru.freebuff.app/api?__path=/api/v1/maintenance/reset" \
  -H "X-Oru-Reset: <random>"
freebuff-deploy env unset ORU_RESET_TOKEN && freebuff-deploy start
```

## The Android app

`android/` is the native client (Java, native views, Retrofit networking, Android Media3 playback).
It is not a WebView or Trusted Web Activity. It talks to the same ORU API as the site.

CI (`.github/workflows/ci.yml`) runs backend and frontend checks, builds the native debug APK, and
publishes it as both a workflow artifact and a GitHub release asset.

- Pushes to `main` create a release tagged `build-<run number>`.
- Pushing a tag like `v1.0.0` creates a release with that tag instead.
- The current raw APK is at `https://github.com/ayoistooslick/oru/releases/latest/download/oru.apk`.
- CI uses a per-run debug signing key, so uninstall an APK from a prior CI run before installing a
  newer one. Configure a stable release keystore before Play Store use.
