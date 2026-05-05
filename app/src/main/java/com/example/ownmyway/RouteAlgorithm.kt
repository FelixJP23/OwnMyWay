package com.example.ownmyway

import android.graphics.Color
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PolylineOptions
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject

data class RouteResult(
    val stops: List<NearbyPlace>,
    val polylineOptions: PolylineOptions?,
    val waypointOrder: List<Int> = emptyList(),
    val totalEstimatedCost: Int = 0
)

class RouteAlgorithm(
    private val mapsApiKey: String,
    private val okHttpClient: OkHttpClient = OkHttpClient(),
    private val gson: Gson = Gson()
) {

    suspend fun buildRoute(origin: LatLng, preferences: RoutePreferences): RouteResult =
        withContext(Dispatchers.IO) {

        val placeWeights   = HobbyData.getPlaceWeights(preferences.selectedHobbies)
        val mandatoryStops = preferences.mandatoryStops.toMutableList()
        val remainingSlots = (preferences.stopCount - mandatoryStops.size).coerceAtLeast(1)

        // 1 ── Fetch candidates in parallel
        val candidatesByType = placeWeights.keys.map { type ->
            async { fetchNearby(origin, type) }
        }.awaitAll().flatten()

        val mandatoryIds = mandatoryStops.map { it.place_id }.toSet()

        val hobbyStops = candidatesByType
            .distinctBy { it.place_id }
            .filter { it.place_id !in mandatoryIds }
            .map { place ->
                val typeWeight  = placeWeights.values.maxOrNull() ?: 1.0
                val ratingScore = place.rating ?: 3.0
                val priceScore  = if (preferences.isLowSpender && (place.price_level ?: 2) > 2) 0.5 else 1.0
                place to (ratingScore * typeWeight * priceScore)
            }
            .sortedByDescending { it.second }
            .take(remainingSlots)
            .map { it.first }
            .toMutableList()

        val allStops = (mandatoryStops + hobbyStops).toMutableList()

        // 2 ── Café first if no hotel breakfast
        if (!preferences.hotelBreakfast) {
            val cafe = fetchNearby(origin, "cafe").firstOrNull()
            if (cafe != null && allStops.none { it.place_id == cafe.place_id }) {
                allStops.add(0, cafe)
            }
        }

        if (allStops.isEmpty()) return@withContext RouteResult(emptyList(), null)

        // 3 ── Enrich each stop with Place Details to get real price_level + types
        val enrichedStops = enrichWithDetails(allStops)

        // 4 ── Total cost with real data
        val totalEstimatedCost = enrichedStops.sumOf { it.estimatedCostBRL }
        Log.d("RouteAlgorithm", "Stops: ${enrichedStops.map { "${it.name}=${it.estimatedCostLabel}" }}")
        Log.d("RouteAlgorithm", "Total: R$$totalEstimatedCost")

        // 5 ── Directions API
        val destination    = enrichedStops.last()
        val waypoints      = enrichedStops.dropLast(1)
        val waypointsParam = if (waypoints.isNotEmpty())
            "optimize:true|" + waypoints.joinToString("|") {
                "${it.geometry.location.lat},${it.geometry.location.lng}"
            } else ""

        val url = buildString {
            append("https://maps.googleapis.com/maps/api/directions/json")
            append("?origin=${origin.latitude},${origin.longitude}")
            append("&destination=${destination.geometry.location.lat},${destination.geometry.location.lng}")
            if (waypointsParam.isNotEmpty()) append("&waypoints=$waypointsParam")
            append("&key=$mapsApiKey")
        }

        return@withContext try {
            val body   = okHttpClient.newCall(okhttp3.Request.Builder().url(url).build())
                .execute().body?.string()
                ?: return@withContext RouteResult(enrichedStops, null, totalEstimatedCost = totalEstimatedCost)
            val json   = JSONObject(body)
            val routes = json.getJSONArray("routes")
            if (routes.length() == 0)
                return@withContext RouteResult(enrichedStops, null, totalEstimatedCost = totalEstimatedCost)

            val route          = routes.getJSONObject(0)
            val optimizedOrder = route.optJSONArray("waypoint_order")
            val orderedStops   = if (optimizedOrder != null && waypoints.isNotEmpty()) {
                val order     = (0 until optimizedOrder.length()).map { optimizedOrder.getInt(it) }
                val reordered = order.map { waypoints[it] }.toMutableList()
                reordered.add(destination)
                reordered
            } else enrichedStops

            val polyline = PolylineOptions()
                .addAll(decodePolyline(route.getJSONObject("overview_polyline").getString("points")))
                .color(Color.parseColor("#4A2080")).width(12f).geodesic(true)

            RouteResult(orderedStops, polyline, totalEstimatedCost = orderedStops.sumOf { it.estimatedCostBRL })
        } catch (e: Exception) {
            Log.e("RouteAlgorithm", "Directions error", e)
            RouteResult(enrichedStops, null, totalEstimatedCost = totalEstimatedCost)
        }
    }

    /**
     * Fetches Place Details for each stop (in batches of 5) to get
     * real price_level and types. Falls back to original on any error.
     */
    private suspend fun enrichWithDetails(stops: List<NearbyPlace>): List<NearbyPlace> =
        withContext(Dispatchers.IO) {
            stops.chunked(5).flatMap { chunk ->
                chunk.map { place ->
                    async {
                        try {
                            val url  = "https://maps.googleapis.com/maps/api/place/details/json" +
                                "?place_id=${place.place_id}" +
                                "&fields=price_level,types,opening_hours,rating,photos" +
                                "&key=$mapsApiKey"
                            val body = okHttpClient.newCall(
                                okhttp3.Request.Builder().url(url).build()
                            ).execute().body?.string()

                            val result = body?.let {
                                gson.fromJson(it, PlaceDetailsResponse::class.java).result
                            }

                            if (result != null) {
                                place.copy(
                                    price_level   = result.price_level   ?: place.price_level,
                                    types         = result.types         ?: place.types,
                                    rating        = result.rating        ?: place.rating,
                                    opening_hours = result.opening_hours ?: place.opening_hours
                                )
                            } else place
                        } catch (e: Exception) {
                            Log.w("RouteAlgorithm", "Detail fetch failed for ${place.name}: ${e.message}")
                            place
                        }
                    }
                }.awaitAll()
            }
        }

    private suspend fun fetchNearby(center: LatLng, type: String): List<NearbyPlace> =
        withContext(Dispatchers.IO) {
            try {
                val url  = "https://maps.googleapis.com/maps/api/place/nearbysearch/json" +
                    "?location=${center.latitude},${center.longitude}&radius=5000&type=$type&key=$mapsApiKey"
                val body = okHttpClient.newCall(okhttp3.Request.Builder().url(url).build())
                    .execute().body?.string() ?: return@withContext emptyList()
                gson.fromJson(body, NearbySearchResponse::class.java).results ?: emptyList()
            } catch (e: Exception) {
                Log.e("RouteAlgorithm", "Nearby $type error", e); emptyList()
            }
        }

    private fun decodePolyline(encoded: String): List<LatLng> {
        val result = mutableListOf<LatLng>()
        var index = 0; var lat = 0; var lng = 0
        while (index < encoded.length) {
            var b: Int; var shift = 0; var res = 0
            do { b = encoded[index++].code - 63; res = res or (b and 0x1f shl shift); shift += 5 } while (b >= 0x20)
            lat += if (res and 1 != 0) (res shr 1).inv() else res shr 1; shift = 0; res = 0
            do { b = encoded[index++].code - 63; res = res or (b and 0x1f shl shift); shift += 5 } while (b >= 0x20)
            lng += if (res and 1 != 0) (res shr 1).inv() else res shr 1
            result.add(LatLng(lat / 1E5, lng / 1E5))
        }
        return result
    }
}
