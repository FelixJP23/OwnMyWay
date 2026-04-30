package com.example.ownmyway

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.gson.Gson
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import android.widget.FrameLayout
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.decodeRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.ownmyway.model.Friendship
import com.example.ownmyway.repository.FriendRepository

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var locationInitialized = false
    private var currentLatLng: LatLng? = null

    private lateinit var placesClient: PlacesClient
    val placeMarkers   = mutableListOf<Marker>()
    val markerPlaceMap = mutableMapOf<String, NearbyPlace>()
    var routePolyline: Polyline? = null

    val okHttpClient = OkHttpClient()
    val gson         = Gson()

    // ── Clima ─────────────────────────────────────────────────────────────
    private lateinit var tvTemperature: TextView
    private lateinit var tvWeatherCondition: TextView
    private lateinit var ivWeatherIcon: android.widget.ImageView

    // ── Last built route (for offline download) ───────────────────────────
    private var lastRouteStops: List<NearbyPlace> = emptyList()
    private var lastRouteId:    String             = ""
    private lateinit var fabOffline: FloatingActionButton

    val mapsApiKey: String by lazy {
        packageManager
            .getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            .metaData.getString("com.google.android.geo.API_KEY") ?: ""
    }

    // ── Autocomplete launcher ─────────────────────────────────────────────
    private val searchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val place = Autocomplete.getPlaceFromIntent(result.data!!)
            place.latLng?.let { latLng ->
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                fetchAndShowPlaceDetail(place.id ?: "", place.name ?: "", latLng)
            }
        }
    }

    // ── Route launcher ────────────────────────────────────────────────────
    private val routeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val data           = result.data!!
            val travelRhythm   = data.getStringExtra("travel_rhythm")  ?: "moderate"
            val spendingLevel  = data.getStringExtra("spending_level") ?: "medium"
            val hobbies        = data.getStringArrayListExtra("hobbies") ?: arrayListOf()
            val hotelBreakfast = data.getBooleanExtra("hotel_breakfast", false)

            // Deserialize mandatory stops
            val mandatoryStops = mutableListOf<NearbyPlace>()
            val stopsJson = data.getStringExtra("mandatory_stops_json")
            if (!stopsJson.isNullOrBlank()) {
                try {
                    val arr = JSONArray(stopsJson)
                    for (i in 0 until arr.length()) {
                        val obj = JSONObject(arr.getString(i))
                        mandatoryStops.add(NearbyPlace(
                            place_id = obj.getString("place_id"),
                            name     = obj.getString("name"),
                            geometry = PlaceGeometry(PlaceLocation(
                                obj.getDouble("lat"), obj.getDouble("lng")
                            )),
                            vicinity = obj.optString("vicinity")
                        ))
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Mandatory stops parse error", e)
                }
            }

            val prefs = RoutePreferences(
                mandatoryStops  = mandatoryStops,
                hotelBreakfast  = hotelBreakfast,
                selectedHobbies = hobbies,
                travelRhythm    = travelRhythm,
                spendingLevel   = spendingLevel
            )

            val origin = currentLatLng ?: map.cameraPosition.target

            CoroutineScope(Dispatchers.IO).launch {
                val algorithm   = RouteAlgorithm(mapsApiKey, okHttpClient, gson)
                val routeResult = algorithm.buildRoute(origin, prefs)

                withContext(Dispatchers.Main) {
                    routePolyline?.remove()
                    placeMarkers.forEach { it.remove() }
                    placeMarkers.clear()
                    markerPlaceMap.clear()

                    routeResult.stops.forEachIndexed { idx, place ->
                        val latLng = LatLng(
                            place.geometry.location.lat,
                            place.geometry.location.lng
                        )
                        val marker = map.addMarker(
                            MarkerOptions()
                                .position(latLng)
                                .title("${idx + 1}. ${place.name}")
                                .icon(BitmapDescriptorFactory.defaultMarker(
                                    BitmapDescriptorFactory.HUE_VIOLET))
                        ) ?: return@forEachIndexed
                        placeMarkers.add(marker)
                        markerPlaceMap[marker.id] = place
                    }

                    if (routeResult.polylineOptions != null) {
                        routePolyline = map.addPolyline(routeResult.polylineOptions)
                    }

                    if (placeMarkers.isNotEmpty()) {
                        val bounds = placeMarkers
                            .fold(LatLngBounds.builder()) { b, m -> b.include(m.position) }
                            .build()
                        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120))
                    }

                    // Store route for offline download and show the FAB
                    if (routeResult.stops.isNotEmpty()) {
                        lastRouteStops = routeResult.stops
                        lastRouteId    = System.currentTimeMillis().toString()
                        fabOffline.visibility = View.VISIBLE
                    } else {
                        fabOffline.visibility = View.GONE
                        Toast.makeText(
                            this@MainActivity,
                            "No places found for your preferences. Try different hobbies!",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvTemperature = findViewById(R.id.tvTemperature)
        tvWeatherCondition = findViewById(R.id.tvWeatherCondition)
        ivWeatherIcon = findViewById(R.id.ivWeatherIcon)

        setupBottomNavigation()

        MealNotificationReceiver.createChannel(this)

        if (!Places.isInitialized()) Places.initialize(applicationContext, mapsApiKey)
        placesClient = Places.createClient(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        (supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment)
            .getMapAsync(this)

        fabOffline = findViewById(R.id.fabOffline)

        findViewById<TextView>(R.id.tvSearch).setOnClickListener { openAutocomplete() }

        findViewById<ImageButton>(R.id.btnFilter).setOnClickListener {
            FilterBottomSheet().show(supportFragmentManager, "filter")
        }

        findViewById<FloatingActionButton>(R.id.fabCamera).setOnClickListener {
            startActivity(Intent(this, CameraActivity::class.java))
        }

        findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener {
            routeLauncher.launch(Intent(this, CreateRouteActivity::class.java))
        }

        fabOffline.setOnClickListener {
            if (lastRouteStops.isEmpty()) {
                Toast.makeText(this, "Create a route first!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivity(Intent(this, OfflineMapActivity::class.java).apply {
                putExtra(OfflineMapActivity.EXTRA_ROUTE_ID,   lastRouteId)
                putExtra(OfflineMapActivity.EXTRA_STOPS_JSON, buildStopsJson(lastRouteStops))
            })
        }

        supportFragmentManager.setFragmentResultListener("filter_result", this) { _, bundle ->
            val names = bundle.getStringArrayList("categories") ?: return@setFragmentResultListener
            val categories = names.mapNotNull { runCatching { PlaceCategory.valueOf(it) }.getOrNull() }
            searchNearby(categories)
        }

        supportFragmentManager.setFragmentResultListener("route_request", this) { _, bundle ->
            drawRoute(
                LatLng(bundle.getDouble("lat"), bundle.getDouble("lng")),
                bundle.getString("name", "")
            )
        }

        intent?.getStringExtra("meal_suggestion")?.let {
            searchNearby(listOf(PlaceCategory.RESTAURANTS))
        }


        verificarPedidosPendentes()
        setupRealtimeFriendRequests()

        findViewById<android.widget.ImageView>(R.id.imgProfile).setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    currentLatLng?.let { latLng ->
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                    } ?: run {
                        Toast.makeText(this, "Localização ainda não disponível", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                R.id.nav_friends -> {
                    atualizarBadgeAmigos(false)
                    startActivity(Intent(this, FriendManagerActivity::class.java))
                    true
                }
                R.id.nav_budget -> {
                    startActivity(Intent(this, BudgetActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun performLogout() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun verificarPedidosPendentes() {
        val myId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: return
        lifecycleScope.launch {
            try {
                val temPedido = FriendRepository.checkPendingRequests(myId)
                atualizarBadgeAmigos(temPedido) // Substitui a busca por ID do XML
            } catch (e: Exception) {
                Log.e("Main", "Erro badge: ${e.message}")
            }
        }
    }

    private fun atualizarBadgeAmigos(exibir: Boolean) {
        val bottomNav = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation)
        // O ID deve ser o mesmo do seu res/menu/bottom_nav_menu.xml
        val badge = bottomNav.getOrCreateBadge(R.id.nav_friends)

        badge.isVisible = exibir
        badge.backgroundColor = Color.RED // Ou a cor do seu bg_red_badge
        badge.badgeGravity = com.google.android.material.badge.BadgeDrawable.TOP_END
    }

    private fun setupRealtimeFriendRequests() {
        val myId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: return

        lifecycleScope.launch {
            try {
                // 1. Criamos o canal
                val channel = SupabaseClient.client.realtime.channel("pedidos_amizade")

                // 2. Configuramos o fluxo para escutar INSERÇÕES na tabela friendships
                val flow = channel.postgresChangeFlow<PostgresAction.Insert>(
                    schema = "public"
                ) {
                    table = "friendships"
                }

                // 3. Ativamos a escuta
                channel.subscribe()

                // 4. O collect deve ficar aqui embaixo! Ele trava o código neste ponto
                // esperando por novos dados do banco.
                flow.collect { action ->
                    try {
                        val newRequest = action.decodeRecord<Friendship>()

                        Log.d("FRIEND_DEBUG", "Novo pedido detectado para: ${newRequest.receiver_id}")

                        // Se o pedido for para o usuário logado e estiver pendente
                        if (newRequest.receiver_id == myId && newRequest.status == "pending") {
                            withContext(Dispatchers.Main) {
                                // Ativa a bolinha vermelha no menu inferior
                                atualizarBadgeAmigos(true)
                                Toast.makeText(this@MainActivity, "Novo pedido de amizade!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("FRIEND_DEBUG", "Erro ao decodificar pedido: ${e.message}")

                        // Fallback: Se houver erro na decodificação mas algo chegou no canal,
                        // ainda assim notificamos o usuário por precaução.
                        withContext(Dispatchers.Main) {
                            atualizarBadgeAmigos(true)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("Realtime", "Erro ao conectar no Realtime: ${e.message}")
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        try {
            map.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style))
        } catch (e: Exception) {}
        map.uiSettings.isZoomControlsEnabled     = false
        map.uiSettings.isMyLocationButtonEnabled = false

        map.setOnMarkerClickListener { marker ->
            val place = markerPlaceMap[marker.id] ?: return@setOnMarkerClickListener false
            val photoUrls = ArrayList(
                (place.photos ?: emptyList()).take(5).map { getPhotoUrl(it.photo_reference) }
            )
            PlaceDetailBottomSheet.newInstance(
                name      = place.name,
                rating    = place.rating ?: 0.0,
                address   = place.vicinity ?: "",
                isOpen    = place.opening_hours?.open_now,
                photoUrls = photoUrls,
                lat       = place.geometry.location.lat,
                lng       = place.geometry.location.lng
            ).show(supportFragmentManager, "place_detail")
            true
        }
        requestLocationPermission()
    }

    private fun requestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) startLocationUpdates()
        else ActivityCompat.requestPermissions(
            this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 200
        )
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return
        map.isMyLocationEnabled = true
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000).build()
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                currentLatLng = LatLng(loc.latitude, loc.longitude)
                if (!locationInitialized) {
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng!!, 15f))
                    locationInitialized = true
                    fetchWeather(currentLatLng!!)
                }
            }
        }
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    private fun fetchWeather(latLng: LatLng) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = "https://api.open-meteo.com/v1/forecast" +
                        "?latitude=${latLng.latitude}&longitude=${latLng.longitude}" +
                        "&current=temperature_2m,weather_code"
                
                val response = okHttpClient.newCall(okhttp3.Request.Builder().url(url).build()).execute()
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                val current = json.getJSONObject("current")
                val temp = current.getDouble("temperature_2m")
                val code = current.getInt("weather_code")

                withContext(Dispatchers.Main) {
                    tvTemperature.text = "${temp.toInt()}°C"
                    updateWeatherUI(code)
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Weather Error", e)
                withContext(Dispatchers.Main) {
                    tvWeatherCondition.text = "Error"
                }
            }
        }
    }

    private fun updateWeatherUI(code: Int) {
        val (iconRes, condition) = when (code) {
            0 -> Pair(android.R.drawable.ic_menu_day, "Céu Limpo")
            1, 2, 3 -> Pair(android.R.drawable.ic_menu_agenda, "Nublado")
            45, 48 -> Pair(android.R.drawable.ic_menu_view, "Neblina")
            51, 53, 55, 61, 63, 65, 80, 81, 82 -> Pair(android.R.drawable.ic_menu_directions, "Chuva")
            71, 73, 75, 85, 86 -> Pair(android.R.drawable.ic_menu_help, "Neve")
            95, 96, 99 -> Pair(android.R.drawable.ic_dialog_alert, "Tempestade")
            else -> Pair(android.R.drawable.ic_menu_compass, "---")
        }
        ivWeatherIcon.setImageResource(iconRes)
        ivWeatherIcon.setColorFilter(Color.parseColor("#4A2080"))
        tvWeatherCondition.text = condition
    }

    private fun openAutocomplete() {
        val fields = listOf(
            Place.Field.ID, Place.Field.NAME,
            Place.Field.LAT_LNG, Place.Field.ADDRESS
        )
        searchLauncher.launch(
            Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields).build(this)
        )
    }

    fun searchNearby(categories: List<PlaceCategory>) {
        val center = currentLatLng ?: map.cameraPosition.target
        placeMarkers.forEach { it.remove() }
        placeMarkers.clear()
        markerPlaceMap.clear()

        CoroutineScope(Dispatchers.IO).launch {
            val allPlaces = mutableListOf<NearbyPlace>()
            categories.map { async { fetchNearbyPlaces(center, it.placeType) } }
                .forEach { allPlaces.addAll(it.await()) }

            withContext(Dispatchers.Main) {
                if (allPlaces.isEmpty()) {
                    Toast.makeText(this@MainActivity, "No places found nearby", Toast.LENGTH_SHORT).show()
                    return@withContext
                }
                allPlaces.forEach { place ->
                    val latLng = LatLng(place.geometry.location.lat, place.geometry.location.lng)
                    val marker = map.addMarker(
                        MarkerOptions()
                            .position(latLng).title(place.name)
                            .icon(BitmapDescriptorFactory.defaultMarker(
                                BitmapDescriptorFactory.HUE_VIOLET))
                    ) ?: return@forEach
                    placeMarkers.add(marker)
                    markerPlaceMap[marker.id] = place
                }
                if (placeMarkers.isNotEmpty())
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                        LatLng(allPlaces[0].geometry.location.lat,
                               allPlaces[0].geometry.location.lng), 13f))
            }
        }
    }

    private suspend fun fetchNearbyPlaces(center: LatLng, type: String): List<NearbyPlace> =
        withContext(Dispatchers.IO) {
            try {
                val url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json" +
                    "?location=${center.latitude},${center.longitude}" +
                    "&radius=3000&type=$type&key=$mapsApiKey"
                val body = okHttpClient.newCall(
                    okhttp3.Request.Builder().url(url).build()
                ).execute().body?.string() ?: return@withContext emptyList()
                gson.fromJson(body, NearbySearchResponse::class.java).results ?: emptyList()
            } catch (e: Exception) {
                Log.e("MainActivity", "Nearby: $type", e); emptyList()
            }
        }

    private fun fetchAndShowPlaceDetail(placeId: String, name: String, latLng: LatLng) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = "https://maps.googleapis.com/maps/api/place/details/json" +
                    "?place_id=$placeId" +
                    "&fields=photos,rating,formatted_address,opening_hours" +
                    "&key=$mapsApiKey"
                val body = okHttpClient.newCall(
                    okhttp3.Request.Builder().url(url).build()
                ).execute().body?.string() ?: return@launch
                val details = gson.fromJson(body, PlaceDetailsResponse::class.java).result
                val photoUrls = ArrayList(
                    (details?.photos ?: emptyList()).take(5).map { getPhotoUrl(it.photo_reference) }
                )
                withContext(Dispatchers.Main) {
                    PlaceDetailBottomSheet.newInstance(
                        name      = name,
                        rating    = details?.rating ?: 0.0,
                        address   = details?.formatted_address ?: "",
                        isOpen    = details?.opening_hours?.open_now,
                        photoUrls = photoUrls,
                        lat       = latLng.latitude,
                        lng       = latLng.longitude
                    ).show(supportFragmentManager, "place_detail")
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Details error", e)
            }
        }
    }

    fun drawRoute(destination: LatLng, placeName: String) {
        val origin = currentLatLng ?: run {
            Toast.makeText(this, "Could not get your location", Toast.LENGTH_SHORT).show()
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = "https://maps.googleapis.com/maps/api/directions/json" +
                    "?origin=${origin.latitude},${origin.longitude}" +
                    "&destination=${destination.latitude},${destination.longitude}" +
                    "&key=$mapsApiKey"
                val body = okHttpClient.newCall(
                    okhttp3.Request.Builder().url(url).build()
                ).execute().body?.string() ?: return@launch
                val routes = JSONObject(body).getJSONArray("routes")
                if (routes.length() == 0) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "No route found", Toast.LENGTH_SHORT).show()
                    }; return@launch
                }
                val points = routes.getJSONObject(0)
                    .getJSONObject("overview_polyline").getString("points")
                withContext(Dispatchers.Main) {
                    routePolyline?.remove()
                    routePolyline = map.addPolyline(
                        PolylineOptions()
                            .addAll(decodePolyline(points))
                            .color(Color.parseColor("#4A2080"))
                            .width(12f)
                            .geodesic(true)
                    )
                    val bounds = LatLngBounds.builder()
                        .include(origin).include(destination).build()
                    map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120))
                    Toast.makeText(this@MainActivity, "Route to $placeName", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Route error", e)
            }
        }
    }

    private fun getPhotoUrl(ref: String) =
        "https://maps.googleapis.com/maps/api/place/photo" +
            "?photo_reference=$ref&maxwidth=800&key=$mapsApiKey"

    private fun buildStopsJson(stops: List<NearbyPlace>): String {
        val arr = JSONArray()
        stops.forEach { place ->
            arr.put(JSONObject().apply {
                put("place_id", place.place_id)
                put("name",     place.name)
                put("lat",      place.geometry.location.lat)
                put("lng",      place.geometry.location.lng)
                put("vicinity", place.vicinity ?: "")
            })
        }
        return arr.toString()
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

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 200 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
            startLocationUpdates()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::locationCallback.isInitialized)
            fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}
