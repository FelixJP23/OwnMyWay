package com.example.ownmyway

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
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
    private lateinit var sectionLastDestination: View
    private lateinit var tvLastDestinationValue: TextView
    private lateinit var btnEditLastDestination: TextView
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

    private var currentUserId: String? = null
    private var currentAvatarUrl: String? = null
    private var currentPreferredTransport: String? = null
    private var selectedImageUri: Uri? = null
    private val selectedStyles = mutableListOf<String>()

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
        setContentView(R.layout.activity_profile)

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
        sectionLastDestination = findViewById(R.id.sectionLastDestination)
        tvLastDestinationValue = findViewById(R.id.tvLastDestinationValue)
        btnEditLastDestination = findViewById(R.id.btnEditLastDestination)
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
    }

    private fun setupListeners() {
        btnEditPhoto.setOnClickListener { openGallery() }
        btnAddStyle.setOnClickListener { showAddStyleDialog() }
        btnEditLastDestination.setOnClickListener { showEditLastDestinationDialog() }
        btnSaveIdentity.setOnClickListener { saveProfile() }
        btnCancel.setOnClickListener { finish() }
    }

    private fun loadProfile() {
        lifecycleScope.launch {
            try {
                val user = SupabaseClient.client.auth.currentUserOrNull()
                if (user == null) {
                    Toast.makeText(
                        this@ProfileActivity,
                        "Usuário não autenticado.",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                    return@launch
                }

                currentUserId = user.id

                val profiles = SupabaseClient.client.postgrest["profiles"]
                    .select {
                        filter { eq("id", user.id) }
                    }
                    .decodeList<UserProfile>()

                val profile = profiles.firstOrNull() ?: return@launch

                currentAvatarUrl = profile.avatar_url
                currentPreferredTransport = profile.preferred_transport

                etName.setText(profile.full_name.orEmpty())
                etUsername.setText(profile.username.orEmpty())
                etBio.setText(profile.bio.orEmpty())

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

                val lastDestination = profile.last_destination?.trim().orEmpty()
                if (lastDestination.isNotBlank()) {
                    sectionLastDestination.visibility = View.VISIBLE
                    tvLastDestinationValue.text = lastDestination
                } else {
                    sectionLastDestination.visibility = View.GONE
                    tvLastDestinationValue.text = ""
                }

            } catch (e: Exception) {
                Toast.makeText(
                    this@ProfileActivity,
                    "Erro ao carregar perfil: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun saveProfile() {
        val userId = currentUserId ?: run {
            Toast.makeText(this, "Usuário não encontrado.", Toast.LENGTH_SHORT).show()
            return
        }

        val fullName = etName.text.toString().trim()
        val username = etUsername.text.toString().trim().removePrefix("@").lowercase()
        val bio = etBio.text.toString().trim()

        val lastDestination = if (sectionLastDestination.visibility == View.VISIBLE) {
            tvLastDestinationValue.text.toString().trim().ifBlank { null }
        } else {
            null
        }

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

                val profileToUpdate = UserProfile(
                    id = userId,
                    full_name = fullName,
                    onboarding_completed = true,
                    budget_level = budgetLevel,
                    travel_pace = travelPace,
                    interests = selectedStyles,
                    preferred_transport = currentPreferredTransport,
                    avatar_url = finalAvatarUrl,
                    username = username,
                    bio = bio.ifBlank { null },
                    last_destination = lastDestination
                )

                SupabaseClient.client.postgrest["profiles"].update(profileToUpdate) {
                    filter { eq("id", userId) }
                }

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

    private suspend fun uploadAvatarIfNeeded(userId: String): String? {
        val uri = selectedImageUri ?: return null
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val path = "$userId/avatar_${UUID.randomUUID()}.jpg"

        SupabaseClient.client.storage["avatars"].upload(path, bytes) {
            upsert = true
        }

        return SupabaseClient.client.storage["avatars"].publicUrl(path)
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

    private fun showEditLastDestinationDialog() {
        val input = EditText(this).apply {
            hint = "Ex: Quioto, Japão"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS

            if (sectionLastDestination.visibility == View.VISIBLE) {
                setText(tvLastDestinationValue.text.toString())
                setSelection(text.length)
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Último destino")
            .setView(input)
            .setPositiveButton("Salvar") { _, _ ->
                val value = input.text.toString().trim()

                if (value.isBlank()) {
                    sectionLastDestination.visibility = View.GONE
                    tvLastDestinationValue.text = ""
                } else {
                    sectionLastDestination.visibility = View.VISIBLE
                    tvLastDestinationValue.text = value
                }
            }
            .setNeutralButton("Remover") { _, _ ->
                sectionLastDestination.visibility = View.GONE
                tvLastDestinationValue.text = ""
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