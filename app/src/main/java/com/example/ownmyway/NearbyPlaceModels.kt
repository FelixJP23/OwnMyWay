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
    val opening_hours: PlaceOpeningHours? = null
)

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
    val opening_hours: PlaceOpeningHours? = null
)
