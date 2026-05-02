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
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.addCallback
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.ownmyway.model.Friendship
import com.example.ownmyway.repository.FriendRepository
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
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject

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

    // ── Weather ───────────────────────────────────────────────────────────
    private lateinit var tvTemperature: TextView
    private lateinit var tvWeatherCondition: TextView
    private lateinit var ivWeatherIcon: ImageView

    // ── Offline FAB ───────────────────────────────────────────────────────
    private var lastRouteStops: List<NearbyPlace> = emptyList()
    private var lastRouteId:    String             = ""
    private var lastRouteTotalCost: Int            = 0
    private lateinit var fabOffline: FloatingActionButton

    // ── Cost counter ──────────────────────────────────────────────────────
    private lateinit var tvCostCounter: TextView

    // ── Speed dial ────────────────────────────────────────────────────────
    private lateinit var fabMenu: FloatingActionButton
    private lateinit var fabScrim: View
    private lateinit var fabItemRoute: View
    private lateinit var fabItemCamera: View
    private lateinit var fabItemManage: View
    private var isFabMenuOpen = false

    val mapsApiKey: String by lazy {
        packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
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

    // ── Manage routes launcher ────────────────────────────────────────
    private val manageRoutesLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val stopsJson = result.data!!.getStringExtra(ManageRoutesActivity.RESULT_STOPS_JSON)
            if (!stopsJson.isNullOrBlank()) {
                restoreRouteFromJson(stopsJson)
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
                            geometry = PlaceGeometry(PlaceLocation(obj.getDouble("lat"), obj.getDouble("lng"))),
                            vicinity = obj.optString("vicinity")
                        ))
                    }
                } catch (e: Exception) { Log.e("MainActivity", "Mandatory stops parse error", e) }
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
                val routeResult = RouteAlgorithm(mapsApiKey, okHttpClient, gson).buildRoute(origin, prefs)

                withContext(Dispatchers.Main) {
                    routePolyline?.remove()
                    placeMarkers.forEach { it.remove() }
                    placeMarkers.clear(); markerPlaceMap.clear()

                    routeResult.stops.forEachIndexed { idx, place ->
                        val latLng = LatLng(place.geometry.location.lat, place.geometry.location.lng)
                        val marker = map.addMarker(MarkerOptions()
                            .position(latLng).title("${idx + 1}. ${place.name}")
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET))
                        ) ?: return@forEachIndexed
                        placeMarkers.add(marker); markerPlaceMap[marker.id] = place
                    }

                    routeResult.polylineOptions?.let { routePolyline = map.addPolyline(it) }

                    if (placeMarkers.isNotEmpty()) {
                        val bounds = placeMarkers.fold(LatLngBounds.builder()) { b, m -> b.include(m.position) }.build()
                        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120))
                    }

                    if (routeResult.stops.isNotEmpty()) {
                        lastRouteStops     = routeResult.stops
                        lastRouteId        = System.currentTimeMillis().toString()
                        lastRouteTotalCost = routeResult.totalEstimatedCost
                        fabOffline.visibility = View.VISIBLE
                        showCostCounter(routeResult.totalEstimatedCost)
                    } else {
                        fabOffline.visibility = View.GONE
                        Toast.makeText(this@MainActivity,
                            "No places found for your preferences. Try different hobbies!", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // ── onCreate ──────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvTemperature      = findViewById(R.id.tvTemperature)
        tvWeatherCondition = findViewById(R.id.tvWeatherCondition)
        ivWeatherIcon      = findViewById(R.id.ivWeatherIcon)
        fabOffline         = findViewById(R.id.fabOffline)
        tvCostCounter      = findViewById(R.id.tvCostCounter)
        fabMenu            = findViewById(R.id.fabMenu)
        fabScrim           = findViewById(R.id.fabScrim)
        fabItemRoute       = findViewById(R.id.fabItemRoute)
        fabItemCamera      = findViewById(R.id.fabItemCamera)
        fabItemManage      = findViewById(R.id.fabItemManage)

        setupBottomNavigation()
        MealNotificationReceiver.createChannel(this)

        if (!Places.isInitialized()) Places.initialize(applicationContext, mapsApiKey)
        placesClient = Places.createClient(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        (supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment).getMapAsync(this)

        // Search bar
        findViewById<TextView>(R.id.tvSearch).setOnClickListener { openAutocomplete() }
        findViewById<ImageButton>(R.id.btnFilter).setOnClickListener {
            if (isFabMenuOpen) closeFabMenu()
            FilterBottomSheet().show(supportFragmentManager, "filter")
        }

        // Speed dial
        fabMenu.setOnClickListener { toggleFabMenu() }
        fabScrim.setOnClickListener { closeFabMenu() }

        // Handle back gesture: close speed dial first if open
        onBackPressedDispatcher.addCallback(this) {
            if (isFabMenuOpen) closeFabMenu()
            else isEnabled = false  // let system handle it
        }

        findViewById<FloatingActionButton>(R.id.fabRouteItem).setOnClickListener {
            closeFabMenu()
            routeLauncher.launch(Intent(this, CreateRouteActivity::class.java))
        }
        fabItemRoute.setOnClickListener {
            closeFabMenu()
            routeLauncher.launch(Intent(this, CreateRouteActivity::class.java))
        }
        findViewById<FloatingActionButton>(R.id.fabCameraItem).setOnClickListener {
            closeFabMenu()
            startActivity(Intent(this, CameraActivity::class.java))
        }
        fabItemCamera.setOnClickListener {
            closeFabMenu()
            startActivity(Intent(this, CameraActivity::class.java))
        }
        val openManageRoutes = {
            closeFabMenu()
            manageRoutesLauncher.launch(Intent(this, ManageRoutesActivity::class.java).apply {
                putExtra(ManageRoutesActivity.EXTRA_CURRENT_STOPS_JSON,  buildStopsJson(lastRouteStops))
                putExtra(ManageRoutesActivity.EXTRA_CURRENT_COST,        lastRouteTotalCost)
                putExtra(ManageRoutesActivity.EXTRA_CURRENT_STOP_COUNT,  lastRouteStops.size)
            })
        }
        findViewById<FloatingActionButton>(R.id.fabManageRoutes).setOnClickListener { openManageRoutes() }
        fabItemManage.setOnClickListener { openManageRoutes() }

        // Offline FAB
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

        // Fragment results
        supportFragmentManager.setFragmentResultListener("filter_result", this) { _, bundle ->
            val names = bundle.getStringArrayList("categories") ?: return@setFragmentResultListener
            val cats  = names.mapNotNull { runCatching { PlaceCategory.valueOf(it) }.getOrNull() }
            searchNearby(cats)
        }
        supportFragmentManager.setFragmentResultListener("route_request", this) { _, bundle ->
            drawRoute(LatLng(bundle.getDouble("lat"), bundle.getDouble("lng")), bundle.getString("name", ""))
        }

        intent?.getStringExtra("meal_suggestion")?.let { searchNearby(listOf(PlaceCategory.RESTAURANTS)) }

        verificarPedidosPendentes()
        setupRealtimeFriendRequests()
        carregarFotoPerfil()

        findViewById<ImageView>(R.id.ivUserProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }

    // ── Speed dial ────────────────────────────────────────────────────────
    private fun toggleFabMenu() { if (isFabMenuOpen) closeFabMenu() else openFabMenu() }

    private fun openFabMenu() {
        fabOffline.visibility = View.GONE
        isFabMenuOpen = true
        fabMenu.animate().rotation(90f).setDuration(250).setInterpolator(OvershootInterpolator()).start()
        fabScrim.visibility = View.VISIBLE
        fabScrim.animate().alpha(1f).setDuration(250).start()

        listOf(fabItemRoute, fabItemCamera, fabItemManage).forEachIndexed { idx, item ->
            item.visibility   = View.VISIBLE
            item.alpha        = 0f
            item.translationY = 40f * resources.displayMetrics.density
            item.animate().alpha(1f).translationY(0f)
                .setDuration(250).setStartDelay((idx * 60).toLong())
                .setInterpolator(DecelerateInterpolator()).start()
        }
    }

    private fun closeFabMenu() {
        isFabMenuOpen = false
        fabMenu.animate().rotation(0f).setDuration(200).setInterpolator(DecelerateInterpolator()).start()
        fabScrim.animate().alpha(0f).setDuration(200).withEndAction { fabScrim.visibility = View.GONE }.start()

        listOf(fabItemManage, fabItemCamera, fabItemRoute).forEachIndexed { idx, item ->
            item.animate().alpha(0f).translationY(40f * resources.displayMetrics.density)
                .setDuration(180).setStartDelay((idx * 40).toLong())
                .setInterpolator(DecelerateInterpolator())
                .withEndAction { item.visibility = View.INVISIBLE }.start()
        }

        // Restore offline FAB if a route was already created
        if (lastRouteStops.isNotEmpty()) {
            fabOffline.visibility = View.VISIBLE
        }
    }

    // ── Cost counter ──────────────────────────────────────────────────────
    private fun showCostCounter(totalCost: Int) {
        tvCostCounter.text = "💰 Gasto estimado: R$ $totalCost"
        tvCostCounter.visibility = View.VISIBLE
        tvCostCounter.translationY = 120f
        tvCostCounter.animate().translationY(0f).setDuration(350)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    // ── Bottom navigation ─────────────────────────────────────────────────
    private fun setupBottomNavigation() {
        val bottomNav = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    currentLatLng?.let { map.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 15f)) }
                        ?: Toast.makeText(this, "Localização ainda não disponível", Toast.LENGTH_SHORT).show()
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

    private fun carregarFotoPerfil() {
        val ivProfile = findViewById<ImageView>(R.id.ivUserProfile)
        lifecycleScope.launch {
            val perfil = FriendRepository.getMyProfile()
            if (perfil?.avatar_url != null) {
                Glide.with(this@MainActivity).load(perfil.avatar_url)
                    .placeholder(R.drawable.ic_user_placeholder).circleCrop().into(ivProfile)
            } else {
                ivProfile.setImageResource(R.drawable.ic_user_placeholder)
            }
        }
    }

    private fun verificarPedidosPendentes() {
        val myId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: return
        lifecycleScope.launch {
            try {
                val temPedido = FriendRepository.checkPendingRequests(myId)
                atualizarBadgeAmigos(temPedido)
            } catch (e: Exception) { Log.e("Main", "Erro badge: ${e.message}") }
        }
    }

    private fun atualizarBadgeAmigos(exibir: Boolean) {
        val bottomNav = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation)
        val badge = bottomNav.getOrCreateBadge(R.id.nav_friends)
        badge.isVisible        = exibir
        badge.backgroundColor  = Color.RED
        badge.badgeGravity     = com.google.android.material.badge.BadgeDrawable.TOP_END
    }

    private fun setupRealtimeFriendRequests() {
        val myId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: return
        lifecycleScope.launch {
            try {
                val channel = SupabaseClient.client.realtime.channel("pedidos_amizade")
                val flow    = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                    table = "friendships"
                }
                channel.subscribe()
                flow.collect { action ->
                    try {
                        val newRequest = action.decodeRecord<Friendship>()
                        if (newRequest.receiver_id == myId && newRequest.status == "pending") {
                            withContext(Dispatchers.Main) {
                                atualizarBadgeAmigos(true)
                                Toast.makeText(this@MainActivity, "Novo pedido de amizade!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) { atualizarBadgeAmigos(true) }
                    }
                }
            } catch (e: Exception) { Log.e("Realtime", "Erro: ${e.message}") }
        }
    }

    // ── Map ready ─────────────────────────────────────────────────────────
    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        try { map.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style)) } catch (e: Exception) {}
        map.uiSettings.isZoomControlsEnabled     = false
        map.uiSettings.isMyLocationButtonEnabled = false

        map.setOnMapClickListener { if (isFabMenuOpen) closeFabMenu() }

        map.setOnMarkerClickListener { marker ->
            if (isFabMenuOpen) { closeFabMenu(); return@setOnMarkerClickListener true }
            val place = markerPlaceMap[marker.id] ?: return@setOnMarkerClickListener false
            PlaceDetailBottomSheet.newInstance(
                name          = place.name,
                rating        = place.rating ?: 0.0,
                address       = place.vicinity ?: "",
                isOpen        = place.opening_hours?.open_now,
                photoUrls     = ArrayList((place.photos ?: emptyList()).take(5).map { getPhotoUrl(it.photo_reference) }),
                lat           = place.geometry.location.lat,
                lng           = place.geometry.location.lng,
                estimatedCost = place.estimatedCostLabel,
                priceTag      = place.priceTag
            ).show(supportFragmentManager, "place_detail")
            true
        }
        requestLocationPermission()
    }

    // ── Location ──────────────────────────────────────────────────────────
    private fun requestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) startLocationUpdates()
        else ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 200)
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
                val url  = "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=${latLng.latitude}&longitude=${latLng.longitude}" +
                    "&current=temperature_2m,weather_code"
                val body = okHttpClient.newCall(okhttp3.Request.Builder().url(url).build()).execute().body?.string() ?: ""
                val current = JSONObject(body).getJSONObject("current")
                val temp = current.getDouble("temperature_2m")
                val code = current.getInt("weather_code")
                withContext(Dispatchers.Main) {
                    tvTemperature.text = "${temp.toInt()}°C"
                    updateWeatherUI(code)
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Weather Error", e)
                withContext(Dispatchers.Main) { tvWeatherCondition.text = "Error" }
            }
        }
    }

    private fun updateWeatherUI(code: Int) {
        val (iconRes, condition) = when (code) {
            0            -> android.R.drawable.ic_menu_day          to "Céu Limpo"
            1, 2, 3      -> android.R.drawable.ic_menu_agenda       to "Nublado"
            45, 48       -> android.R.drawable.ic_menu_view         to "Neblina"
            51,53,55,61,63,65,80,81,82 -> android.R.drawable.ic_menu_directions to "Chuva"
            71,73,75,85,86 -> android.R.drawable.ic_menu_help       to "Neve"
            95,96,99     -> android.R.drawable.ic_dialog_alert       to "Tempestade"
            else         -> android.R.drawable.ic_menu_compass       to "---"
        }
        ivWeatherIcon.setImageResource(iconRes)
        ivWeatherIcon.setColorFilter(Color.parseColor("#4A2080"))
        tvWeatherCondition.text = condition
    }

    // ── Autocomplete ──────────────────────────────────────────────────────
    private fun openAutocomplete() {
        val fields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG, Place.Field.ADDRESS)
        searchLauncher.launch(Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields).build(this))
    }

    // ── Nearby search ─────────────────────────────────────────────────────
    fun searchNearby(categories: List<PlaceCategory>) {
        val center = currentLatLng ?: map.cameraPosition.target
        placeMarkers.forEach { it.remove() }
        placeMarkers.clear(); markerPlaceMap.clear()

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
                    val marker = map.addMarker(MarkerOptions().position(latLng).title(place.name)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET))
                    ) ?: return@forEach
                    placeMarkers.add(marker); markerPlaceMap[marker.id] = place
                }
                if (placeMarkers.isNotEmpty())
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                        LatLng(allPlaces[0].geometry.location.lat, allPlaces[0].geometry.location.lng), 13f))
            }
        }
    }

    private suspend fun fetchNearbyPlaces(center: LatLng, type: String): List<NearbyPlace> =
        withContext(Dispatchers.IO) {
            try {
                val url  = "https://maps.googleapis.com/maps/api/place/nearbysearch/json" +
                    "?location=${center.latitude},${center.longitude}&radius=3000&type=$type&key=$mapsApiKey"
                val body = okHttpClient.newCall(okhttp3.Request.Builder().url(url).build())
                    .execute().body?.string() ?: return@withContext emptyList()
                gson.fromJson(body, NearbySearchResponse::class.java).results ?: emptyList()
            } catch (e: Exception) { Log.e("MainActivity", "Nearby: $type", e); emptyList() }
        }

    // ── Place detail (autocomplete) ───────────────────────────────────────
    private fun fetchAndShowPlaceDetail(placeId: String, name: String, latLng: LatLng) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url  = "https://maps.googleapis.com/maps/api/place/details/json" +
                    "?place_id=$placeId&fields=photos,rating,formatted_address,opening_hours,price_level&key=$mapsApiKey"
                val body = okHttpClient.newCall(okhttp3.Request.Builder().url(url).build())
                    .execute().body?.string() ?: return@launch
                val details   = gson.fromJson(body, PlaceDetailsResponse::class.java).result
                val photoUrls = ArrayList((details?.photos ?: emptyList()).take(5).map { getPhotoUrl(it.photo_reference) })
                val tempPlace = NearbyPlace(
                    place_id    = placeId, name = name,
                    geometry    = PlaceGeometry(PlaceLocation(latLng.latitude, latLng.longitude)),
                    price_level = details?.price_level
                )
                withContext(Dispatchers.Main) {
                    PlaceDetailBottomSheet.newInstance(
                        name          = name,
                        rating        = details?.rating ?: 0.0,
                        address       = details?.formatted_address ?: "",
                        isOpen        = details?.opening_hours?.open_now,
                        photoUrls     = photoUrls,
                        lat           = latLng.latitude,
                        lng           = latLng.longitude,
                        estimatedCost = tempPlace.estimatedCostLabel,
                        priceTag      = tempPlace.priceTag
                    ).show(supportFragmentManager, "place_detail")
                }
            } catch (e: Exception) { Log.e("MainActivity", "Details error", e) }
        }
    }

    // ── Draw route ────────────────────────────────────────────────────────
    fun drawRoute(destination: LatLng, placeName: String) {
        val origin = currentLatLng ?: run {
            Toast.makeText(this, "Could not get your location", Toast.LENGTH_SHORT).show(); return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url  = "https://maps.googleapis.com/maps/api/directions/json" +
                    "?origin=${origin.latitude},${origin.longitude}" +
                    "&destination=${destination.latitude},${destination.longitude}&key=$mapsApiKey"
                val body = okHttpClient.newCall(okhttp3.Request.Builder().url(url).build())
                    .execute().body?.string() ?: return@launch
                val routes = JSONObject(body).getJSONArray("routes")
                if (routes.length() == 0) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "No route found", Toast.LENGTH_SHORT).show()
                    }; return@launch
                }
                val points = routes.getJSONObject(0).getJSONObject("overview_polyline").getString("points")
                withContext(Dispatchers.Main) {
                    routePolyline?.remove()
                    routePolyline = map.addPolyline(PolylineOptions()
                        .addAll(decodePolyline(points)).color(Color.parseColor("#4A2080")).width(12f).geodesic(true))
                    map.animateCamera(CameraUpdateFactory.newLatLngBounds(
                        LatLngBounds.builder().include(origin).include(destination).build(), 120))
                    Toast.makeText(this@MainActivity, "Route to $placeName", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) { Log.e("MainActivity", "Route error", e) }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private fun getPhotoUrl(ref: String) =
        "https://maps.googleapis.com/maps/api/place/photo?photo_reference=$ref&maxwidth=800&key=$mapsApiKey"

    private fun buildStopsJson(stops: List<NearbyPlace>): String {
        val arr = JSONArray()
        stops.forEach { place ->
            arr.put(JSONObject().apply {
                put("place_id", place.place_id); put("name", place.name)
                put("lat", place.geometry.location.lat); put("lng", place.geometry.location.lng)
                put("vicinity", place.vicinity ?: "")
            })
        }
        return arr.toString()
    }

    // ── Restore a saved route on the map ─────────────────────────────────
    private fun restoreRouteFromJson(stopsJson: String) {
        val stops = mutableListOf<NearbyPlace>()
        try {
            val arr = org.json.JSONArray(stopsJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                stops.add(NearbyPlace(
                    place_id = obj.getString("place_id"),
                    name     = obj.getString("name"),
                    geometry = PlaceGeometry(PlaceLocation(
                        obj.getDouble("lat"), obj.getDouble("lng")
                    )),
                    vicinity = obj.optString("vicinity")
                ))
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "restoreRoute parse error", e)
            Toast.makeText(this, "Erro ao abrir a rota", Toast.LENGTH_SHORT).show()
            return
        }

        routePolyline?.remove()
        placeMarkers.forEach { it.remove() }
        placeMarkers.clear(); markerPlaceMap.clear()

        // Place numbered pins
        stops.forEachIndexed { idx, place ->
            val latLng = LatLng(place.geometry.location.lat, place.geometry.location.lng)
            val marker = map.addMarker(MarkerOptions()
                .position(latLng).title("${idx + 1}. ${place.name}")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET))
            ) ?: return@forEachIndexed
            placeMarkers.add(marker); markerPlaceMap[marker.id] = place
        }

        if (placeMarkers.isNotEmpty()) {
            val bounds = placeMarkers.fold(LatLngBounds.builder()) { b, m -> b.include(m.position) }.build()
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120))
        }

        lastRouteStops = stops
        lastRouteId    = System.currentTimeMillis().toString()
        fabOffline.visibility = View.VISIBLE

        // Draw purple polyline through all stops via Directions API
        if (stops.size >= 2) {
            drawRestoredRoute(stops)
        }

        Toast.makeText(this, "Rota carregada no mapa!", Toast.LENGTH_SHORT).show()
    }

    private fun drawRestoredRoute(stops: List<NearbyPlace>) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val origin      = stops.first()
                val destination = stops.last()
                val waypoints   = stops.drop(1).dropLast(1)

                val waypointsParam = if (waypoints.isNotEmpty())
                    "&waypoints=" + waypoints.joinToString("|") {
                        "${it.geometry.location.lat},${it.geometry.location.lng}"
                    } else ""

                val url = "https://maps.googleapis.com/maps/api/directions/json" +
                    "?origin=${origin.geometry.location.lat},${origin.geometry.location.lng}" +
                    "&destination=${destination.geometry.location.lat},${destination.geometry.location.lng}" +
                    waypointsParam +
                    "&key=$mapsApiKey"

                val body = okHttpClient.newCall(
                    okhttp3.Request.Builder().url(url).build()
                ).execute().body?.string() ?: return@launch

                val routes = org.json.JSONObject(body).getJSONArray("routes")
                if (routes.length() == 0) return@launch

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
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "drawRestoredRoute error", e)
            }
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 200 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startLocationUpdates()
    }



    override fun onDestroy() {
        super.onDestroy()
        if (::locationCallback.isInitialized) fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}
