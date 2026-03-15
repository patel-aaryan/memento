from fastapi import APIRouter, Depends, HTTPException, Security
from pydantic import BaseModel
from sqlalchemy.orm import Session

from app.config.db import get_db
from app.dependencies.auth import get_current_user, security
from app.repositories import device_repository
from app.services.notification_service import (
    run_daily_anniversary_job,
    send_push_to_user,
    ANNIVERSARY_TITLE,
    ANNIVERSARY_BODY,
)

router = APIRouter(prefix="/notifications", tags=["notifications"])


class RegisterDeviceRequest(BaseModel):
    fcm_token: str


@router.post(
    "/register-device",
    dependencies=[Security(security)],
)
async def register_device(
    request: RegisterDeviceRequest,
    current_user: dict = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """Register or update the current device's FCM token. Called from onNewToken when logged in."""
    if request.fcm_token:
        device_repository.upsert_device_token(db, current_user["id"], request.fcm_token)
    return {"success": True}


@router.post(
    "/send-anniversary-push",
    dependencies=[Security(security)],
)
async def send_anniversary_push(
    current_user: dict = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """
    Called by the Android client when /location/anniversary-check returns has_match=true.
    Sends a visible FCM push notification to all of the user's devices.
    """
    try:
        send_push_to_user(
            db,
            current_user["id"],
            ANNIVERSARY_TITLE,
            ANNIVERSARY_BODY,
            {"type": "anniversary"},
        )
        return {"success": True, "message": "Anniversary push sent"}
    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=f"Failed to send anniversary push: {str(e)}",
        )


@router.post("/trigger-anniversary-check")
async def trigger_anniversary_check(db: Session = Depends(get_db)):
    """
    Manually trigger the daily anniversary job.
    Useful for testing and can be called by an external cron.
    """
    try:
        run_daily_anniversary_job(db)
        return {"success": True, "message": "Anniversary check completed"}
    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=f"Failed to run anniversary check: {str(e)}",
        )
