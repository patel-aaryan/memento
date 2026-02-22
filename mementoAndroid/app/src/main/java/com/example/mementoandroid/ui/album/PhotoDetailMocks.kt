package com.example.mementoandroid.ui.album

/**
 * Mock metadata for the enlarged photo screen. Varies by album and photo.
 * Will be replaced by real photo metadata in a later step.
 */
data class PhotoDetailMock(
    val dateTime: String,
    val location: String?,
    val uploaderName: String?,  // null for personal albums
    val caption: String,
    val hasAudio: Boolean
)

private val sharedFriends = listOf("isla", "blair", "shannon", "nick")

fun getPhotoDetailMock(albumName: String, photoId: String): PhotoDetailMock {
    return when (albumName) {
        "Sweet Dreams Bubble Tea" -> when (photoId) {
            "1" -> PhotoDetailMock(
                dateTime = "Jan 15, 2025 · 3:42 PM",
                location = "Sweet Dreams Bubble Tea",
                uploaderName = null,
                caption = "Tried the new winter special — so good!",
                hasAudio = false
            )
            "2" -> PhotoDetailMock(
                dateTime = "Jan 22, 2025 · 7:15 PM",
                location = "Sweet Dreams Bubble Tea",
                uploaderName = null,
                caption = "Second visit this week, no regrets.",
                hasAudio = true
            )
            else -> personalFallback(albumName, "Sweet Dreams Bubble Tea")
        }
        "Grad Trip" -> when (photoId) {
            "1" -> PhotoDetailMock(
                dateTime = "May 10, 2025 · 11:20 AM",
                location = "CN Tower, Toronto",
                uploaderName = "isla",
                caption = "View from the top! Couldn't ask for a better grad trip.",
                hasAudio = false
            )
            "2" -> PhotoDetailMock(
                dateTime = "May 11, 2025 · 4:05 PM",
                location = "Niagara Falls",
                uploaderName = "blair",
                caption = "Maid of the Mist was incredible. Soaked but worth it.",
                hasAudio = true
            )
            else -> sharedFallback(albumName, listOf("CN Tower, Toronto", "Niagara Falls", "Toronto Harbourfront"))
        }
        "Ken's Sushi" -> when (photoId) {
            "1" -> PhotoDetailMock(
                dateTime = "Feb 3, 2025 · 12:30 PM",
                location = "Ken's Sushi",
                uploaderName = null,
                caption = "Lunch special hit different today.",
                hasAudio = false
            )
            "2" -> PhotoDetailMock(
                dateTime = "Feb 8, 2025 · 7:00 PM",
                location = "Ken's Sushi",
                uploaderName = null,
                caption = "Date night at our usual spot.",
                hasAudio = false
            )
            else -> personalFallback(albumName, "Ken's Sushi")
        }
        "Square One Shopping" -> when (photoId) {
            "1" -> PhotoDetailMock(
                dateTime = "Feb 12, 2025 · 2:15 PM",
                location = "Square One, Mississauga",
                uploaderName = "shannon",
                caption = "Found the perfect jacket at Zara.",
                hasAudio = false
            )
            "2" -> PhotoDetailMock(
                dateTime = "Feb 12, 2025 · 4:45 PM",
                location = "Square One Food Court",
                uploaderName = "nick",
                caption = "Food court run before heading home.",
                hasAudio = true
            )
            else -> sharedFallback(albumName, listOf("Square One, Mississauga", "Square One Food Court"))
        }
        "Niagara Falls Adventure" -> when (photoId) {
            "1" -> PhotoDetailMock(
                dateTime = "Feb 14, 2025 · 10:00 AM",
                location = "Niagara Falls, Canadian Side",
                uploaderName = "isla",
                caption = "Morning mist at the falls. Breathtaking.",
                hasAudio = false
            )
            "2" -> PhotoDetailMock(
                dateTime = "Feb 14, 2025 · 6:30 PM",
                location = "Clifton Hill, Niagara Falls",
                uploaderName = "blair",
                caption = "Clifton Hill at night — so much fun.",
                hasAudio = true
            )
            else -> sharedFallback(albumName, listOf("Niagara Falls, Canadian Side", "Clifton Hill, Niagara Falls"))
        }
        "Cancun Fam Trip" -> when (photoId) {
            "1" -> PhotoDetailMock(
                dateTime = "Dec 22, 2024 · 1:00 PM",
                location = "Playa del Carmen",
                uploaderName = "shannon",
                caption = "Family beach day. Best vacation ever.",
                hasAudio = true
            )
            "2" -> PhotoDetailMock(
                dateTime = "Dec 24, 2024 · 8:30 PM",
                location = "Cancun Hotel Zone",
                uploaderName = "nick",
                caption = "Christmas Eve dinner with the whole crew.",
                hasAudio = false
            )
            else -> sharedFallback(albumName, listOf("Playa del Carmen", "Cancun Hotel Zone", "Tulum"))
        }
        "2nd Anniversary" -> when (photoId) {
            "1" -> PhotoDetailMock(
                dateTime = "Oct 18, 2025 · 7:45 PM",
                location = "The Rooftop, Toronto",
                uploaderName = null,
                caption = "Two years and still the best decision I ever made.",
                hasAudio = false
            )
            "2" -> PhotoDetailMock(
                dateTime = "Oct 18, 2025 · 9:20 PM",
                location = "The Rooftop, Toronto",
                uploaderName = null,
                caption = "Dessert and city lights.",
                hasAudio = true
            )
            else -> personalFallback(albumName, "The Rooftop, Toronto")
        }
        "Sister's Euro Trip" -> when (photoId) {
            "1" -> PhotoDetailMock(
                dateTime = "Feb 2, 2024 · 11:00 AM",
                location = "Eiffel Tower, Paris",
                uploaderName = "isla",
                caption = "Paris day one. Still can't believe we're here.",
                hasAudio = true
            )
            "2" -> PhotoDetailMock(
                dateTime = "Feb 5, 2024 · 3:30 PM",
                location = "Sagrada Família, Barcelona",
                uploaderName = "blair",
                caption = "Barcelona stop. This place is unreal.",
                hasAudio = false
            )
            else -> sharedFallback(albumName, listOf("Eiffel Tower, Paris", "Sagrada Família, Barcelona", "Rome", "Amsterdam"))
        }
        else -> PhotoDetailMock(
            dateTime = "—",
            location = albumName,
            uploaderName = null,
            caption = "No caption.",
            hasAudio = false
        )
    }
}

private fun personalFallback(albumName: String, location: String): PhotoDetailMock =
    PhotoDetailMock(
        dateTime = "—",
        location = location,
        uploaderName = null,
        caption = "Added to $albumName",
        hasAudio = false
    )

private fun sharedFallback(albumName: String, locations: List<String>): PhotoDetailMock =
    PhotoDetailMock(
        dateTime = "—",
        location = locations.first(),
        uploaderName = sharedFriends.first(),
        caption = "Added to $albumName",
        hasAudio = false
    )
