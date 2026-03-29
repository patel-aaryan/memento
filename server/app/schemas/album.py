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
    # Oldest image in the album (same ordering as cover thumb); for default titles
    first_image_location_name: Optional[str] = None
    first_image_taken_at: Optional[str] = None
    first_image_date_added: Optional[str] = None

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

