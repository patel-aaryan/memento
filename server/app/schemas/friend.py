from pydantic import BaseModel, EmailStr


class AddFriendRequest(BaseModel):
    email: EmailStr


class AddFriendByLinkRequest(BaseModel):
    token: str


class FriendLinkResponse(BaseModel):
    link: str
