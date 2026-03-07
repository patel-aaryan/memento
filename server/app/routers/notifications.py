from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from firebase_admin import messaging

router = APIRouter(prefix="/notifications", tags=["notifications"])


class TestNotificationRequest(BaseModel):
    title: str = "Test Notification"
    body: str = "This is a test notification from Memento!"


@router.post("/test", responses={500: {"description": "Failed to send test notification"}})
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
