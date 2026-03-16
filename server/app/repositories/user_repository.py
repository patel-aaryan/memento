from sqlalchemy import text
from sqlalchemy.orm import Session
from typing import Optional, List, Any
from app.utils.auth import get_password_hash


def create_user(db: Session, email: str, password: str, name: str) -> Optional[dict]:
    """Create a new user in the database."""
    password_hash = get_password_hash(password)
    
    query = text("""
        INSERT INTO users (email, password_hash, name)
        VALUES (:email, :password_hash, :name)
        RETURNING id, email, name, created_at, updated_at, profile_picture_url
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
            return _row_to_user_dict(row, full_row=True)
        return None
    except Exception as e:
        db.rollback()
        raise e


def _row_to_user_dict(row, full_row: bool = False) -> dict:
    """Map DB row to user dict. full_row: include updated_at and profile_picture_url (5/6 cols)."""
    if full_row and len(row) >= 6:
        return {
            "id": row[0],
            "email": row[1],
            "name": row[2],
            "created_at": str(row[3]),
            "updated_at": str(row[4]),
            "profile_picture_url": row[5] if row[5] is not None else None
        }
    return {
        "id": row[0],
        "email": row[1],
        "name": row[2],
        "created_at": str(row[3])
    }


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
    """Get a user by ID (full user object: id, email, name, created_at, updated_at, profile_picture_url)."""
    query = text("""
        SELECT id, email, name, created_at, updated_at, profile_picture_url
        FROM users
        WHERE id = :user_id
    """)
    
    result = db.execute(query, {"user_id": user_id})
    row = result.fetchone()
    
    if row:
        return _row_to_user_dict(row, full_row=True)
    return None


def get_users_by_ids(db: Session, user_ids: List[int]) -> List[dict]:
    """Get multiple users by IDs. Returns list of full user dicts."""
    if not user_ids:
        return []
    query = text("""
        SELECT id, email, name, created_at, updated_at, profile_picture_url
        FROM users
        WHERE id = ANY(:user_ids)
    """)
    result = db.execute(query, {"user_ids": user_ids})
    return [_row_to_user_dict(row, full_row=True) for row in result]


def update_user(db: Session, user_id: int, **fields: Any) -> Optional[dict]:
    """Update user fields. Only provided keyword args are updated. Use profile_picture_url=None to clear."""
    allowed = {"name", "profile_picture_url"}
    updates: List[str] = []
    params: dict[str, Any] = {"user_id": user_id}
    for key, value in fields.items():
        if key not in allowed:
            continue
        if key == "name":
            updates.append("name = :name")
            params["name"] = value
        elif key == "profile_picture_url":
            updates.append("profile_picture_url = :profile_picture_url")
            params["profile_picture_url"] = value
    if not updates:
        return get_user_by_id(db, user_id)
    query = text(f"""
        UPDATE users
        SET {", ".join(updates)}
        WHERE id = :user_id
        RETURNING id, email, name, created_at, updated_at, profile_picture_url
    """)
    try:
        result = db.execute(query, params)
        db.commit()
        row = result.fetchone()
        if row:
            return _row_to_user_dict(row, full_row=True)
        return None
    except Exception as e:
        db.rollback()
        raise e


def update_user_password_hash(db: Session, user_id: int, password_hash: str) -> bool:
    """Update only the password hash for the user."""
    query = text(
        """
        UPDATE users
        SET password_hash = :password_hash
        WHERE id = :user_id
        RETURNING id
        """
    )
    try:
        result = db.execute(
            query,
            {
                "user_id": user_id,
                "password_hash": password_hash,
            },
        )
        row = result.fetchone()
        db.commit()
        return row is not None
    except Exception as e:
        db.rollback()
        raise e
