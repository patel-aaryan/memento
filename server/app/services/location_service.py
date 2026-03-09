import logging
import requests
from typing import Any, Optional
from app.config.settings import get_settings


logger = logging.getLogger(__name__)
PLACES_NEARBY_URL = "https://places.googleapis.com/v1/places:searchNearby"
PLACES_AUTOCOMPLETE_URL = "https://places.googleapis.com/v1/places:autocomplete"
PLACES_DETAILS_BASE = "https://places.googleapis.com/v1/places"


def nearest_place_display_name(
    lat: float,
    lng: float,
    *,
    radius_m: int = 100,
    api_key: Optional[str] = None,
) -> Optional[str]:
    """
    Returns the nearest place's displayName.text for a given lat/lng using
    Places API Nearby Search (New). Returns None if no place is found.

    Notes:
    - Field masking is required for Places API (New). We only request places.displayName.
    """
    settings = get_settings()
    api_key = api_key or settings.google_api_key
    if not api_key:
        raise RuntimeError("Missing API key. Set GOOGLE_API_KEY in your environment.")

    body = {
        "maxResultCount": 1,
        "locationRestriction": {
            "circle": {
                "center": {"latitude": lat, "longitude": lng},
                "radius": 10,
            }
        },
        # rank by distance so the first result is the closest
#         "rankPreference": "DISTANCE",
    }

    headers = {
        "Content-Type": "application/json",
        "X-Goog-Api-Key": api_key,
        # FieldMask is REQUIRED; request only what you need
        "X-Goog-FieldMask": "places.displayName",
    }

    resp = requests.post(PLACES_NEARBY_URL, json=body, headers=headers, timeout=20)
    resp.raise_for_status()
    data = resp.json()

    places = data.get("places", [])
    if not places:
        return None

    display = places[0].get("displayName") or {}
    logger.info(places[0])
    return display.get("text")


def _get_api_key() -> str:
    settings = get_settings()
    key = settings.google_api_key
    if not key:
        raise RuntimeError("Missing API key. Set GOOGLE_API_KEY in your environment.")
    return key


def autocomplete_places(input_text: str, *, api_key: Optional[str] = None) -> list[dict[str, Any]]:
    """
    Returns place suggestions for the given input using Places API Autocomplete (New).
    Each item is {"description": str, "place_id": str}.
    """
    api_key = api_key or _get_api_key()
    if not input_text or not input_text.strip():
        return []

    body = {"input": input_text.strip()}
    headers = {
        "Content-Type": "application/json",
        "X-Goog-Api-Key": api_key,
        "X-Goog-FieldMask": "suggestions.placePrediction.text,suggestions.placePrediction.placeId",
    }
    resp = requests.post(PLACES_AUTOCOMPLETE_URL, json=body, headers=headers, timeout=15)
    resp.raise_for_status()
    data = resp.json()

    out = []
    for s in data.get("suggestions", []):
        pred = s.get("placePrediction") or {}
        place_id = pred.get("placeId")
        text_obj = pred.get("text") or {}
        description = (text_obj.get("text") or "").strip()
        if place_id and description:
            out.append({"description": description, "place_id": place_id})
    return out


def get_place_details(place_id: str, *, api_key: Optional[str] = None) -> dict[str, Any]:
    """
    Returns latitude, longitude, and display name for a place using Place Details (New).
    Returns {"latitude": float, "longitude": float, "display_name": str}.
    """
    api_key = api_key or _get_api_key()
    if not place_id or not place_id.strip():
        raise ValueError("place_id is required")

    place_id = place_id.strip()
    url = f"{PLACES_DETAILS_BASE}/{place_id}"
    headers = {
        "X-Goog-Api-Key": api_key,
        "X-Goog-FieldMask": "location,displayName",
    }
    resp = requests.get(url, headers=headers, timeout=15)
    resp.raise_for_status()
    data = resp.json()

    loc = data.get("location") or {}
    lat = loc.get("latitude")
    lng = loc.get("longitude")
    display = data.get("displayName") or {}
    display_name = (display.get("text") or "").strip()

    if lat is None or lng is None:
        raise ValueError("Place has no location (latitude/longitude)")
    return {"latitude": float(lat), "longitude": float(lng), "display_name": display_name or None}

# Test API
# if __name__ == "__main__":
#     # Example: Googleplex-ish
#     name = nearest_place_display_name(37.4221, -122.0841, radius_m=150)
#     print(name)