"""The beta line, and the door to it.

ORU ships two builds: the stable one everybody gets, and a beta that carries
features which are not finished. The beta is not advertised in the app and it is
not a public download — a listener asks for it, a moderator reads the request,
and only an approved account can fetch it. That is the whole point: with a small
app, the first people to run unfinished work should be chosen, not whoever
clicks fastest.

Endpoints:

- ``GET  /beta/me``            what this account's request looks like
- ``POST /beta/apply``         ask for the beta (re-asking updates the request)
- ``GET  /beta/download``      a redirect to the build, approved accounts only
- ``GET  /moderation/beta``    the queue a moderator reads
- ``POST /moderation/beta/{id}/decision``  approve or decline, with a note
"""

from __future__ import annotations

import os

from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import RedirectResponse
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.database import get_db
from app.dependencies import admin_user, current_user
from app.models import BetaApplication, User, now_utc
from app.schemas import BetaApply, BetaDecision

router = APIRouter(tags=["beta"])

# Where the beta APK lives. A GitHub pre-release tagged `beta` keeps the stable
# `/releases/latest` pointer untouched, which matters: that link is what every
# installed release build updates from.
DEFAULT_BETA_APK = "https://github.com/ayoistooslick/oru/releases/download/beta/oru-beta.apk"


def _beta_apk_url() -> str:
    return os.environ.get("BETA_APK_URL", "") or DEFAULT_BETA_APK


def _application_data(application: BetaApplication | None) -> dict:
    if application is None:
        return {"status": "none", "note": "", "created_at": None,
                "reviewed_at": None, "review_note": ""}
    return {
        "status": application.status,
        "note": application.note,
        "created_at": application.created_at,
        "reviewed_at": application.reviewed_at,
        "review_note": application.review_note,
    }


def _for_user(db: Session, user_id: int) -> BetaApplication | None:
    return db.scalar(select(BetaApplication).where(
        BetaApplication.user_id == user_id))


@router.get("/beta/me")
def my_beta_status(user: User = Depends(current_user),
                   db: Session = Depends(get_db)):
    application = _for_user(db, user.id)
    data = _application_data(application)
    data["download_available"] = application is not None and application.status == "approved"
    return data


@router.post("/beta/apply", status_code=201)
def apply_for_beta(payload: BetaApply, user: User = Depends(current_user),
                   db: Session = Depends(get_db)):
    application = _for_user(db, user.id)
    if application is not None and application.status == "approved":
        # Already in; a second request would only queue a duplicate for a human
        # to read.
        return {"applied": False, **_application_data(application)}
    if application is None:
        application = BetaApplication(user_id=user.id, note=payload.note.strip())
        db.add(application)
    else:
        # A declined request can be made again, but it goes back in the queue.
        application.note = payload.note.strip()
        application.status = "pending"
        application.reviewed_at = None
        application.reviewed_by = None
        application.review_note = ""
    db.commit()
    db.refresh(application)
    return {"applied": True, **_application_data(application)}


@router.get("/beta/download")
def download_beta(user: User = Depends(current_user),
                  db: Session = Depends(get_db)):
    """Hands an approved account the build; everyone else gets a refusal.

    A redirect rather than a proxy: the APK is a static file and there is no
    reason to stream ten megabytes through the API.
    """
    application = _for_user(db, user.id)
    if application is None or application.status != "approved":
        raise HTTPException(status_code=403, detail={
            "code": "beta_not_approved",
            "message": "The beta build is only for approved testers",
        })
    return RedirectResponse(_beta_apk_url(), status_code=302)


@router.get("/beta/link")
def beta_link(user: User = Depends(current_user), db: Session = Depends(get_db)):
    """The address itself, for a browser that hands it to the download manager.

    A browser cannot put the bearer token on a link, so the redirect endpoint is
    for the app and this one is for the page. Approval is still checked here.
    """
    application = _for_user(db, user.id)
    if application is None or application.status != "approved":
        raise HTTPException(status_code=403, detail={
            "code": "beta_not_approved",
            "message": "The beta build is only for approved testers",
        })
    return {"url": _beta_apk_url()}


@router.get("/moderation/beta")
def beta_queue(status: str = "pending", moderator: User = Depends(admin_user),
               db: Session = Depends(get_db)):
    if status not in {"pending", "approved", "declined", "all"}:
        raise HTTPException(status_code=422, detail={
            "code": "invalid_status",
            "message": "Status must be pending, approved, declined or all",
        })
    query = select(BetaApplication).order_by(BetaApplication.created_at)
    if status != "all":
        query = query.where(BetaApplication.status == status)
    applications = db.scalars(query.limit(200)).all()
    return {"items": [{
        "id": application.id,
        "user_id": application.user_id,
        "handle": application.user.handle if application.user else None,
        "display_name": application.user.display_name if application.user else None,
        "email": application.user.email if application.user else None,
        "note": application.note,
        "status": application.status,
        "created_at": application.created_at,
        "reviewed_at": application.reviewed_at,
        "review_note": application.review_note,
    } for application in applications]}


@router.post("/moderation/beta/{application_id}/decision")
def decide_beta(application_id: int, payload: BetaDecision,
                moderator: User = Depends(admin_user),
                db: Session = Depends(get_db)):
    application = db.get(BetaApplication, application_id)
    if application is None:
        raise HTTPException(status_code=404, detail={
            "code": "not_found", "message": "That request was not found",
        })
    application.status = "approved" if payload.approve else "declined"
    application.review_note = payload.note.strip()
    application.reviewed_at = now_utc()
    application.reviewed_by = moderator.id
    db.commit()
    return {"id": application.id, "status": application.status}
