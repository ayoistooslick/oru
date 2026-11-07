"""One-off catalogue reset, reachable only while an operator asks for it.

ORU_DEMO_RESET=1 in the environment switches it on for a single deploy; the
route answers 404 otherwise. The seed itself refuses to run when the demo
artists are already there, so a repeat call (or a stay-behind env var) cannot
delete the demo catalogue again.
"""

from __future__ import annotations

import os

from fastapi import APIRouter, HTTPException

from app.database import SessionLocal

router = APIRouter(tags=["maintenance"])


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
