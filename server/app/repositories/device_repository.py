from sqlalchemy import text
from sqlalchemy.orm import Session
from typing import List


def upsert_device_token(db: Session, user_id: int, fcm_token: str) -> None:
    """Insert or update a device token for a user. Handles UNIQUE(user_id, fcm_token) constraint."""
    if not fcm_token or not fcm_token.strip():
        return

    query = text("""
        INSERT INTO user_devices (user_id, fcm_token)
        VALUES (:user_id, :fcm_token)
        ON CONFLICT (user_id, fcm_token) DO UPDATE SET updated_at = CURRENT_TIMESTAMP
    """)
    db.execute(query, {"user_id": user_id, "fcm_token": fcm_token.strip()})
    db.commit()


def get_tokens_for_user(db: Session, user_id: int) -> List[str]:
    """Return all FCM tokens for a user."""
    query = text("""
        SELECT fcm_token FROM user_devices
        WHERE user_id = :user_id
    """)
    result = db.execute(query, {"user_id": user_id})
    return [row[0] for row in result]


def get_all_users_with_devices(db: Session) -> List[int]:
    """Return user IDs that have at least one registered device."""
    query = text("""
        SELECT DISTINCT user_id FROM user_devices
    """)
    result = db.execute(query)
    return [row[0] for row in result]


def remove_device_token(db: Session, fcm_token: str) -> None:
    """Delete a stale or invalidated token."""
    query = text("""
        DELETE FROM user_devices WHERE fcm_token = :fcm_token
    """)
    db.execute(query, {"fcm_token": fcm_token})
    db.commit()


def remove_device_token_for_user(db: Session, user_id: int, fcm_token: str) -> None:
    """Remove a device token for a specific user. Only removes if both user_id and fcm_token match."""
    if not fcm_token or not fcm_token.strip():
        return
    query = text("""
        DELETE FROM user_devices WHERE user_id = :user_id AND fcm_token = :fcm_token
    """)
    db.execute(query, {"user_id": user_id, "fcm_token": fcm_token.strip()})
    db.commit()
