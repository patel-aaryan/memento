from fastapi import APIRouter, Depends, HTTPException, status, Security
from sqlalchemy.orm import Session
from typing import List
from app.config.db import get_db
from app.dependencies.auth import get_current_user, security
from app.schemas.album import AlbumCreate, AlbumUpdate, AlbumResponse, AlbumMemberAdd, AlbumMemberResponse
from app.schemas.auth import UserResponse
from app.services import album_service

router = APIRouter(prefix="/albums", tags=["Albums"])


@router.post("", response_model=AlbumResponse, status_code=status.HTTP_201_CREATED, dependencies=[Security(security)])
async def create_album(
    album_data: AlbumCreate,
    current_user: dict = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """Create a new album. The authenticated user becomes the owner."""
    return album_service.create_album(db, album_data, current_user["id"])


@router.get("", response_model=List[AlbumResponse], dependencies=[Security(security)])
async def get_albums(
    current_user: dict = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """Get all albums for the authenticated user (owned or member)."""
    return album_service.get_user_albums(db, current_user["id"])


@router.get("/{album_id}", response_model=AlbumResponse, dependencies=[Security(security)])
async def get_album(
    album_id: int,
    current_user: dict = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """Get an album by ID. User must be owner or member."""
    return album_service.get_album(db, album_id, current_user["id"])


@router.get("/{album_id}/members", response_model=List[UserResponse], dependencies=[Security(security)])
async def get_album_members(
    album_id: int,
    current_user: dict = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """Get all members of an album as full user objects. Caller must be owner or member."""
    return album_service.get_album_members(db, album_id, current_user["id"])


@router.put("/{album_id}", response_model=AlbumResponse, dependencies=[Security(security)])
async def update_album(
    album_id: int,
    album_data: AlbumUpdate,
    current_user: dict = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """Update an album. Only the owner can update."""
    return album_service.update_album(db, album_id, album_data, current_user["id"])


@router.delete("/{album_id}", status_code=status.HTTP_204_NO_CONTENT, dependencies=[Security(security)])
async def delete_album(
    album_id: int,
    current_user: dict = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """Delete an album. Only the owner can delete."""
    album_service.delete_album(db, album_id, current_user["id"])
    return None


@router.post("/{album_id}/members", response_model=dict, status_code=status.HTTP_201_CREATED, dependencies=[Security(security)])
async def add_album_member(
    album_id: int,
    member_data: AlbumMemberAdd,
    current_user: dict = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """Add a member to an album. Only the owner can add members."""
    return album_service.add_album_member(db, album_id, member_data, current_user["id"])


@router.delete("/{album_id}/members/{user_id}", status_code=status.HTTP_204_NO_CONTENT, dependencies=[Security(security)])
async def remove_album_member(
    album_id: int,
    user_id: int,
    current_user: dict = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """Remove a member from an album. Only the owner can remove members."""
    album_service.remove_album_member(db, album_id, user_id, current_user["id"])
    return None


@router.delete("/{album_id}/leave", status_code=status.HTTP_204_NO_CONTENT, dependencies=[Security(security)])
async def leave_album(
    album_id: int,
    current_user: dict = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """Leave an album (remove yourself). Only members can leave; owner must delete the album."""
    album_service.leave_album(db, album_id, current_user["id"])
    return None

