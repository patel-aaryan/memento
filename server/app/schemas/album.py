from pydantic import BaseModel
from typing import Optional, List
from datetime import datetime


class AlbumCreate(BaseModel):
    name: str


class AlbumUpdate(BaseModel):
    name: Optional[str] = None


class AlbumResponse(BaseModel):
    id: int
    name: str
    owner_id: int
    created_at: str
    updated_at: str
    cover_image_urls: Optional[List[str]] = []

    class Config:
        from_attributes = True


class AlbumMemberAdd(BaseModel):
    user_id: int


class AlbumMemberResponse(BaseModel):
    id: int
    album_id: int
    user_id: int
    created_at: str

    class Config:
        from_attributes = True


class AlbumWithMembers(AlbumResponse):
    members: List[AlbumMemberResponse] = []

