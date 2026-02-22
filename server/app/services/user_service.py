from fastapi import HTTPException, status
from sqlalchemy.orm import Session
from app.repositories.user_repository import get_user_by_id
from app.schemas.auth import UserResponse


def get_user(db: Session, user_id: int) -> UserResponse:
    """Get a user by ID. Returns 404 if not found."""
    user = get_user_by_id(db, user_id)
    if not user:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="User not found"
        )
    return UserResponse(**user)
