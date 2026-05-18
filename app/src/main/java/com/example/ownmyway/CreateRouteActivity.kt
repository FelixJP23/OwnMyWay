package com.example.ownmyway

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.gson.Gson
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class CreateRouteActivity : AppCompatActivity() {

    private lateinit var chipGroupSpecific: ChipGroup
    private lateinit var etSpecificPlace: EditText
    private lateinit var btnAddPlace: ImageButton
    private lateinit var tvSearchStatus: TextView
    private lateinit var rgBreakfast: RadioGroup
    private lateinit var rgLunch: RadioGroup
    private lateinit var rgDinner: RadioGroup
    private lateinit var chipGroupHobbies: ChipGroup
    private lateinit var btnCreateRoute: Button
    private lateinit var progressBar: ProgressBar

    // Stores resolved NearbyPlace objects from Maps API
    private val resolvedPlaces = mutableListOf<NearbyPlace>()

    private var travelRhythm  = "moderate"
    private var spendingLevel = "medium"
    private val savedHobbies  = mutableListOf<String>()

    private val supabaseUrl by lazy { BuildConfig.SUPABASE_URL }
    private val supabaseKey by lazy { BuildConfig.SUPABASE_KEY }
    private val okHttpClient = OkHttpClient()
    private val gson         = Gson()

    private val chipBgColors by lazy {
        ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(
                ContextCompat.getColor(this@CreateRouteActivity, R.color.omw_purple_main),
                ContextCompat.getColor(this@CreateRouteActivity, R.color.omw_input_background_alt)
            )
        )
    }

    private val chipTextColors by lazy {
        ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(
                ContextCompat.getColor(this@CreateRouteActivity, R.color.omw_on_primary),
                ContextCompat.getColor(this@CreateRouteActivity, R.color.omw_purple_main)
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_route)

        bindViews()
        setupSpecificPlaces()
        buildHobbyChips()
        fetchUserData()

        btnCreateRoute.setOnClickListener { onCreateRoute() }
        findViewById<ImageButton>(R.id.btnBackRoute).setOnClickListener { finish() }
    }

    private fun bindViews() {
        chipGroupSpecific = findViewById(R.id.chipGroupSpecific)
        etSpecificPlace   = findViewById(R.id.etSpecificPlace)
        btnAddPlace       = findViewById(R.id.btnAddPlace)
        tvSearchStatus    = findViewById(R.id.tvSearchStatus)
        rgBreakfast       = findViewById(R.id.rgBreakfast)
        rgLunch           = findViewById(R.id.rgLunch)
        rgDinner          = findViewById(R.id.rgDinner)
        chipGroupHobbies  = findViewById(R.id.chipGroupHobbies)
        btnCreateRoute    = findViewById(R.id.btnCreateRoute)
        progressBar       = findViewById(R.id.progressRouteBar)
    }

    // ── Specific places — search via Maps Text Search API ────────────────────
    private fun setupSpecificPlaces() {
        btnAddPlace.setOnClickListener {
            val query = etSpecificPlace.text.toString().trim()
            if (query.isEmpty()) return@setOnClickListener
            searchAndAddPlace(query)
        }
    }

    private fun searchAndAddPlace(query: String) {
        btnAddPlace.isEnabled = false
        tvSearchStatus.visibility = View.VISIBLE
        tvSearchStatus.text = "🔍 Buscando \"$query\"..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = "https://maps.googleapis.com/maps/api/place/textsearch/json" +
                    "?query=${java.net.URLEncoder.encode(query, "UTF-8")}" +
                    "&key=${getMapsApiKey()}"

                val body = okHttpClient.newCall(
                    Request.Builder().url(url).build()
                ).execute().body?.string()

                val results = body?.let {
                    gson.fromJson(it, NearbySearchResponse::class.java).results
                } ?: emptyList()

                withContext(Dispatchers.Main) {
                    btnAddPlace.isEnabled = true
                    if (results.isEmpty()) {
                        tvSearchStatus.text = "❌ Lugar não encontrado. Tente outro nome."
                        tvSearchStatus.setTextColor(ContextCompat.getColor(this@CreateRouteActivity, R.color.omw_danger))
                    } else {
                        val place = results.first()
                        // Check not already added
                        if (resolvedPlaces.any { it.place_id == place.place_id }) {
                            tvSearchStatus.text = "⚠️ \"${place.name}\" já foi adicionado."
                            tvSearchStatus.setTextColor(ContextCompat.getColor(this@CreateRouteActivity, R.color.omw_purple_accent))
                        } else {
                            resolvedPlaces.add(place)
                            addSpecificChip(place)
                            etSpecificPlace.setText("")
                            tvSearchStatus.text = "✅ \"${place.name}\" adicionado à sua rota!"
                            tvSearchStatus.setTextColor(ContextCompat.getColor(this@CreateRouteActivity, R.color.omw_success))
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    btnAddPlace.isEnabled = true
                    tvSearchStatus.text = "❌ Erro na busca. Verifique sua conexão."
                    tvSearchStatus.setTextColor(ContextCompat.getColor(this@CreateRouteActivity, R.color.omw_danger))
                }
            }
        }
    }

    private fun addSpecificChip(place: NearbyPlace) {
        val displayName = buildString {
            append(place.name)
            place.vicinity?.let { append(" · $it") }
        }
        val chip = Chip(this).apply {
            text = displayName
            isCloseIconVisible = true
            chipBackgroundColor = ColorStateList.valueOf(ContextCompat.getColor(this@CreateRouteActivity, R.color.omw_input_background_alt))
            setTextColor(ContextCompat.getColor(this@CreateRouteActivity, R.color.omw_purple_main))
            chipStrokeWidth = 0f
            setOnCloseIconClickListener {
                resolvedPlaces.remove(place)
                chipGroupSpecific.removeView(this)
                if (resolvedPlaces.isEmpty()) tvSearchStatus.visibility = View.GONE
            }
        }
        chipGroupSpecific.addView(chip)
    }

    // ── Hobby chips ───────────────────────────────────────────────────────────
    private fun buildHobbyChips() {
        chipGroupHobbies.removeAllViews()
        HobbyData.ALL_HOBBIES.forEach { hobby ->
            val chip = Chip(this).apply {
                text = hobby.displayName
                isCheckable = true
                chipBackgroundColor = chipBgColors
                setTextColor(chipTextColors)
                chipStrokeWidth = 0f
                isCheckedIconVisible = false
                textSize = 13f
                tag = hobby.displayName
            }
            chipGroupHobbies.addView(chip)
        }
    }

    private fun preSelectHobbies(hobbies: List<String>) {
        for (i in 0 until chipGroupHobbies.childCount) {
            val chip = chipGroupHobbies.getChildAt(i) as? Chip ?: continue
            chip.isChecked = chip.tag in hobbies
        }
    }

    private fun getSelectedHobbies(): List<String> {
        val result = mutableListOf<String>()
        for (i in 0 until chipGroupHobbies.childCount) {
            val chip = chipGroupHobbies.getChildAt(i) as? Chip ?: continue
            if (chip.isChecked) result.add(chip.tag as String)
        }
        return result
    }

    // ── Fetch user data ───────────────────────────────────────────────────────
    private fun fetchUserData() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val profileUrl = "$supabaseUrl/rest/v1/profiles" +
                    "?select=travel_pace,budget_level&limit=1"
                val profileBody = okHttpClient.newCall(supabaseGet(profileUrl))
                    .execute().body?.string()

                if (!profileBody.isNullOrBlank() && profileBody != "[]") {
                    val arr = JSONArray(profileBody)
                    if (arr.length() > 0) {
                        val obj = arr.getJSONObject(0)
                        travelRhythm  = obj.optString("travel_pace",  "moderate")
                        spendingLevel = obj.optString("budget_level", "medium")
                    }
                }

                val hobbiesUrl = "$supabaseUrl/rest/v1/user_hobbies?select=hobbies&limit=1"
                val hobbiesBody = okHttpClient.newCall(supabaseGet(hobbiesUrl))
                    .execute().body?.string()

                if (!hobbiesBody.isNullOrBlank() && hobbiesBody != "[]") {
                    val arr = JSONArray(hobbiesBody)
                    if (arr.length() > 0) {
                        val hobbiesJson = arr.getJSONObject(0).optJSONArray("hobbies")
                        if (hobbiesJson != null) {
                            savedHobbies.clear()
                            for (i in 0 until hobbiesJson.length())
                                savedHobbies.add(hobbiesJson.getString(i))
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    if (savedHobbies.isNotEmpty()) preSelectHobbies(savedHobbies)
                }
            } catch (e: Exception) {
                android.util.Log.e("CreateRoute", "Fetch user data error", e)
            }
        }
    }

    // ── Save hobbies — always REPLACES current selection in DB ───────────────
    private suspend fun saveHobbies(hobbies: List<String>) = withContext(Dispatchers.IO) {
        try {
            // Get the current user session token for auth
            val session = try {
                SupabaseClient.client.auth.currentSessionOrNull()
            } catch (e: Exception) { null }

            val authToken = session?.accessToken ?: supabaseKey
            val userId    = try {
                SupabaseClient.client.auth.currentUserOrNull()?.id
            } catch (e: Exception) { null }

            if (userId == null) {
                android.util.Log.e("CreateRoute", "No user ID — cannot save hobbies")
                return@withContext
            }

            // UPSERT with user_id included → Supabase replaces the entire hobbies array
            val body = JSONObject().apply {
                put("user_id", userId)
                put("hobbies", JSONArray(hobbies))  // full replacement, not merge
            }.toString()

            val req = Request.Builder()
                .url("$supabaseUrl/rest/v1/user_hobbies")
                .addHeader("apikey", supabaseKey)
                .addHeader("Authorization", "Bearer $authToken")
                .addHeader("Content-Type", "application/json")
                // resolution=merge-duplicates + unique(user_id) → UPDATE on conflict
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            okHttpClient.newCall(req).execute()
            android.util.Log.d("CreateRoute", "Hobbies saved: $hobbies")
        } catch (e: Exception) {
            android.util.Log.e("CreateRoute", "Save hobbies error", e)
        }
    }

    // ── Create route ──────────────────────────────────────────────────────────
    private fun onCreateRoute() {
        val selectedHobbies = getSelectedHobbies()
        if (selectedHobbies.isEmpty()) {
            Toast.makeText(this,
                "Selecione pelo menos um hobby para personalizar sua rota!",
                Toast.LENGTH_SHORT).show()
            return
        }

        btnCreateRoute.isEnabled = false
        progressBar.visibility   = View.VISIBLE

        CoroutineScope(Dispatchers.Main).launch {
            // Always save current selection → replaces old hobbies in DB
            saveHobbies(selectedHobbies)

            MealNotificationReceiver.cancelAll(this@CreateRouteActivity)
            if (rgLunch.checkedRadioButtonId  == R.id.rbLunchYes)
                MealNotificationReceiver.scheduleLunch(this@CreateRouteActivity)
            if (rgDinner.checkedRadioButtonId == R.id.rbDinnerYes)
                MealNotificationReceiver.scheduleDinner(this@CreateRouteActivity)

            val resultIntent = Intent().apply {
                putStringArrayListExtra("hobbies",        ArrayList(selectedHobbies))
                putExtra("hotel_breakfast",   rgBreakfast.checkedRadioButtonId == R.id.rbBreakfastYes)
                putExtra("lunch_suggestion",  rgLunch.checkedRadioButtonId     == R.id.rbLunchYes)
                putExtra("dinner_suggestion", rgDinner.checkedRadioButtonId    == R.id.rbDinnerYes)
                putExtra("travel_rhythm",     travelRhythm)
                putExtra("spending_level",    spendingLevel)
                // Pass resolved mandatory places as JSON so MainActivity can deserialize
                putExtra("mandatory_stops_json", JSONArray(
                    resolvedPlaces.map { place ->
                        JSONObject().apply {
                            put("place_id", place.place_id)
                            put("name",     place.name)
                            put("lat",      place.geometry.location.lat)
                            put("lng",      place.geometry.location.lng)
                            put("vicinity", place.vicinity ?: "")
                        }
                    }.map { it.toString() }
                ).toString())
            }
            setResult(RESULT_OK, resultIntent)

            progressBar.visibility   = View.GONE
            btnCreateRoute.isEnabled = true
            finish()
        }
    }

    private fun getMapsApiKey(): String =
        packageManager.getApplicationInfo(packageName,
            android.content.pm.PackageManager.GET_META_DATA)
            .metaData.getString("com.google.android.geo.API_KEY") ?: ""

    private fun supabaseGet(url: String) = Request.Builder()
        .url(url)
        .addHeader("apikey", supabaseKey)
        .addHeader("Authorization", "Bearer $supabaseKey")
        .get()
        .build()
}
