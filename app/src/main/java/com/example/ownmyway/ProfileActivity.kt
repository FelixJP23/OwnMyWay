package com.example.ownmyway

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.ownmyway.network.UserProfile
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.launch
import java.util.UUID

class ProfileActivity : AppCompatActivity() {

    private lateinit var ivProfile: ImageView
    private lateinit var btnEditPhoto: ImageView
    private lateinit var etName: EditText
    private lateinit var etUsername: EditText
    private lateinit var etBio: EditText
    private lateinit var etVisitedPlaces: EditText
    private lateinit var etWantToVisit: EditText
    private lateinit var chipGroupStyles: ChipGroup
    private lateinit var btnAddStyle: Button
    private lateinit var radioGroupBudget: RadioGroup
    private lateinit var radioBudgetLow: RadioButton
    private lateinit var radioBudgetMedium: RadioButton
    private lateinit var radioBudgetHigh: RadioButton
    private lateinit var radioGroupPace: RadioGroup
    private lateinit var radioPaceRelaxed: RadioButton
    private lateinit var radioPaceModerate: RadioButton
    private lateinit var radioPaceFast: RadioButton
    private lateinit var btnSaveIdentity: Button
    private lateinit var btnCancel: TextView
    private lateinit var btnLogoutProfile: Button

    private var currentUserId: String? = null
    private var currentAvatarUrl: String? = null
    private var currentPreferredTransport: String? = null
    private var selectedImageUri: Uri? = null
    private val selectedStyles = mutableListOf<String>()

    private val localProfilePrefs by lazy {
        getSharedPreferences("profile_local_fields", MODE_PRIVATE)
    }

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data
                if (uri != null) {
                    selectedImageUri = uri
                    ivProfile.setImageURI(uri)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_profile)
        AppBottomNavigation.setup(this, selectedItemId = null)

