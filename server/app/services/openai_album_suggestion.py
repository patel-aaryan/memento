import json
import logging
from typing import Any, Dict, List, Tuple

from openai import OpenAI

from app.config.settings import get_settings

logger = logging.getLogger(__name__)


def vision_safe_image_url(url: str) -> str:
    """OpenAI vision only accepts png/jpeg/gif/webp; Cloudinary can deliver HEIC etc. Force JPEG."""
    if not url:
        return url
    needle = "/upload/"
    i = url.find(needle)
    if i < 0:
        return url
    rest = url[i + len(needle) :]
    if rest.startswith("f_jpg") or rest.startswith("f_auto") or rest.startswith("fl_"):
        return url
    return url[: i + len(needle)] + "f_jpg,q_auto/" + rest


def _with_safe_urls(images: List[dict]) -> List[dict]:
    return [{**img, "image_url": vision_safe_image_url(img.get("image_url") or "")} for img in images]


_SUGGESTION_JSON_SCHEMA: Dict[str, Any] = {
    "type": "object",
    "properties": {
        "has_suggestion": {"type": "boolean"},
        "album_name": {"type": "string"},
        "image_ids": {
            "type": "array",
            "items": {"type": "integer"},
        },
    },
    "required": ["has_suggestion", "album_name", "image_ids"],
    "additionalProperties": False,
}


def request_album_suggestion(
    sampled_images: List[dict],
) -> Tuple[bool, str, List[int]]:
    """
    Call OpenAI vision with image URLs and DB image ids.
    Returns (has_suggestion, album_name, image_ids).
    """
    settings = get_settings()
    if not settings.openai_api_key:
        raise RuntimeError("OpenAI API key not configured")

    sampled_images = _with_safe_urls(sampled_images)

    allowed_ids = {int(img["id"]) for img in sampled_images}
    lines = []
    for img in sampled_images:
        cap = (img.get("caption") or "").replace("\n", " ")[:200]
        taken = img.get("taken_at") or img.get("date_added") or ""
        lines.append(
            f"id={img['id']} url={img['image_url']} caption={cap!r} when={taken!r}"
        )
    catalog = "\n".join(lines)

    n = len(sampled_images)
    system = (
        "You suggest ONE new album from a batch of photos in the user's personal library (My Photos). "
        "Each catalog line is: database id, public image URL, optional caption, and timestamp. "
        "You see the actual images via the image_url parts of the message. "
        "\n\n"
        "DEFAULT: lean toward suggesting an album. The product wants to help users tidy a messy camera roll. "
        "Do NOT require a 'perfect' theme. If at least 3 photos could reasonably live together in one album, "
        "set has_suggestion to true. "
        "\n\n"
        "Good reasons to group 3+ photos include: same outing or setting, similar subjects (people, pets, food, nature), "
        "similar visual mood or color, photos taken close in time (same day or same upload batch), "
        "or simply 'these feel like one casual batch the user might want split out of My Photos'. "
        "Album names can be specific ('Coffee with friends') OR loose ('Tonight', 'This week', 'March snapshots', "
        "'Camera roll picks', 'Random favorites')—short, friendly titles are fine. "
        "\n\n"
        "Only set has_suggestion to false when you truly cannot justify grouping ANY 3+ images "
        "(e.g. every image is a totally unrelated one-off with no time or visual batch coherence). "
        "Being 'unsure' or 'only loosely related' is NOT a reason to decline—pick the best plausible subset of 3 or more. "
        "\n\n"
        "Rules: use only image ids from the catalog; return at least 3 ids when has_suggestion is true; "
        "one album name; do not invent ids."
    )

    user_text = (
        f"There are {n} photos below (newest are usually listed first in the text catalog).\n"
        "Task: if at least 3 belong together under ANY reasonable album idea, respond with has_suggestion true, "
        "a short album_name, and those image_ids. Prefer suggesting over declining.\n\n"
        f"Catalog:\n{catalog}\n\n"
        f"You may only use these ids: {sorted(allowed_ids)}"
    )

    content: List[Dict[str, Any]] = [{"type": "text", "text": user_text}]
    for img in sampled_images:
        content.append(
            {
                "type": "image_url",
                "image_url": {"url": img["image_url"], "detail": "low"},
            }
        )

    messages = [
        {"role": "system", "content": system},
        {"role": "user", "content": content},
    ]
    api_request_log = {
        "model": settings.openai_model,
        "messages": messages,
        "max_tokens": 800,
        "response_format": {
            "type": "json_schema",
            "json_schema": {
                "name": "album_suggestion",
                "strict": True,
                "schema": _SUGGESTION_JSON_SCHEMA,
            },
        },
    }
    request_json = json.dumps(api_request_log, indent=2, default=str)
    print(f"[openai_album_suggestion] API request:\n{request_json}", flush=True)
    logger.info(
        "OpenAI album suggestion request: model=%s image_count=%d (full body printed above)",
        settings.openai_model,
        len(sampled_images),
    )

    client = OpenAI(api_key=settings.openai_api_key)
    completion = client.chat.completions.create(
        model=settings.openai_model,
        messages=messages,
        response_format={
            "type": "json_schema",
            "json_schema": {
                "name": "album_suggestion",
                "strict": True,
                "schema": _SUGGESTION_JSON_SCHEMA,
            },
        },
        max_tokens=800,
    )

    raw = completion.choices[0].message.content
    usage = getattr(completion, "usage", None)
    usage_dict = None
    if usage is not None:
        usage_dict = (
            usage.model_dump() if hasattr(usage, "model_dump") else dict(usage)
        )
    response_log = {
        "completion_id": getattr(completion, "id", None),
        "raw_message_content": raw,
        "usage": usage_dict,
        "finish_reason": completion.choices[0].finish_reason
        if completion.choices
        else None,
    }
    print(
        f"[openai_album_suggestion] API response:\n{json.dumps(response_log, indent=2, default=str)}",
        flush=True,
    )
    logger.info(
        "OpenAI album suggestion response: completion_id=%s usage=%s (full body printed above)",
        response_log.get("completion_id"),
        usage_dict,
    )

    if not raw:
        return False, "", []

    try:
        data = json.loads(raw)
    except json.JSONDecodeError:
        logger.warning("OpenAI suggestion JSON parse failed: %r", raw[:500])
        return False, "", []

    has_suggestion = bool(data.get("has_suggestion"))
    name = (data.get("album_name") or "").strip()
    ids = data.get("image_ids") or []
    out_ids = [int(x) for x in ids if int(x) in allowed_ids]
    # de-dupe preserve order
    seen = set()
    deduped = []
    for i in out_ids:
        if i not in seen:
            seen.add(i)
            deduped.append(i)
    out_ids = deduped

    if not has_suggestion or len(out_ids) < 3:
        return False, "", []

    if len(name) < 1 or len(name) > 120:
        return False, "", []

    return True, name, out_ids
