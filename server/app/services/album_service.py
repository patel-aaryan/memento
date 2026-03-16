import logging
from fastapi import HTTPException, status
from sqlalchemy.orm import Session
from typing import List
from app.repositories import album_repository, album_member_repository, image_repository
from app.repositories.user_repository import get_users_by_ids, get_user_by_id
from app.schemas.album import AlbumCreate, AlbumUpdate, AlbumResponse, AlbumMemberAdd
from app.schemas.auth import UserResponse
from app.services import notification_service

logger = logging.getLogger(__name__)


PERSONAL_ALBUM_NAME = "My Photos"


def get_or_create_personal_album(db: Session, user_id: int) -> dict:
    """Get or create the user's personal album (for homepage/standalone photo uploads)."""
    album = album_repository.get_album_by_owner_and_name(db, user_id, PERSONAL_ALBUM_NAME)
    if album:
        return album
    album = album_repository.create_album(db, PERSONAL_ALBUM_NAME, user_id)
    if not album:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to create personal album",
        )
    album_member_repository.add_album_member(db, album["id"], user_id)
    return album


def create_album(db: Session, album_data: AlbumCreate, owner_id: int) -> AlbumResponse:
    """Create a new album and add owner as a member."""
    # Create the album
    album = album_repository.create_album(db, album_data.name, owner_id)
    if not album:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to create album"
        )
    
    # Add owner as a member
    album_member_repository.add_album_member(db, album["id"], owner_id)
    
    return AlbumResponse(**album)


def get_album(db: Session, album_id: int, user_id: int) -> AlbumResponse:
    """Get an album by ID. User must be owner or member."""
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
    
    return AlbumResponse(**album)


def update_album(db: Session, album_id: int, album_data: AlbumUpdate, user_id: int) -> AlbumResponse:
    """Update an album. Only owner can update."""
    album = album_repository.get_album_by_id(db, album_id)
    if not album:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Album not found"
        )
    
    # Check if user is owner
    if album["owner_id"] != user_id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Only the album owner can update the album"
        )
    
    # Update album
    if album_data.name is None:
        return AlbumResponse(**album)
    
    updated_album = album_repository.update_album(db, album_id, album_data.name)
    if not updated_album:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to update album"
        )
    
    return AlbumResponse(**updated_album)


def delete_album(db: Session, album_id: int, user_id: int) -> None:
    """Delete an album. Only owner can delete."""
    album = album_repository.get_album_by_id(db, album_id)
    if not album:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Album not found"
        )
    
    # Check if user is owner
    if album["owner_id"] != user_id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Only the album owner can delete the album"
        )
    
    success = album_repository.delete_album(db, album_id)
    if not success:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to delete album"
        )


def get_user_albums(db: Session, user_id: int) -> List[AlbumResponse]:
    """Get all albums for a user (owned or member), with cover image URLs."""
    albums = album_repository.get_user_albums(db, user_id)
    result = []
    for album in albums:
        cover_urls = image_repository.get_album_cover_urls(db, album["id"], limit=4)
        result.append(AlbumResponse(cover_image_urls=cover_urls, **album))
    return result


def add_album_member(db: Session, album_id: int, member_data: AlbumMemberAdd, user_id: int) -> dict:
    """Add a member to an album. Only owner can add members."""
    album = album_repository.get_album_by_id(db, album_id)
    if not album:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Album not found"
        )
    
    # Check if user is owner
    if album["owner_id"] != user_id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Only the album owner can add members"
        )
    
    # Don't allow adding the owner as a member (they're already added during creation)
    if album["owner_id"] == member_data.user_id:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Album owner is already a member"
        )
    
    member = album_member_repository.add_album_member(db, album_id, member_data.user_id)
    if not member:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="User is already a member of this album"
        )
    
    # Send push notification to the invited user
    try:
        inviter = get_user_by_id(db, user_id)
        if inviter:
            title = "Album invite"
            body = f"{inviter['name']} added you to the album \"{album['name']}\""
            notification_service.send_push_to_user(
                db,
                member_data.user_id,
                title,
                body,
                data={"type": "album_invite", "album_id": str(album_id)}
            )
    except Exception as e:
        logger.warning(
            "Failed to send album invite notification to user_id=%s: %s",
            member_data.user_id,
            e
        )
    
    return member


def remove_album_member(db: Session, album_id: int, member_user_id: int, user_id: int) -> None:
    """Remove a member from an album. Only owner can remove members."""
    album = album_repository.get_album_by_id(db, album_id)
    if not album:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Album not found"
        )
    
    # Check if user is owner
    if album["owner_id"] != user_id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Only the album owner can remove members"
        )
    
    # Don't allow removing the owner
    if album["owner_id"] == member_user_id:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Cannot remove the album owner"
        )
    
    success = album_member_repository.remove_album_member(db, album_id, member_user_id)
    if not success:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Member not found in album"
        )


def leave_album(db: Session, album_id: int, user_id: int) -> None:
    """Remove the current user from an album (leave). Owner cannot leave."""
    album = album_repository.get_album_by_id(db, album_id)
    if not album:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Album not found"
        )
    if album["owner_id"] == user_id:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Album owner cannot leave; delete the album to remove it for everyone"
        )
    if not album_member_repository.is_album_member(db, album_id, user_id):
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="You are not a member of this album"
        )
    success = album_member_repository.remove_album_member(db, album_id, user_id)
    if not success:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Member not found in album"
        )


def get_album_members(db: Session, album_id: int, user_id: int) -> List[UserResponse]:
    """Get all members of an album as full user objects. Caller must be owner or member."""
    album = album_repository.get_album_by_id(db, album_id)
    if not album:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Album not found"
        )
    is_owner = album["owner_id"] == user_id
    is_member = album_member_repository.is_album_member(db, album_id, user_id)
    if not (is_owner or is_member):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="You don't have access to this album"
        )
    member_rows = album_member_repository.get_album_members(db, album_id)
    owner_id = album["owner_id"]
    user_ids = list({owner_id} | {m["user_id"] for m in member_rows})
    users = get_users_by_ids(db, user_ids)
    user_by_id = {u["id"]: u for u in users}
    ordered = [user_by_id[owner_id]] if owner_id in user_by_id else []
    for m in member_rows:
        uid = m["user_id"]
        if uid != owner_id and uid in user_by_id:
            ordered.append(user_by_id[uid])
    return [UserResponse(**u) for u in ordered]