        bindViews()
        setupListeners()
        loadProfile()
    }

    private fun bindViews() {
        ivProfile = findViewById(R.id.ivProfile)
        btnEditPhoto = findViewById(R.id.btnEditPhoto)
        etName = findViewById(R.id.etName)
        etUsername = findViewById(R.id.etUsername)
        etBio = findViewById(R.id.etBio)
        etVisitedPlaces = findViewById(R.id.etVisitedPlaces)
        etWantToVisit = findViewById(R.id.etWantToVisit)
        chipGroupStyles = findViewById(R.id.chipGroupStyles)
        btnAddStyle = findViewById(R.id.btnAddStyle)
        radioGroupBudget = findViewById(R.id.radioGroupBudget)
        radioBudgetLow = findViewById(R.id.radioBudgetLow)
        radioBudgetMedium = findViewById(R.id.radioBudgetMedium)
        radioBudgetHigh = findViewById(R.id.radioBudgetHigh)
        radioGroupPace = findViewById(R.id.radioGroupPace)
        radioPaceRelaxed = findViewById(R.id.radioPaceRelaxed)
        radioPaceModerate = findViewById(R.id.radioPaceModerate)
        radioPaceFast = findViewById(R.id.radioPaceFast)
        btnSaveIdentity = findViewById(R.id.btnSaveIdentity)
        btnCancel = findViewById(R.id.btnCancel)
        btnLogoutProfile = findViewById(R.id.btnLogoutProfile)
    }

    private fun setupListeners() {
        btnEditPhoto.setOnClickListener { openGallery() }
        btnAddStyle.setOnClickListener { showAddStyleDialog() }
        btnSaveIdentity.setOnClickListener { saveProfile() }
        btnCancel.setOnClickListener { finish() }
        btnLogoutProfile.setOnClickListener { performLogout() }
    }

    private fun loadProfile() {
        lifecycleScope.launch {
            try {
                // 1. Lógica de seleção de ID (Amigo vs Próprio)
                val intentUserId = intent.getStringExtra("USER_ID")
                val userIdToLoad = intentUserId ?: SupabaseClient.client.auth.currentUserOrNull()?.id

                if (userIdToLoad == null) {
                    Toast.makeText(this@ProfileActivity, "Usuário não autenticado.", Toast.LENGTH_LONG).show()
                    finish()
                    return@launch
                }

                currentUserId = userIdToLoad

                val profiles = SupabaseClient.client.postgrest["profiles"]
                    .select {
                        filter { eq("id", userIdToLoad) }
                    }
                    .decodeList<UserProfile>()

                val profile = profiles.firstOrNull() ?: return@launch

                // 2. Preenchimento normal dos campos
                currentAvatarUrl = profile.avatar_url
                currentPreferredTransport = profile.preferred_transport
                loadAvatarIntoView(currentAvatarUrl)
                etName.setText(profile.full_name.orEmpty())
                etUsername.setText(profile.username.orEmpty())
                etBio.setText(profile.bio.orEmpty())
                loadLocalTravelTexts(userIdToLoad)
                selectedStyles.clear()
                selectedStyles.addAll(profile.interests ?: emptyList())
                renderStyleChips()

                when (profile.budget_level) {
                    "low" -> radioBudgetLow.isChecked = true
                    "medium" -> radioBudgetMedium.isChecked = true
                    "high" -> radioBudgetHigh.isChecked = true
                }

                when (profile.travel_pace) {
                    "relaxed" -> radioPaceRelaxed.isChecked = true
                    "moderate" -> radioPaceModerate.isChecked = true
                    "fast" -> radioPaceFast.isChecked = true
                }

                // --- BLOQUEIO DE EDIÇÃO ---
                if (intentUserId != null) {
                    disableEditing()
                }

            } catch (e: Exception) {
                Toast.makeText(this@ProfileActivity, "Erro ao carregar perfil: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Função auxiliar para desativar todos os campos e esconder botões
     */
    private fun disableEditing() {
        // Esconde os botões que permitem alteração
        btnSaveIdentity.visibility = android.view.View.GONE
        btnEditPhoto.visibility = android.view.View.GONE
        btnAddStyle.visibility = android.view.View.GONE
        btnLogoutProfile.visibility = android.view.View.GONE
        // Desativa a edição dos campos de texto
        etName.isEnabled = false
        etUsername.isEnabled = false
        etBio.isEnabled = false
        etVisitedPlaces.isEnabled = false
        etWantToVisit.isEnabled = false

        // Desativa os RadioGroups (Budget e Pace)
        for (i in 0 until radioGroupBudget.childCount) {
            radioGroupBudget.getChildAt(i).isEnabled = false
        }
        for (i in 0 until radioGroupPace.childCount) {
            radioGroupPace.getChildAt(i).isEnabled = false
        }

        btnCancel.text = "Voltar"
    }

    private fun saveProfile() {
        val userId = currentUserId ?: run {
            Toast.makeText(this, "Usuário não encontrado.", Toast.LENGTH_SHORT).show()
            return
        }

        val fullName = etName.text.toString().trim()
        val username = etUsername.text.toString().trim().removePrefix("@").lowercase()
        val bio = etBio.text.toString().trim()
        val visitedPlaces = etVisitedPlaces.text.toString().trim()
        val wantToVisit = etWantToVisit.text.toString().trim()

        val budgetLevel = when (radioGroupBudget.checkedRadioButtonId) {
            R.id.radioBudgetLow -> "low"
            R.id.radioBudgetMedium -> "medium"
            R.id.radioBudgetHigh -> "high"
            else -> null
        }

        val travelPace = when (radioGroupPace.checkedRadioButtonId) {
            R.id.radioPaceRelaxed -> "relaxed"
            R.id.radioPaceModerate -> "moderate"
            R.id.radioPaceFast -> "fast"
            else -> null
        }

        if (fullName.isBlank()) {
            etName.error = "Informe seu nome"
            etName.requestFocus()
            return
        }

        if (username.isBlank()) {
            etUsername.error = "Informe um username"
            etUsername.requestFocus()
            return
        }

        lifecycleScope.launch {
            try {
                val uploadedAvatarUrl = uploadAvatarIfNeeded(userId)
                val finalAvatarUrl = uploadedAvatarUrl ?: currentAvatarUrl
                currentAvatarUrl = finalAvatarUrl

                SupabaseClient.client.postgrest["profiles"].update(
                    {
                        set("full_name", fullName)
                        set("onboarding_completed", true)
                        set("budget_level", budgetLevel)
                        set("travel_pace", travelPace)
                        set("interests", selectedStyles)
                        set("preferred_transport", currentPreferredTransport)
                        set("avatar_url", finalAvatarUrl)
                        set("username", username)
                        set("bio", bio.ifBlank { null })
                    }
                ) {
                    filter { eq("id", userId) }
                }

                saveLocalTravelTexts(
                    userId = userId,
                    visitedPlaces = visitedPlaces,
                    wantToVisit = wantToVisit
                )

                selectedImageUri = null

                Toast.makeText(
                    this@ProfileActivity,
                    "Perfil salvo com sucesso!",
                    Toast.LENGTH_SHORT
                ).show()

                val intent = Intent(this@ProfileActivity, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                finish()

            } catch (e: Exception) {
                Toast.makeText(
                    this@ProfileActivity,
                    "Erro ao salvar perfil: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun performLogout() {
        AlertDialog.Builder(this)
            .setTitle("Sair da conta")
            .setMessage("Tem certeza que deseja sair da sua conta?")
            .setPositiveButton("Sair") { _, _ ->
                lifecycleScope.launch {
                    try {
                        // 1. O signOut invalida o token no servidor e aciona o deleteSession() do SharedPreferences
                        SupabaseClient.client.auth.signOut()

                        Toast.makeText(this@ProfileActivity, "Logout realizado com sucesso!", Toast.LENGTH_SHORT).show()

                        // 2. ALTERAÇÃO DE LOGÍSTICA: Redireciona para a SplashActivity
                        val intent = Intent(this@ProfileActivity, SplashActivity::class.java).apply {
                            // Limpa todo o histórico de telas anteriores da memória (inclusive a MainActivity de onde você veio)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(intent)
                        finish()
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@ProfileActivity,
                            "Erro ao sair da conta: ${e.localizedMessage}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    private fun loadLocalTravelTexts(userId: String) {
        etVisitedPlaces.setText(localProfilePrefs.getString("visited_places_$userId", "").orEmpty())
        etWantToVisit.setText(localProfilePrefs.getString("want_to_visit_$userId", "").orEmpty())
    }

    private fun saveLocalTravelTexts(
        userId: String,
        visitedPlaces: String,
        wantToVisit: String
    ) {
        localProfilePrefs.edit()
            .putString("visited_places_$userId", visitedPlaces)
            .putString("want_to_visit_$userId", wantToVisit)
            .apply()
    }

    private suspend fun uploadAvatarIfNeeded(userId: String): String? {
        val uri = selectedImageUri ?: return null
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val path = "$userId/avatar_${UUID.randomUUID()}.jpg"

        SupabaseClient.client.storage.from("avatars").upload(path, bytes) {
            upsert = true
        }

        return SupabaseClient.client.storage.from("avatars").publicUrl(path)
    }

    private fun loadAvatarIntoView(avatarUrl: String?) {
        if (avatarUrl.isNullOrBlank()) {
            ivProfile.setImageResource(R.drawable.default_profile)
            return
        }

        Glide.with(this)
            .load(avatarUrl)
            .placeholder(R.drawable.default_profile)
            .error(R.drawable.default_profile)
            .centerCrop()
            .into(ivProfile)
    }

    private fun renderStyleChips() {
        chipGroupStyles.removeAllViews()

        selectedStyles.forEach { style ->
            val chip = Chip(this).apply {
                text = style
                isCloseIconVisible = true
                setEnsureMinTouchTargetSize(false)
                setTextColor(resources.getColor(android.R.color.white, theme))
                chipBackgroundColor = getColorStateList(R.color.omw_purple_mid)
                closeIconTint = getColorStateList(android.R.color.white)

                setOnCloseIconClickListener {
                    selectedStyles.remove(style)
                    renderStyleChips()
                }
            }
            chipGroupStyles.addView(chip)
        }
    }

    private fun showAddStyleDialog() {
        val input = EditText(this).apply {
            hint = "Ex: Aventura, Cultura, Mochileiro"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }

        AlertDialog.Builder(this)
            .setTitle("Adicionar estilo")
            .setView(input)
            .setPositiveButton("Adicionar") { _, _ ->
                val value = input.text.toString().trim()
                if (value.isBlank()) return@setPositiveButton

                val alreadyExists = selectedStyles.any { it.equals(value, ignoreCase = true) }
                if (!alreadyExists) {
                    selectedStyles.add(value)
                    renderStyleChips()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK).apply {
            type = "image/*"
        }
        pickImageLauncher.launch(intent)
    }
}