from datetime import datetime, timedelta, timezone
import logging

from fastapi import APIRouter, Depends, HTTPException, status, Security
from sqlalchemy.orm import Session
from app.config.db import get_db
from app.config.settings import get_settings
from app.schemas.auth import (
    UserRegister,
    UserLogin,
    Token,
    UserResponse,
    UserUpdate,
    ForgotPasswordRequest,
    ResetPasswordRequest,
    ForgotPasswordResponse,
    MessageResponse,
)
from app.repositories import device_repository, password_reset_repository
from app.repositories.user_repository import (
    create_user,
    get_user_by_email,
    update_user_password_hash,
)
from app.utils.auth import (
    verify_password,
    create_access_token,
    get_password_hash,
    generate_reset_code,
    hash_reset_code,
)
from app.dependencies.auth import get_current_user, security
from app.services import user_service, email_service

router = APIRouter(prefix="/auth", tags=["Authentication"])
settings = get_settings()
logger = logging.getLogger(__name__)


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
        if user_data.fcm_token:
            device_repository.upsert_device_token(db, new_user["id"], user_data.fcm_token)
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
    
    if user_data.fcm_token:
        device_repository.upsert_device_token(db, user["id"], user_data.fcm_token)
    
    # Create access token
    access_token = create_access_token(data={"sub": str(user["id"])})
    
    return {
        "access_token": access_token,
        "token_type": "bearer"
    }


@router.post("/forgot-password", response_model=ForgotPasswordResponse)
async def forgot_password(data: ForgotPasswordRequest, db: Session = Depends(get_db)):
    """
    Request a password reset code.
    Always returns a generic success message to avoid email enumeration.
    """
    generic_message = "If the email exists, a reset code has been sent."
    user = get_user_by_email(db, data.email)
    if not user:
        return {"message": generic_message}

    latest_created_at = password_reset_repository.latest_pending_created_at(db, user["id"])
    if latest_created_at:
        now = datetime.now(timezone.utc)
        if now - latest_created_at < timedelta(seconds=30):
            return {"message": generic_message}

    password_reset_repository.invalidate_active_codes_for_user(db, user["id"])
    code = generate_reset_code()
    code_hash = hash_reset_code(data.email, code)
    expires_at = datetime.now(timezone.utc) + timedelta(
        minutes=settings.password_reset_code_expire_minutes
    )
    password_reset_repository.create_reset_code(db, user["id"], code_hash, expires_at)

    try:
        sent = email_service.send_password_reset_code(data.email, code)
        if not sent:
            logger.warning("Reset code not sent to %s because Resend is not configured", data.email)
    except Exception:
        logger.exception("Failed sending password reset code for %s", data.email)

    # Local/dev helper: return OTP when debug mode is enabled.
    if settings.debug:
        return {"message": generic_message, "debug_code": code}
    return {"message": generic_message}


@router.post("/reset-password", response_model=MessageResponse)
async def reset_password(data: ResetPasswordRequest, db: Session = Depends(get_db)):
    """Reset a password with email + OTP code."""
    if len(data.new_password) < 8:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="New password must be at least 8 characters",
        )

    user = get_user_by_email(db, data.email)
    if not user:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Invalid or expired reset code",
        )

    code_hash = hash_reset_code(data.email, data.code.strip())
    consumed = password_reset_repository.consume_reset_code(db, user["id"], code_hash)
    if not consumed:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Invalid or expired reset code",
        )

    new_hash = get_password_hash(data.new_password)
    updated = update_user_password_hash(db, user["id"], new_hash)
    if not updated:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to update password",
        )

    password_reset_repository.invalidate_active_codes_for_user(db, user["id"])
    return {"message": "Password reset successfully"}


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
