from fastapi import APIRouter, HTTPException, Query
import logging

from app.services.location_service import (
    nearest_place_display_name,
    autocomplete_places,
    get_place_details,
)


logger = logging.getLogger(__name__)
router = APIRouter(prefix="/location", tags=["Location"])


@router.get("/nearest-place")
async def get_nearest_place(
    lat: float = Query(..., description="Latitude in decimal degrees"),
    lng: float = Query(..., description="Longitude in decimal degrees"),
    radius_m: int = Query(100, description="Search radius in meters (max 10m)"),
):
    """
    Return the nearest place display name for the given coordinates using
    the Google Places API (via `nearest_place_display_name` service).
    """
    logger.info("GET /location/nearest-place called (lat=%s, lng=%s, radius_m=%s)", lat, lng, radius_m)
    logger.info(
        "Nearest-place lookup requested",
        extra={"lat": lat, "lng": lng, "radius_m": radius_m},
    )
    try:
        place_name = nearest_place_display_name(lat=lat, lng=lng, radius_m=radius_m)
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
    q: str = Query(..., min_length=1, description="Search query for place suggestions"),
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

