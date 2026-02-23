from fastapi import HTTPException, status
from sqlalchemy.orm import Session
from app.repositories.user_repository import get_user_by_id, update_user as repo_update_user
from app.schemas.auth import UserResponse, UserUpdate


def get_user(db: Session, user_id: int) -> UserResponse:
    """Get a user by ID. Returns 404 if not found."""
    user = get_user_by_id(db, user_id)
    if not user:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="User not found"
        )
    return UserResponse(**user)


def update_me(db: Session, user_id: int, data: UserUpdate) -> UserResponse:
    """Update the current user. Only provided fields are updated. Returns updated user."""
    payload = data.model_dump(exclude_unset=True)
    if not payload:
        user = get_user_by_id(db, user_id)
        if not user:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="User not found"
            )
        return UserResponse(**user)
    updated = repo_update_user(db, user_id, **payload)
    if not updated:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="User not found"
        )
    return UserResponse(**updated)
