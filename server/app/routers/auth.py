from fastapi import APIRouter, Depends, HTTPException, status, Security
from sqlalchemy.orm import Session
from app.config.db import get_db
from app.schemas.auth import UserRegister, UserLogin, Token, UserResponse, UserUpdate
from app.repositories.user_repository import create_user, get_user_by_email
from app.utils.auth import verify_password, create_access_token
from app.dependencies.auth import get_current_user, security
from app.services import user_service

router = APIRouter(prefix="/auth", tags=["Authentication"])


@router.post("/register", response_model=UserResponse, status_code=status.HTTP_201_CREATED)
async def register(user_data: UserRegister, db: Session = Depends(get_db)):
    """Register a new user."""
    # Check if user already exists
    existing_user = get_user_by_email(db, user_data.email)
    if existing_user:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Email already registered"
        )
    
    # Create new user
    try:
        new_user = create_user(db, user_data.email, user_data.password, user_data.name)
        if new_user is None:
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail="Failed to create user"
            )
        return new_user
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Error creating user: {str(e)}"
        )


@router.post("/login", response_model=Token)
async def login(user_data: UserLogin, db: Session = Depends(get_db)):
    """Login and get access token."""
    # Get user by email
    user = get_user_by_email(db, user_data.email)
    if not user:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Incorrect email or password"
        )
    
    # Verify password
    if not verify_password(user_data.password, user["password_hash"]):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Incorrect email or password"
        )
    
    # Create access token
    access_token = create_access_token(data={"sub": str(user["id"])})
    
    return {
        "access_token": access_token,
        "token_type": "bearer"
    }


@router.get(
    "/me",
    response_model=UserResponse,
    dependencies=[Security(security)]
)
async def get_me(current_user: dict = Depends(get_current_user)):
    """Get current authenticated user (full user object: id, email, name, created_at, updated_at, profile_picture_url)."""
    return current_user


@router.patch(
    "/me",
    response_model=UserResponse,
    dependencies=[Security(security)]
)
async def update_me(
    data: UserUpdate,
    current_user: dict = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """Update the current user. Send only the fields you want to change (name, profile_picture_url). Use null to clear profile_picture_url."""
    return user_service.update_me(db, current_user["id"], data)
