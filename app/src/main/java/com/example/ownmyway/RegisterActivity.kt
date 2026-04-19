package com.example.ownmyway

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.widget.ViewFlipper
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.ownmyway.network.UserProfile
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private enum class EmailAvailabilityState {
    IDLE,
    AVAILABLE,
    TAKEN,
    ERROR
}

private enum class PasswordStrength {
    NONE,
    VERY_WEAK,
    WEAK,
    STRONG
}

class RegisterActivity : AppCompatActivity() {

    private lateinit var registerFlipper: ViewFlipper
    private lateinit var progressRegister: ProgressBar
    private lateinit var tvProgressCount: TextView
    private lateinit var tvIntro: TextView
    private lateinit var tvStepTitle: TextView
    private lateinit var tvStepHint: TextView
    private lateinit var tvLogin: TextView
    private lateinit var tvEmailAvailability: TextView
    private lateinit var tvPasswordStrength: TextView
    private lateinit var progressPasswordStrength: ProgressBar

    private lateinit var btnBack: Button
    private lateinit var btnNext: Button

    private lateinit var etName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText

    private lateinit var fields: List<EditText>

    private val stepTitles = listOf(
        "Qual é o seu nome?",
        "Agora digite seu e-mail",
        "Crie sua senha",
        "Confirme sua senha"
    )

    private val stepHints = listOf(
        "",
        "Esse e-mail será usado para entrar no aplicativo.",
        "Use pelo menos 6 caracteres. Senhas com caracteres especiais ficam mais fortes.",
        "Digite novamente a senha criada."
    )

    private val lastStepIndex: Int
        get() = stepTitles.lastIndex

