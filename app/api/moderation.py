from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.database import get_db
from app.dependencies import admin_user, current_user
from app.models import ArtistPost, ArtistProfile, ModerationAction, Report, Song, User
from app.schemas import ModerationDecision, ReportCreate

router = APIRouter(tags=["moderation"])


def target_exists(db: Session, target_type: str, target_id: int) -> bool:
    if target_type == "song":
        return db.get(Song, target_id) is not None
    if target_type == "post":
        return db.get(ArtistPost, target_id) is not None
    if target_type == "artist":
        return db.get(ArtistProfile, target_id) is not None
    return False


@router.post("/reports", status_code=201)
def create_report(payload: ReportCreate, user: User = Depends(current_user), db: Session = Depends(get_db)):
    if not target_exists(db, payload.target_type, payload.target_id):
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "The reported item was not found"})
    report = Report(reporter_id=user.id, target_type=payload.target_type, target_id=payload.target_id,
                    reason=payload.reason, details=payload.details.strip())
    db.add(report)
    db.commit()
    db.refresh(report)
    return {"report": {"id": report.id, "target_type": report.target_type, "target_id": report.target_id, "reason": report.reason, "status": report.status, "created_at": report.created_at}}


@router.get("/moderation/reports")
def list_reports(status: str = "open", moderator: User = Depends(admin_user), db: Session = Depends(get_db)):
    if status not in {"open", "reviewed", "actioned"}:
        raise HTTPException(status_code=422, detail={"code": "invalid_status", "message": "Status must be open, reviewed or actioned"})
    reports = db.scalars(select(Report).where(Report.status == status).order_by(Report.created_at).limit(100)).all()
    return {"items": [{"id": r.id, "reporter_id": r.reporter_id, "target_type": r.target_type, "target_id": r.target_id, "reason": r.reason, "details": r.details, "status": r.status, "created_at": r.created_at} for r in reports]}


@router.post("/moderation/reports/{report_id}/decision")
def decide_report(report_id: int, payload: ModerationDecision, moderator: User = Depends(admin_user), db: Session = Depends(get_db)):
    report = db.get(Report, report_id)
    if report is None:
        raise HTTPException(status_code=404, detail={"code": "not_found", "message": "Report not found"})
    target = None
    if report.target_type == "song":
        target = db.get(Song, report.target_id)
    elif report.target_type == "post":
        target = db.get(ArtistPost, report.target_id)
    elif report.target_type == "artist":
        target = db.get(User, report.target_id)
    if payload.action == "hide" and target is not None:
        if report.target_type == "song":
            target.status = "hidden"
        elif report.target_type == "post":
            target.is_hidden = True
        elif report.target_type == "artist":
            target.is_active = False
    elif payload.action == "restore" and target is not None:
        if report.target_type == "song" and target.status == "hidden":
            target.status = "published"
        elif report.target_type == "post":
            target.is_hidden = False
        elif report.target_type == "artist":
            target.is_active = True
    report.status = "actioned" if payload.action != "dismiss" else "reviewed"
    db.add(ModerationAction(moderator_id=moderator.id, report_id=report.id, action=payload.action, reason=payload.reason.strip()))
    db.commit()
    return {"report_id": report.id, "status": report.status, "action": payload.action}
