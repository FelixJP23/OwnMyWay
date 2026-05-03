package com.example.ownmyway

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
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
            renderRecommendations(results)
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
            chipGroupProfile.addView(Chip(this).apply {
                text = label
                isCheckable = false
                isClickable = false
                chipBackgroundColor = android.content.res.ColorStateList.valueOf(Color.parseColor("#F3EBFB"))
                setTextColor(Color.parseColor("#4A2080"))
                chipStrokeColor = android.content.res.ColorStateList.valueOf(Color.parseColor("#D7C5F0"))
                chipStrokeWidth = 1f
                textSize = 13f
            })
        }
    }

    private fun renderSummary(profile: UserTravelProfile, results: List<RecommendationResult>) {
        val topCountries = results.take(3).joinToString(", ") { it.destination.country }
        val budgetLabel = when (profile.budgetLevel) {
            "low" -> "econômico"
            "high" -> "premium"
            else -> "moderado"
        }
        val paceLabel = when (profile.travelPace) {
            "relaxed" -> "tranquilo"
            "fast" -> "aventureiro"
            else -> "equilibrado"
        }

        tvRecommendationSummary.text =
            "Com base nos seus interesses e no seu estilo $budgetLabel/$paceLabel, os destinos mais fortes para você são: $topCountries."
    }

    private fun renderRecommendations(results: List<RecommendationResult>) {
        recommendationsContainer.removeAllViews()

        results.take(6).forEach { result ->
            val destination = result.destination
            val item = layoutInflater.inflate(
                R.layout.item_country_recommendation,
                recommendationsContainer,
                false
            )

            item.findViewById<TextView>(R.id.tvCountryEmoji).text = destination.emoji
            item.findViewById<TextView>(R.id.tvCountryName).text = destination.country
            item.findViewById<TextView>(R.id.tvCountrySubtitle).text = destination.subtitle
            item.findViewById<TextView>(R.id.tvCountryMatch).text = "${result.matchPercent}% compatível"
            item.findViewById<TextView>(R.id.tvCountryReason).text = destination.reason
            item.findViewById<TextView>(R.id.tvCountryHighlights).text =
                destination.highlights.joinToString(separator = "  •  ")
            item.findViewById<TextView>(R.id.tvCountryTags).text =
                "Combina com: ${destination.tags.take(4).joinToString(", ")}"

            item.findViewById<Button>(R.id.btnOpenCountry).setOnClickListener {
                openCountryInAppMap(destination)
            }

            recommendationsContainer.addView(item)
        }
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
        val destinations = countryDestinations()

        val scored = destinations.map { destination ->
            val tagScore = destination.tags.intersect(profileTags).size * 12
            val budgetScore = when {
                destination.budgetFit == profile.budgetLevel -> 10
                destination.budgetFit == "medium" || profile.budgetLevel == "medium" -> 5
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
                    tags.addAll(listOf("compras", "urbano", "gastronomia"))

                value.contains("hist") ->
                    tags.addAll(listOf("história", "cultura", "museus"))

                value.contains("photo") ->
                    tags.addAll(listOf("fotografia", "natureza", "cultura"))

                value.contains("program") || value.contains("coding") || value.contains("game") ->
                    tags.addAll(listOf("tecnologia", "cafés", "urbano"))

                value.contains("reading") || value.contains("writing") || value.contains("journal") ->
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
            reason = "Ótimo para quem gosta de caminhar sem pressa, comer bem e visitar lugares históricos sem perder o conforto."
        ),
        CountryDestination(
            country = "Japão",
            emoji = "🇯🇵",
            subtitle = "Tecnologia, cultura, comida e fotografia urbana",
            tags = setOf("tecnologia", "cultura", "gastronomia", "fotografia", "urbano"),
            budgetFit = "high",
            paceFit = "fast",
            highlights = listOf("Tóquio", "Kyoto", "Osaka"),
            reason = "Combina com perfis curiosos, que gostam de explorar bastante e alternar tradição, modernidade e experiências diferentes."
        ),
        CountryDestination(
            country = "Itália",
            emoji = "🇮🇹",
            subtitle = "Arte, museus, história e comida marcante",
            tags = setOf("arte", "museus", "história", "gastronomia", "cultura"),
            budgetFit = "medium",
            paceFit = "moderate",
            highlights = listOf("Roma", "Florença", "Veneza"),
            reason = "Uma escolha forte para quem curte arte, arquitetura, cidades bonitas e uma viagem com cara de experiência memorável."
        ),
        CountryDestination(
            country = "Canadá",
            emoji = "🇨🇦",
            subtitle = "Natureza, parques, trilhas e paisagens enormes",
            tags = setOf("natureza", "aventura", "fotografia", "ar livre", "bem-estar"),
            budgetFit = "high",
            paceFit = "moderate",
            highlights = listOf("Banff", "Vancouver", "Toronto"),
            reason = "Ideal para equilibrar cidade grande com montanhas, lagos e experiências ao ar livre."
        ),
        CountryDestination(
            country = "Peru",
            emoji = "🇵🇪",
            subtitle = "Trilhas, história ancestral e gastronomia",
            tags = setOf("aventura", "história", "natureza", "gastronomia", "fotografia"),
            budgetFit = "low",
            paceFit = "fast",
            highlights = listOf("Cusco", "Machu Picchu", "Lima"),
            reason = "Perfeito para quem quer uma viagem com sensação de conquista, paisagens fortes e muita história."
        ),
        CountryDestination(
            country = "Chile",
            emoji = "🇨🇱",
            subtitle = "Deserto, montanhas, vinhos e rotas cênicas",
            tags = setOf("natureza", "aventura", "fotografia", "gastronomia", "ar livre"),
            budgetFit = "medium",
            paceFit = "moderate",
            highlights = listOf("Atacama", "Santiago", "Patagônia"),
            reason = "Boa opção para quem gosta de paisagens diferentes e quer montar uma rota visualmente marcante."
        ),
        CountryDestination(
            country = "Tailândia",
            emoji = "🇹🇭",
            subtitle = "Praias, templos, comida e viagem econômica",
            tags = setOf("gastronomia", "natureza", "cultura", "bem-estar", "slow travel"),
            budgetFit = "low",
            paceFit = "relaxed",
            highlights = listOf("Bangkok", "Chiang Mai", "Phi Phi"),
            reason = "Funciona muito bem para quem quer gastar menos, conhecer outra cultura e variar entre cidade, praia e templos."
        ),
        CountryDestination(
            country = "Espanha",
            emoji = "🇪🇸",
            subtitle = "Arte, vida noturna, comida e cidades vibrantes",
            tags = setOf("vida noturna", "arte", "gastronomia", "urbano", "cultura"),
            budgetFit = "medium",
            paceFit = "moderate",
            highlights = listOf("Barcelona", "Madrid", "Sevilha"),
            reason = "Boa para quem quer uma viagem viva, com ruas cheias, museus, comida boa e opções para sair à noite."
        ),
        CountryDestination(
            country = "Coreia do Sul",
            emoji = "🇰🇷",
            subtitle = "Música, tecnologia, cafés e cultura pop",
            tags = setOf("música", "tecnologia", "cafés", "urbano", "gastronomia"),
            budgetFit = "medium",
            paceFit = "fast",
            highlights = listOf("Seul", "Busan", "Jeju"),
            reason = "Combina com perfis que gostam de cidade, cultura pop, cafés temáticos e experiências modernas."
        ),
        CountryDestination(
            country = "Argentina",
            emoji = "🇦🇷",
            subtitle = "Gastronomia, cultura, natureza e bom custo-benefício",
            tags = setOf("gastronomia", "cultura", "natureza", "arte", "slow travel"),
            budgetFit = "low",
            paceFit = "relaxed",
            highlights = listOf("Buenos Aires", "Bariloche", "Mendoza"),
            reason = "Uma alternativa próxima e versátil para misturar cidade, comida, paisagens e uma viagem mais econômica."
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
                budget = obj.optString("budget_level", "medium")
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
        val reason: String
    )
}
