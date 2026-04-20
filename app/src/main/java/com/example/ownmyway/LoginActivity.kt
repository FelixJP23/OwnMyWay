package com.example.ownmyway

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.CheckBox
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
        supportActionBar?.hide()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val cbRememberMe = findViewById<CheckBox>(R.id.cbRememberMe)

        // 1. Botão de Voltar (Requisito da Task)
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            val intent = Intent(this, SplashActivity::class.java)

            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP

            startActivity(intent)
            finish()
        }

        // 2. Botão de Login com animação simples
        findViewById<Button>(R.id.btnLogin).setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                performLogin(email, password, cbRememberMe.isChecked)
            } else {
                Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show()
            }
        }

        // 3. Esqueci a Senha (Requisito da Task)
        findViewById<TextView>(R.id.tvForgotPassword).setOnClickListener {
            val email = etEmail.text.toString().trim()
            if (email.isNotEmpty()) {
                resetPassword(email)
            } else {
                Toast.makeText(this, "Digite seu e-mail acima primeiro", Toast.LENGTH_SHORT).show()
            }
        }

        // 4. Não tem uma conta? (Requisito da Task)
        findViewById<TextView>(R.id.tvRegisterLink).setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }

    private fun performLogin(email: String, pass: String, remember: Boolean) {
        lifecycleScope.launch {
            try {
                // Autenticação no Supabase
                SupabaseClient.client.auth.signInWith(Email) {
                    this.email = email
                    this.password = pass
                }

                // Lógica de "Manter Conectado"
                // O Supabase já mantém a sessão, mas aqui você pode salvar
                // uma flag se desejar um controle manual mais rígido.
                if (remember) {
                    Log.d("Login", "Usuário optou por manter conectado")
                }

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
                SupabaseClient.client.auth.resetPasswordForEmail(email)
                Toast.makeText(this@LoginActivity, "E-mail de recuperação enviado!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@LoginActivity, "Erro ao enviar e-mail", Toast.LENGTH_SHORT).show()
            }
        }
    }
}