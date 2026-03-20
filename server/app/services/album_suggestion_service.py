import logging
import random
from datetime import datetime, timedelta, timezone
from typing import List, Optional

from fastapi import HTTPException, status
from sqlalchemy.orm import Session

from app.config.db import SessionLocal
from app.repositories import album_repository, image_repository, suggestion_repository
from app.schemas.album import AlbumCreate
from app.services import album_service
from app.services.openai_album_suggestion import request_album_suggestion

logger = logging.getLogger(__name__)

MIN_MY_PHOTOS = 4
MAX_VISION_IMAGES = 24
MAX_DAILY_OPENAI_CALLS = 20


def _personal_album_name() -> str:
    return album_service.PERSONAL_ALBUM_NAME


def bump_suggestion_stale(db: Session, user_id: int) -> None:
    """My Photos changed or user accepted/rejected — next GET may run OpenAI (subject to cooldown)."""
    suggestion_repository.bump_content_generation(db, user_id)


def _sample_images(images: List[dict], max_n: int) -> List[dict]:
    if len(images) <= max_n:
        return images
    recent = images[:16]
    rest = images[16:]
    need = max_n - len(recent)
    if need <= 0:
        return images[:max_n]
    extra = random.sample(rest, min(need, len(rest)))
    return recent + extra


def try_refresh_suggestion_from_poll(db: Session, user_id: int) -> None:
    """
    If content is stale vs last processed OpenAI run, and cooldown elapsed, call OpenAI now.
    Does nothing when a pending suggestion already exists (caller should skip).
    """
    from app.config.settings import get_settings

    settings = get_settings()
    if not settings.openai_api_key:
        return

    if suggestion_repository.get_pending_for_user(db, user_id):
        return

    content_gen, processed_gen = suggestion_repository.get_generation_and_processed(
        db, user_id
    )
    if content_gen <= processed_gen:
        print(
            f"[album_suggestion_service] skip OpenAI user_id={user_id}: "
            f"content_generation={content_gen} <= processed_generation={processed_gen}",
            flush=True,
        )
        return

    now = datetime.now(timezone.utc)
    cooldown_s = settings.album_suggestion_openai_cooldown_seconds
    last_openai = suggestion_repository.get_last_openai_at(db, user_id)
    if last_openai is not None:
        elapsed = (now - last_openai).total_seconds()
        if elapsed < cooldown_s:
            print(
                f"[album_suggestion_service] skip OpenAI user_id={user_id}: "
                f"cooldown {elapsed:.0f}s < {cooldown_s}s",
                flush=True,
            )
            return

    personal = album_repository.get_album_by_owner_and_name(
        db, user_id, _personal_album_name()
    )
    if not personal:
        return

    personal_id = int(personal["id"])
    n = image_repository.count_images_in_album(db, personal_id)
    if n < MIN_MY_PHOTOS:
        return

    used = suggestion_repository.get_usage_today_utc(db, user_id)
    if used >= MAX_DAILY_OPENAI_CALLS:
        print(
            f"[album_suggestion_service] skip OpenAI user_id={user_id}: daily cap",
            flush=True,
        )
        return

    gen_snapshot = suggestion_repository.get_generation_and_processed(db, user_id)[0]

    images = image_repository.get_album_images(db, personal_id)
    sampled = _sample_images(images, MAX_VISION_IMAGES)
    allowed = {int(i["id"]) for i in sampled}

    print(
        f"[album_suggestion_service] OpenAI (sync GET) user_id={user_id} "
        f"my_photos_total={n} images_sent={len(sampled)} gen_snapshot={gen_snapshot}",
        flush=True,
    )
    try:
        has_sug, name, ids = request_album_suggestion(sampled)
    except Exception as e:
        print(
            f"[album_suggestion_service] OpenAI error user_id={user_id}: {e}",
            flush=True,
        )
        logger.warning("OpenAI album suggestion failed user_id=%s: %s", user_id, e)
        return

    suggestion_repository.increment_usage(db, user_id)
    suggestion_repository.mark_openai_completed(db, user_id, gen_snapshot)

    if not has_sug or len(ids) < 3:
        print(
            f"[album_suggestion_service] no cluster user_id={user_id} "
            f"has_suggestion={has_sug} ids_len={len(ids)}",
            flush=True,
        )
        return

    ids = [i for i in ids if i in allowed]
    if len(ids) < 3:
        return

    in_my_photos = {int(i["id"]) for i in images}
    still_ok = [i for i in ids if i in in_my_photos]
    if len(still_ok) < 3:
        return

    suggestion_repository.insert_pending(db, user_id, name, still_ok)
    print(
        f"[album_suggestion_service] pending suggestion user_id={user_id} "
        f"name={name!r} image_ids={still_ok}",
        flush=True,
    )


