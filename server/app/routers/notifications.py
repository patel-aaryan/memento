from fastapi import APIRouter, HTTPException, Depends
from pydantic import BaseModel
from firebase_admin import messaging
from typing import Optional
from app.dependencies.auth import get_current_user

router = APIRouter(prefix="/notifications", tags=["notifications"])


class NotificationRequest(BaseModel):
    token: str
    title: str
    body: str
    data: Optional[dict] = None


class TestNotificationRequest(BaseModel):
    title: str = "Test Notification"
    body: str = "This is a test notification from Memento!"


@router.post("/send")
async def send_notification(
    request: NotificationRequest,
    current_user: dict = Depends(get_current_user)
):
    """Send a push notification to a specific device token"""
    try:
        message = messaging.Message(
            notification=messaging.Notification(
                title=request.title,
                body=request.body,
            ),
            data=request.data or {},
            token=request.token,
        )

        response = messaging.send(message)
        return {
            "success": True,
            "message_id": response,
            "details": "Notification sent successfully"
        }
    except Exception as e:
        raise HTTPException(
            status_code=500, detail=f"Failed to send notification: {str(e)}")


@router.post("/test")
async def test_notification(
    token: str,
    request: TestNotificationRequest = TestNotificationRequest(),
):
    """
    Test endpoint to send a notification without authentication.
    Remove this in production!
    """
    try:
        message = messaging.Message(
            notification=messaging.Notification(
                title=request.title,
                body=request.body,
            ),
            data={"type": "test"},
            token=token,
        )

        response = messaging.send(message)
        return {
            "success": True,
            "message_id": response,
            "details": "Test notification sent successfully",
            "token_used": token[:20] + "..." if len(token) > 20 else token
        }
    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=f"Failed to send test notification: {str(e)}"
        )
