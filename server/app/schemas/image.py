from pydantic import BaseModel
from typing import Optional
from decimal import Decimal


class ImageCreate(BaseModel):
    album_id: int
    caption: Optional[str] = None
    image_url: str
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    taken_at: Optional[str] = None  # ISO datetime when photo was taken (from EXIF)


class ImageUpdate(BaseModel):
    caption: Optional[str] = None
    image_url: Optional[str] = None
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    taken_at: Optional[str] = None  # ISO datetime when photo was taken (from EXIF)


class ImageResponse(BaseModel):
    id: int
    album_id: int
    caption: Optional[str]
    image_url: str
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    date_added: str
    taken_at: Optional[str] = None  # When photo was taken (from metadata), if set
    user_id: int
    created_at: str
    updated_at: str

    class Config:
        from_attributes = True

