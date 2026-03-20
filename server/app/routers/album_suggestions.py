import asyncio
import logging

from fastapi import APIRouter, Depends, Security, status
from sqlalchemy.orm import Session

from app.config.db import get_db
from app.dependencies.auth import get_current_user, security
from app.schemas.album_suggestion import (
    AlbumSuggestionAcceptResponse,
    AlbumSuggestionCurrentResponse,
)
from app.services import album_suggestion_service

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/album-suggestions", tags=["Album suggestions"])


@router.get(
    "/current",
    response_model=AlbumSuggestionCurrentResponse,
    dependencies=[Security(security)],
)
async def get_current_suggestion(
    current_user: dict = Depends(get_current_user),
):
    uid = current_user["id"]
    payload = await asyncio.to_thread(
        album_suggestion_service.fetch_current_suggestion_blocking,
        uid,
    )
    if payload:
        line = (
            f"[album_suggestions] GET /current user_id={uid} "
            f"-> pending suggestion id={payload.get('id')} "
            f"name={payload.get('album_name')!r} "
            f"image_count={payload.get('image_count')}"
        )
    else:
        line = (
            f"[album_suggestions] GET /current user_id={uid} -> no pending suggestion "
            f"(stale/cooldown/min-photos or no OpenAI key)"
        )
    print(line, flush=True)
    logger.info(line)
    return AlbumSuggestionCurrentResponse(suggestion=payload)


@router.post(
    "/{suggestion_id}/accept",
    response_model=AlbumSuggestionAcceptResponse,
    dependencies=[Security(security)],
)
async def accept_suggestion(
    suggestion_id: int,
    current_user: dict = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    return album_suggestion_service.accept_suggestion(
        db, current_user["id"], suggestion_id
    )


@router.post(
    "/{suggestion_id}/reject",
    status_code=status.HTTP_204_NO_CONTENT,
    dependencies=[Security(security)],
)
async def reject_suggestion(
    suggestion_id: int,
    current_user: dict = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    album_suggestion_service.reject_suggestion(
        db, current_user["id"], suggestion_id
    )
    return None
