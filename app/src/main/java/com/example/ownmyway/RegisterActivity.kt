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

        btnRegister.setOnClickListener {
            registerUser()
        }

        tvLogin.setOnClickListener {
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

        btnRegister.isEnabled = false
        btnRegister.text = "Criando..."

        lifecycleScope.launch {
            try {
                // 1) Cria no auth.users
                SupabaseClient.client.auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                }

                // 2) Pega o usuário autenticado/criado
                val user = SupabaseClient.client.auth.currentUserOrNull()

                if (user == null) {
                    btnRegister.isEnabled = true
                    btnRegister.text = "Entrar"
                    Toast.makeText(
                        this@RegisterActivity,
                        "Usuário criado, mas não foi possível recuperar a sessão. Verifique a confirmação por e-mail no Supabase.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                // 3) Cria a linha na tabela profiles com o MESMO id do auth.users.id
                val profile = UserProfile(
                    id = user.id,
                    full_name = name,
                    onboarding_completed = false,
                    budget_level = null,
                    travel_pace = null,
                    interests = emptyList(),
                    preferred_transport = null
                )

                SupabaseClient.client.postgrest["profiles"].insert(profile)

                Toast.makeText(
                    this@RegisterActivity,
                    "Conta criada com sucesso!",
                    Toast.LENGTH_SHORT
                ).show()

                // 4) Vai para onboarding
                startActivity(Intent(this@RegisterActivity, OnboardingActivity::class.java))
                finish()

            } catch (e: Exception) {
                btnRegister.isEnabled = true
                btnRegister.text = "Entrar"

                Toast.makeText(
                    this@RegisterActivity,
                    "Erro ao criar conta: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}