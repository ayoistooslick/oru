# Contributing to ORU

ORU is going open source. These are the rules so that contributions stay reviewable and the
product stays coherent.

## The short version

1. Fork the repository and branch off `main`.
2. Keep one pull request to one concern.
3. Run `pytest -q` and `npm run build` before you push. CI runs exactly those.
4. Write plainly in the product itself: short sentences, everyday words.
5. Say what you changed and why in the pull request.

## Before you start

- Search the issues first. If the change is a new feature or a redesign, open an issue and agree on
  the approach before writing code. Unsolicited large PRs get closed.
- Read [docs/API.md](docs/API.md) if you touch routes, and
  [docs/DECISIONS.md](docs/DECISIONS.md) if you touch discovery or ranking.
- Small fixes (a typo, a broken link, an off-by-one) need no discussion. Just send them.

## Setting up a development copy

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
alembic upgrade head
uvicorn app.main:app --reload
```

In a second terminal:

```bash
npm install
sh ./scripts/preview.sh
```

`sh ./scripts/preview.sh` builds the frontend if needed and serves it from the API process, which
is the same shape as production. The app is then at `http://127.0.0.1:8000`.

## Rules for code

- **Keep the existing structure.** Backend routes live in `app/api/`, React pages in `src/pages/`,
  shared state in `src/state/`, small helpers in `src/lib/`. Do not introduce a second way of doing
  the same thing (another router, another state library, another CSS framework).
- **Backend changes come with tests.** Add a case in `tests/` that fails without your change. The
  suite has to stay green.
- **Do not add a dependency casually.** If the change works with what is already installed, do that.
  A new package needs a sentence in the PR explaining why it is worth the weight.
- **Database changes go through Alembic.** Change the model, then
  `alembic revision --autogenerate -m "what changed"`, then read the generated file before
  committing it. Never replace migrations with `create_all()` in deployed code.
- **Never commit secrets or local state.** `.env`, `*.db`, `*.db-shm`, `*.db-wal`, `data/media/`,
  `dist/`, `node_modules/`, `tsconfig.tsbuildinfo` and any `*.keystore` file are already in
  `.gitignore`. Keep it that way.

## Rules for the interface

This part is strict, because ORU is meant to be usable by people with low literacy and cheap
phones.

- Black surfaces (`#121212`), white text, `#1ed760` as the accent. Use the existing Tailwind tokens
  and CSS variables instead of new colors.
- No Spotify logos, wordmarks or artwork. The look is borrowed, the brand is not.
- Copy is short and plain. Everyday words, one idea per sentence, no jargon, no marketing tone.
- No em dashes, en dashes or curly quotes anywhere in `src/`. Straight quotes and periods only.
- New screens need a screenshot in the pull request. Reviewers cannot judge layout from a diff.
- Keep it light: fewer pages, less text, less decoration.

## Commits and pull requests

- Commit messages are imperative and specific: `Fix like button on release page`, not `update` or
  `stuff`.
- One concern per pull request. A reviewer has to read the whole diff, so split unrelated work.
- The pull request description says what changed, why, and how you checked it (which tests, what
  you looked at in the browser).
- CI must be green. A red build gets fixed or the pull request waits; it does not get merged around.
- Do not rewrite or force-push history that other people have already reviewed. Add commits.

## Reporting bugs

Open an issue with:

- what you did,
- what you expected,
- what happened instead,
- your browser or phone, and a screenshot if the interface is involved.

For security problems such as exposed data or broken authentication, do not open a public issue.
Use the repository's **Security** tab and "Report a vulnerability" so it stays private until there
is a fix.

## Behaviour

- Be respectful in issues, reviews and pull requests. Critique the work, not the person.
- No harassment, no personal attacks, no sexist, racist or discriminatory language.
- Do not publish private data about users or contributors.
- Maintainers may close contributions that are rude, off-topic or refuse to follow these rules.

## What is welcome

Bug fixes, accessibility, tests, documentation, translations, performance work, offline behaviour,
and small improvements to the interface that keep the design above.

New paid-service integrations, large redesigns and big new product features need an issue and
agreement first.

## About the license

There is no `LICENSE` file yet. Open source needs one, so the maintainer will pick it before this
repository is announced widely. If you want to send code before that, ask in an issue first so
everyone knows the terms.
