from typing import Optional

from pydantic import BaseModel, EmailStr
from app.schemas.auth import UserResponse


class AddFriendRequest(BaseModel):
    email: EmailStr


class AddFriendByLinkRequest(BaseModel):
    token: str


class FriendLinkResponse(BaseModel):
    link: str


class AddFriendEmailResponse(BaseModel):
    """Email-based add: either a new pending request or instant friendship (incoming was pending)."""
    pending: bool
    user: Optional[UserResponse] = None


class IncomingFriendRequestResponse(BaseModel):
    id: int
    requester: UserResponse
