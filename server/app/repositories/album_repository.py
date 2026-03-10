from sqlalchemy import text
from sqlalchemy.orm import Session
from typing import Optional, List


def create_album(db: Session, name: str, owner_id: int) -> Optional[dict]:
    """Create a new album."""
    query = text("""
        INSERT INTO albums (name, owner_id)
        VALUES (:name, :owner_id)
        RETURNING id, name, owner_id, created_at, updated_at
    """)
    
    try:
        result = db.execute(query, {
            "name": name,
            "owner_id": owner_id
        })
        db.commit()
        row = result.fetchone()
        
        if row:
            return {
                "id": row[0],
                "name": row[1],
                "owner_id": row[2],
                "created_at": str(row[3]),
                "updated_at": str(row[4])
            }
        return None
    except Exception as e:
        db.rollback()
        raise e


def get_album_by_owner_and_name(db: Session, owner_id: int, name: str) -> Optional[dict]:
    """Get an album by owner and exact name."""
    query = text("""
        SELECT id, name, owner_id, created_at, updated_at
        FROM albums
        WHERE owner_id = :owner_id AND name = :name
        LIMIT 1
    """)
    result = db.execute(query, {"owner_id": owner_id, "name": name})
    row = result.fetchone()
    if row:
        return {
            "id": row[0],
            "name": row[1],
            "owner_id": row[2],
            "created_at": str(row[3]),
            "updated_at": str(row[4]),
        }
    return None


def get_album_by_id(db: Session, album_id: int) -> Optional[dict]:
    """Get an album by ID."""
    query = text("""
        SELECT id, name, owner_id, created_at, updated_at
        FROM albums
        WHERE id = :album_id
    """)
    
    result = db.execute(query, {"album_id": album_id})
    row = result.fetchone()
    
    if row:
        return {
            "id": row[0],
            "name": row[1],
            "owner_id": row[2],
            "created_at": str(row[3]),
            "updated_at": str(row[4])
        }
    return None


def update_album(db: Session, album_id: int, name: str) -> Optional[dict]:
    """Update an album's name."""
    query = text("""
        UPDATE albums
        SET name = :name
        WHERE id = :album_id
        RETURNING id, name, owner_id, created_at, updated_at
    """)
    
    try:
        result = db.execute(query, {
            "album_id": album_id,
            "name": name
        })
        db.commit()
        row = result.fetchone()
        
        if row:
            return {
                "id": row[0],
                "name": row[1],
                "owner_id": row[2],
                "created_at": str(row[3]),
                "updated_at": str(row[4])
            }
        return None
    except Exception as e:
        db.rollback()
        raise e


def delete_album(db: Session, album_id: int) -> bool:
    """Delete an album."""
    query = text("""
        DELETE FROM albums
        WHERE id = :album_id
    """)
    
    try:
        result = db.execute(query, {"album_id": album_id})
        db.commit()
        return result.rowcount > 0
    except Exception as e:
        db.rollback()
        raise e


def get_user_albums(db: Session, user_id: int) -> List[dict]:
    """Get all albums where user is owner or member."""
    query = text("""
        SELECT DISTINCT a.id, a.name, a.owner_id, a.created_at, a.updated_at
        FROM albums a
        LEFT JOIN album_members am ON a.id = am.album_id
        WHERE a.owner_id = :user_id OR am.user_id = :user_id
        ORDER BY a.created_at DESC
    """)
    
    result = db.execute(query, {"user_id": user_id})
    albums = []
    
    for row in result:
        albums.append({
            "id": row[0],
            "name": row[1],
            "owner_id": row[2],
            "created_at": str(row[3]),
            "updated_at": str(row[4])
        })
    
    return albums

