package com.example.ownmyway

import android.Manifest
import android.content.Context
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.Window
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import java.net.URLEncoder
import java.util.Calendar

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    companion object {
        const val EXTRA_RECOMMENDED_MAP_QUERY = "extra_recommended_map_query"
        const val EXTRA_RECOMMENDED_MAP_TITLE = "extra_recommended_map_title"
        private const val KEY_TRAVEL_PROMPT_LAST_SHOWN = "last_shown"
        private var greetingShownThisSession = false
    }

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
    private var currentWeatherCode: Int = -1
    private var currentCondition: String = ""

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

    // ── Busca integrada ───────────────────────────────────────────────────
    private lateinit var searchInput: EditText
    private lateinit var searchSuggestionsPanel: LinearLayout
    private lateinit var rowDestinationSuggestion: View
    private lateinit var predictionsContainer: LinearLayout
    private lateinit var cardGreetingOverlay: View
    private lateinit var tvMainGreeting: TextView
    private var autocompleteSearchJob: Job? = null
    private var pendingRecommendedMapQuery: String? = null
    private var pendingRecommendedMapTitle: String? = null

    // ── Sugestão de destinos ──────────────────────────────────────────────
    private val travelPromptHandler = Handler(Looper.getMainLooper())
    private val mainGreetingHandler = Handler(Looper.getMainLooper())
    private var travelPromptRunnable: Runnable? = null
    private var travelPromptDialog: Dialog? = null
    private val travelPromptDelayMs = 90_000L       // aparece após 1min30 sem rota
    private val travelPromptCooldownMs = 24 * 60 * 60 * 1000L // reaparece só depois de 24h

    private val travelPromptPrefs by lazy {
        getSharedPreferences("travel_prompt_prefs", MODE_PRIVATE)
    }

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
                        checkWeatherRouteInteraction(hobbies)
                        cancelTravelPromptWatcher()
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

    private fun checkWeatherRouteInteraction(hobbies: List<String>) {
        if (currentWeatherCode == -1) return

        val isAdventure = hobbies.any { 
            val h = it.lowercase()
            h.contains("hiking") || h.contains("trekking") || 
            h.contains("running") || h.contains("cycling") ||
            h.contains("exploring") || h.contains("swimming")
        }

        val isRainy = currentWeatherCode in listOf(51, 53, 55, 61, 63, 65, 80, 81, 82, 95, 96, 99)
        val isSunny = currentWeatherCode == 0 || currentWeatherCode == 1

        if (isAdventure && isRainy) {
            showWeatherAlert("Cuidado! Previsão de chuva, isso pode deixar aventuras mais perigosas.")
        } else if (isSunny) {
            showWeatherAlert("Dia ensolarado! Ótimo tempo para aproveitar sua viagem!")
        }
    }

    private fun showWeatherAlert(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Alerta de Viagem")
            .setMessage(message)
            .setPositiveButton("Entendido", null)
            .show()
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
        searchInput        = findViewById(R.id.tvSearch)
        searchSuggestionsPanel = findViewById(R.id.searchSuggestionsPanel)
        rowDestinationSuggestion = findViewById(R.id.rowDestinationSuggestion)
        predictionsContainer = findViewById(R.id.predictionsContainer)
        cardGreetingOverlay = findViewById(R.id.cardGreetingOverlay)
        tvMainGreeting = findViewById(R.id.tvMainGreeting)

        setupBottomNavigation()
        MealNotificationReceiver.createChannel(this)

        if (!Places.isInitialized()) Places.initialize(applicationContext, mapsApiKey)
        placesClient = Places.createClient(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        (supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment).getMapAsync(this)

        // Search bar integrada
        setupIntegratedSearchBar()
        findViewById<ImageButton>(R.id.btnFilter).setOnClickListener {
            hideSearchSuggestions()
            if (isFabMenuOpen) closeFabMenu()
            FilterBottomSheet().show(supportFragmentManager, "filter")
        }

        // Speed dial
        fabMenu.setOnClickListener { toggleFabMenu() }
        fabScrim.setOnClickListener {
            hideSearchSuggestions()
            closeFabMenu()
        }

        // Handle back gesture: close speed dial first if open
        onBackPressedDispatcher.addCallback(this) {
            when {
                searchSuggestionsPanel.visibility == View.VISIBLE -> hideSearchSuggestions()
                isFabMenuOpen -> closeFabMenu()
                else -> isEnabled = false  // let system handle it
            }
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
        showMainGreetingIfNeeded()

        findViewById<ImageView>(R.id.ivUserProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        setupTravelPromptWatcher()
        captureRecommendedDestinationIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureRecommendedDestinationIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        setupTravelPromptWatcher()
    }

    override fun onPause() {
        super.onPause()
        hideSearchSuggestions(clearFocus = false)
        cancelTravelPromptWatcher()
    }

    // ── Busca integrada + sugestão de destino ───────────────────────────────
    private fun setupIntegratedSearchBar() {
        rowDestinationSuggestion.setOnClickListener {
            hideKeyboard()
            hideSearchSuggestions()
            startActivity(Intent(this, CountryRecommendationsActivity::class.java))
        }

        searchInput.setOnClickListener {
            showDestinationSuggestionIfEmpty()
        }

        searchInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) showDestinationSuggestionIfEmpty()
            else hideSearchSuggestions(clearFocus = false)
        }

        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = searchInput.text.toString().trim()
                if (query.isNotBlank()) {
                    hideKeyboard()
                    hideSearchSuggestions(clearFocus = false)
                    searchTextOnIntegratedMap(query, query)
                }
                true
            } else {
                false
            }
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun afterTextChanged(s: Editable?) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!searchInput.hasFocus()) return
                val query = s?.toString()?.trim().orEmpty()

                if (query.isBlank()) {
                    autocompleteSearchJob?.cancel()
                    predictionsContainer.removeAllViews()
                    showDestinationSuggestionIfEmpty()
                } else {
                    rowDestinationSuggestion.visibility = View.GONE
                    searchSuggestionsPanel.visibility = View.VISIBLE
                    fetchSearchPredictions(query)
                }
            }
        })
    }

    private fun showDestinationSuggestionIfEmpty() {
        if (searchInput.text.toString().trim().isNotEmpty()) return
        predictionsContainer.removeAllViews()
        rowDestinationSuggestion.visibility = View.VISIBLE
        searchSuggestionsPanel.visibility = View.VISIBLE
    }

    private fun hideSearchSuggestions(clearFocus: Boolean = true) {
        autocompleteSearchJob?.cancel()
        autocompleteSearchJob = null
        predictionsContainer.removeAllViews()
        searchSuggestionsPanel.visibility = View.GONE
        if (clearFocus) searchInput.clearFocus()
    }

    private fun fetchSearchPredictions(query: String) {
        autocompleteSearchJob?.cancel()
        autocompleteSearchJob = lifecycleScope.launch {
            delay(250)
            val request = FindAutocompletePredictionsRequest.builder()
                .setQuery(query)
                .build()

            placesClient.findAutocompletePredictions(request)
                .addOnSuccessListener { response ->
                    if (searchInput.text.toString().trim() != query) return@addOnSuccessListener
                    renderPlacePredictions(response.autocompletePredictions.take(5))
                }
                .addOnFailureListener {
                    predictionsContainer.removeAllViews()
                }
        }
    }

    private fun renderPlacePredictions(predictions: List<AutocompletePrediction>) {
        predictionsContainer.removeAllViews()
        rowDestinationSuggestion.visibility = View.GONE

        predictions.forEach { prediction ->
            predictionsContainer.addView(createPredictionRow(prediction))
        }
    }

    private fun createPredictionRow(prediction: AutocompletePrediction): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            minimumHeight = dp(56)
            setPadding(dp(16), 0, dp(16), 0)
            isClickable = true
            isFocusable = true
            foreground = selectableItemBackground()
        }

        val icon = TextView(this).apply {
            text = "📍"
            textSize = 20f
        }
        row.addView(icon, LinearLayout.LayoutParams(dp(32), LinearLayout.LayoutParams.WRAP_CONTENT))

        val textBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val primary = TextView(this).apply {
            text = prediction.getPrimaryText(null).toString()
            setTextColor(Color.parseColor("#2D1060"))
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            maxLines = 1
        }

        val secondary = TextView(this).apply {
            text = prediction.getSecondaryText(null).toString()
            setTextColor(Color.parseColor("#7A6B8F"))
            textSize = 12f
            maxLines = 1
        }

        textBox.addView(primary)
        textBox.addView(secondary)
        row.addView(textBox, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        row.setOnClickListener {
            openPredictionOnIntegratedMap(prediction.placeId)
        }

        return row
    }

    private fun openPredictionOnIntegratedMap(placeId: String) {
        hideKeyboard()
        hideSearchSuggestions()

        val fields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG, Place.Field.ADDRESS)
        val request = FetchPlaceRequest.newInstance(placeId, fields)

        placesClient.fetchPlace(request)
            .addOnSuccessListener { response ->
                val place = response.place
                val latLng = place.latLng ?: return@addOnSuccessListener
                searchInput.setText(place.name ?: "")
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                fetchAndShowPlaceDetail(place.id ?: placeId, place.name ?: "", latLng)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Não foi possível abrir esse lugar no mapa.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun captureRecommendedDestinationIntent(intent: Intent?) {
        val query = intent?.getStringExtra(EXTRA_RECOMMENDED_MAP_QUERY)?.takeIf { it.isNotBlank() }
            ?: return

        pendingRecommendedMapQuery = query
        pendingRecommendedMapTitle = intent.getStringExtra(EXTRA_RECOMMENDED_MAP_TITLE) ?: query
        intent.removeExtra(EXTRA_RECOMMENDED_MAP_QUERY)
        intent.removeExtra(EXTRA_RECOMMENDED_MAP_TITLE)
        handlePendingRecommendedDestinationSearch()
    }

    private fun handlePendingRecommendedDestinationSearch() {
        if (!::map.isInitialized) return
        val query = pendingRecommendedMapQuery ?: return
        val title = pendingRecommendedMapTitle ?: query
        pendingRecommendedMapQuery = null
        pendingRecommendedMapTitle = null
        searchTextOnIntegratedMap(query, title)
    }

    private fun searchTextOnIntegratedMap(query: String, title: String) {
        if (!::map.isInitialized) {
            pendingRecommendedMapQuery = query
            pendingRecommendedMapTitle = title
            return
        }

        hideKeyboard()
        hideSearchSuggestions()
        cancelTravelPromptWatcher()
        Toast.makeText(this, "Abrindo $title no mapa...", Toast.LENGTH_SHORT).show()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val url = "https://maps.googleapis.com/maps/api/place/textsearch/json" +
                    "?query=$encodedQuery&key=$mapsApiKey"

                val body = okHttpClient.newCall(
                    okhttp3.Request.Builder().url(url).build()
                ).execute().body?.string() ?: return@launch

                val response = gson.fromJson(body, NearbySearchResponse::class.java)
                val places = response?.results.orEmpty().take(10)

                withContext(Dispatchers.Main) {
                    routePolyline?.remove()
                    routePolyline = null
                    placeMarkers.forEach { it.remove() }
                    placeMarkers.clear()
                    markerPlaceMap.clear()
                    lastRouteStops = emptyList()
                    lastRouteId = ""
                    lastRouteTotalCost = 0
                    fabOffline.visibility = View.GONE
                    tvCostCounter.visibility = View.GONE

                    if (places.isEmpty()) {
                        Toast.makeText(this@MainActivity, "Não encontrei resultados para $title.", Toast.LENGTH_SHORT).show()
                        setupTravelPromptWatcher()
                        return@withContext
                    }

                    places.forEach { place ->
                        val latLng = LatLng(place.geometry.location.lat, place.geometry.location.lng)
                        val marker = map.addMarker(
                            MarkerOptions()
                                .position(latLng)
                                .title(place.name)
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET))
                        ) ?: return@forEach
                        placeMarkers.add(marker)
                        markerPlaceMap[marker.id] = place
                    }

                    val bounds = placeMarkers.fold(LatLngBounds.builder()) { builder, marker ->
                        builder.include(marker.position)
                    }.build()
                    map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120))
                    Toast.makeText(this@MainActivity, "$title aberto no mapa integrado", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Integrated map search error", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Erro ao abrir $title no mapa.", Toast.LENGTH_SHORT).show()
                    setupTravelPromptWatcher()
                }
            }
        }
    }

    private fun hideKeyboard() {
        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(searchInput.windowToken, 0)
    }

    private fun selectableItemBackground(): android.graphics.drawable.Drawable? {
        val attrs = intArrayOf(android.R.attr.selectableItemBackground)
        val typedArray = obtainStyledAttributes(attrs)
        val drawable = typedArray.getDrawable(0)
        typedArray.recycle()
        return drawable
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    // ── Pop-up inteligente de recomendações ────────────────────────────────
    private fun setupTravelPromptWatcher() {
        cancelTravelPromptWatcher()

        if (!shouldScheduleTravelPrompt()) return

        travelPromptRunnable = Runnable {
            when {
                !shouldScheduleTravelPrompt() -> Unit
                isFabMenuOpen -> setupTravelPromptWatcher()
                else -> showTravelRecommendationPrompt()
            }
        }
        travelPromptHandler.postDelayed(travelPromptRunnable!!, travelPromptDelayMs)
        Log.d("TravelPrompt", "scheduled in ${travelPromptDelayMs}ms for key=${currentTravelPromptKey()}")
    }

    private fun currentTravelPromptKey(): String {
        // O controle do pop-up precisa ser por usuário, não por instalação.
        // Assim, se trocar de conta, uma conta nova não fica presa no cooldown da conta anterior.
        val userId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: "guest"
        return "${KEY_TRAVEL_PROMPT_LAST_SHOWN}_$userId"
    }

    private fun shouldScheduleTravelPrompt(): Boolean {
        if (isFinishing || isDestroyed) return false
        if (lastRouteStops.isNotEmpty() || routePolyline != null) return false
        if (travelPromptDialog?.isShowing == true) return false

        val lastShown = travelPromptPrefs.getLong(currentTravelPromptKey(), 0L)
        val canShow = System.currentTimeMillis() - lastShown >= travelPromptCooldownMs

        Log.d(
            "TravelPrompt",
            "canShow=$canShow lastShown=$lastShown key=${currentTravelPromptKey()} routeStops=${lastRouteStops.size} hasPolyline=${routePolyline != null}"
        )

        return canShow
    }

    private fun cancelTravelPromptWatcher() {
        travelPromptRunnable?.let { travelPromptHandler.removeCallbacks(it) }
        travelPromptRunnable = null
    }

    private fun markTravelPromptShown() {
        travelPromptPrefs.edit()
            .putLong(currentTravelPromptKey(), System.currentTimeMillis())
            // Remove a chave antiga global, usada na primeira versão do recurso.
            // Isso evita que restauração/reinstalação do app carregue cooldown velho para outra conta.
            .remove(KEY_TRAVEL_PROMPT_LAST_SHOWN)
            .apply()
    }

    private fun showTravelRecommendationPrompt() {
        if (travelPromptDialog?.isShowing == true) return

        markTravelPromptShown()

        val dialog = Dialog(this)
        travelPromptDialog = dialog
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_travel_recommendation_prompt)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCanceledOnTouchOutside(true)

        dialog.findViewById<Button>(R.id.btnTravelPromptPrimary).setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, CountryRecommendationsActivity::class.java))
        }

        dialog.findViewById<TextView>(R.id.btnTravelPromptSecondary).setOnClickListener {
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            travelPromptDialog = null
        }

        dialog.show()
        dialog.window?.apply {
            setLayout((resources.displayMetrics.widthPixels * 0.88f).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.CENTER)
        }
    }

    // ── Speed dial ────────────────────────────────────────────────────────
    private fun toggleFabMenu() { if (isFabMenuOpen) closeFabMenu() else openFabMenu() }

    private fun openFabMenu() {
        hideSearchSuggestions()
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
        AppBottomNavigation.setup(
            activity = this,
            selectedItemId = R.id.nav_home,
            onHomeSelected = {
                currentLatLng?.let { map.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 15f)) }
                    ?: Toast.makeText(this, "Localização ainda não disponível", Toast.LENGTH_SHORT).show()
            },
            onFriendsSelected = {
                atualizarBadgeAmigos(false)
            }
        )
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

    private fun showMainGreetingIfNeeded() {
        if (greetingShownThisSession) return
        greetingShownThisSession = true

        lifecycleScope.launch {
            val firstName = getGreetingUserName()
            tvMainGreeting.text = buildGreetingMessage(firstName)
            animateMainGreeting()
        }
    }

    private suspend fun getGreetingUserName(): String {
        val profile = FriendRepository.getMyProfile()

        val rawName = profile?.full_name
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: profile?.username
                ?.trim()
                ?.removePrefix("@")
                ?.takeIf { it.isNotBlank() }

        return rawName
            ?.split(Regex("\\s+"))
            ?.firstOrNull()
            ?.replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase() else char.toString()
            }
            ?: "Viajante"
    }

    private fun buildGreetingMessage(firstName: String): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> "Bom dia, $firstName! Pronto pra explorar?"
            in 12..17 -> "Boa tarde, $firstName! Bora descobrir um novo lugar?"
            else -> "Boa noite, $firstName! Planejando a próxima aventura?"
        }
    }

    private fun animateMainGreeting() {
        cardGreetingOverlay.apply {
            mainGreetingHandler.removeCallbacksAndMessages(null)
            alpha = 0f
            translationY = -dp(18).toFloat()
            scaleX = 0.96f
            scaleY = 0.96f
            visibility = View.VISIBLE
            animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(480)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        mainGreetingHandler.postDelayed({
            cardGreetingOverlay.animate()
                .alpha(0f)
                .translationY(-dp(14).toFloat())
                .scaleX(0.98f)
                .scaleY(0.98f)
                .setDuration(420)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    cardGreetingOverlay.visibility = View.GONE
                }
                .start()
        }, 4_000L)
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

        map.setOnMapClickListener {
            hideSearchSuggestions()
            if (isFabMenuOpen) closeFabMenu()
        }

        map.setOnMarkerClickListener { marker ->
            hideSearchSuggestions()
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
        handlePendingRecommendedDestinationSearch()
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
                    currentWeatherCode = code
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
        currentCondition = condition
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
                    cancelTravelPromptWatcher()
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
        cancelTravelPromptWatcher()

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
        cancelTravelPromptWatcher()
        mainGreetingHandler.removeCallbacksAndMessages(null)
        autocompleteSearchJob?.cancel()
        travelPromptDialog?.dismiss()
        travelPromptDialog = null
        super.onDestroy()
        if (::locationCallback.isInitialized) fusedLocationClient.removeLocationUpdates(locationCallback)
    }

}