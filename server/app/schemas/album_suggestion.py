from typing import List, Optional

from pydantic import BaseModel, Field


class AlbumSuggestionPayload(BaseModel):
    id: int
    album_name: str
    image_ids: List[int]
    preview_image_urls: List[str] = Field(default_factory=list)
    image_count: int


class AlbumSuggestionCurrentResponse(BaseModel):
    suggestion: Optional[AlbumSuggestionPayload] = None


class AlbumSuggestionAcceptResponse(BaseModel):
    album_id: int
    album_name: str
    images_moved: int
