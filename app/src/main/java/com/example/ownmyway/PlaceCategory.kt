package com.example.ownmyway

enum class PlaceCategory(
    val displayName: String,
    val emoji: String,
    val placeType: String,
    val group: Group
) {
    // Your traveling helper
    ART("Art", "🎨", "art_gallery", Group.TRAVELER),
    MUSEUMS("Museums", "🏛️", "museum", Group.TRAVELER),
    ATTRACTIONS("Attractions", "🗺️", "tourist_attraction", Group.TRAVELER),
    NIGHTLIFE("Nightlife", "🌙", "night_club", Group.TRAVELER),
    SHOPPING("Shopping", "🛍️", "shopping_mall", Group.TRAVELER),
    PARKS("Parks", "🌳", "park", Group.TRAVELER),
    ENTERTAINMENT("Entertainment", "🎭", "amusement_park", Group.TRAVELER),
    // General
    RESTAURANTS("Restaurants", "🍽️", "restaurant", Group.GENERAL),
    BARS("Bars", "🍺", "bar", Group.GENERAL),
    HOTELS("Hotels", "🏨", "lodging", Group.GENERAL),
    CAFES("Cafés", "☕", "cafe", Group.GENERAL);

    enum class Group { TRAVELER, GENERAL }
}
