import logging
import requests
from typing import Any, Optional
from app.config.settings import get_settings


# Use the same logger name as the location router so all logs
# show up together in your existing log stream.
logger = logging.getLogger("app.routers.location")
PLACES_NEARBY_URL = "https://places.googleapis.com/v1/places:searchNearby"
PLACES_AUTOCOMPLETE_URL = "https://places.googleapis.com/v1/places:autocomplete"
PLACES_DETAILS_BASE = "https://places.googleapis.com/v1/places"
GEOCODE_URL = "https://maps.googleapis.com/maps/api/geocode/json"


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

    # Places Nearby requires a sensible radius; 10m almost always returns no results.
    # Clamp to API limits (typical max ~50km).
    search_radius_m = max(50, min(int(radius_m), 50_000))

    body = {
        "maxResultCount": 5,
        "locationRestriction": {
            "circle": {
                "center": {"latitude": lat, "longitude": lng},
                "radius": float(search_radius_m),
            }
        },
        "rankPreference": "DISTANCE",
    }

    headers = {
        "Content-Type": "application/json",
        "X-Goog-Api-Key": api_key,
        # Request displayName and addressComponents so we can fall back to a city
        # when displayName is null/empty.
        "X-Goog-FieldMask": "places.displayName,places.addressComponents",
    }

    logger.info(
        "nearest_place_display_name: calling Places Nearby for lat=%s lng=%s radius=%s",
        lat,
        lng,
        radius_m,
    )
    resp = requests.post(PLACES_NEARBY_URL, json=body, headers=headers, timeout=20)
    resp.raise_for_status()
    data = resp.json()

    places = data.get("places", [])
    if not places:
        logger.warning(
            "nearest_place_display_name: Places API returned no places for lat=%s lng=%s radius=%s raw_response=%s",
            lat,
            lng,
            radius_m,
            data,
        )
        return None

    def _label_from_place(place: dict) -> Optional[str]:
        display = place.get("displayName") or {}
        name = (display.get("text") or "").strip()
        if name:
            return name
        components = place.get("addressComponents") or []
        city_candidate: Optional[str] = None
        for comp in components:
            types = comp.get("types") or []
            long_text = (comp.get("longText") or "").strip()
            short_text = (comp.get("shortText") or "").strip()
            if "locality" in types:
                return long_text or short_text or None
            if not city_candidate and (
                "postal_town" in types
                or "administrative_area_level_3" in types
                or "administrative_area_level_2" in types
            ):
                city_candidate = long_text or short_text or None
        return city_candidate

    display_name: Optional[str] = None
    for i, place in enumerate(places):
        place = place or {}
        display_name = _label_from_place(place)
        if display_name:
            logger.info(
                "nearest_place_display_name: using place index=%s name=%r for lat=%s lng=%s",
                i,
                display_name,
                lat,
                lng,
            )
            break

    if not display_name:
        logger.warning(
            "nearest_place_display_name: no display/city on any of %s places for lat=%s lng=%s",
            len(places),
            lat,
            lng,
        )

    logger.info("nearest_place_display_name: final display_name=%r for lat=%s lng=%s", display_name, lat, lng)
    return display_name or None


def _get_api_key() -> str:
    settings = get_settings()
    key = settings.google_api_key
    if not key:
        raise RuntimeError("Missing API key. Set GOOGLE_API_KEY in your environment.")
    return key


def _city_from_geocode_results(results: list) -> Optional[str]:
    """
    Pick a human-readable city/locality from Geocoding API results.
    Scans all results and components; prefers locality, then postal_town, etc.
    """
    priority_types = (
        "locality",
        "postal_town",
        "sublocality_level_1",
        "sublocality",
        "neighborhood",
        "administrative_area_level_3",
        "administrative_area_level_2",
        "administrative_area_level_1",
    )
    for want in priority_types:
        for res in results:
            for comp in res.get("address_components") or []:
                types = comp.get("types") or []
                if want not in types:
                    continue
                long_name = (comp.get("long_name") or "").strip()
                short_name = (comp.get("short_name") or "").strip()
                label = long_name or short_name or None
                if label:
                    return label
    return None


