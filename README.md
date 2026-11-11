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

APK: <https://is.gd/P2A3Io>, which redirects to the signed APK on the latest release
(`https://github.com/ayoistooslick/oru/releases/latest/download/oru.apk`).

## Links open in the app

Every page on the site has an address in the app's own scheme, so a link opened on an Android phone
that has ORU installed lands on the screen it names instead of a browser.

| Web page | App link | App screen |
| --- | --- | --- |
| `/song/<id>` | `oru://song/<id>` | the player, on that song |
| `/artist/<id>` | `oru://artist/<id>` | the artist page |
| `/release/<id>` | `oru://release/<id>` | the release |
| `/library/<id>` | `oru://playlist/<id>` | the playlist |
| `/search?q=…` | `oru://search?q=…` | search, already run |
| `/listen`, `/library`, `/shares`, `/studio` | `oru://<tab>` | that tab |
| `/notifications` | `oru://notifications` | Drops |
| `/settings`, `/moderation`, `/about` | `oru://<screen>` | that screen |

- Each page is public and plays or shows what it names, so a link sent to an iPhone, a laptop or a
  phone without the app still works.
- On Android the hand-off happens twice, deliberately. A script in `index.html` fires
  `intent://<kind>/<id>;package=com.oru.app` before the bundle loads, so the app usually opens before
  the browser paints a frame of the same song; `OpenInApp` repeats it if that script was skipped.
  Chrome opens the app when it is installed and reloads the page when it is not, with a
  `sessionStorage` key stopping a browser without the app from retrying. No digital asset link and no
  signing-key fingerprint is needed, which matters because every CI build is signed fresh.
- A release build is also verified for App Links: `https://oru.freebuff.app/.well-known/assetlinks.json`
  carries the SHA-256 of the release certificate, so Android hands those links to the app itself,
  with no browser frame at all. Verification happens at install time against the certificate, so a
  debug-signed test build falls back to the hand-off above.
- Android only fires the hand-off on the page a tab was opened on, so clicking around inside the
  site never keeps throwing people into the app. Browsers that cannot resolve intent addresses
  (Firefox and its forks) get the bare `oru://` scheme instead; the app answers that directly.
- `OpenLinkActivity` is the single place links are resolved. It keeps the link across a sign-in,
  then opens the song, artist, release, playlist, search or tab it names. A link that names
  something gone falls back to the home tab with a short explanation.
- `src/lib/links.ts` holds the mapping; the table in `index.html` mirrors it for the pre-paint
  script, and the intent filters in `AndroidManifest.xml` mirror it on the app side. Adding a route
  means editing all three.

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
- React 19, TypeScript, Vite and Tailwind CSS 4 for the browser client, which is the same product as
  the app, with GSAP for animation. The palette and type tokens are copied from the app's
  `res/values/colors.xml` and `themes.xml`.
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
- `GITTY_BANK_API_KEY` - the media service key. With it set, every upload (audio, covers, avatars,
  banners) goes to Gitty Bank and the database stores the public file URL it returns. Media
  responses redirect to that URL, so Range requests still work and audio never streams through the
  API. Uploads are public by design: never store anything confidential. Removing a song, replacing
  artwork or clearing an avatar calls `DELETE /api/v1/files/{id}`, which any key with write
  permission may do. A read-only key is refused, and that refusal is logged instead of failing the
  user's action, so a file only lingers when the key cannot delete it.
- `STORAGE` - `gitty`, `s3` or `local`. Leave it empty and the API picks Gitty Bank when
  `GITTY_BANK_API_KEY` is set, otherwise S3 when it is configured, otherwise the local disk.
- `MEDIA_DIRECTORY` - where local uploads land, `./data/media` by default. Only used when `STORAGE`
  resolves to `local`; it does not survive a redeploy on ephemeral hosting.
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

- Pushes to `main` create a **pre-release** tagged `build-<run number>`, so `/releases/latest`
  keeps pointing at a tagged release instead of whichever commit ran last.
- Pushing a tag like `v1.1.0` creates the release with that tag.
- The current raw APK is at `https://github.com/ayoistooslick/oru/releases/latest/download/oru.apk`.

### Signing

Two keys exist, and only one of them ships:

- **Release key** (`android/keystore/oru-release.jks`, git-ignored). Signs tagged releases. Its
  SHA-256 is the fingerprint in `public/.well-known/assetlinks.json`, which is what makes
  `https://oru.freebuff.app/...` links open the app directly. Replacing the key means updating that
  file and every install will need one uninstall, so keep a backup.
- **Debug key** (per CI run). Signs `build-<n>` test builds only. App Links do not verify on them and
  they do not install over a release build.

CI signs with the release key when these repository secrets are set, and otherwise publishes a
clearly-labelled debug build:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | `base64 -w0 android/keystore/oru-release.jks` |
| `ANDROID_KEYSTORE_PASSWORD` | store password |
| `ANDROID_KEY_ALIAS` | `oru` |
| `ANDROID_KEY_PASSWORD` | key password |

Locally, `android/keystore.properties` supplies the same four values; `assembleRelease` then produces
`android/app/build/outputs/apk/release/app-release.apk`.
