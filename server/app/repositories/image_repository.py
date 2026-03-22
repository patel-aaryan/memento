from sqlalchemy import text
from sqlalchemy.orm import Session
from typing import Optional, List, Sequence


def create_image(
    db: Session,
    album_id: int,
    image_url: str,
    user_id: int,
    caption: Optional[str] = None,
    audio_url: Optional[str] = None,
    latitude: Optional[float] = None,
    longitude: Optional[float] = None,
    taken_at: Optional[str] = None,
    location_name: Optional[str] = None,
) -> Optional[dict]:
    """Create a new image. taken_at is ISO datetime from photo EXIF (when the photo was taken)."""
    query = text("""
        INSERT INTO images (album_id, caption, image_url, audio_url, latitude, longitude, user_id, taken_at, location_name)
        VALUES (:album_id, :caption, :image_url, :audio_url, :latitude, :longitude, :user_id, CAST(:taken_at AS TIMESTAMPTZ), :location_name)
        RETURNING id, album_id, caption, image_url, audio_url, latitude, longitude, date_added, taken_at, user_id, location_name, created_at, updated_at
    """)

    try:
        result = db.execute(query, {
            "album_id": album_id,
            "caption": caption,
            "image_url": image_url,
            "audio_url": audio_url,
            "latitude": latitude,
            "longitude": longitude,
            "user_id": user_id,
            "taken_at": taken_at,
            "location_name": location_name,
        })
        db.commit()
        row = result.fetchone()

        if row:
            return _row_to_image_dict(row)
        return None
    except Exception as e:
        db.rollback()
        raise e


def _row_to_image_dict(row) -> dict:
    """Map DB row (id, album_id, caption, image_url, audio_url, latitude, longitude,
    date_added, taken_at, user_id, location_name, created_at, updated_at) to dict."""
    return {
        "id": row[0],
        "album_id": row[1],
        "caption": row[2],
        "image_url": row[3],
        "audio_url": str(row[4]) if row[4] is not None else None,
        "latitude": float(row[5]) if row[5] is not None else None,
        "longitude": float(row[6]) if row[6] is not None else None,
        "date_added": str(row[7]),
        "taken_at": str(row[8]) if row[8] is not None else None,
        "user_id": row[9],
        "location_name": row[10],
        "created_at": str(row[11]),
        "updated_at": str(row[12])
    }


def get_image_by_id(db: Session, image_id: int) -> Optional[dict]:
    """Get an image by ID."""
    query = text("""
        SELECT id, album_id, caption, image_url, audio_url, latitude, longitude,
               date_added, taken_at, user_id, location_name, created_at, updated_at
        FROM images
        WHERE id = :image_id
    """)

    result = db.execute(query, {"image_id": image_id})
    row = result.fetchone()

    if row:
        return _row_to_image_dict(row)
    return None


_UPDATEABLE_IMAGE_KEYS = (
    "caption",
    "image_url",
    "audio_url",
    "latitude",
    "longitude",
    "taken_at",
    "location_name",
)


def update_image(
    db: Session,
    image_id: int,
    updates: dict,
) -> Optional[dict]:
    """Update an image. updates is a dict of field -> value (e.g. from model_dump(exclude_unset=True)).
    Values can be None to clear optional fields (e.g. audio_url)."""
    # Build dynamic update query from only the keys that were sent
    set_clauses = []
    params = {"image_id": image_id}
    for key in _UPDATEABLE_IMAGE_KEYS:
        if key not in updates:
            continue
        value = updates[key]
        if key == "taken_at":
            set_clauses.append("taken_at = CAST(:taken_at AS TIMESTAMPTZ)")
            params["taken_at"] = value
        else:
            set_clauses.append(f"{key} = :{key}")
            params[key] = value

    if not set_clauses:
        return get_image_by_id(db, image_id)

    set_clauses.append("updated_at = CURRENT_TIMESTAMP")

    query = text(f"""
        UPDATE images
        SET {', '.join(set_clauses)}
        WHERE id = :image_id
        RETURNING id, album_id, caption, image_url, audio_url, latitude, longitude,
                  date_added, taken_at, user_id, location_name, created_at, updated_at
    """)

    try:
        print(
            f"[image_repository] UPDATE images SET {', '.join(set_clauses)} WHERE id=:image_id params={params}", flush=True)
        result = db.execute(query, params)
        row = result.fetchone()
        db.commit()
        print(
            f"[image_repository] UPDATE rowcount={result.rowcount} returned_caption={row[2] if row else None!r}", flush=True)
        if row:
            return _row_to_image_dict(row)
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


def bulk_move_images_to_album(
    db: Session,
    image_ids: Sequence[int],
    new_album_id: int,
    user_id: int,
    from_album_id: int,
) -> int:
    """Move images to another album if they belong to user and from_album_id. Returns rows updated."""
    ids = [int(x) for x in image_ids]
    if not ids:
        return 0
    placeholders = ", ".join(f":id_{i}" for i in range(len(ids)))
    params: dict = {
        "new_album_id": new_album_id,
        "user_id": user_id,
        "from_album_id": from_album_id,
    }
    for i, v in enumerate(ids):
        params[f"id_{i}"] = v
    query = text(f"""
        UPDATE images
        SET album_id = :new_album_id, updated_at = CURRENT_TIMESTAMP
        WHERE id IN ({placeholders})
          AND user_id = :user_id
          AND album_id = :from_album_id
    """)
    try:
        result = db.execute(query, params)
        db.commit()
        return result.rowcount or 0
    except Exception as e:
        db.rollback()
        raise e


def count_images_in_album(db: Session, album_id: int) -> int:
    q = text("SELECT COUNT(*) FROM images WHERE album_id = :album_id")
    row = db.execute(q, {"album_id": album_id}).fetchone()
    return int(row[0]) if row else 0


def get_album_images(db: Session, album_id: int) -> List[dict]:
    """Get all images in an album."""
    query = text("""
        SELECT id, album_id, caption, image_url, audio_url, latitude, longitude,
               date_added, taken_at, user_id, location_name, created_at, updated_at
        FROM images
        WHERE album_id = :album_id
        ORDER BY date_added DESC
    """)

    result = db.execute(query, {"album_id": album_id})
    return [_row_to_image_dict(row) for row in result]


def get_album_cover_urls(db: Session, album_id: int, limit: int = 4) -> List[str]:
    """Get up to N image URLs for an album (for cover/thumbnail). Uses oldest-first so when
    there are fewer than 4 photos, the single cover shown is the oldest uploaded."""
    query = text("""
        SELECT image_url FROM images
        WHERE album_id = :album_id
        ORDER BY date_added ASC
        LIMIT :limit
    """)
    result = db.execute(query, {"album_id": album_id, "limit": limit})
    return [row[0] for row in result]


def get_user_images_with_location_on_date(
    db: Session,
    user_id: int,
    target_date: str,
) -> List[dict]:
    """
    Return all images for a user that have latitude/longitude and whose
    (taken_at or date_added) falls on the given calendar date (YYYY-MM-DD).
    """
    query = text("""
        SELECT id, album_id, caption, image_url, audio_url, latitude, longitude,
               date_added, taken_at, user_id, location_name, created_at, updated_at
        FROM images
        WHERE user_id = :user_id
          AND latitude IS NOT NULL
          AND longitude IS NOT NULL
          AND DATE(COALESCE(taken_at, date_added)) = :target_date
    """)

    result = db.execute(
        query,
        {
            "user_id": user_id,
            "target_date": target_date,
        },
    )
    return [_row_to_image_dict(row) for row in result]
