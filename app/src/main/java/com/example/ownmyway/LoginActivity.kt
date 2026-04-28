package com.example.ownmyway

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val currentSession = SupabaseClient.client.auth.currentSessionOrNull()
        if (currentSession != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        supportActionBar?.hide()
        setContentView(R.layout.activity_login)

        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)

        // Botão de Voltar
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            val intent = Intent(this, SplashActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        // Botão de Login (Removido o parâmetro do CheckBox)
        findViewById<Button>(R.id.btnLogin).setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                performLogin(email, password)
            } else {
                Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show()
            }
        }

        // Esqueci a Senha
        findViewById<TextView>(R.id.tvForgotPassword).setOnClickListener {
            val email = etEmail.text.toString().trim()
            if (email.isNotEmpty()) {
                resetPassword(email)
            } else {
                Toast.makeText(this, "Digite seu e-mail acima primeiro", Toast.LENGTH_SHORT).show()
            }
        }

        // Registrar
        findViewById<TextView>(R.id.tvRegisterLink).setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }

    private fun performLogin(email: String, pass: String) {
        lifecycleScope.launch {
            try {
                SupabaseClient.client.auth.signInWith(Email) {
                    this.email = email
                    this.password = pass
                }

                // O Supabase já persiste o token localmente por padrão.
                // Ao entrar aqui, a próxima vez que o app abrir, o 'currentSessionOrNull()' será positivo.

                Toast.makeText(this@LoginActivity, "Bem-vindo!", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                finish()
            } catch (e: Exception) {
                Log.e("LoginError", "Erro: ${e.message}")
                Toast.makeText(this@LoginActivity, "Falha no login: Senha ou e-mail incorretos", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun resetPassword(email: String) {
        lifecycleScope.launch {
            try {
                SupabaseClient.client.auth.resetPasswordForEmail(
                    email = email,
                    redirectUrl = "ownmyway://reset-password"
                )
                Toast.makeText(this@LoginActivity, "E-mail enviado! Verifique sua caixa de entrada.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                // Isso vai mostrar se o erro é 'User not found' ou 'Rate limit exceeded'
                val errorMessage = e.localizedMessage ?: "Erro desconhecido"
                Log.e("ResetError", errorMessage)
                Toast.makeText(this@LoginActivity, "Erro: $errorMessage", Toast.LENGTH_LONG).show()
            }
        }
    }
}