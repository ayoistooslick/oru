"""Operator-only resets.

Two one-off jobs live here, both switched off unless the environment names a
secret for them, so a deployed API never exposes a button that deletes data:

- ``POST /maintenance/reset`` empties the database completely (``ORU_RESET_TOKEN``).
- ``POST /maintenance/demo-catalogue`` seeds the demo artists (``ORU_DEMO_RESET``).
"""

from __future__ import annotations

import os
import secrets

from fastapi import APIRouter, Header, HTTPException

from app.database import Base, SessionLocal, engine

router = APIRouter(tags=["maintenance"])


def _guard(field: str, expected: str) -> None:
    """Answer 404 when unconfigured, 403 on a wrong secret.

    Constant-time comparison keeps the secret from being guessed a character at
    a time, even though the route is only meant to exist for a minute.
    """
    if not secrets.compare_digest(field or "", expected):
        raise HTTPException(status_code=403,
                            detail={"code": "forbidden", "message": "Not here"})


@router.post("/maintenance/reset")
def reset_database(x_oru_reset: str = Header(default="")):
    """Drop every table and build the schema back, so ORU starts from nothing."""
    expected = os.environ.get("ORU_RESET_TOKEN", "")
    if not expected:
        raise HTTPException(status_code=404,
                            detail={"code": "not_found", "message": "Not here"})
    _guard(x_oru_reset, expected)

    tables = sorted(Base.metadata.tables)
    Base.metadata.drop_all(bind=engine)
    Base.metadata.create_all(bind=engine)
    # Columns added to live tables after create_all's schema are re-attached so
    # the app boots on the fresh database exactly as it does on an old one.
    from app.main import ensure_schema

    ensure_schema()
    return {"reset": True, "tables": len(tables)}


@router.post("/maintenance/demo-catalogue")
def demo_catalogue():
    if os.environ.get("ORU_DEMO_RESET") != "1":
        raise HTTPException(status_code=404,
                            detail={"code": "not_found", "message": "Not here"})

    try:
        from app.demo_catalogue import already_seeded, reset_and_seed

        with SessionLocal() as db:
            if already_seeded(db):
                return {"reset": False,
                        "reason": "the demo catalogue is already in place"}

        result = reset_and_seed(quick=True)
    except Exception as error:  # noqa: BLE001 - the caller wants the reason
        raise HTTPException(status_code=500,
                            detail={"code": "seed_failed",
                                    "message": f"{type(error).__name__}: {error}"})
    return {"reset": True, **result}
