from sqlalchemy import text
from sqlalchemy.orm import Session
from typing import Optional, List
from app.utils.auth import get_password_hash


def create_user(db: Session, email: str, password: str, name: str) -> Optional[dict]:
    """Create a new user in the database."""
    password_hash = get_password_hash(password)
    
    query = text("""
        INSERT INTO users (email, password_hash, name)
        VALUES (:email, :password_hash, :name)
        RETURNING id, email, name, created_at
    """)
    
    try:
        result = db.execute(query, {
            "email": email,
            "password_hash": password_hash,
            "name": name
        })
        db.commit()
        row = result.fetchone()
        
        if row:
            return {
                "id": row[0],
                "email": row[1],
                "name": row[2],
                "created_at": str(row[3])
            }
        return None
    except Exception as e:
        db.rollback()
        raise e


def get_user_by_email(db: Session, email: str) -> Optional[dict]:
    """Get a user by email."""
    query = text("""
        SELECT id, email, password_hash, name, created_at
        FROM users
        WHERE email = :email
    """)
    
    result = db.execute(query, {"email": email})
    row = result.fetchone()
    
    if row:
        return {
            "id": row[0],
            "email": row[1],
            "password_hash": row[2],
            "name": row[3],
            "created_at": str(row[4])
        }
    return None


def get_user_by_id(db: Session, user_id: int) -> Optional[dict]:
    """Get a user by ID."""
    query = text("""
        SELECT id, email, name, created_at
        FROM users
        WHERE id = :user_id
    """)
    
    result = db.execute(query, {"user_id": user_id})
    row = result.fetchone()
    
    if row:
        return {
            "id": row[0],
            "email": row[1],
            "name": row[2],
            "created_at": str(row[3])
        }
    return None


def get_users_by_ids(db: Session, user_ids: List[int]) -> List[dict]:
    """Get multiple users by IDs. Returns list of user dicts (id, email, name, created_at)."""
    if not user_ids:
        return []
    query = text("""
        SELECT id, email, name, created_at
        FROM users
        WHERE id = ANY(:user_ids)
    """)
    result = db.execute(query, {"user_ids": user_ids})
    return [
        {"id": row[0], "email": row[1], "name": row[2], "created_at": str(row[3])}
        for row in result
    ]
