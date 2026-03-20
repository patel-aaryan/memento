from __future__ import annotations

from datetime import datetime, timezone
from typing import List, Optional, Tuple

from sqlalchemy import text
from sqlalchemy.orm import Session

STATUS_PENDING = "pending"
STATUS_ACCEPTED = "accepted"
STATUS_REJECTED = "rejected"
STATUS_SUPERSEDED = "superseded"
STATUS_EMPTY = "empty"
STATUS_FAILED = "failed"


def supersede_pending_for_user(db: Session, user_id: int) -> None:
    q = text("""
        UPDATE album_suggestions
        SET status = :superseded, updated_at = CURRENT_TIMESTAMP
        WHERE user_id = :user_id AND status = :pending
    """)
    db.execute(
        q,
        {
            "user_id": user_id,
            "pending": STATUS_PENDING,
            "superseded": STATUS_SUPERSEDED,
        },
    )
    db.commit()


def ensure_user_state_row(db: Session, user_id: int) -> None:
    q = text("""
        INSERT INTO album_suggestion_user_state (user_id, content_generation, processed_generation)
        VALUES (:user_id, 0, -1)
        ON CONFLICT (user_id) DO NOTHING
    """)
    db.execute(q, {"user_id": user_id})
    db.commit()


def bump_content_generation(db: Session, user_id: int) -> None:
    ensure_user_state_row(db, user_id)
    q = text("""
        UPDATE album_suggestion_user_state
        SET content_generation = content_generation + 1
        WHERE user_id = :user_id
    """)
    db.execute(q, {"user_id": user_id})
    db.commit()


def get_generation_and_processed(db: Session, user_id: int) -> Tuple[int, int]:
    ensure_user_state_row(db, user_id)
    q = text("""
        SELECT content_generation, processed_generation
        FROM album_suggestion_user_state
        WHERE user_id = :user_id
    """)
    row = db.execute(q, {"user_id": user_id}).fetchone()
    if not row:
        return (0, -1)
    return (int(row[0]), int(row[1]))


def get_last_openai_at(db: Session, user_id: int) -> Optional[datetime]:
    q = text("""
        SELECT last_openai_at FROM album_suggestion_user_state
        WHERE user_id = :user_id
    """)
    row = db.execute(q, {"user_id": user_id}).fetchone()
    if not row or row[0] is None:
        return None
    return row[0]


def mark_openai_completed(
    db: Session, user_id: int, processed_up_to_generation: int
) -> None:
    """After a successful OpenAI response (even if no cluster): advance processed + cooldown clock."""
    ensure_user_state_row(db, user_id)
    now = datetime.now(timezone.utc)
    q = text("""
        UPDATE album_suggestion_user_state
        SET processed_generation = :pg,
            last_openai_at = :ts
        WHERE user_id = :user_id
    """)
    db.execute(
        q,
        {
            "user_id": user_id,
            "pg": processed_up_to_generation,
            "ts": now,
        },
    )
    db.commit()


def get_pending_for_user(db: Session, user_id: int) -> Optional[dict]:
    q = text("""
        SELECT id, user_id, status, album_name, image_ids, error_message, created_at, updated_at
        FROM album_suggestions
        WHERE user_id = :user_id AND status = :pending
        ORDER BY created_at DESC
        LIMIT 1
    """)
    row = db.execute(
        q, {"user_id": user_id, "pending": STATUS_PENDING}
    ).fetchone()
    if not row:
        return None
    return _row_to_dict(row)


def get_by_id_for_user(db: Session, suggestion_id: int, user_id: int) -> Optional[dict]:
    q = text("""
        SELECT id, user_id, status, album_name, image_ids, error_message, created_at, updated_at
        FROM album_suggestions
        WHERE id = :sid AND user_id = :user_id
    """)
    row = db.execute(q, {"sid": suggestion_id, "user_id": user_id}).fetchone()
    if not row:
        return None
    return _row_to_dict(row)


def _row_to_dict(row) -> dict:
    image_ids = row[4]
    if image_ids is None:
        ids_list: List[int] = []
    else:
        ids_list = [int(x) for x in list(image_ids)]
    return {
        "id": row[0],
        "user_id": row[1],
        "status": row[2],
        "album_name": row[3] or "",
        "image_ids": ids_list,
        "error_message": row[5],
        "created_at": str(row[6]),
        "updated_at": str(row[7]),
    }


def insert_pending(
    db: Session,
    user_id: int,
    album_name: str,
    image_ids: List[int],
) -> dict:
    pg_array = "{" + ",".join(str(int(i)) for i in image_ids) + "}"
    q = text("""
        INSERT INTO album_suggestions (user_id, status, album_name, image_ids)
        VALUES (:user_id, :status, :album_name, CAST(:image_ids AS INTEGER[]))
        RETURNING id, user_id, status, album_name, image_ids, error_message, created_at, updated_at
    """)
    result = db.execute(
        q,
        {
            "user_id": user_id,
            "status": STATUS_PENDING,
            "album_name": album_name,
            "image_ids": pg_array,
        },
    )
    row = result.fetchone()
    db.commit()
    if not row:
        return {}
    return _row_to_dict(row)


def mark_status(db: Session, suggestion_id: int, user_id: int, status: str) -> bool:
    q = text("""
        UPDATE album_suggestions
        SET status = :status, updated_at = CURRENT_TIMESTAMP
        WHERE id = :sid AND user_id = :user_id
    """)
    result = db.execute(
        q, {"status": status, "sid": suggestion_id, "user_id": user_id}
    )
    db.commit()
    return (result.rowcount or 0) > 0


def record_failed(db: Session, user_id: int, message: str) -> None:
    q = text("""
        INSERT INTO album_suggestions (user_id, status, album_name, image_ids, error_message)
        VALUES (:user_id, :status, '', '{}', :err)
    """)
    db.execute(
        q,
        {
            "user_id": user_id,
            "status": STATUS_FAILED,
            "err": message[:2000],
        },
    )
    db.commit()


def get_usage_today_utc(db: Session, user_id: int) -> int:
    today = datetime.now(timezone.utc).date()
    q = text("""
        SELECT api_calls FROM album_suggestion_usage
        WHERE user_id = :user_id AND usage_date = :d
    """)
    row = db.execute(q, {"user_id": user_id, "d": today}).fetchone()
    return int(row[0]) if row else 0


def increment_usage(db: Session, user_id: int) -> None:
    today = datetime.now(timezone.utc).date()
    q = text("""
        INSERT INTO album_suggestion_usage (user_id, usage_date, api_calls)
        VALUES (:user_id, :d, 1)
        ON CONFLICT (user_id, usage_date)
        DO UPDATE SET api_calls = album_suggestion_usage.api_calls + 1
    """)
    db.execute(q, {"user_id": user_id, "d": today})
    db.commit()
