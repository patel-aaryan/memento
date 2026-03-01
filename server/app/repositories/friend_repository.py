from sqlalchemy import text
from sqlalchemy.orm import Session
from typing import List, Optional


def add_friendship(db: Session, user_id: int, friend_id: int) -> bool:
    """
    Add a bidirectional friendship. Returns True if added, False if already friends.
    """
    if user_id == friend_id:
        return False
    ua, ub = min(user_id, friend_id), max(user_id, friend_id)
    query = text("""
        INSERT INTO friends (user_a_id, user_b_id)
        VALUES (:ua, :ub)
        ON CONFLICT (user_a_id, user_b_id) DO NOTHING
        RETURNING id
    """)
    try:
        result = db.execute(query, {"ua": ua, "ub": ub})
        db.commit()
        return result.fetchone() is not None
    except Exception:
        db.rollback()
        raise


def get_friend_ids(db: Session, user_id: int) -> List[int]:
    """Get IDs of all friends for a user (bidirectional)."""
    query = text("""
        SELECT user_a_id, user_b_id FROM friends
        WHERE user_a_id = :uid OR user_b_id = :uid
    """)
    result = db.execute(query, {"uid": user_id})
    ids = []
    for row in result:
        other = row[1] if row[0] == user_id else row[0]
        ids.append(other)
    return ids
