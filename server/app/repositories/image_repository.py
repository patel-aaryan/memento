from sqlalchemy import text
from sqlalchemy.orm import Session
from typing import Optional, List


def create_image(
    db: Session,
    album_id: int,
    image_url: str,
    user_id: int,
    caption: Optional[str] = None,
    latitude: Optional[float] = None,
    longitude: Optional[float] = None
) -> Optional[dict]:
    """Create a new image."""
    query = text("""
        INSERT INTO images (album_id, caption, image_url, latitude, longitude, user_id)
        VALUES (:album_id, :caption, :image_url, :latitude, :longitude, :user_id)
        RETURNING id, album_id, caption, image_url, latitude, longitude, date_added, user_id, created_at, updated_at
    """)
    
    try:
        result = db.execute(query, {
            "album_id": album_id,
            "caption": caption,
            "image_url": image_url,
            "latitude": latitude,
            "longitude": longitude,
            "user_id": user_id
        })
        db.commit()
        row = result.fetchone()
        
        if row:
            return {
                "id": row[0],
                "album_id": row[1],
                "caption": row[2],
                "image_url": row[3],
                "latitude": float(row[4]) if row[4] is not None else None,
                "longitude": float(row[5]) if row[5] is not None else None,
                "date_added": str(row[6]),
                "user_id": row[7],
                "created_at": str(row[8]),
                "updated_at": str(row[9])
            }
        return None
    except Exception as e:
        db.rollback()
        raise e


def get_image_by_id(db: Session, image_id: int) -> Optional[dict]:
    """Get an image by ID."""
    query = text("""
        SELECT id, album_id, caption, image_url, latitude, longitude, date_added, user_id, created_at, updated_at
        FROM images
        WHERE id = :image_id
    """)
    
    result = db.execute(query, {"image_id": image_id})
    row = result.fetchone()
    
    if row:
        return {
            "id": row[0],
            "album_id": row[1],
            "caption": row[2],
            "image_url": row[3],
            "latitude": float(row[4]) if row[4] is not None else None,
            "longitude": float(row[5]) if row[5] is not None else None,
            "date_added": str(row[6]),
            "user_id": row[7],
            "created_at": str(row[8]),
            "updated_at": str(row[9])
        }
    return None


def update_image(
    db: Session,
    image_id: int,
    caption: Optional[str] = None,
    image_url: Optional[str] = None,
    latitude: Optional[float] = None,
    longitude: Optional[float] = None,
    **kwargs  # ignore any extra keys from model_dump()
) -> Optional[dict]:
    """Update an image. Only include fields that are not None."""
    # Build dynamic update query
    updates = []
    params = {"image_id": image_id}
    
    if caption is not None:
        updates.append("caption = :caption")
        params["caption"] = caption
    
    if image_url is not None:
        updates.append("image_url = :image_url")
        params["image_url"] = image_url
    
    if latitude is not None:
        updates.append("latitude = :latitude")
        params["latitude"] = latitude
    
    if longitude is not None:
        updates.append("longitude = :longitude")
        params["longitude"] = longitude
    
    if not updates:
        return get_image_by_id(db, image_id)
    
    # Explicitly set updated_at so the row is touched and trigger runs
    updates.append("updated_at = CURRENT_TIMESTAMP")
    
    query = text(f"""
        UPDATE images
        SET {', '.join(updates)}
        WHERE id = :image_id
        RETURNING id, album_id, caption, image_url, latitude, longitude, date_added, user_id, created_at, updated_at
    """)
    
    try:
        print(f"[image_repository] UPDATE images SET {', '.join(updates)} WHERE id=:image_id params={params}", flush=True)
        result = db.execute(query, params)
        row = result.fetchone()
        db.commit()
        print(f"[image_repository] UPDATE rowcount={result.rowcount} returned_caption={row[2] if row else None!r}", flush=True)
        if row:
            return {
                "id": row[0],
                "album_id": row[1],
                "caption": row[2],
                "image_url": row[3],
                "latitude": float(row[4]) if row[4] is not None else None,
                "longitude": float(row[5]) if row[5] is not None else None,
                "date_added": str(row[6]),
                "user_id": row[7],
                "created_at": str(row[8]),
                "updated_at": str(row[9])
            }
        return None
    except Exception as e:
        db.rollback()
        raise e


def delete_image(db: Session, image_id: int) -> bool:
    """Delete an image."""
    query = text("""
        DELETE FROM images
        WHERE id = :image_id
    """)
    
    try:
        result = db.execute(query, {"image_id": image_id})
        db.commit()
        return result.rowcount > 0
    except Exception as e:
        db.rollback()
        raise e


def get_album_images(db: Session, album_id: int) -> List[dict]:
    """Get all images in an album."""
    query = text("""
        SELECT id, album_id, caption, image_url, latitude, longitude, date_added, user_id, created_at, updated_at
        FROM images
        WHERE album_id = :album_id
        ORDER BY date_added DESC
    """)
    
    result = db.execute(query, {"album_id": album_id})
    images = []
    
    for row in result:
        images.append({
            "id": row[0],
            "album_id": row[1],
            "caption": row[2],
            "image_url": row[3],
            "latitude": float(row[4]) if row[4] is not None else None,
            "longitude": float(row[5]) if row[5] is not None else None,
            "date_added": str(row[6]),
            "user_id": row[7],
            "created_at": str(row[8]),
            "updated_at": str(row[9])
        })
    
    return images

