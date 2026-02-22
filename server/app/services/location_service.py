import logging
import requests
from typing import Optional
from app.config.settings import get_settings


logger = logging.getLogger(__name__)
PLACES_NEARBY_URL = "https://places.googleapis.com/v1/places:searchNearby"


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

# Test API
# if __name__ == "__main__":
#     # Example: Googleplex-ish
#     name = nearest_place_display_name(37.4221, -122.0841, radius_m=150)
#     print(name)