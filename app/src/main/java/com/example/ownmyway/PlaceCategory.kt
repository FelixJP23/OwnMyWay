package com.example.ownmyway

enum class PlaceCategory(
    val displayName: String,
    val emoji: String,
    val placeType: String,
    val group: Group
) {
    // Your traveling helper
    ART("Arte", "🎨", "art_gallery", Group.TRAVELER),
    MUSEUMS("Museus", "🏛️", "museum", Group.TRAVELER),
    ATTRACTIONS("Atrações", "🗺️", "tourist_attraction", Group.TRAVELER),
    NIGHTLIFE("Vida noturna", "🌙", "night_club", Group.TRAVELER),
    SHOPPING("Compras", "🛍️", "shopping_mall", Group.TRAVELER),
    PARKS("Parques", "🌳", "park", Group.TRAVELER),
    ENTERTAINMENT("Entretenimento", "🎭", "amusement_park", Group.TRAVELER),
    // General
    RESTAURANTS("Restaurantes", "🍽️", "restaurant", Group.GENERAL),
    BARS("Bares", "🍺", "bar", Group.GENERAL),
    HOTELS("Hotéis", "🏨", "lodging", Group.GENERAL),
    CAFES("Cafés", "☕", "cafe", Group.GENERAL);

    enum class Group { TRAVELER, GENERAL }
}
