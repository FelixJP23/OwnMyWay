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

    companion object {
        private const val PREFS_REGISTER = "register_prefs"
        private const val KEY_PENDING_NAME = "pending_name"
        private const val KEY_PENDING_EMAIL = "pending_email"
        private const val KEY_PENDING_PASSWORD = "pending_password"

        private const val REGISTER_CALLBACK_SCHEME = "ownmyway"
        private const val REGISTER_CALLBACK_HOST = "register-callback"
    }

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
    private lateinit var tvVerificationEmail: TextView
    private lateinit var tvVerificationStatus: TextView

    private lateinit var btnBack: Button
    private lateinit var btnNext: Button

    private lateinit var etName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText

    private lateinit var fields: List<EditText>

    private val verificationStepIndex = 4

    private var currentStep = 0
    private var emailAvailabilityState = EmailAvailabilityState.IDLE
    private var emailCheckJob: Job? = null
    private var verificationReady = false

    private val stepTitles = listOf(
        "Qual é o seu nome?",
        "Agora digite seu e-mail",
        "Crie sua senha",
        "Confirme sua senha",
        "Estamos quase finalizando!"
    )

    private val stepHints = listOf(
        "",
        "Esse e-mail será usado para entrar no aplicativo.",
        "Use pelo menos 6 caracteres. Senhas com caracteres especiais ficam mais fortes.",
        "Digite novamente a senha criada.",
        "Te mandamos um email de verificação, cheque seu email para finalizar o cadastro"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_register)

        bindViews()
        restorePendingRegistration()
        setupListeners()
        updateStepUi()
        updatePasswordStrengthUi()
        handleAuthCallback(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthCallback(intent)
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
        tvVerificationEmail = findViewById(R.id.tvVerificationEmail)
        tvVerificationStatus = findViewById(R.id.tvVerificationStatus)

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
            when (currentStep) {
                1, 2, 3, verificationStepIndex -> showStep(currentStep - 1)
            }
        }

        btnNext.setOnClickListener {
            when (currentStep) {
                0, 1, 2 -> advanceToNextStep()
                3 -> signUpPendingUser()
                verificationStepIndex -> {
                    if (verificationReady) {
                        continueAfterVerification()
                    } else {
                        checkIfEmailWasConfirmed()
                    }
                }
            }
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
                        when (currentStep) {
                            0, 1, 2 -> advanceToNextStep()
                            3 -> signUpPendingUser()
                        }
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
                    when (currentStep) {
                        0 -> finish()
                        1, 2, 3, verificationStepIndex -> showStep(currentStep - 1)
                    }
                }
            }
        )
    }

    private fun savePendingRegistration(name: String, email: String, password: String) {
        getSharedPreferences(PREFS_REGISTER, MODE_PRIVATE)
            .edit()
            .putString(KEY_PENDING_NAME, name)
            .putString(KEY_PENDING_EMAIL, email)
            .putString(KEY_PENDING_PASSWORD, password)
            .apply()
    }

    private fun restorePendingRegistration() {
        val prefs = getSharedPreferences(PREFS_REGISTER, MODE_PRIVATE)

        etName.setText(prefs.getString(KEY_PENDING_NAME, etName.text.toString()))
        etEmail.setText(prefs.getString(KEY_PENDING_EMAIL, etEmail.text.toString()))
        etPassword.setText(prefs.getString(KEY_PENDING_PASSWORD, etPassword.text.toString()))
        etConfirmPassword.setText(prefs.getString(KEY_PENDING_PASSWORD, etConfirmPassword.text.toString()))
    }

    private fun clearPendingRegistration() {
        getSharedPreferences(PREFS_REGISTER, MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    private fun advanceToNextStep() {
        if (!validateCurrentStep(showError = true)) return
        showStep(currentStep + 1)
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

        tvProgressCount.text = "${currentStep + 1} de 5"
        progressRegister.max = 5
        progressRegister.progress = currentStep + 1

        tvIntro.visibility = if (currentStep == 0) View.VISIBLE else View.GONE

        when (currentStep) {
            0 -> {
                tvStepTitle.text = "Qual é o seu nome?"
                tvStepHint.visibility = View.GONE
            }

            1 -> {
                tvStepTitle.text = "Agora digite seu e-mail"
                tvStepHint.visibility = View.VISIBLE
                tvStepHint.text = "Esse e-mail será usado para entrar no aplicativo."
            }

            2 -> {
                tvStepTitle.text = "Crie sua senha"
                tvStepHint.visibility = View.VISIBLE
                tvStepHint.text = "Use pelo menos 6 caracteres. Senhas com caracteres especiais ficam mais fortes."
            }

            3 -> {
                tvStepTitle.text = "Confirme sua senha"
                tvStepHint.visibility = View.VISIBLE
                tvStepHint.text = "Digite novamente a senha criada."
            }

            verificationStepIndex -> {
                tvVerificationEmail.text = etEmail.text.toString().trim()

                if (verificationReady) {
                    tvStepTitle.text = "Email confirmado, pronto para começar?"
                    tvStepHint.visibility = View.GONE
                    tvVerificationStatus.visibility = View.VISIBLE
                    tvVerificationStatus.text = "Sua conta foi confirmada com sucesso."
                    btnNext.text = "Vamos lá"
                } else {
                    tvStepTitle.text = "Estamos quase finalizando!"
                    tvStepHint.visibility = View.VISIBLE
                    tvStepHint.text =
                        "Te mandamos um email de verificação, cheque seu email para finalizar o cadastro"
                    tvVerificationStatus.visibility = View.GONE
                    btnNext.text = "Verificar"
                }
            }
        }

        val showBack = currentStep in 1..verificationStepIndex
        btnBack.visibility = if (showBack) View.VISIBLE else View.INVISIBLE
        btnBack.isEnabled = showBack

        if (currentStep != verificationStepIndex) {
            btnNext.text = if (currentStep == 3) "Concluir" else "Próximo"
        }

        if (currentStep != 1) {
            tvEmailAvailability.visibility = View.GONE
        }

        updateButtonsState()
        updatePasswordStrengthUi()

        if (currentStep < fields.size) {
            fields[currentStep].requestFocus()
        }
    }

    private fun updateButtonsState() {
        btnNext.isEnabled = when (currentStep) {
            0, 1, 2, 3 -> validateCurrentStep(showError = false)
            verificationStepIndex -> true
            else -> false
        }
        btnNext.alpha = if (btnNext.isEnabled) 1f else 0.45f
    }

    private fun validateCurrentStep(showError: Boolean): Boolean {
        return when (currentStep) {
            0 -> validateName(showError)
            1 -> validateEmail(showError)
            2 -> validatePassword(showError)
            3 -> validateConfirmPassword(showError)
            else -> true
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

            val available = callBooleanRpc("is_email_available", email)

            setEmailAvailabilityState(
                when (available) {
                    true -> EmailAvailabilityState.AVAILABLE
                    false -> EmailAvailabilityState.TAKEN
                    null -> EmailAvailabilityState.ERROR
                }
            )
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

    private fun signUpPendingUser() {
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

        savePendingRegistration(name, email, password)

        setLoading(true)

        lifecycleScope.launch {
            try {
                SupabaseClient.client.auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                }

                verificationReady = false
                setLoading(false)
                showStep(verificationStepIndex)

                Toast.makeText(
                    this@RegisterActivity,
                    "Enviamos um email de verificação.",
                    Toast.LENGTH_SHORT
                ).show()
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
                        "Erro ao enviar email de verificação: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun handleAuthCallback(intent: Intent?) {
        val data = intent?.data ?: return

        val isRegisterCallback =
            data.scheme == REGISTER_CALLBACK_SCHEME &&
                    data.host == REGISTER_CALLBACK_HOST

        if (!isRegisterCallback) return

        lifecycleScope.launch {
            restorePendingRegistration()

            val email = etEmail.text.toString().trim()
            if (email.isBlank()) return@launch

            val confirmed = callBooleanRpc(
                functionName = "is_email_confirmed",
                email = email
            ) == true

            if (confirmed) {
                verificationReady = true
                showStep(verificationStepIndex)
                updateStepUi()

                Toast.makeText(
                    this@RegisterActivity,
                    "Email confirmado com sucesso!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun checkIfEmailWasConfirmed() {
        val email = etEmail.text.toString().trim()

        setLoading(true)

        lifecycleScope.launch {
            val confirmed = callBooleanRpc(
                functionName = "is_email_confirmed",
                email = email
            ) == true

            setLoading(false)

            if (confirmed) {
                verificationReady = true
                updateStepUi()
            } else {
                Toast.makeText(
                    this@RegisterActivity,
                    "Seu email ainda não foi confirmado. Abra o link enviado e tente novamente.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun continueAfterVerification() {
        val name = etName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()

        setLoading(true)

        lifecycleScope.launch {
            try {
                if (SupabaseClient.client.auth.currentUserOrNull() == null) {
                    SupabaseClient.client.auth.signInWith(Email) {
                        this.email = email
                        this.password = password
                    }
                }

                val user = SupabaseClient.client.auth.currentUserOrNull()
                if (user == null) {
                    setLoading(false)
                    Toast.makeText(
                        this@RegisterActivity,
                        "Não foi possível iniciar sua sessão agora.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                ensureUserProfile(user.id, name)
                clearPendingRegistration()

                setLoading(false)

                startActivity(Intent(this@RegisterActivity, OnboardingActivity::class.java))
                finish()
            } catch (e: Exception) {
                setLoading(false)
                Toast.makeText(
                    this@RegisterActivity,
                    "Erro ao continuar: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private suspend fun ensureUserProfile(userId: String, name: String) {
        val existingProfiles = SupabaseClient.client.postgrest["profiles"]
            .select {
                filter { eq("id", userId) }
            }
            .decodeList<UserProfile>()

        if (existingProfiles.isEmpty()) {
            SupabaseClient.client.postgrest["profiles"].insert(
                UserProfile(
                    id = userId,
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
                filter { eq("id", userId) }
            }
        }
    }

    private suspend fun callBooleanRpc(functionName: String, email: String): Boolean? {
        return try {
            val result = SupabaseClient.client.postgrest.rpc(
                functionName,
                buildJsonObject {
                    put("p_email", email)
                }
            )

            result.data.trim()
                .removePrefix("\"")
                .removeSuffix("\"")
                .toBooleanStrictOrNull()
        } catch (_: Exception) {
            null
        }
    }

    private fun setLoading(isLoading: Boolean) {
        val showBack = currentStep in 1..verificationStepIndex

        btnBack.isEnabled = !isLoading && showBack
        btnBack.alpha = if (btnBack.isEnabled || !showBack) 1f else 0.45f

        btnNext.isEnabled = if (isLoading) {
            false
        } else {
            when (currentStep) {
                0, 1, 2, 3 -> validateCurrentStep(showError = false)
                verificationStepIndex -> true
                else -> false
            }
        }

        btnNext.alpha = if (btnNext.isEnabled) 1f else 0.45f

        btnNext.text = if (isLoading) {
            when (currentStep) {
                3 -> "Enviando..."
                verificationStepIndex -> {
                    if (verificationReady) "Abrindo..." else "Verificando..."
                }
                else -> "Carregando..."
            }
        } else {
            when (currentStep) {
                3 -> "Concluir"
                verificationStepIndex -> {
                    if (verificationReady) "Vamos lá" else "Verificar"
                }
                else -> "Próximo"
            }
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