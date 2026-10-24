# API guide

Base URL for local work: `http://127.0.0.1:8000/api/v1`. OpenAPI and interactive docs are available at `/openapi.json` and `/docs` beneath that base path.

Protected routes use `Authorization: Bearer <access_token>`. Routes marked **Artist** require the signed-in account to have an artist profile. Routes marked **Moderator** require its email to have been listed in `ADMIN_EMAILS` when it was registered. Public routes do not require a token.

## Responses and errors

Successful responses are JSON objects or paginated objects with `items`, `limit`, and `offset`. Errors use this shape:

```json
{"error":{"code":"validation_error","message":"Some of the submitted data is invalid","details":[{"field":"body.email","message":"Invalid email address"}]}}
```

Validation errors are `422`, missing or private resources are `404`, duplicate accounts are `409`, unsupported uploads are `415`, and unauthenticated/unauthorized requests are `401`/`403`. Unexpected errors return a generic `500`; details are kept in server logs. General API requests are limited to 120 per minute per client IP, while registration and login share a stricter 10-per-minute limit. These counters are process-local. Put a shared rate limiter in front of a multi-worker deployment.

## Accounts and profiles

| Method | Path | Access | Purpose |
| --- | --- | --- | --- |
| POST | `/auth/register` | Public | Create listener account and get a token |
| POST | `/auth/login` | Public | Sign in and get a token |
| GET | `/auth/me` | Signed in | Current account |
| PATCH | `/listeners/me` | Signed in | Change display name or bio |
| POST | `/artists/me` | Signed in | Create an artist profile |
| GET | `/artists` | Public | Search/list artists with `q`, `limit`, `offset` |
| GET | `/artists/{artist_id}` | Public | Artist profile and counts |

Registration example:

```bash
curl -X POST http://127.0.0.1:8000/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"listener@example.com","handle":"listener_one","display_name":"Listener One","password":"a-long-local-password"}'
```

Response includes `user`, `access_token`, and `token_type`. Keep the token private and send it as a bearer token on protected requests.

## Music and releases

| Method | Path | Access | Purpose |
| --- | --- | --- | --- |
| GET | `/songs` | Public | Browse/search published tracks with `q`, `genre`, `limit`, `offset` |
| GET | `/songs/{song_id}` | Public for published songs | Track details |
| GET | `/songs/{song_id}/audio` | Public for published songs; owner/moderator for drafts | Stream original uploaded file |
| GET | `/songs/{song_id}/cover` | Public for published songs; owner/moderator for drafts | Read uploaded cover artwork |
| POST | `/artists/me/songs` | Artist | Upload audio and optional cover as multipart form data; creates a draft |
| POST | `/artists/me/songs/{song_id}/publish` | Owning Artist | Publish a draft |
| POST | `/artists/me/songs/{song_id}/unpublish` | Owning Artist | Return a track to draft |
| POST | `/artists/me/releases` | Artist | Create draft single, EP, or album from owned tracks |
| POST | `/artists/me/releases/{release_id}/publish` | Owning Artist | Publish the release and its tracks |
| GET | `/artists/{artist_id}/releases` | Public | List published releases by an artist |
| GET | `/releases/{release_id}` | Public | Get a published release and its published tracks |
| GET | `/feed/following` | Signed in | Recent published tracks by artists you follow |
| GET | `/artists/me/stats` | Artist | Counts for own catalog |

Create an artist profile first. Upload example:

```bash
curl -X POST http://127.0.0.1:8000/api/v1/artists/me/songs \
  -H 'Authorization: Bearer YOUR_TOKEN' \
  -F 'title=First Light' -F 'description=Demo track' \
  -F 'genres=ambient' -F 'genres=electronic' \
  -F 'audio=@first-light.mp3' -F 'cover=@cover.jpg'
```

The upload is private until published. Supported audio suffixes are MP3, WAV, OGG, M4A, FLAC, and OPUS. Artwork accepts JPEG, PNG, or WebP. Type is checked using the filename extension and multipart content type, and size is capped by settings. This is not a malware scanner or media transcoder. Upload goes through the API with normal multipart progress events in the browser; interrupted uploads must be restarted.

The original media is stored outside the relational database. This version only implements local disk storage. Production object storage, resumable uploads, metadata probing, transcoding and adaptive streaming are not implemented yet.

## Discovery and listening

| Method | Path | Access | Purpose |
| --- | --- | --- | --- |
| GET | `/discover` | Public | Ranked published tracks; optional `genre`, `limit`, `offset` |
| POST | `/songs/{song_id}/listen` | Signed in | Record listener-reported listening duration |

The `explainable-v1` score uses likes, qualified listens in the last 30 days, recency, a modest boost for artists the listener follows, and a small-catalogue opportunity boost. Plays count toward this score only at 30 seconds, and the same account/song cannot add another counted listen inside 30 minutes. This is a basic heuristic. It has not been shown to prevent coordinated manipulation or guarantee fair exposure.

Example:

```bash
curl -X POST http://127.0.0.1:8000/api/v1/songs/42/listen \
  -H 'Authorization: Bearer YOUR_TOKEN' \
  -H 'Content-Type: application/json' \
  -d '{"listened_ms":45000}'
```

The response says whether the event was stored and counted for discovery. The client should report actual playback progress, but the server cannot verify a dishonest client in this version.

## Community

| Method | Path | Access | Purpose |
| --- | --- | --- | --- |
| POST/DELETE | `/artists/{artist_id}/follow` | Signed in | Follow/unfollow an artist |
| POST/DELETE | `/songs/{song_id}/likes` | Signed in | Like/unlike a published track |
| POST | `/playlists` | Signed in | Create playlist |
| GET | `/playlists/{playlist_id}` | Public for public lists; owner for private lists | Get playlist and available tracks |
| PATCH | `/playlists/{playlist_id}` | Owner | Edit title or visibility |
| POST/DELETE | `/playlists/{playlist_id}/songs/{song_id}` | Owner | Add/remove a published track |
| POST | `/artists/me/posts` | Artist | Publish a text update |
| GET | `/artists/{artist_id}/posts` | Public | Read visible posts |

The following feed currently lists new tracks, not a personalized notification stream. It does not include release artwork or delivery notifications.

Post example:

```bash
curl -X POST http://127.0.0.1:8000/api/v1/artists/me/posts \
  -H 'Authorization: Bearer YOUR_TOKEN' -H 'Content-Type: application/json' \
  -d '{"body":"New track is up today."}'
```

## Reports and moderation

| Method | Path | Access | Purpose |
| --- | --- | --- | --- |
| POST | `/reports` | Signed in | Report a song, artist, or artist post |
| GET | `/moderation/reports?status=open` | Moderator | Read a queue of reports |
| POST | `/moderation/reports/{report_id}/decision` | Moderator | Hide, restore, or dismiss with a recorded reason |

A report is a request for review, not proof that a rule or law was broken. This backend records a reason and moderation decision but does not implement takedown notices, appeals, counter-notices, legal deadlines, or a complete policy workflow.

## Health

`GET /health` checks whether the API can query its configured database. It returns `503` when that check fails.
