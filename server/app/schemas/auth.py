from pydantic import BaseModel, EmailStr
from typing import Optional


class UserRegister(BaseModel):
    email: EmailStr
    password: str
    name: str
    fcm_token: Optional[str] = None


class UserLogin(BaseModel):
    email: EmailStr
    password: str
    fcm_token: Optional[str] = None


class Token(BaseModel):
    access_token: str
    token_type: str


class UserResponse(BaseModel):
    """Full user object returned by /auth/me, /users/{id}, and album members."""
    id: int
    email: str
    name: str
    created_at: str
    updated_at: str
    profile_picture_url: Optional[str] = None


class UserUpdate(BaseModel):
    """Fields that can be updated by the user (PATCH /auth/me)."""
    name: Optional[str] = None
    profile_picture_url: Optional[str] = None
