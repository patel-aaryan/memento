package com.example.mementoandroid.util

/**
 * Default placeholder image URL when a user has no profile picture.
 */
const val DEFAULT_AVATAR_URL = "https://res.cloudinary.com/da8ac6bua/image/upload/v1774818134/pfp_x8ekxh.png"

/**
 * Returns the profile picture URL, or the default placeholder if empty/null.
 * Note: JSONObject.optString() returns the literal string "null" when the JSON value is null,
 * so we treat "null" as empty to avoid AsyncImage failing to load.
 */
fun String?.orDefaultAvatar(): String =
    this?.takeIf { it.isNotBlank() && it != "null" } ?: DEFAULT_AVATAR_URL