    private var currentStep = 0
    private var emailAvailabilityState = EmailAvailabilityState.IDLE
    private var emailCheckJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_register)

        bindViews()
        setupListeners()
        updateStepUi()
        updatePasswordStrengthUi()
    }

    override fun onDestroy() {
        emailCheckJob?.cancel()
        super.onDestroy()
    }

    private fun bindViews() {
        registerFlipper = findViewById(R.id.registerFlipper)
        progressRegister = findViewById(R.id.progressRegister)
        tvProgressCount = findViewById(R.id.tvProgressCount)
        tvIntro = findViewById(R.id.tvIntro)
        tvStepTitle = findViewById(R.id.tvStepTitle)
        tvStepHint = findViewById(R.id.tvStepHint)
        tvLogin = findViewById(R.id.tvLogin)
        tvEmailAvailability = findViewById(R.id.tvEmailAvailability)
        tvPasswordStrength = findViewById(R.id.tvPasswordStrength)
        progressPasswordStrength = findViewById(R.id.progressPasswordStrength)

        btnBack = findViewById(R.id.btnBack)
        btnNext = findViewById(R.id.btnNext)

        etName = findViewById(R.id.etName)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)

        fields = listOf(etName, etEmail, etPassword, etConfirmPassword)
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            if (currentStep > 0) showStep(currentStep - 1)
        }

        btnNext.setOnClickListener {
            advanceOrSubmit()
        }

        tvLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        etName.doAfterTextChanged {
            if (currentStep == 0) updateButtonsState()
        }

        etEmail.doAfterTextChanged { editable ->
            val email = editable?.toString().orEmpty()
            checkEmailAvailabilityDebounced(email)
            if (currentStep == 1) updateButtonsState()
        }

        etPassword.doAfterTextChanged {
            updatePasswordStrengthUi()
            if (currentStep == 2) updateButtonsState()
        }

        etConfirmPassword.doAfterTextChanged {
            if (currentStep == 3) updateButtonsState()
        }

        fields.forEachIndexed { index, editText ->
            editText.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_DONE) {
                    if (index == currentStep) {
                        advanceOrSubmit()
                    }
                    true
                } else {
                    false
                }
            }
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (currentStep == 0) {
                        finish()
                    } else {
                        showStep(currentStep - 1)
                    }
                }
            }
        )
    }

    private fun advanceOrSubmit() {
        if (!validateCurrentStep(showError = true)) return

        if (currentStep == lastStepIndex) {
            registerUser()
        } else {
            showStep(currentStep + 1)
        }
    }

    private fun showStep(step: Int) {
        if (step == currentStep) return

        val goingForward = step > currentStep
        registerFlipper.inAnimation =
            if (goingForward) createSlideInFromRight() else createSlideInFromLeft()
        registerFlipper.outAnimation =
            if (goingForward) createSlideOutToLeft() else createSlideOutToRight()

        currentStep = step
        updateStepUi()
    }

    private fun updateStepUi() {
        registerFlipper.displayedChild = currentStep

        tvProgressCount.text = "${currentStep + 1} de ${stepTitles.size}"
        progressRegister.max = stepTitles.size
        progressRegister.progress = currentStep + 1

        tvIntro.visibility = if (currentStep == 0) View.VISIBLE else View.GONE
        tvStepTitle.text = stepTitles[currentStep]

        val hint = stepHints[currentStep]
        tvStepHint.text = hint
        tvStepHint.visibility = if (hint.isBlank()) View.GONE else View.VISIBLE

        btnBack.visibility = if (currentStep == 0) View.INVISIBLE else View.VISIBLE
        btnBack.isEnabled = currentStep != 0
        btnNext.text = if (currentStep == lastStepIndex) "Concluir" else "Próximo"

        if (currentStep != 1) {
            tvEmailAvailability.visibility = View.GONE
        }

        updateButtonsState()
        updatePasswordStrengthUi()
        fields[currentStep].requestFocus()
    }

    private fun updateButtonsState() {
        btnNext.isEnabled = validateCurrentStep(showError = false)
        btnNext.alpha = if (btnNext.isEnabled) 1f else 0.45f
    }

    private fun validateCurrentStep(showError: Boolean): Boolean {
        return when (currentStep) {
            0 -> validateName(showError)
            1 -> validateEmail(showError)
            2 -> validatePassword(showError)
            3 -> validateConfirmPassword(showError)
            else -> false
        }
    }

    private fun validateName(showError: Boolean): Boolean {
        val name = etName.text.toString().trim()
        val isValid = name.length >= 3

        if (!isValid && showError) {
            etName.error = "Digite um nome válido"
            etName.requestFocus()
        }

        return isValid
    }

    private fun validateEmail(showError: Boolean): Boolean {
        val email = etEmail.text.toString().trim()

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            if (showError) {
                etEmail.error = "E-mail inválido"
                etEmail.requestFocus()
            }
            return false
        }

        return when (emailAvailabilityState) {
            EmailAvailabilityState.AVAILABLE -> true

            EmailAvailabilityState.TAKEN -> {
                if (showError) {
                    etEmail.error = "Email já cadastrado"
                    etEmail.requestFocus()
                }
                false
            }

            EmailAvailabilityState.IDLE,
            EmailAvailabilityState.ERROR -> {
                if (showError) {
                    etEmail.error = "Aguarde a verificação do e-mail"
                    etEmail.requestFocus()
                }
                false
            }
        }
    }

    private fun validatePassword(showError: Boolean): Boolean {
        val password = etPassword.text.toString()
        val isValid = password.length >= 6

        if (!isValid && showError) {
            etPassword.error = "A senha deve ter pelo menos 6 caracteres"
            etPassword.requestFocus()
        }

        return isValid
    }

    private fun validateConfirmPassword(showError: Boolean): Boolean {
        val password = etPassword.text.toString()
        val confirmPassword = etConfirmPassword.text.toString()

        val isValid = confirmPassword.isNotEmpty() && confirmPassword == password

        if (!isValid && showError) {
            etConfirmPassword.error = if (confirmPassword.isBlank()) {
                "Confirme sua senha"
            } else {
                "As senhas não coincidem"
            }
            etConfirmPassword.requestFocus()
        }

        return isValid
    }

    private fun checkEmailAvailabilityDebounced(rawEmail: String) {
        emailCheckJob?.cancel()

        val email = rawEmail.trim().lowercase()

        if (email.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            setEmailAvailabilityState(EmailAvailabilityState.IDLE)
            return
        }

        setEmailAvailabilityState(EmailAvailabilityState.IDLE)

        emailCheckJob = lifecycleScope.launch {
            delay(450)

            try {
                val result = SupabaseClient.client.postgrest.rpc(
                    "is_email_available",
                    buildJsonObject {
                        put("p_email", email)
                    }
                )

                val normalized = result.data.trim()
                    .removePrefix("\"")
                    .removeSuffix("\"")

                val isAvailable = normalized.toBooleanStrictOrNull()

                setEmailAvailabilityState(
                    when (isAvailable) {
                        true -> EmailAvailabilityState.AVAILABLE
                        false -> EmailAvailabilityState.TAKEN
                        null -> EmailAvailabilityState.ERROR
                    }
                )
            } catch (_: Exception) {
                setEmailAvailabilityState(EmailAvailabilityState.ERROR)
            }
        }
    }

    private fun setEmailAvailabilityState(state: EmailAvailabilityState) {
        emailAvailabilityState = state

        when (state) {
            EmailAvailabilityState.IDLE -> {
                tvEmailAvailability.visibility = View.GONE
                tvEmailAvailability.text = ""
            }

            EmailAvailabilityState.AVAILABLE -> {
                tvEmailAvailability.visibility = View.VISIBLE
                tvEmailAvailability.text = "email disponível!"
                tvEmailAvailability.setTextColor(Color.parseColor("#16A34A"))
            }

            EmailAvailabilityState.TAKEN -> {
                tvEmailAvailability.visibility = View.VISIBLE
                tvEmailAvailability.text = "email já cadastrado"
                tvEmailAvailability.setTextColor(Color.parseColor("#DC2626"))
            }

            EmailAvailabilityState.ERROR -> {
                tvEmailAvailability.visibility = View.GONE
                tvEmailAvailability.text = ""
            }
        }

        if (currentStep == 1) {
            updateButtonsState()
        }
    }

    private fun evaluatePasswordStrength(password: String): PasswordStrength {
        if (password.isBlank()) return PasswordStrength.NONE

        val hasSpecialChar = password.any { !it.isLetterOrDigit() }

        return when {
            password.length < 6 -> PasswordStrength.VERY_WEAK
            password.length > 6 && hasSpecialChar -> PasswordStrength.STRONG
            else -> PasswordStrength.WEAK
        }
    }

    private fun updatePasswordStrengthUi() {
        val password = etPassword.text.toString()
        val strength = evaluatePasswordStrength(password)

        when (strength) {
            PasswordStrength.NONE -> {
                progressPasswordStrength.visibility = View.GONE
                tvPasswordStrength.visibility = View.GONE
            }

            PasswordStrength.VERY_WEAK -> {
                progressPasswordStrength.visibility = View.VISIBLE
                tvPasswordStrength.visibility = View.VISIBLE

                progressPasswordStrength.progress = 33
                progressPasswordStrength.progressTintList =
                    ColorStateList.valueOf(Color.parseColor("#DC2626"))

                tvPasswordStrength.text = "muito fraca"
                tvPasswordStrength.setTextColor(Color.parseColor("#DC2626"))
            }

            PasswordStrength.WEAK -> {
                progressPasswordStrength.visibility = View.VISIBLE
                tvPasswordStrength.visibility = View.VISIBLE

                progressPasswordStrength.progress = 66
                progressPasswordStrength.progressTintList =
                    ColorStateList.valueOf(Color.parseColor("#EAB308"))

                tvPasswordStrength.text = "fraca"
                tvPasswordStrength.setTextColor(Color.parseColor("#CA8A04"))
            }

            PasswordStrength.STRONG -> {
                progressPasswordStrength.visibility = View.VISIBLE
                tvPasswordStrength.visibility = View.VISIBLE

                progressPasswordStrength.progress = 100
                progressPasswordStrength.progressTintList =
                    ColorStateList.valueOf(Color.parseColor("#16A34A"))

                tvPasswordStrength.text = "forte"
                tvPasswordStrength.setTextColor(Color.parseColor("#16A34A"))
            }
        }
    }

    private fun registerUser() {
        if (!validateName(showError = true)) {
            showStep(0)
            return
        }

        if (!validateEmail(showError = true)) {
            showStep(1)
            return
        }

        if (!validatePassword(showError = true)) {
            showStep(2)
            return
        }

        if (!validateConfirmPassword(showError = true)) {
            showStep(3)
            return
        }

        val name = etName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()

        setLoading(true)

        lifecycleScope.launch {
            try {
                SupabaseClient.client.auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                }

                SupabaseClient.client.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }

                val user = SupabaseClient.client.auth.currentUserOrNull()
                if (user == null) {
                    setLoading(false)
                    Toast.makeText(
                        this@RegisterActivity,
                        "Conta criada, mas não foi possível entrar.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                val existingProfiles = SupabaseClient.client.postgrest["profiles"]
                    .select {
                        filter { eq("id", user.id) }
                    }
                    .decodeList<UserProfile>()

                if (existingProfiles.isEmpty()) {
                    SupabaseClient.client.postgrest["profiles"].insert(
                        UserProfile(
                            id = user.id,
                            full_name = name,
                            onboarding_completed = false
                        )
                    )
                } else {
                    SupabaseClient.client.postgrest["profiles"].update(
                        {
                            set("full_name", name)
                        }
                    ) {
                        filter { eq("id", user.id) }
                    }
                }

                setLoading(false)

                Toast.makeText(
                    this@RegisterActivity,
                    "Conta criada com sucesso!",
                    Toast.LENGTH_SHORT
                ).show()

                startActivity(Intent(this@RegisterActivity, OnboardingActivity::class.java))
                finish()

            } catch (e: Exception) {
                setLoading(false)

                val message = e.message.orEmpty()

                if (message.contains("already registered", ignoreCase = true) ||
                    message.contains("already been registered", ignoreCase = true)
                ) {
                    showStep(1)
                    setEmailAvailabilityState(EmailAvailabilityState.TAKEN)
                    etEmail.requestFocus()
                    Toast.makeText(
                        this@RegisterActivity,
                        "Esse e-mail já está cadastrado.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        this@RegisterActivity,
                        "Erro ao criar conta: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        btnBack.isEnabled = !isLoading && currentStep != 0
        btnNext.isEnabled = !isLoading && validateCurrentStep(showError = false)

        btnBack.alpha = if (btnBack.isEnabled || currentStep == 0) 1f else 0.45f
        btnNext.alpha = if (btnNext.isEnabled) 1f else 0.45f

        btnNext.text = if (isLoading) {
            "Criando..."
        } else {
            if (currentStep == lastStepIndex) "Concluir" else "Próximo"
        }
    }

    private fun createSlideInFromRight(): Animation {
        return TranslateAnimation(
            Animation.RELATIVE_TO_PARENT, 1f,
            Animation.RELATIVE_TO_PARENT, 0f,
            Animation.RELATIVE_TO_PARENT, 0f,
            Animation.RELATIVE_TO_PARENT, 0f
        ).apply {
            duration = 250
            interpolator = AccelerateDecelerateInterpolator()
        }
    }

    private fun createSlideOutToLeft(): Animation {
        return TranslateAnimation(
            Animation.RELATIVE_TO_PARENT, 0f,
            Animation.RELATIVE_TO_PARENT, -1f,
            Animation.RELATIVE_TO_PARENT, 0f,
            Animation.RELATIVE_TO_PARENT, 0f
        ).apply {
            duration = 250
            interpolator = AccelerateDecelerateInterpolator()
        }
    }

    private fun createSlideInFromLeft(): Animation {
        return TranslateAnimation(
            Animation.RELATIVE_TO_PARENT, -1f,
            Animation.RELATIVE_TO_PARENT, 0f,
            Animation.RELATIVE_TO_PARENT, 0f,
            Animation.RELATIVE_TO_PARENT, 0f
        ).apply {
            duration = 250
            interpolator = AccelerateDecelerateInterpolator()
        }
    }

    private fun createSlideOutToRight(): Animation {
        return TranslateAnimation(
            Animation.RELATIVE_TO_PARENT, 0f,
            Animation.RELATIVE_TO_PARENT, 1f,
            Animation.RELATIVE_TO_PARENT, 0f,
            Animation.RELATIVE_TO_PARENT, 0f
        ).apply {
            duration = 250
            interpolator = AccelerateDecelerateInterpolator()
        }
    }
}