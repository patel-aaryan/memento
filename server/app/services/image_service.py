from fastapi import HTTPException, status
from sqlalchemy.orm import Session
from typing import List
from app.repositories import image_repository, album_repository, album_member_repository
from app.schemas.image import ImageCreate, ImageUpdate, ImageResponse


def create_image(db: Session, image_data: ImageCreate, user_id: int) -> ImageResponse:
    """Create a new image. User must be owner or member of the album."""
    # Verify album exists and user has access
    album = album_repository.get_album_by_id(db, image_data.album_id)
    if not album:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Album not found"
        )
    
    # Check if user is owner or member
    is_owner = album["owner_id"] == user_id
    is_member = album_member_repository.is_album_member(db, image_data.album_id, user_id)
    
    if not (is_owner or is_member):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="You don't have access to this album"
        )
    
    # Create the image (taken_at = when photo was taken from EXIF, if provided)
    image = image_repository.create_image(
        db=db,
        album_id=image_data.album_id,
        image_url=image_data.image_url,
        user_id=user_id,
        caption=image_data.caption,
        audio_url=image_data.audio_url,
        latitude=image_data.latitude,
        longitude=image_data.longitude,
        taken_at=image_data.taken_at
    )
    
    if not image:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to create image"
        )
    
    return ImageResponse(**image)


def get_image(db: Session, image_id: int, user_id: int) -> ImageResponse:
    """Get an image by ID. User must have access to the album."""
    image = image_repository.get_image_by_id(db, image_id)
    if not image:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Image not found"
        )
    
    # Verify user has access to the album
    album = album_repository.get_album_by_id(db, image["album_id"])
    if not album:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Album not found"
        )
    
    # Check if user is owner or member
    is_owner = album["owner_id"] == user_id
    is_member = album_member_repository.is_album_member(db, image["album_id"], user_id)
    
    if not (is_owner or is_member):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="You don't have access to this image"
        )
    
    return ImageResponse(**image)


def update_image(db: Session, image_id: int, image_data: ImageUpdate, user_id: int) -> ImageResponse:
    """Update an image. Only the creator can update."""
    image = image_repository.get_image_by_id(db, image_id)
    if not image:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Image not found"
        )
    
    # Check if user is the creator
    if image["user_id"] != user_id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Only the image creator can update the image"
        )
    
    # Only pass fields that were actually sent in the request (exclude_unset)
    update_kwargs = image_data.model_dump(exclude_unset=True)
    if not update_kwargs:
        return ImageResponse(**image)
    
    print(f"[image_service] update_image id={image_id} update_kwargs={update_kwargs}", flush=True)
    # Update the image
    updated_image = image_repository.update_image(
        db=db,
        image_id=image_id,
        **update_kwargs
    )
    
    if not updated_image:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to update image"
        )
    
    print(f"[image_service] update_image returned caption={updated_image.get('caption')!r}", flush=True)
    return ImageResponse(**updated_image)


def delete_image(db: Session, image_id: int, user_id: int) -> None:
    """Delete an image. Only the creator can delete."""
    image = image_repository.get_image_by_id(db, image_id)
    if not image:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Image not found"
        )
    
    # Check if user is the creator
    if image["user_id"] != user_id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Only the image creator can delete the image"
        )
    
    success = image_repository.delete_image(db, image_id)
    if not success:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to delete image"
        )


def get_album_images(db: Session, album_id: int, user_id: int) -> List[ImageResponse]:
    """Get all images in an album. User must have access to the album."""
    # Verify album exists and user has access
    album = album_repository.get_album_by_id(db, album_id)
    if not album:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Album not found"
        )
    
    # Check if user is owner or member
    is_owner = album["owner_id"] == user_id
    is_member = album_member_repository.is_album_member(db, album_id, user_id)
    
    if not (is_owner or is_member):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="You don't have access to this album"
        )
    
    images = image_repository.get_album_images(db, album_id)
    return [ImageResponse(**image) for image in images]

