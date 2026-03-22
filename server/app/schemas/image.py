from pydantic import BaseModel
from typing import Optional
from decimal import Decimal


class ImageCreate(BaseModel):
    album_id: Optional[int] = None  # If omitted, image is added to user's personal "My Photos" album
    caption: Optional[str] = None
    image_url: str
    audio_url: Optional[str] = None  # Cloudinary URL after audio upload
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    taken_at: Optional[str] = None  # ISO datetime when photo was taken (from EXIF)
    location_name: Optional[str] = None  # Human-readable place name (cached on upload)


class ImageUpdate(BaseModel):
    caption: Optional[str] = None
    image_url: Optional[str] = None
    audio_url: Optional[str] = None  # Cloudinary URL after audio upload
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    taken_at: Optional[str] = None  # ISO datetime when photo was taken (from EXIF)
    location_name: Optional[str] = None  # Allow user to modify cached place name


class ImageResponse(BaseModel):
    id: int
    album_id: int
    caption: Optional[str]
    image_url: str
    audio_url: Optional[str] = None  # Cloudinary URL for voice note
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    location_name: Optional[str] = None  # Cached human-readable place name (if available)
    date_added: str
    taken_at: Optional[str] = None  # When photo was taken (from metadata), if set
    user_id: int
    created_at: str
    updated_at: str

    class Config:
        from_attributes = True

