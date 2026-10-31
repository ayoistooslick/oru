# ORU

ORU is a music app for artists who are just starting out. Listeners find new songs, artists upload
their own, and the front page is scored so that small artists can still show up there.

Live at [oru.freebuff.app](https://oru.freebuff.app).

## What the app does

- **Listeners** can sign up, browse, search, like songs, build playlists, follow artists and play
  music. Playback controls are available in the sticky in-app player and, on supported devices,
  in the lock-screen or notification media controls. The installed app shell opens offline, while
  account data, search and streamed audio still need a connection.
- **Artists** get a studio page where they upload audio and cover art, save drafts, publish songs
  and releases, and see plays, likes and follows.
- **Moderators** see reports from listeners and mark them resolved or dismissed.
- The whole thing is one FastAPI process serving one React build, so there is no CORS setup and no
  second server to keep running.

## Stack

- Python 3.11+ with FastAPI, SQLAlchemy and Alembic. SQLite by default, PostgreSQL through
  `DATABASE_URL`.
- React 19, TypeScript, Vite and Tailwind CSS 4 for the web app. GSAP for animation, lucide-react
  for icons.
- SQLite and local files for media in development. Production runs on PostgreSQL and
  S3-compatible object storage. Uploads are streamed as-is: there is no transcoding and no CDN.

## Where things live

```
app/                  FastAPI app. Routes are in app/api/, models in app/models.py
api/index.py          production entrypoint used by the host. The host only routes /api, so
                      calls travel as /api?__path=/api/v1/... and app/main.py puts the path back
src/                  React app: pages in src/pages, state in src/state
public/               web manifest, service worker, icons, share images
docs/API.md           every route with example requests
docs/DECISIONS.md     why discovery and ranking work the way they do
tests/                pytest suite
scripts/preview.sh    runs API and built frontend on one port
scripts/generate_images.py   regenerates icons and share images
twa-manifest.json     Android app config, read by CI
.github/workflows/    CI: tests, then the Android build and release
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

The script builds the frontend if `dist/` is missing, then starts the API process that serves it.
One process, one port.

If you would rather run Vite on its own, point it at the API with `VITE_API_BASE_URL` and add its
origin to `CORS_ORIGINS` in `.env`:

```bash
npm install
VITE_API_BASE_URL=http://127.0.0.1:8000/api/v1 npm run dev
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
- `JWT_EXPIRE_MINUTES` - access token lifetime.
- `CORS_ORIGINS` - JSON array of frontend origins, only needed when the frontend runs elsewhere.
- `MEDIA_DIRECTORY` - where uploads land, `./data/media` by default.
- `MAX_AUDIO_BYTES`, `MAX_IMAGE_BYTES` - upload limits.
- `ADMIN_EMAILS` - addresses that get moderator access on registration. Keep it private.
- `SITE_URL` - the public origin of the app, for example `https://oru.freebuff.app`. Used to build
  absolute links in share previews. Leave it empty locally.

`.env` and the database files are ignored by Git. Never commit them.

## The Android app

CI (`.github/workflows/ci.yml`) runs the test suite, then builds a Trusted Web Activity package
with Bubblewrap and publishes a GitHub release with `oru.apk` attached. The same APK is also
attached to the workflow run as an artifact.

- Pushes to `main` create a release tagged `build-<run number>`.
- Pushing a tag like `v1.0.0` creates a release with that tag instead.
- `twa-manifest.json` holds the Android config: package id `app.oru.music`, host
  `oru.freebuff.app`, icons and colors. Edit it and rerun the workflow to change the app.
- The build reads the live site, so it needs `oru.freebuff.app` to be up.

Signing needs four repository secrets (`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`,
`ANDROID_KEY_PASSWORD`, `ANDROID_KEY_ALIAS`). Without them CI generates a throwaway key, which is
fine for sideloading a test build but not for Play Store uploads or upgrades. See
[CONTRIBUTING.md](CONTRIBUTING.md) for how to create them.

To remove the browser address bar from the installed app, host the asset links file generated by
`bubblewrap fingerprint generateAssetLinks` at
`https://oru.freebuff.app/.well-known/assetlinks.json`.

## Recent listener update

- Listeners can submit an ORU artist's song for review, track and withdraw pending submissions,
  and see who shared an approved song. The credited artist or a moderator can approve or reject it.
- The player has a seek bar, shuffle, repeat-one and repeat-queue controls. Its mini-player remains
  available across signed-in pages, and supported browsers expose playback through system media
  controls.
- The expanded player actions menu opens and closes from its toggle, the explicit hide control,
  Escape, or an outside click.
- Liked songs have playlist artwork, play-all and shuffle actions, and a complete queue/list view.
- Search supports recent terms and result scopes. Artist names on track rows link to their profile.
- Mobile captions and touch targets are larger. The app explains offline limits and provides a
  first-launch setup checklist for notification permission and persistent browser storage.

The browser must wait for a user action before asking for notification permission; storage
persistence is requested through the browser storage API. A PWA/TWA cannot request Android's
default music-player role. Android file handling is declared separately in the TWA configuration.

## Recording, pictures and weekly charts

- **Record in the app.** The studio can record straight from the device microphone, or take a
  file. Either way the sound lands in the same editor before it is uploaded.
- **Trim, fade and level.** The editor draws a waveform, sets a start and an end, adds a fade in
  and a fade out, can even out the volume, and saves a WAV. The cut happens in the browser, so
  nothing is uploaded until the artist saves the clip.
- **Seek properly.** Audio responses answer HTTP Range requests, so dragging the player no longer
  restarts the song. The scrubber follows the finger while it is held, then hands control back to
  the audio clock.
- **Pictures.** Everyone can add a profile picture. Artists also get a wide banner behind their
  name. Both use the same storage and size rules as cover artwork.
- **Features.** A song can credit up to five other artists. They show as `feat.` wherever the song
  plays and appear on their own page under "Featured on".
- **This week.** The home page shows the most played tracks of the last seven days, the studio
  shows weekly plays, weekly likes and the week's top song, and `/rankings/weekly` serves the same
  numbers over the API.
- **Phones.** The signed-in app is drawn at 92 percent on small screens, so more of it fits without
  shrinking touches below a comfortable size.

The expanded player closes from its down arrow again, and opens whenever a song starts.

## Promo video

`public/promo/oru-promo.mp4` is a 34 second 1080p promo built from the real product screenshots.
`scripts/promo/build_video.py` draws every frame with Pillow, writes the soundtrack with numpy and
hands the frames to ffmpeg. To rebuild it:

```bash
.venv/bin/pip install pillow numpy
npm install --no-save --ignore-scripts ffmpeg-static && node node_modules/ffmpeg-static/install.js
.venv/bin/python scripts/promo/build_video.py
```

## Plan for the next update

- **Listener upgrades.** Smaller quality-of-life work for people who only listen: better search,
  clearer offline behaviour, and quicker ways to get from one song to more by the same artist.
- **Playlists that feel like playlists.** A collapsible queue, drag to reorder, and a proper next
  up view while a song plays.
- **Saved takes.** Keep several recordings in the studio, so a second attempt does not replace the
  first.

## Documentation

- [CONTRIBUTING.md](CONTRIBUTING.md) - how to send a change.
- [docs/API.md](docs/API.md) - routes, payloads, example requests.
- [docs/DECISIONS.md](docs/DECISIONS.md) - ranking and discovery choices, and what is deliberately
  not claimed.

## Known limits

This is a working product, not a finished hosting service. Audio is streamed in its original file,
there is no resume or retry on uploads, no email verification or password reset, and rate limiting
is in-process. The report endpoints record what people said and what moderators decided; that is a
log, not a legal process.
