package com.example.ownmyway

data class NearbySearchResponse(
    val results: List<NearbyPlace>,
    val status: String
)

data class NearbyPlace(
    val place_id: String,
    val name: String,
    val geometry: PlaceGeometry,
    val rating: Double? = null,
    val user_ratings_total: Int? = null,
    val vicinity: String? = null,
    val photos: List<PlacePhotoRef>? = null,
    val opening_hours: PlaceOpeningHours? = null,
    val price_level: Int? = null,          // 0=free 1=cheap 2=moderate 3=expensive 4=very expensive
    val types: List<String>? = null        // e.g. ["cafe", "food", "establishment"]
) {
    /** Cost in BRL: Google price_level first, then type heuristic, then R$60 */
    val estimatedCostBRL: Int get() {
        price_level?.let { return when (it) {
            0 -> 0; 1 -> 35; 2 -> 90; 3 -> 200; 4 -> 350; else -> 60
        }}
        val t = types?.firstOrNull { it != "point_of_interest" && it != "establishment" }
        return when (t) {
            "cafe"                                    -> 30
            "restaurant", "food"                      -> 75
            "bar"                                     -> 55
            "night_club"                              -> 80
            "museum"                                  -> 25
            "art_gallery"                             -> 20
            "tourist_attraction"                      -> 35
            "amusement_park"                          -> 60
            "zoo", "aquarium"                         -> 45
            "stadium"                                 -> 80
            "shopping_mall", "store"                  -> 120
            "spa"                                     -> 150
            "gym", "health"                           -> 40
            "park", "natural_feature"                 -> 0
            "church", "place_of_worship", "library"   -> 0
            "lodging"                                 -> 0
            "movie_theater"                           -> 35
            "bowling_alley"                           -> 50
            else                                      -> 60
        }
    }

    val estimatedCostLabel: String get() =
        if (estimatedCostBRL == 0) "Gratuito" else "R$ ~$estimatedCostBRL"

    val priceTag: String get() {
        price_level?.let { return when (it) {
            0 -> "Grátis"; 1 -> "$"; 2 -> "$$"; 3 -> "$$$"; 4 -> "$$$$"; else -> "$$"
        }}
        return when (estimatedCostBRL) {
            0         -> "Grátis"
            in 1..50  -> "$"
            in 51..100 -> "$$"
            in 101..250 -> "$$$"
            else      -> "$$$$"
        }
    }
}

data class PlaceGeometry(val location: PlaceLocation)
data class PlaceLocation(val lat: Double, val lng: Double)
data class PlacePhotoRef(val photo_reference: String)
data class PlaceOpeningHours(val open_now: Boolean? = null)

data class PlaceDetailsResponse(
    val result: PlaceDetailsResult?,
    val status: String
)

data class PlaceDetailsResult(
    val photos: List<PlacePhotoRef>? = null,
    val rating: Double? = null,
    val formatted_address: String? = null,
    val opening_hours: PlaceOpeningHours? = null,
    val price_level: Int? = null,
    val types: List<String>? = null
)
