package com.example.ownmyway

data class RoutePreferences(
    val specificPlaces: List<String> = emptyList(),
    val mandatoryStops: List<NearbyPlace> = emptyList(), // resolved from Maps API
    val hotelBreakfast: Boolean = false,
    val wantsLunchSuggestion: Boolean = false,
    val wantsDinnerSuggestion: Boolean = false,
    val selectedHobbies: List<String> = emptyList(),
    val travelRhythm: String = "moderate",   // "relaxed" | "moderate" | "fast"
    val spendingLevel: String = "medium"     // "low" | "medium" | "high"
) {
    // Total stops including mandatory ones
    val stopCount: Int get() = when (travelRhythm) {
        "relaxed" -> 3
        "fast"    -> 7
        else      -> 5
    }
    val isLowSpender: Boolean get() = spendingLevel == "low"
}
