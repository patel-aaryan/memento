from datetime import datetime, timezone
from typing import Optional

from sqlalchemy import text
from sqlalchemy.orm import Session


def create_reset_code(
    db: Session,
    user_id: int,
    code_hash: str,
    expires_at: datetime,
) -> None:
    query = text(
        """
        INSERT INTO password_reset_codes (user_id, code_hash, expires_at)
        VALUES (:user_id, :code_hash, :expires_at)
        """
    )
    db.execute(
        query,
        {
            "user_id": user_id,
            "code_hash": code_hash,
            "expires_at": expires_at,
        },
    )
    db.commit()


def invalidate_active_codes_for_user(db: Session, user_id: int) -> None:
    query = text(
        """
        UPDATE password_reset_codes
        SET used_at = NOW()
        WHERE user_id = :user_id
          AND used_at IS NULL
          AND expires_at > NOW()
        """
    )
    db.execute(query, {"user_id": user_id})
    db.commit()


def consume_reset_code(db: Session, user_id: int, code_hash: str) -> bool:
    """
    Mark an active matching code as used.
    Returns True only if a valid, unexpired code was consumed.
    """
    query = text(
        """
        UPDATE password_reset_codes
        SET used_at = NOW()
        WHERE id = (
            SELECT id
            FROM password_reset_codes
            WHERE user_id = :user_id
              AND code_hash = :code_hash
              AND used_at IS NULL
              AND expires_at > NOW()
            ORDER BY created_at DESC
            LIMIT 1
            FOR UPDATE
        )
        RETURNING id
        """
    )
    result = db.execute(query, {"user_id": user_id, "code_hash": code_hash})
    row = result.fetchone()
    db.commit()
    return row is not None


def latest_pending_created_at(db: Session, user_id: int) -> Optional[datetime]:
    """
    Return the newest active code timestamp for basic resend throttling.
    """
    query = text(
        """
        SELECT created_at
        FROM password_reset_codes
        WHERE user_id = :user_id
          AND used_at IS NULL
          AND expires_at > NOW()
        ORDER BY created_at DESC
        LIMIT 1
        """
    )
    result = db.execute(query, {"user_id": user_id})
    row = result.fetchone()
    if row is None:
        return None
    created_at = row[0]
    if created_at.tzinfo is None:
        created_at = created_at.replace(tzinfo=timezone.utc)
    return created_at
