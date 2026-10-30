#!/bin/sh
# One process serves both halves of ORU: the FastAPI backend and the built
# single-page frontend, exactly the way the deployed app is put together.
# Freebuff supplies PORT; fall back to 8000 when it is run by hand.

cd "$(dirname "$0")/.." || exit 1

if [ ! -f dist/index.html ]; then
  echo "dist/ is missing — building the frontend first."
  npm run build || exit 1
fi

PY=./.venv/bin/python
if [ ! -x "$PY" ]; then
  PY=python3
  if ! "$PY" -c "import fastapi" >/dev/null 2>&1; then
    echo "Setting up the Python environment."
    python3 -m venv .venv || exit 1
    ./.venv/bin/pip install -q -r requirements.txt || exit 1
    PY=./.venv/bin/python
  fi
fi

export PORT="${PORT:-8000}"
exec "$PY" -m app
