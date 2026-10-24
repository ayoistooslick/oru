# Decisions and open work

## Current choices

- Keep the backend a single FastAPI application, grouped by accounts, music, community, and moderation routes.
- Use SQLAlchemy for database access and Alembic for schema changes. SQLite is the no-service local default; PostgreSQL is the intended shared database choice.
- Store audio and artwork outside the relational database behind a storage adapter. Local disk works for development and single-machine testing. It is not a durable multi-server media solution.
- Use bearer tokens to keep the first frontend integration straightforward. Tokens currently have no refresh or revocation flow.
- Count a listen for ranking only after the client reports 30 seconds, and limit repeated counted events per signed-in listener and track for 30 minutes. This discourages accidental repetition but does not prove playback or stop account farms.
- Keep discovery ranking deterministic and explainable. Likes and listens help, while recency and artist catalogue size give newer or smaller catalogs some additional chance. It is not a fairness claim.

## Decisions needed before launch

1. Pick an object-storage provider, private/public access model, CDN and retention policy. Add upload scanning, image/audio file signature checks, retry behavior and cleanup for abandoned uploads.
2. Decide whether artists must verify email or identity, how accounts recover access, and whether refreshable/revocable sessions are needed.
3. Write actual community rules and the copyright reporting, takedown, counter-notice and appeal workflow with qualified legal advice. Reports alone do not make the platform legally compliant.
4. Define moderation staffing, response targets, abuse escalation and audit retention.
5. Decide privacy controls for profiles, listening history, analytics and account deletion.
6. Test migrations and query behavior against PostgreSQL, then add backups, monitoring, deployment secrets, HTTPS and an operational incident plan.
7. Revisit discovery quality with real usage data and evaluate manipulation resistance before making fairness or anti-fraud promises.
8. Add release artwork and complete public release detail/list endpoints, plus user-facing following feeds, if they are part of the first frontend release.
