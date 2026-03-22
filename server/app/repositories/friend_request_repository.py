from sqlalchemy import text
from sqlalchemy.orm import Session
from typing import List, Optional, Any


def find_pending_incoming(db: Session, recipient_id: int) -> List[dict]:
    """Rows where someone requested to be friends with recipient_id."""
    query = text("""
        SELECT fr.id, fr.requester_id, fr.created_at
        FROM friend_requests fr
        WHERE fr.recipient_id = :rid
        ORDER BY fr.created_at ASC
    """)
    rows = db.execute(query, {"rid": recipient_id}).fetchall()
    return [
        {"id": row[0], "requester_id": row[1], "created_at": row[2]}
        for row in rows
    ]


def find_pending_between(db: Session, user_a: int, user_b: int) -> Optional[dict]:
    """Return pending request row if either direction exists (requester, recipient)."""
    query = text("""
        SELECT id, requester_id, recipient_id
        FROM friend_requests
        WHERE (requester_id = :a AND recipient_id = :b)
           OR (requester_id = :b AND recipient_id = :a)
        LIMIT 1
    """)
    row = db.execute(query, {"a": user_a, "b": user_b}).fetchone()
    if not row:
        return None
    return {"id": row[0], "requester_id": row[1], "recipient_id": row[2]}


def create_request(db: Session, requester_id: int, recipient_id: int) -> bool:
    """Insert pending request. Returns True if inserted, False if duplicate."""
    query = text("""
        INSERT INTO friend_requests (requester_id, recipient_id)
        VALUES (:req, :rec)
        ON CONFLICT (requester_id, recipient_id) DO NOTHING
        RETURNING id
    """)
    try:
        result = db.execute(query, {"req": requester_id, "rec": recipient_id})
        db.commit()
        return result.fetchone() is not None
    except Exception:
        db.rollback()
        raise


def get_request_by_id_for_recipient(
    db: Session, request_id: int, recipient_id: int
) -> Optional[dict]:
    query = text("""
        SELECT id, requester_id, recipient_id
        FROM friend_requests
        WHERE id = :id AND recipient_id = :rec
    """)
    row = db.execute(query, {"id": request_id, "rec": recipient_id}).fetchone()
    if not row:
        return None
    return {"id": row[0], "requester_id": row[1], "recipient_id": row[2]}


def delete_request(db: Session, request_id: int) -> None:
    query = text("DELETE FROM friend_requests WHERE id = :id")
    db.execute(query, {"id": request_id})
    db.commit()
