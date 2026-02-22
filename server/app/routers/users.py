from fastapi import APIRouter, Depends, Security
from sqlalchemy.orm import Session
from app.config.db import get_db
from app.dependencies.auth import get_current_user, security
from app.schemas.auth import UserResponse
from app.services import user_service

router = APIRouter(prefix="/users", tags=["Users"])


@router.get("/{user_id}", response_model=UserResponse, dependencies=[Security(security)])
async def get_user(
    user_id: int,
    current_user: dict = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """Get a user by ID. Returns the full user object (id, email, name, created_at)."""
    return user_service.get_user(db, user_id)
