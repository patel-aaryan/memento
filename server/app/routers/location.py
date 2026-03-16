from datetime import date, datetime, timedelta
from zoneinfo import ZoneInfo

from app.config.settings import get_settings
import logging
from math import atan2, cos, radians, sin, sqrt

from fastapi import APIRouter, Depends, HTTPException, Query, Security
from sqlalchemy.orm import Session

from app.config.db import get_db
from app.dependencies.auth import get_current_user, security
from app.repositories import image_repository
from app.services.location_service import (
    nearest_place_display_name,
    autocomplete_places,
    get_place_details,
)


logger = logging.getLogger(__name__)
router = APIRouter(prefix="/location", tags=["Location"])


def _haversine_m(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    """Return distance in meters between two lat/lng points."""
    R = 6371000.0  # earth radius in meters
    phi1 = radians(lat1)
    phi2 = radians(lat2)
    dphi = radians(lat2 - lat1)
    dlambda = radians(lng2 - lng1)

    a = sin(dphi / 2.0) ** 2 + cos(phi1) * cos(phi2) * sin(dlambda / 2.0) ** 2
    c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return R * c


@router.get("/nearest-place")
async def get_nearest_place(
    lat: float = Query(..., description="Latitude in decimal degrees"),
    lng: float = Query(..., description="Longitude in decimal degrees"),
    radius_m: int = Query(
        100, description="Search radius in meters (max 10m)"),
):
    """
    Return the nearest place display name for the given coordinates using
    the Google Places API (via `nearest_place_display_name` service).
    """
    logger.info(
        "GET /location/nearest-place called (lat=%s, lng=%s, radius_m=%s)", lat, lng, radius_m)
    logger.info(
        "Nearest-place lookup requested",
        extra={"lat": lat, "lng": lng, "radius_m": radius_m},
    )
    try:
        place_name = nearest_place_display_name(
            lat=lat, lng=lng, radius_m=radius_m)
    except RuntimeError as exc:
        # Typically raised when the API key is missing or misconfigured
        logger.error("Location service failed: %s", exc, exc_info=True)
        raise HTTPException(status_code=500, detail=str(exc))

    if place_name:
        logger.info("Nearest-place result: %s", place_name)
    else:
        # Explicitly log when the Places API returned no display name / null result
        logger.warning(
            "Nearest-place result has null place_name for lat=%s lng=%s radius_m=%s",
            lat,
            lng,
            radius_m,
        )

    # If no place is found, return a 200 with null to keep the client logic simple
    return {"place_name": place_name}


@router.get("/autocomplete")
async def location_autocomplete(
    q: str = Query(..., min_length=1,
                   description="Search query for place suggestions"),
):
    """
    Return place suggestions for the given input using Google Places Autocomplete (New).
    Each suggestion has description and place_id; use place_id with /location/place-details to get lat/lng.
    """
    logger.info("GET /location/autocomplete called (q=%s)", q)
    try:
        predictions = autocomplete_places(q)
    except RuntimeError as exc:
        logger.error("Autocomplete failed: %s", exc, exc_info=True)
        raise HTTPException(status_code=500, detail=str(exc))
    return {"predictions": predictions}


@router.get("/place-details")
async def location_place_details(
    place_id: str = Query(..., description="Place ID from autocomplete"),
):
    """
    Return latitude, longitude, and display name for a place (e.g. after user selects an autocomplete suggestion).
    """
    logger.info("GET /location/place-details called (place_id=%s)", place_id)
    try:
        details = get_place_details(place_id)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    except RuntimeError as exc:
        logger.error("Place details failed: %s", exc, exc_info=True)
        raise HTTPException(status_code=500, detail=str(exc))
    return details


@router.get("/anniversary-check", dependencies=[Security(security)])
async def anniversary_check(
    lat: float = Query(..., description="Current latitude"),
    lng: float = Query(..., description="Current longitude"),
    radius_m: float = Query(5000.0, gt=0.0, le=5000.0,
                            description="Match radius in meters"),
    current_user: dict = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """
    Check if the user has any photos taken at (roughly) this location exactly one year ago.

    Returns:
    - has_match: bool
    - matches: up to a few closest matches with image_id/album_id/distance_m
    """
    tz = ZoneInfo(get_settings().anniversary_timezone)
    today = datetime.now(tz).date()
    try:
        # Try exact same calendar day one year earlier (handles most dates)
        target_date = today.replace(year=today.year - 1)
    except ValueError:
        # Fallback for Feb 29, etc.
        target_date = today - timedelta(days=365)

    logger.info(
        "Anniversary check called for user=%s at lat=%s lng=%s target_date=%s radius_m=%s",
        current_user["id"],
        lat,
        lng,
        target_date.isoformat(),
        radius_m,
    )

    images = image_repository.get_user_images_with_location_on_date(
        db=db,
        user_id=current_user["id"],
        target_date=target_date.isoformat(),
    )

    matches = []
    for img in images:
        img_lat = img.get("latitude")
        img_lng = img.get("longitude")
        if img_lat is None or img_lng is None:
            continue
        dist = _haversine_m(lat, lng, img_lat, img_lng)
        if dist <= radius_m:
            matches.append(
                {
                    "image_id": img["id"],
                    "album_id": img["album_id"],
                    "distance_m": dist,
                }
            )

    has_match = bool(matches)
    matches_sorted = sorted(matches, key=lambda m: m["distance_m"])[:5]

    logger.info(
        "Anniversary check result for user=%s has_match=%s count=%s",
        current_user["id"],
        has_match,
        len(matches_sorted),
    )

    return {"has_match": has_match, "matches": matches_sorted}
