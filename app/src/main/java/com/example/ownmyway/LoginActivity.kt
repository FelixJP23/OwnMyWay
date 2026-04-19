package com.example.ownmyway

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. REMOVE A TARJA PRETA (ActionBar)
        supportActionBar?.hide()

        setContentView(R.layout.activity_login)

        // 2. Inicialização das Views
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val imgLogo = findViewById<View>(R.id.logoImage)
        val editEmail = findViewById<EditText>(R.id.editEmail)
        val editPassword = findViewById<EditText>(R.id.editPassword)
        val txtForgotPassword = findViewById<TextView>(R.id.txtForgotPassword)
        val btnEnter = findViewById<Button>(R.id.btnEnter)
        // INSERÇÃO AQUI: Vincular o novo TextView
        val txtRegisterLink = findViewById<TextView>(R.id.txtRegisterLink)

        // 3. Configuração do Estado Inicial
        btnBack.alpha = 1f

        // INSERÇÃO AQUI: Adicionar 'txtRegisterLink' na lista para começar invisível
        val viewsToAnimate = listOf(imgLogo, editEmail, editPassword, txtForgotPassword, btnEnter, txtRegisterLink)
        viewsToAnimate.forEach { view ->
            view.alpha = 0f
            view.translationY = 40f
        }

        // 4. Início das Animações
        animateViewIn(imgLogo, 100)
        animateViewIn(editEmail, 200)
        animateViewIn(editPassword, 300)
        animateViewIn(txtForgotPassword, 400)
        animateViewIn(btnEnter, 500)
        // INSERÇÃO AQUI: Animá-lo por último na cascata
        animateViewIn(txtRegisterLink, 600)

        // 5. Lógica do Botão de Voltar
        btnBack.setOnClickListener {
            val intent = Intent(this, SplashActivity::class.java)
            intent.putExtra("SKIP_ANIMATION", true)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        // 6. Lógica de Login
        btnEnter.setOnClickListener {
            val email = editEmail.text.toString()
            val password = editPassword.text.toString()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                loginUser(email, password)
            } else {
                Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show()
            }
        }

        // INSERÇÃO AQUI: Lógica para abrir a tela de Registro
        txtRegisterLink.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

        // 8. Lógica de Esqueci minha Senha
        txtForgotPassword.setOnClickListener {
            val email = editEmail.text.toString().trim()
            if (email.isNotEmpty()) {
                resetPassword(email)
            } else {
                Toast.makeText(this, "Digite seu e-mail para recuperar a senha", Toast.LENGTH_SHORT).show()
                editEmail.requestFocus()
            }
        }
    }

    private fun animateViewIn(view: View, delay: Long) {
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(500)
            .setStartDelay(delay)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun loginUser(emailText: String, passwordText: String) {
        lifecycleScope.launch {
            try {
                SupabaseClient.client.auth.signInWith(Email) {
                    email = emailText
                    password = passwordText
                }
                Toast.makeText(this@LoginActivity, "Bem-vindo!", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@LoginActivity, "E-mail ou senha inválidos", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun resetPassword(emailText: String) {
        lifecycleScope.launch {
            try {
                // Solicitação de redefinição para o Supabase
                SupabaseClient.client.auth.resetPasswordForEmail(emailText)
                Toast.makeText(this@LoginActivity, "E-mail de recuperação enviado!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@LoginActivity, "Erro ao enviar e-mail", Toast.LENGTH_SHORT).show()
            }
        }
    }
}