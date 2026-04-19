package com.example.ownmyway

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
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
import android.widget.ImageView

// SUPABASE V3
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime

import kotlinx.coroutines.*
import okhttp3.OkHttpClient

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var locationInitialized = false
    private var currentLatLng: LatLng? = null

    private lateinit var placesClient: PlacesClient
    private val placeMarkers = mutableListOf<Marker>()
    private val markerPlaceMap = mutableMapOf<String, NearbyPlace>()
    private var routePolyline: Polyline? = null

    private val okHttpClient = OkHttpClient()
    private val gson = Gson()

    private val mapsApiKey: String by lazy {
        packageManager
            .getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            .metaData.getString("com.google.android.geo.API_KEY") ?: ""
    }

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Logout
        findViewById<Button>(R.id.btnLogout).setOnClickListener { performLogout() }

        // --- MUDANÇA AQUI: Agora abre a tela de listagem de viajantes ---
        findViewById<Button>(R.id.btnAdicionarAmigo).setOnClickListener {
            val intent = Intent(this, FriendRequestActivity::class.java)
            startActivity(intent)
        }

        if (!Places.isInitialized()) Places.initialize(applicationContext, mapsApiKey)
        placesClient = Places.createClient(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        (supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment)
            .getMapAsync(this)

        setupUIListeners()
        setupFriendRequestObserver()
    }

    private fun setupUIListeners() {
        findViewById<TextView>(R.id.tvSearch).setOnClickListener {
            openAutocomplete()
        }

        findViewById<ImageButton>(R.id.btnFilter).setOnClickListener {
            FilterBottomSheet().show(supportFragmentManager, "filter")
        }

        findViewById<ImageView>(R.id.imgProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener {
            startActivity(Intent(this, CreateRouteActivity::class.java))
        }

        findViewById<FloatingActionButton>(R.id.fabCamera).setOnClickListener {
            startActivity(Intent(this, CameraActivity::class.java))
        }

        supportFragmentManager.setFragmentResultListener("filter_result", this) { _, bundle ->
            val names = bundle.getStringArrayList("categories") ?: return@setFragmentResultListener
            val categories = names.mapNotNull {
                runCatching { PlaceCategory.valueOf(it) }.getOrNull()
            }
            searchNearby(categories)
        }

        supportFragmentManager.setFragmentResultListener("route_request", this) { _, bundle ->
            drawRoute(
                LatLng(bundle.getDouble("lat"), bundle.getDouble("lng")),
                bundle.getString("name", "")
            )
        }
    }

    // --- REALTIME NOTIFICATIONS ---

    private fun setupFriendRequestObserver() {
        val myId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: return

        lifecycleScope.launch {
            try {
                val channel = SupabaseClient.client.realtime.channel("friendships_channel")
                val flow = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                    table = "friendships"
                }
                channel.subscribe()

                flow.collect { action ->
                    val newRequest = action.decodeRecord<Friendship>()
                    // Só notifica se eu for o destinatário do pedido
                    if (newRequest.receiver_id == myId) {
                        showFriendNotification(newRequest)
                    }
                }
            } catch (e: Exception) {
                Log.e("Realtime", "Observer Error: ${e.message}")
            }
        }
    }

    private fun showFriendNotification(request: Friendship) {
        val channelId = "friend_requests_channel"
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Pedidos de Amizade", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        // Abre a FriendRequestActivity enviando o ID de quem mandou o pedido
        val intent = Intent(this, FriendRequestActivity::class.java).apply {
            putExtra("SENDER_ID", request.sender_id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            request.sender_id.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Use o ícone do seu app aqui
            .setContentTitle("Novo Viajante!")
            .setContentText("Alguém quer viajar com você. Toque para ver.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(request.sender_id.hashCode(), notification)
    }

    private fun performLogout() {
        lifecycleScope.launch {
            try {
                SupabaseClient.client.auth.signOut()
                val intent = Intent(this@MainActivity, SplashActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Erro ao sair", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- MAP LÓGICA ---

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        try { map.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style)) } catch (e: Exception) {}

        map.uiSettings.isZoomControlsEnabled = false
        map.uiSettings.isMyLocationButtonEnabled = false

        map.setOnMarkerClickListener { marker ->
            val place = markerPlaceMap[marker.id] ?: return@setOnMarkerClickListener false
            fetchAndShowPlaceDetail(place.place_id ?: "", place.name, LatLng(place.geometry.location.lat, place.geometry.location.lng))
            true
        }
        requestLocationPermission()
    }

    private fun requestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) startLocationUpdates()
        else ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 200)
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return
        map.isMyLocationEnabled = true
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build()
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                currentLatLng = LatLng(loc.latitude, loc.longitude)
                if (!locationInitialized) {
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng!!, 15f))
                    locationInitialized = true
                }
            }
        }
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    // --- GOOGLE PLACES MÉTODOS ---

    private fun openAutocomplete() {
        val fields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG, Place.Field.ADDRESS)
        searchLauncher.launch(Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields).build(this))
    }

    private fun searchNearby(categories: List<PlaceCategory>) {
        val center = currentLatLng ?: map.cameraPosition.target
        placeMarkers.forEach { it.remove() }
        placeMarkers.clear(); markerPlaceMap.clear()

        lifecycleScope.launch(Dispatchers.IO) {
            val allPlaces = mutableListOf<NearbyPlace>()
            categories.map { async { fetchNearbyPlaces(center, it.placeType) } }
                .forEach { allPlaces.addAll(it.await()) }

            withContext(Dispatchers.Main) {
                allPlaces.forEach { place ->
                    val latLng = LatLng(place.geometry.location.lat, place.geometry.location.lng)
                    val marker = map.addMarker(MarkerOptions()
                        .position(latLng).title(place.name)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET))
                    ) ?: return@forEach
                    placeMarkers.add(marker); markerPlaceMap[marker.id] = place
                }
            }
        }
    }

    private suspend fun fetchNearbyPlaces(center: LatLng, type: String): List<NearbyPlace> =
        withContext(Dispatchers.IO) {
            try {
                val url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json" +
                        "?location=${center.latitude},${center.longitude}&radius=3000&type=$type&key=$mapsApiKey"
                val body = okHttpClient.newCall(okhttp3.Request.Builder().url(url).build())
                    .execute().body?.string() ?: return@withContext emptyList()
                gson.fromJson(body, NearbySearchResponse::class.java).results ?: emptyList()
            } catch (e: Exception) { emptyList() }
        }

    private fun fetchAndShowPlaceDetail(placeId: String, name: String, latLng: LatLng) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = "https://maps.googleapis.com/maps/api/place/details/json" +
                        "?place_id=$placeId&fields=photos,rating,formatted_address,opening_hours&key=$mapsApiKey"
                val body = okHttpClient.newCall(okhttp3.Request.Builder().url(url).build())
                    .execute().body?.string() ?: return@launch
                val details = gson.fromJson(body, PlaceDetailsResponse::class.java).result
                val photoUrls = ArrayList((details?.photos ?: emptyList()).take(5)
                    .map { getPhotoUrl(it.photo_reference) })
                withContext(Dispatchers.Main) {
                    PlaceDetailBottomSheet.newInstance(
                        name = name,
                        rating = details?.rating ?: 0.0,
                        address = details?.formatted_address ?: "",
                        isOpen = details?.opening_hours?.open_now,
                        photoUrls = photoUrls,
                        lat = latLng.latitude,
                        lng = latLng.longitude
                    ).show(supportFragmentManager, "place_detail")
                }
            } catch (e: Exception) { Log.e("MainActivity", "Details error", e) }
        }
    }

    private fun drawRoute(destination: LatLng, placeName: String) {
        val origin = currentLatLng ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = "https://maps.googleapis.com/maps/api/directions/json" +
                        "?origin=${origin.latitude},${origin.longitude}" +
                        "&destination=${destination.latitude},${destination.longitude}&key=$mapsApiKey"
                val body = okHttpClient.newCall(okhttp3.Request.Builder().url(url).build())
                    .execute().body?.string() ?: return@launch
                val routes = org.json.JSONObject(body).getJSONArray("routes")
                if (routes.length() > 0) {
                    val points = routes.getJSONObject(0).getJSONObject("overview_polyline").getString("points")
                    withContext(Dispatchers.Main) {
                        routePolyline?.remove()
                        routePolyline = map.addPolyline(PolylineOptions()
                            .addAll(decodePolyline(points))
                            .color(Color.parseColor("#4A2080")).width(12f))
                        map.animateCamera(CameraUpdateFactory.newLatLngBounds(
                            LatLngBounds.builder().include(origin).include(destination).build(), 120))
                    }
                }
            } catch (e: Exception) { Log.e("MainActivity", "Route error", e) }
        }
    }

    private fun getPhotoUrl(ref: String) =
        "https://maps.googleapis.com/maps/api/place/photo?photo_reference=$ref&maxwidth=800&key=$mapsApiKey"

    private fun decodePolyline(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>(); var index = 0; var lat = 0; var lng = 0
        while (index < encoded.length) {
            var b: Int; var shift = 0; var result = 0
            do { b = encoded[index++].code - 63; result = result or (b and 0x1f shl shift); shift += 5 } while (b >= 0x20)
            lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1
            shift = 0; result = 0
            do { b = encoded[index++].code - 63; result = result or (b and 0x1f shl shift); shift += 5 } while (b >= 0x20)
            lng += if (result and 1 != 0) (result shr 1).inv() else result shr 1
            poly.add(LatLng(lat.toDouble() / 1E5, lng.toDouble() / 1E5))
        }
        return poly
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::locationCallback.isInitialized) fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}