def fetch_current_suggestion_blocking(user_id: int) -> Optional[dict]:
    """Used from GET /current via thread pool: own DB session, may call OpenAI synchronously."""
    db = SessionLocal()
    try:
        payload = get_current_suggestion_payload(db, user_id)
        if payload:
            return payload
        try_refresh_suggestion_from_poll(db, user_id)
        return get_current_suggestion_payload(db, user_id)
    finally:
        db.close()


def get_current_suggestion_payload(
    db: Session, user_id: int
) -> Optional[dict]:
    row = suggestion_repository.get_pending_for_user(db, user_id)
    if not row:
        return None

    personal = album_repository.get_album_by_owner_and_name(
        db, user_id, _personal_album_name()
    )
    if not personal:
        return None

    images = image_repository.get_album_images(db, int(personal["id"]))
    by_id = {int(i["id"]): i for i in images}
    preview_urls: List[str] = []
    for iid in row["image_ids"]:
        img = by_id.get(iid)
        if img:
            preview_urls.append(img["image_url"])
        if len(preview_urls) >= 6:
            break

    return {
        "id": row["id"],
        "album_name": row["album_name"],
        "image_ids": row["image_ids"],
        "preview_image_urls": preview_urls,
        "image_count": len(row["image_ids"]),
    }


def reject_suggestion(db: Session, user_id: int, suggestion_id: int) -> None:
    row = suggestion_repository.get_by_id_for_user(db, suggestion_id, user_id)
    if not row or row["status"] != suggestion_repository.STATUS_PENDING:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Suggestion not found")
    suggestion_repository.mark_status(
        db, suggestion_id, user_id, suggestion_repository.STATUS_REJECTED
    )
    bump_suggestion_stale(db, user_id)


def accept_suggestion(db: Session, user_id: int, suggestion_id: int) -> dict:
    row = suggestion_repository.get_by_id_for_user(db, suggestion_id, user_id)
    if not row or row["status"] != suggestion_repository.STATUS_PENDING:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Suggestion not found")

    personal = album_repository.get_album_by_owner_and_name(
        db, user_id, _personal_album_name()
    )
    if not personal:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Personal album not found",
        )
    personal_id = int(personal["id"])

    image_ids = row["image_ids"]
    images = image_repository.get_album_images(db, personal_id)
    in_album = {int(i["id"]) for i in images}
    valid = [i for i in image_ids if i in in_album]
    if len(valid) < 3:
        suggestion_repository.mark_status(
            db, suggestion_id, user_id, suggestion_repository.STATUS_SUPERSEDED
        )
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Suggested photos are no longer all in My Photos",
        )

    album_name = (row["album_name"] or "New album").strip() or "New album"
    created = album_service.create_album(
        db, AlbumCreate(name=album_name), user_id
    )
    new_id = int(created.id)

    moved = image_repository.bulk_move_images_to_album(
        db, valid, new_id, user_id, personal_id
    )
    if moved != len(valid):
        logger.warning(
            "accept_suggestion partial move user_id=%s expected=%s got=%s",
            user_id,
            len(valid),
            moved,
        )

    suggestion_repository.mark_status(
        db, suggestion_id, user_id, suggestion_repository.STATUS_ACCEPTED
    )
    bump_suggestion_stale(db, user_id)

    return {"album_id": new_id, "album_name": album_name, "images_moved": moved}
