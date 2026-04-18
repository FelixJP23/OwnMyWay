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
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
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

        // 1. Inicialização do Logout
        val btnLogout = findViewById<Button>(R.id.btnLogout)
        btnLogout.setOnClickListener {
            performLogout()
        }

        // 2. Inicialização do Google Places e Location
        if (!Places.isInitialized()) Places.initialize(applicationContext, mapsApiKey)
        placesClient = Places.createClient(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // 3. Configuração do Mapa
        (supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment)
            .getMapAsync(this)

        // 4. Listeners de UI
        findViewById<TextView>(R.id.tvSearch).setOnClickListener { openAutocomplete() }
        findViewById<ImageButton>(R.id.btnFilter).setOnClickListener {
            FilterBottomSheet().show(supportFragmentManager, "filter")
        }
        findViewById<FloatingActionButton>(R.id.fabCamera).setOnClickListener {
            startActivity(Intent(this, CameraActivity::class.java))
        }
        findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener { /* TODO */ }

        // 5. Fragment Result Listeners
        supportFragmentManager.setFragmentResultListener("filter_result", this) { _, bundle ->
            val names = bundle.getStringArrayList("categories") ?: return@setFragmentResultListener
            val categories = names.mapNotNull { runCatching { PlaceCategory.valueOf(it) }.getOrNull() }
            searchNearby(categories)
        }

        supportFragmentManager.setFragmentResultListener("route_request", this) { _, bundle ->
            drawRoute(LatLng(bundle.getDouble("lat"), bundle.getDouble("lng")), bundle.getString("name", ""))
        }
    }

    private fun performLogout() {
        lifecycleScope.launch {
            try {
                // Remove a sessão do Supabase no dispositivo
                SupabaseClient.client.auth.signOut()

                // Volta para a SplashActivity limpando a pilha de telas
                val intent = Intent(this@MainActivity, SplashActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Erro ao sair", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        try { map.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style)) } catch (e: Exception) {}
        map.uiSettings.isZoomControlsEnabled = false
        map.uiSettings.isMyLocationButtonEnabled = false

        map.setOnMarkerClickListener { marker ->
            val place = markerPlaceMap[marker.id] ?: return@setOnMarkerClickListener false
            val photoUrls = ArrayList((place.photos ?: emptyList()).take(5)
                .map { getPhotoUrl(it.photo_reference) })
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

    // --- MÉTODOS DE LOCALIZAÇÃO E GOOGLE MAPS (Mantidos conforme seu original) ---

    private fun requestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) startLocationUpdates()
        else ActivityCompat.requestPermissions(this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 200)
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
                }
            }
        }
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    private fun openAutocomplete() {
        val fields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG, Place.Field.ADDRESS)
        searchLauncher.launch(Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields).build(this))
    }

    private fun searchNearby(categories: List<PlaceCategory>) {
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
                    val marker = map.addMarker(MarkerOptions()
                        .position(latLng).title(place.name)
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
                val url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json" +
                        "?location=${center.latitude},${center.longitude}&radius=3000&type=$type&key=$mapsApiKey"
                val body = okHttpClient.newCall(okhttp3.Request.Builder().url(url).build())
                    .execute().body?.string() ?: return@withContext emptyList()
                gson.fromJson(body, NearbySearchResponse::class.java).results ?: emptyList()
            } catch (e: Exception) { Log.e("MainActivity", "Nearby: $type", e); emptyList() }
        }

    private fun fetchAndShowPlaceDetail(placeId: String, name: String, latLng: LatLng) {
        CoroutineScope(Dispatchers.IO).launch {
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
                        name      = name,
                        rating    = details?.rating ?: 0.0,
                        address   = details?.formatted_address ?: "",
                        isOpen    = details?.opening_hours?.open_now,
                        photoUrls = photoUrls,
                        lat       = latLng.latitude,
                        lng       = latLng.longitude
                    ).show(supportFragmentManager, "place_detail")
                }
            } catch (e: Exception) { Log.e("MainActivity", "Details error", e) }
        }
    }

    private fun drawRoute(destination: LatLng, placeName: String) {
        val origin = currentLatLng ?: run {
            Toast.makeText(this, "Could not get your location", Toast.LENGTH_SHORT).show(); return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = "https://maps.googleapis.com/maps/api/directions/json" +
                        "?origin=${origin.latitude},${origin.longitude}" +
                        "&destination=${destination.latitude},${destination.longitude}&key=$mapsApiKey"
                val body = okHttpClient.newCall(okhttp3.Request.Builder().url(url).build())
                    .execute().body?.string() ?: return@launch
                val routes = org.json.JSONObject(body).getJSONArray("routes")
                if (routes.length() == 0) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "No route found", Toast.LENGTH_SHORT).show()
                    }; return@launch
                }
                val points = routes.getJSONObject(0)
                    .getJSONObject("overview_polyline").getString("points")
                withContext(Dispatchers.Main) {
                    routePolyline?.remove()
                    routePolyline = map.addPolyline(PolylineOptions()
                        .addAll(decodePolyline(points))
                        .color(Color.parseColor("#4A2080")).width(12f).geodesic(true))
                    val bounds = LatLngBounds.builder().include(origin).include(destination).build()
                    map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120))
                    Toast.makeText(this@MainActivity, "Route to $placeName", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) { Log.e("MainActivity", "Route error", e) }
        }
    }

    private fun getPhotoUrl(ref: String) =
        "https://maps.googleapis.com/maps/api/place/photo?photo_reference=$ref&maxwidth=800&key=$mapsApiKey"

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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 200 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startLocationUpdates()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::locationCallback.isInitialized) fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}