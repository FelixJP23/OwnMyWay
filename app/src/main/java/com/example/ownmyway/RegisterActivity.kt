package com.example.ownmyway

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ownmyway.network.UserProfile
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    private lateinit var etName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var btnRegister: Button
    private lateinit var tvLogin: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_register)

        etName = findViewById(R.id.etName)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        btnRegister = findViewById(R.id.btnRegister)
        tvLogin = findViewById(R.id.tvLogin)

        btnRegister.setOnClickListener { registerUser() }
        tvLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun registerUser() {
        val name = etName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()
        val confirmPassword = etConfirmPassword.text.toString()

        when {
            name.isEmpty() -> {
                etName.error = "Digite seu nome"
                etName.requestFocus()
                return
            }
            email.isEmpty() -> {
                etEmail.error = "Digite seu e-mail"
                etEmail.requestFocus()
                return
            }
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                etEmail.error = "E-mail inválido"
                etEmail.requestFocus()
                return
            }
            password.length < 6 -> {
                etPassword.error = "A senha deve ter pelo menos 6 caracteres"
                etPassword.requestFocus()
                return
            }
            password != confirmPassword -> {
                etConfirmPassword.error = "As senhas não coincidem"
                etConfirmPassword.requestFocus()
                return
            }
        }

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
                Toast.makeText(
                    this@RegisterActivity,
                    "Erro ao criar conta: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        btnRegister.isEnabled = !isLoading
        btnRegister.text = if (isLoading) "Criando..." else "Criar conta"
    }
}