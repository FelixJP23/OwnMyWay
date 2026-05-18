package com.example.ownmyway

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

class CountryRecommendationsActivity : AppCompatActivity() {

    private lateinit var chipGroupProfile: ChipGroup
    private lateinit var tvRecommendationSummary: TextView
    private lateinit var recommendationsContainer: LinearLayout
    private lateinit var progressBar: ProgressBar

    private val okHttpClient = OkHttpClient()
    private val supabaseUrl by lazy { BuildConfig.SUPABASE_URL }
    private val supabaseKey by lazy { BuildConfig.SUPABASE_KEY }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_country_recommendations)

        chipGroupProfile = findViewById(R.id.chipGroupProfile)
        tvRecommendationSummary = findViewById(R.id.tvRecommendationSummary)
        recommendationsContainer = findViewById(R.id.recommendationsContainer)
        progressBar = findViewById(R.id.progressRecommendations)

        findViewById<ImageButton>(R.id.btnBackRecommendations).setOnClickListener { finish() }

        loadRecommendations()
    }

    private fun loadRecommendations() {
        progressBar.visibility = View.VISIBLE
        recommendationsContainer.visibility = View.GONE

        lifecycleScope.launch {
            val profile = withContext(Dispatchers.IO) { fetchUserTravelProfile() }
            val results = buildRecommendations(profile)

            progressBar.visibility = View.GONE
            recommendationsContainer.visibility = View.VISIBLE

            renderProfileChips(profile)
            renderSummary(profile, results)
            renderRecommendations(profile, results)
        }
    }

    private fun renderProfileChips(profile: UserTravelProfile) {
        chipGroupProfile.removeAllViews()

        val visibleTags = if (profile.interests.isNotEmpty()) {
            profile.interests.take(5)
        } else {
            listOf("Viagem", "Cultura", "Natureza")
        }

        visibleTags.forEach { label ->
            chipGroupProfile.addView(createTagChip(label, emphasize = true))
        }
    }

    private fun renderSummary(profile: UserTravelProfile, results: List<RecommendationResult>) {
        val topCountries = results.take(3).joinToString(", ") { it.destination.country }
        val budgetLabel = when (normalizeBudget(profile.budgetLevel)) {
            "low" -> "econômico (\$)"
            "high" -> "luxuoso (\$\$\$)"
            else -> "moderado (\$\$)"
        }
        val paceLabel = when (profile.travelPace) {
            "relaxed" -> "tranquilo"
            "fast" -> "aventureiro"
            else -> "equilibrado"
        }

        tvRecommendationSummary.text =
            "Com base nos seus interesses e no seu estilo $budgetLabel/$paceLabel, os destinos mais fortes para você são: $topCountries. "
    }

    private fun renderRecommendations(profile: UserTravelProfile, results: List<RecommendationResult>) {
        recommendationsContainer.removeAllViews()

        results.take(6).forEach { result ->
            val destination = result.destination
            val item = layoutInflater.inflate(
                R.layout.item_country_recommendation,
                recommendationsContainer,
                false
            )

            val cover = item.findViewById<ImageView>(R.id.ivCountryCover)
            Glide.with(this)
                .load(destination.coverImageUrl)
                .centerCrop()
                .placeholder(R.drawable.bg_country_cover_fallback)
                .error(R.drawable.bg_country_cover_fallback)
                .into(cover)

            item.findViewById<TextView>(R.id.tvCountryEmoji).text = destination.emoji
            item.findViewById<TextView>(R.id.tvCountryName).text = destination.country
            item.findViewById<TextView>(R.id.tvCountrySubtitle).text = destination.subtitle
            item.findViewById<TextView>(R.id.tvCountryMatch).text = "${result.matchPercent}% compatível"
            item.findViewById<TextView>(R.id.tvCountryCallout).text = destination.callout
            item.findViewById<TextView>(R.id.tvCountryReason).text = destination.reason
            item.findViewById<TextView>(R.id.tvCountryHighlights).text =
                destination.highlights.joinToString(separator = "  •  ")

            val chipGroup = item.findViewById<ChipGroup>(R.id.chipGroupCountryTags)
            chipGroup.removeAllViews()
            destination.tags.take(4).forEach { tag ->
                chipGroup.addView(createTagChip(tag, emphasize = destination.tags.intersect(tagsFromInterests(profile.interests)).contains(tag)))
            }

            val budgetChip = item.findViewById<TextView>(R.id.tvBudgetChip)
            renderBudgetChip(budgetChip, profile.budgetLevel, destination.budgetFit)

            val favoriteButton = item.findViewById<Button>(R.id.btnAddToFavorites)
            updateFavoriteButton(favoriteButton, destination.country)
            favoriteButton.setOnClickListener {
                toggleFavorite(destination.country)
                updateFavoriteButton(favoriteButton, destination.country)
            }

            item.findViewById<Button>(R.id.btnOpenCountry).setOnClickListener {
                openCountryInAppMap(destination)
            }

            recommendationsContainer.addView(item)
        }
    }

    private fun createTagChip(label: String, emphasize: Boolean = false): Chip {
        return Chip(this).apply {
            text = label.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            isCheckable = false
            isClickable = false
            chipBackgroundColor = ColorStateList.valueOf(
                ContextCompat.getColor(
                    this@CountryRecommendationsActivity,
                    if (emphasize) R.color.omw_input_background_alt else R.color.omw_input_background
                )
            )
            setTextColor(ContextCompat.getColor(this@CountryRecommendationsActivity, R.color.omw_purple_main))
            chipStrokeColor = ColorStateList.valueOf(ContextCompat.getColor(this@CountryRecommendationsActivity, R.color.omw_avatar_ring))
            chipStrokeWidth = 1f
            textSize = 12.5f
        }
    }

    private fun renderBudgetChip(chip: TextView, userBudget: String, destinationBudget: String) {
        val userLevel = budgetRank(userBudget)
        val destinationLevel = budgetRank(destinationBudget)
        val colorRes = when {
            destinationLevel < userLevel -> R.color.omw_budget_good
            destinationLevel == userLevel -> R.color.omw_budget_equal
            else -> R.color.omw_budget_over
        }
        val label = when (destinationLevel) {
            1 -> "💸 \$"
            3 -> "💸 \$\$\$"
            else -> "💸 \$\$"
        }
        val hint = when {
            destinationLevel < userLevel -> "abaixo do seu orçamento"
            destinationLevel == userLevel -> "dentro do seu orçamento"
            else -> "acima do seu orçamento"
        }

        chip.text = "$label · $hint"
        chip.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(18).toFloat()
            setColor(ContextCompat.getColor(this@CountryRecommendationsActivity, colorRes))
        }
        chip.setTextColor(Color.WHITE)
    }

    private fun updateFavoriteButton(button: Button, countryName: String) {
        val saved = isFavorite(countryName)
        button.text = if (saved) "✓ Salvo como ideia de destino" else "♡ Salvar como ideia de destino"
        button.alpha = if (saved) 0.92f else 1f
    }

    private fun toggleFavorite(countryName: String) {
        val userId = currentUserId()
        if (userId == null) {
            Toast.makeText(this, "Faça login para salvar favoritos", Toast.LENGTH_SHORT).show()
            return
        }

        val prefs = getSharedPreferences("profile_local_fields", MODE_PRIVATE)
        val key = "want_to_visit_$userId"
        val countries = favoriteCountries().toMutableList()

        if (countries.any { it.equals(countryName, ignoreCase = true) }) {
            val updated = countries.filterNot { it.equals(countryName, ignoreCase = true) }
            prefs.edit().putString(key, updated.joinToString(", ")).apply()
            Toast.makeText(this, "$countryName removido dos favoritos", Toast.LENGTH_SHORT).show()
        } else {
            countries.add(countryName)
            prefs.edit().putString(key, countries.distinct().joinToString(", ")).apply()
            Toast.makeText(this, "$countryName salvo como ideia de destino!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isFavorite(countryName: String): Boolean =
        favoriteCountries().any { it.equals(countryName, ignoreCase = true) }

    private fun favoriteCountries(): List<String> {
        val userId = currentUserId() ?: return emptyList()
        val prefs = getSharedPreferences("profile_local_fields", MODE_PRIVATE)
        val currentText = prefs.getString("want_to_visit_$userId", "").orEmpty()
        return currentText.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }

    private fun currentUserId(): String? = try {
        SupabaseClient.client.auth.currentUserOrNull()?.id
    } catch (_: Exception) {
        null
    }

    private fun openCountryInAppMap(destination: CountryDestination) {
        val query = "${destination.country} pontos turísticos ${destination.highlights.joinToString(" ")}"
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_RECOMMENDED_MAP_QUERY, query)
            putExtra(MainActivity.EXTRA_RECOMMENDED_MAP_TITLE, destination.country)
        }
        startActivity(intent)
        finish()
    }

    private fun buildRecommendations(profile: UserTravelProfile): List<RecommendationResult> {
        val profileTags = tagsFromInterests(profile.interests)
        val normalizedBudget = normalizeBudget(profile.budgetLevel)
        val destinations = countryDestinations()

        val scored = destinations.map { destination ->
            val tagScore = destination.tags.intersect(profileTags).size * 12
            val budgetScore = when {
                destination.budgetFit == normalizedBudget -> 10
                budgetRank(destination.budgetFit) < budgetRank(normalizedBudget) -> 8
                budgetRank(destination.budgetFit) == budgetRank(normalizedBudget) + 1 -> 3
                else -> 1
            }
            val paceScore = when {
                destination.paceFit == profile.travelPace -> 8
                destination.paceFit == "moderate" || profile.travelPace == "moderate" -> 4
                else -> 1
            }
            val score = tagScore + budgetScore + paceScore
            destination to score
        }.sortedByDescending { it.second }

        val maxScore = scored.firstOrNull()?.second?.coerceAtLeast(1) ?: 1
        return scored.mapIndexed { index, (destination, score) ->
            val percent = ((score.toDouble() / maxScore.toDouble()) * 26 + 72 - index).toInt()
                .coerceIn(70, 98)
            RecommendationResult(destination, percent)
        }
    }

    private fun tagsFromInterests(interests: List<String>): Set<String> {
        val tags = mutableSetOf<String>()

        interests.forEach { raw ->
            val value = raw.lowercase()
            when {
                value.contains("muse") || value.contains("arte") || value.contains("drawing") ||
                        value.contains("painting") || value.contains("graphic") ->
                    tags.addAll(listOf("arte", "cultura", "museus", "história"))

                value.contains("trilha") || value.contains("nature") || value.contains("hiking") ||
                        value.contains("running") || value.contains("cycling") || value.contains("gardening") ->
                    tags.addAll(listOf("natureza", "aventura", "fotografia", "ar livre"))

                value.contains("gastronomia") || value.contains("cooking") || value.contains("food") ->
                    tags.addAll(listOf("gastronomia", "cultura"))

                value.contains("noturna") || value.contains("dancing") || value.contains("music") ||
                        value.contains("instrument") ->
                    tags.addAll(listOf("vida noturna", "música", "urbano"))

                value.contains("compras") || value.contains("craft") || value.contains("shopping") ->
                    tags.addAll(listOf("compras", "urbano", "cultura"))

                value.contains("photo") || value.contains("video") || value.contains("film") ->
                    tags.addAll(listOf("fotografia", "urbano", "natureza"))

                value.contains("book") || value.contains("writing") || value.contains("journal") ->
                    tags.addAll(listOf("cafés", "cultura", "slow travel"))

                value.contains("meditation") || value.contains("swimming") || value.contains("gym") ->
                    tags.addAll(listOf("bem-estar", "natureza", "slow travel"))

                else -> tags.addAll(listOf("cultura", "natureza"))
            }
        }

        if (tags.isEmpty()) tags.addAll(listOf("cultura", "natureza", "gastronomia"))
        return tags
    }

    private fun countryDestinations(): List<CountryDestination> = listOf(
        CountryDestination(
            country = "Portugal",
            emoji = "🇵🇹",
            subtitle = "História, gastronomia e cidades caminháveis",
            tags = setOf("história", "cultura", "gastronomia", "museus", "slow travel"),
            budgetFit = "medium",
            paceFit = "relaxed",
            highlights = listOf("Lisboa", "Porto", "Sintra"),
            callout = "Perfeito pra quem ama história, cafés charmosos e cultura local.",
            reason = "Ótimo para quem gosta de caminhar sem pressa, comer bem e visitar lugares históricos sem perder o conforto.",
            coverImageUrl = "https://images.unsplash.com/photo-1555881400-74d7acaacd8b?w=1200&q=80"
        ),
        CountryDestination(
            country = "Japão",
            emoji = "🇯🇵",
            subtitle = "Tecnologia, cultura, comida e fotografia urbana",
            tags = setOf("tecnologia", "cultura", "gastronomia", "fotografia", "urbano"),
            budgetFit = "high",
            paceFit = "fast",
            highlights = listOf("Tóquio", "Kyoto", "Osaka"),
            callout = "Perfeito pra quem quer tradição, modernidade e muitos lugares fotogênicos.",
            reason = "Combina com perfis curiosos, que gostam de explorar bastante e alternar tradição, modernidade e experiências diferentes.",
            coverImageUrl = "https://images.unsplash.com/photo-1493976040374-85c8e12f0c0e?w=1200&q=80"
        ),
        CountryDestination(
            country = "Itália",
            emoji = "🇮🇹",
            subtitle = "Arte, museus, história e comida marcante",
            tags = setOf("arte", "museus", "história", "gastronomia", "cultura"),
            budgetFit = "medium",
            paceFit = "moderate",
            highlights = listOf("Roma", "Florença", "Veneza"),
            callout = "Perfeito pra quem ama arte, arquitetura e refeições memoráveis.",
            reason = "Uma escolha forte para quem curte arte, arquitetura, cidades bonitas e uma viagem com cara de experiência memorável.",
            coverImageUrl = "https://images.unsplash.com/photo-1523906834658-6e24ef2386f9?w=1200&q=80"
        ),
        CountryDestination(
            country = "Canadá",
            emoji = "🇨🇦",
            subtitle = "Natureza, parques, trilhas e paisagens enormes",
            tags = setOf("natureza", "aventura", "fotografia", "ar livre", "bem-estar"),
            budgetFit = "high",
            paceFit = "moderate",
            highlights = listOf("Banff", "Vancouver", "Toronto"),
            callout = "Perfeito pra quem ama trilhas, lagos e cidades organizadas.",
            reason = "Ideal para equilibrar cidade grande com montanhas, lagos e experiências ao ar livre.",
            coverImageUrl = "https://images.unsplash.com/photo-1501785888041-af3ef285b470?w=1200&q=80"
        ),
        CountryDestination(
            country = "Peru",
            emoji = "🇵🇪",
            subtitle = "Trilhas, história ancestral e gastronomia",
            tags = setOf("aventura", "história", "natureza", "gastronomia", "fotografia"),
            budgetFit = "low",
            paceFit = "fast",
            highlights = listOf("Cusco", "Machu Picchu", "Lima"),
            callout = "Perfeito pra quem ama trilhas, cultura ancestral e paisagens intensas.",
            reason = "Perfeito para quem quer uma viagem com sensação de conquista, paisagens fortes e muita história.",
            coverImageUrl = "https://images.unsplash.com/photo-1526392060635-9d6019884377?w=1200&q=80"
        ),
        CountryDestination(
            country = "Chile",
            emoji = "🇨🇱",
            subtitle = "Deserto, montanhas, vinhos e rotas cênicas",
            tags = setOf("natureza", "aventura", "fotografia", "gastronomia", "ar livre"),
            budgetFit = "medium",
            paceFit = "moderate",
            highlights = listOf("Atacama", "Santiago", "Patagônia"),
            callout = "Perfeito pra quem busca paisagens cinematográficas e rotas ao ar livre.",
            reason = "Boa opção para quem gosta de paisagens diferentes e quer montar uma rota visualmente marcante.",
            coverImageUrl = "https://images.unsplash.com/photo-1519681393784-d120267933ba?w=1200&q=80"
        ),
        CountryDestination(
            country = "Tailândia",
            emoji = "🇹🇭",
            subtitle = "Praias, templos, comida e viagem econômica",
            tags = setOf("gastronomia", "natureza", "cultura", "bem-estar", "slow travel"),
            budgetFit = "low",
            paceFit = "relaxed",
            highlights = listOf("Bangkok", "Chiang Mai", "Phi Phi"),
            callout = "Perfeito pra quem quer praias, templos e boa gastronomia gastando menos.",
            reason = "Funciona muito bem para quem quer gastar menos, conhecer outra cultura e variar entre cidade, praia e templos.",
            coverImageUrl = "https://images.unsplash.com/photo-1508009603885-50cf7c579365?w=1200&q=80"
        ),
        CountryDestination(
            country = "Espanha",
            emoji = "🇪🇸",
            subtitle = "Arte, vida noturna, comida e cidades vibrantes",
            tags = setOf("vida noturna", "arte", "gastronomia", "urbano", "cultura"),
            budgetFit = "medium",
            paceFit = "moderate",
            highlights = listOf("Barcelona", "Madrid", "Sevilha"),
            callout = "Perfeito pra quem ama arte, tapas e cidades cheias de energia.",
            reason = "Boa para quem quer uma viagem viva, com ruas cheias, museus, comida boa e opções para sair à noite.",
            coverImageUrl = "https://images.unsplash.com/photo-1539037116277-4db20889f2d4?w=1200&q=80"
        ),
        CountryDestination(
            country = "Coreia do Sul",
            emoji = "🇰🇷",
            subtitle = "Música, tecnologia, cafés e cultura pop",
            tags = setOf("música", "tecnologia", "cafés", "urbano", "gastronomia"),
            budgetFit = "medium",
            paceFit = "fast",
            highlights = listOf("Seul", "Busan", "Jeju"),
            callout = "Perfeito pra quem curte cultura pop, cafés e experiências urbanas.",
            reason = "Combina com perfis que gostam de cidade, cultura pop, cafés temáticos e experiências modernas.",
            coverImageUrl = "https://images.unsplash.com/photo-1538485399081-7c8edcb7eb20?w=1200&q=80"
        ),
        CountryDestination(
            country = "Argentina",
            emoji = "🇦🇷",
            subtitle = "Gastronomia, cultura, natureza e bom custo-benefício",
            tags = setOf("gastronomia", "cultura", "natureza", "arte", "slow travel"),
            budgetFit = "low",
            paceFit = "relaxed",
            highlights = listOf("Buenos Aires", "Bariloche", "Mendoza"),
            callout = "Perfeito pra quem quer boa comida, cultura e paisagens com custo-benefício.",
            reason = "Uma alternativa próxima e versátil para misturar cidade, comida, paisagens e uma viagem mais econômica.",
            coverImageUrl = "https://images.unsplash.com/photo-1589909202802-8f4aadce1849?w=1200&q=80"
        )
    )

    private suspend fun fetchUserTravelProfile(): UserTravelProfile {
        val user = try { SupabaseClient.client.auth.currentUserOrNull() } catch (_: Exception) { null }
            ?: return UserTravelProfile()

        val token = try {
            SupabaseClient.client.auth.currentSessionOrNull()?.accessToken
        } catch (_: Exception) { null } ?: supabaseKey

        val userId = user.id
        val hobbies = fetchJsonArrayField(
            url = "$supabaseUrl/rest/v1/user_hobbies?select=hobbies&user_id=eq.$userId&limit=1",
            field = "hobbies",
            token = token
        )

        val profileBody = runCatching {
            okHttpClient.newCall(
                supabaseGet(
                    url = "$supabaseUrl/rest/v1/profiles?select=interests,budget_level,travel_pace&id=eq.$userId&limit=1",
                    token = token
                )
            ).execute().body?.string()
        }.getOrNull()

        var interestsFromProfile = emptyList<String>()
        var budget = "medium"
        var pace = "moderate"

        if (!profileBody.isNullOrBlank() && profileBody != "[]") {
            val arr = JSONArray(profileBody)
            if (arr.length() > 0) {
                val obj = arr.getJSONObject(0)
                budget = normalizeBudget(obj.optString("budget_level", "medium"))
                pace = obj.optString("travel_pace", "moderate")
                val interestsJson = obj.optJSONArray("interests")
                if (interestsJson != null) {
                    interestsFromProfile = List(interestsJson.length()) { index ->
                        interestsJson.getString(index)
                    }
                }
            }
        }

        val mergedInterests = (hobbies + interestsFromProfile)
            .filter { it.isNotBlank() }
            .distinct()

        return UserTravelProfile(
            interests = mergedInterests,
            budgetLevel = budget.ifBlank { "medium" },
            travelPace = pace.ifBlank { "moderate" }
        )
    }

    private fun fetchJsonArrayField(url: String, field: String, token: String): List<String> {
        return try {
            val body = okHttpClient.newCall(supabaseGet(url, token))
                .execute().body?.string()

            if (body.isNullOrBlank() || body == "[]") return emptyList()

            val arr = JSONArray(body)
            if (arr.length() == 0) return emptyList()

            val values = arr.getJSONObject(0).optJSONArray(field) ?: return emptyList()
            List(values.length()) { index -> values.getString(index) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun normalizeBudget(raw: String): String {
        return when (raw.lowercase().trim()) {
            "low", "\$", "economico", "econômico" -> "low"
            "high", "\$\$\$", "luxo", "luxuoso", "premium" -> "high"
            else -> "medium"
        }
    }

    private fun budgetRank(raw: String): Int = when (normalizeBudget(raw)) {
        "low" -> 1
        "high" -> 3
        else -> 2
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun supabaseGet(url: String, token: String): Request = Request.Builder()
        .url(url)
        .addHeader("apikey", supabaseKey)
        .addHeader("Authorization", "Bearer $token")
        .get()
        .build()

    private data class UserTravelProfile(
        val interests: List<String> = emptyList(),
        val budgetLevel: String = "medium",
        val travelPace: String = "moderate"
    )

    private data class RecommendationResult(
        val destination: CountryDestination,
        val matchPercent: Int
    )

    private data class CountryDestination(
        val country: String,
        val emoji: String,
        val subtitle: String,
        val tags: Set<String>,
        val budgetFit: String,
        val paceFit: String,
        val highlights: List<String>,
        val callout: String,
        val reason: String,
        val coverImageUrl: String
    )
}