def reverse_geocode_city(
    lat: float,
    lng: float,
    *,
    api_key: Optional[str] = None,
) -> Optional[str]:
    """
    Use Google Geocoding API to get a city-like name for raw coordinates.
    Does not use result_type (invalid values like postal_town break the API).
    """
    api_key = api_key or _get_api_key()
    params = {
        "latlng": f"{lat},{lng}",
        "key": api_key,
    }
    logger.info(
        "reverse_geocode_city: calling Geocoding API for lat=%s lng=%s",
        lat,
        lng,
    )
    resp = requests.get(GEOCODE_URL, params=params, timeout=15)
    resp.raise_for_status()
    data = resp.json()
    status = data.get("status")
    if status != "OK":
        logger.warning(
            "reverse_geocode_city: Geocoding API status=%s lat=%s lng=%s raw=%s",
            status,
            lat,
            lng,
            data,
        )
        return None

    results = data.get("results") or []
    if not results:
        logger.warning(
            "reverse_geocode_city: no results for lat=%s lng=%s raw=%s",
            lat,
            lng,
            data,
        )
        return None

    city_candidate = _city_from_geocode_results(results)
    logger.info(
        "reverse_geocode_city: resolved city=%r for lat=%s lng=%s",
        city_candidate,
        lat,
        lng,
    )
    return city_candidate


def resolve_location_label_for_coordinates(
    lat: float,
    lng: float,
    *,
    radius_m: int = 150,
) -> Optional[str]:
    """
    Best-effort human-readable label for coordinates:
    1) Nearest Places display name (or city from that place's address)
    2) City from Geocoding API reverse lookup
    """
    try:
        name = nearest_place_display_name(lat=lat, lng=lng, radius_m=radius_m)
    except Exception as e:
        logger.warning(
            "resolve_location_label_for_coordinates: nearest_place failed lat=%s lng=%s: %s",
            lat,
            lng,
            e,
        )
        name = None
    if name:
        return name
    try:
        return reverse_geocode_city(lat=lat, lng=lng)
    except Exception as e:
        logger.warning(
            "resolve_location_label_for_coordinates: reverse_geocode failed lat=%s lng=%s: %s",
            lat,
            lng,
            e,
        )
        return None


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
        # Also request addressComponents so we can fall back to a city name
        # if the place has no displayName (null / empty).
        "X-Goog-FieldMask": "location,displayName,addressComponents",
    }
    resp = requests.get(url, headers=headers, timeout=15)
    resp.raise_for_status()
    data = resp.json()

    loc = data.get("location") or {}
    lat = loc.get("latitude")
    lng = loc.get("longitude")

    display = data.get("displayName") or {}
    display_name = (display.get("text") or "").strip()

    # If the place_details call returns a null / empty displayName, fall back
    # to a city-like component from the address, e.g. "locality".
    if not display_name:
        components = data.get("addressComponents") or []
        city_candidate: Optional[str] = None
        for comp in components:
            types = comp.get("types") or []
            long_text = (comp.get("longText") or "").strip()
            short_text = (comp.get("shortText") or "").strip()

            # Prefer locality; fall back to other city-level components.
            if "locality" in types:
                city_candidate = long_text or short_text or None
                break
            if not city_candidate and (
                "postal_town" in types
                or "administrative_area_level_3" in types
                or "administrative_area_level_2" in types
            ):
                city_candidate = long_text or short_text or None

        display_name = city_candidate or display_name

    if lat is None or lng is None:
        raise ValueError("Place has no location (latitude/longitude)")
    return {
        "latitude": float(lat),
        "longitude": float(lng),
        "display_name": display_name or None,
    }

# Test API
# if __name__ == "__main__":
#     # Example: Googleplex-ish
#     name = nearest_place_display_name(37.4221, -122.0841, radius_m=150)
#     print(name)