import logging
from fastapi import APIRouter, Depends, HTTPException, status, Security
from sqlalchemy.orm import Session
from typing import List
from app.config.db import get_db
from app.dependencies.auth import get_current_user, security
from app.schemas.image import ImageCreate, ImageUpdate, ImageResponse
from app.services import image_service

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/images", tags=["Images"])


@router.post("", response_model=ImageResponse, status_code=status.HTTP_201_CREATED, dependencies=[Security(security)])
async def create_image(
    image_data: ImageCreate,
    current_user: dict = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """Create a new image. User must be owner or member of the album."""
    return image_service.create_image(db, image_data, current_user["id"])


@router.get("/{image_id}", response_model=ImageResponse, dependencies=[Security(security)])
async def get_image(
    image_id: int,
    current_user: dict = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """Get an image by ID. User must have access to the album."""
    return image_service.get_image(db, image_id, current_user["id"])


@router.put("/{image_id}", response_model=ImageResponse, dependencies=[Security(security)])
async def update_image(
    image_id: int,
    image_data: ImageUpdate,
    current_user: dict = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """Update an image. Only the creator can update."""
    body = image_data.model_dump()
    print(f"[images] PUT /images/{image_id} body={body}", flush=True)
    return image_service.update_image(db, image_id, image_data, current_user["id"])


@router.delete("/{image_id}", status_code=status.HTTP_204_NO_CONTENT, dependencies=[Security(security)])
async def delete_image(
    image_id: int,
    current_user: dict = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """Delete an image. Only the creator can delete."""
    image_service.delete_image(db, image_id, current_user["id"])
    return None


@router.get("/album/{album_id}", response_model=List[ImageResponse], dependencies=[Security(security)])
async def get_album_images(
    album_id: int,
    current_user: dict = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """Get all images in an album. User must have access to the album."""
    images = image_service.get_album_images(db, album_id, current_user["id"])
    # Debug: print what we're returning so you can confirm caption in DB/response
    for img in images:
        print(f"[images] GET /images/album/{album_id} id={img.id} caption={img.caption!r}", flush=True)
    return images

