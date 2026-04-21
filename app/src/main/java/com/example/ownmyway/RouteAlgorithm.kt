package com.example.ownmyway

import android.graphics.Color
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PolylineOptions
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject

data class RouteResult(
    val stops: List<NearbyPlace>,
    val polylineOptions: PolylineOptions?,
    val waypointOrder: List<Int> = emptyList()
)

class RouteAlgorithm(
    private val mapsApiKey: String,
    private val okHttpClient: OkHttpClient = OkHttpClient(),
    private val gson: Gson = Gson()
) {

    suspend fun buildRoute(
        origin: LatLng,
        preferences: RoutePreferences
    ): RouteResult = withContext(Dispatchers.IO) {

        val placeWeights = HobbyData.getPlaceWeights(preferences.selectedHobbies)

        // 1 ── Mandatory stops come FIRST, always included regardless of scoring
        val mandatoryStops = preferences.mandatoryStops.toMutableList()

        // 2 ── How many hobby-based stops to fill remaining slots
        val remainingSlots = (preferences.stopCount - mandatoryStops.size).coerceAtLeast(1)

        // 3 ── Fetch and score hobby-based candidates
        val candidatesByType = placeWeights.keys.map { type ->
            async { fetchNearby(origin, type) }
        }.flatMap { it.await() }

        val mandatoryIds = mandatoryStops.map { it.place_id }.toSet()

        val hobbyStops = candidatesByType
            .distinctBy { it.place_id }
            .filter { it.place_id !in mandatoryIds } // don't duplicate mandatory stops
            .map { place ->
                val typeWeight = placeWeights.values.maxOrNull() ?: 1.0
                val ratingScore = (place.rating ?: 3.0)
                val totalScore = ratingScore * typeWeight
                place to totalScore
            }
            .sortedByDescending { it.second }
            .take(remainingSlots)
            .map { it.first }
            .toMutableList()

        // 4 ── Final ordered list: mandatory first, then hobby-based
        val allStops = (mandatoryStops + hobbyStops).toMutableList()

        // 5 ── If no hotel breakfast → café is ALWAYS the very first stop,
        //      even before mandatory places (breakfast comes before anything else)
        if (!preferences.hotelBreakfast) {
            val cafe = fetchNearby(origin, "cafe").firstOrNull()
            val alreadyIncluded = allStops.any { it.place_id == cafe?.place_id }
            if (cafe != null && !alreadyIncluded) {
                allStops.add(0, cafe)
            }
        }

        if (allStops.isEmpty()) return@withContext RouteResult(emptyList(), null)

        // 6 ── Build optimized Directions API request
        val destination = allStops.last()
        val waypoints   = allStops.dropLast(1)

        val waypointsParam = if (waypoints.isNotEmpty()) {
            "optimize:true|" + waypoints.joinToString("|") {
                "${it.geometry.location.lat},${it.geometry.location.lng}"
            }
        } else ""

        val destParam = "${destination.geometry.location.lat},${destination.geometry.location.lng}"
        val url = buildString {
            append("https://maps.googleapis.com/maps/api/directions/json")
            append("?origin=${origin.latitude},${origin.longitude}")
            append("&destination=$destParam")
            if (waypointsParam.isNotEmpty()) append("&waypoints=$waypointsParam")
            append("&key=$mapsApiKey")
        }

        return@withContext try {
            val body = okHttpClient.newCall(
                okhttp3.Request.Builder().url(url).build()
            ).execute().body?.string() ?: return@withContext RouteResult(allStops, null)

            val json   = JSONObject(body)
            val routes = json.getJSONArray("routes")
            if (routes.length() == 0) return@withContext RouteResult(allStops, null)

            val route = routes.getJSONObject(0)

            // Re-order hobby stops by optimised waypoint_order
            // Mandatory stops keep their original order (they are first)
            val optimizedOrder = route.optJSONArray("waypoint_order")
            val orderedStops = if (optimizedOrder != null && waypoints.isNotEmpty()) {
                val order     = (0 until optimizedOrder.length()).map { optimizedOrder.getInt(it) }
                val reordered = order.map { waypoints[it] }.toMutableList()
                reordered.add(destination)
                reordered
            } else allStops

            val points = route
                .getJSONObject("overview_polyline")
                .getString("points")

            val polyline = PolylineOptions()
                .addAll(decodePolyline(points))
                .color(Color.parseColor("#4A2080"))
                .width(12f)
                .geodesic(true)

            RouteResult(orderedStops, polyline)
        } catch (e: Exception) {
            Log.e("RouteAlgorithm", "Directions API error", e)
            RouteResult(allStops, null)
        }
    }

    private suspend fun fetchNearby(center: LatLng, type: String): List<NearbyPlace> =
        withContext(Dispatchers.IO) {
            try {
                val url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json" +
                    "?location=${center.latitude},${center.longitude}" +
                    "&radius=5000&type=$type&key=$mapsApiKey"
                val body = okHttpClient.newCall(
                    okhttp3.Request.Builder().url(url).build()
                ).execute().body?.string() ?: return@withContext emptyList()
                gson.fromJson(body, NearbySearchResponse::class.java).results ?: emptyList()
            } catch (e: Exception) {
                Log.e("RouteAlgorithm", "Nearby $type error", e)
                emptyList()
            }
        }

    private fun decodePolyline(encoded: String): List<LatLng> {
        val result = mutableListOf<LatLng>()
        var index = 0; var lat = 0; var lng = 0
        while (index < encoded.length) {
            var b: Int; var shift = 0; var res = 0
            do { b = encoded[index++].code - 63; res = res or (b and 0x1f shl shift); shift += 5 } while (b >= 0x20)
            lat += if (res and 1 != 0) (res shr 1).inv() else res shr 1
            shift = 0; res = 0
            do { b = encoded[index++].code - 63; res = res or (b and 0x1f shl shift); shift += 5 } while (b >= 0x20)
            lng += if (res and 1 != 0) (res shr 1).inv() else res shr 1
            result.add(LatLng(lat / 1E5, lng / 1E5))
        }
        return result
    }
}
