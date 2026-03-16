import logging
from datetime import date, datetime, timedelta
from typing import Optional
from zoneinfo import ZoneInfo

from firebase_admin import messaging

from app.config.settings import get_settings
from sqlalchemy.orm import Session

from app.repositories import device_repository, image_repository

logger = logging.getLogger(__name__)

ANNIVERSARY_TITLE = "Memory anniversary"
ANNIVERSARY_BODY = "You were here a year ago. Want to add a new photo?"


def send_push_to_user(
    db: Session,
    user_id: int,
    title: str,
    body: str,
    data: Optional[dict] = None,
) -> None:
    """
    Send a visible push notification to all of a user's devices.
    Removes stale tokens (UnregisteredError) from the database.
    """
    tokens = device_repository.get_tokens_for_user(db, user_id)
    if not tokens:
        logger.warning("No FCM tokens for user_id=%s", user_id)
        return

    data = data or {}
    messages = [
        messaging.Message(
            notification=messaging.Notification(title=title, body=body),
            data={str(k): str(v) for k, v in data.items()},
            token=token,
        )
        for token in tokens
    ]

    try:
        batch_response = messaging.send_each(messages)
        for i, response in enumerate(batch_response.responses):
            if not response.success and response.exception:
                if isinstance(response.exception, messaging.UnregisteredError):
                    device_repository.remove_device_token(db, tokens[i])
                    logger.info(
                        "Removed stale FCM token for user_id=%s", user_id)
                else:
                    logger.warning(
                        "FCM send failed for user_id=%s: %s",
                        user_id,
                        response.exception,
                    )
    except Exception as e:
        logger.error("FCM send_each failed for user_id=%s: %s", user_id, e)
        raise


def send_data_message_to_user(db: Session, user_id: int, data: dict) -> None:
    """
    Send a silent data-only FCM message (no notification payload).
    Used to trigger the client's location check.
    """
    tokens = device_repository.get_tokens_for_user(db, user_id)
    if not tokens:
        logger.warning("No FCM tokens for user_id=%s", user_id)
        return

    str_data = {str(k): str(v) for k, v in data.items()}
    messages = [
        messaging.Message(data=str_data, token=token) for token in tokens
    ]

    try:
        batch_response = messaging.send_each(messages)
        for i, response in enumerate(batch_response.responses):
            if not response.success and response.exception:
                if isinstance(response.exception, messaging.UnregisteredError):
                    device_repository.remove_device_token(db, tokens[i])
                    logger.info(
                        "Removed stale FCM token for user_id=%s", user_id)
                else:
                    logger.warning(
                        "FCM data message failed for user_id=%s: %s",
                        user_id,
                        response.exception,
                    )
    except Exception as e:
        logger.error(
            "FCM send_each (data) failed for user_id=%s: %s", user_id, e
        )
        raise


def run_daily_anniversary_job(db: Session) -> None:
    """
    Scheduled job: find users with geotagged photos from exactly 1 year ago,
    send a silent data message to trigger their devices to run the location check.
    Uses anniversary_timezone for "today" so dates match user expectations.
    """
    tz = ZoneInfo(get_settings().anniversary_timezone)
    today = datetime.now(tz).date()
    try:
        target_date = today.replace(year=today.year - 1)
    except ValueError:
        target_date = today - timedelta(days=365)

    target_date_str = target_date.isoformat()
    user_ids = device_repository.get_all_users_with_devices(db)
    for user_id in user_ids:
        images = image_repository.get_user_images_with_location_on_date(
            db=db,
            user_id=user_id,
            target_date=target_date_str,
        )
        if images:
            logger.info(
                "User %s has %s anniversary photo(s), sending location check trigger",
                user_id,
                len(images),
            )
            send_data_message_to_user(
                db,
                user_id,
                {"type": "anniversary_location_check"},
            )
