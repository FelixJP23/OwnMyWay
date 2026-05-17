package com.example.ownmyway

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

class UpdatePasswordActivity : AppCompatActivity() {

    companion object {
        private const val RESET_CALLBACK_SCHEME = "ownmyway"
        private const val RESET_CALLBACK_HOST = "reset-password"
    }

    private var isTokenValidated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_update_password)

        // Captura o link caso a Activity seja criada do zero pelo clique no e-mail
        handleResetCallback(intent)

        // Salva a nova senha
        findViewById<Button>(R.id.btnUpdatePassword).setOnClickListener {
            val newPassword = findViewById<EditText>(R.id.etNewPassword).text.toString()
            if (newPassword.length >= 6) {
                updateUserPassword(newPassword)
            } else {
                Toast.makeText(this, "A senha deve conter no mínimo 6 caracteres.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Captura o link se a Activity já estiver aberta/em background
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleResetCallback(intent)
    }

    private fun handleResetCallback(intent: Intent?) {
        val data = intent?.data ?: return
        val isResetLink = data.scheme == RESET_CALLBACK_SCHEME && data.host == RESET_CALLBACK_HOST

        if (isResetLink) {
            lifecycleScope.launch {
                try {
                    // 1. Pega tudo que está depois do '#' na URL
                    val fragment = data.fragment

                    if (fragment != null) {
                        // 2. Converte o texto "access_token=123&refresh_token=456" em variáveis separadas
                        val params = fragment.split("&").associate {
                            val parts = it.split("=")
                            parts[0] to if (parts.size > 1) parts[1] else ""
                        }

                        val accessToken = params["access_token"]
                        val refreshToken = params["refresh_token"]

                        if (accessToken != null && refreshToken != null) {
                            // 3. Importa a sessão manualmente para o cliente Supabase
                            SupabaseClient.client.auth.importAuthToken(
                                accessToken = accessToken,
                                refreshToken = refreshToken
                            )

                            isTokenValidated = true
                            Toast.makeText(this@UpdatePasswordActivity, "Link validado! Digite sua nova senha.", Toast.LENGTH_LONG).show()
                        } else {
                            throw Exception("Tokens de acesso não encontrados no link.")
                        }
                    } else {
                        throw Exception("O link não contém as credenciais de segurança.")
                    }
                } catch (e: Exception) {
                    Log.e("ResetTokenError", "Erro ao processar token: ${e.message}")
                    Toast.makeText(this@UpdatePasswordActivity, "Link inválido ou expirado.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun updateUserPassword(newPassword: String) {
        if (!isTokenValidated) {
            Toast.makeText(this, "Ação não autorizada ou token expirado.", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                // Atualiza a senha na sessão temporária injetada pelo Deep Link
                SupabaseClient.client.auth.updateUser {
                    password = newPassword
                }

                // Desconecta o usuário por segurança após trocar a senha
                SupabaseClient.client.auth.signOut()

                Toast.makeText(this@UpdatePasswordActivity, "Senha alterada com sucesso!", Toast.LENGTH_SHORT).show()

                // Limpa a pilha e envia o usuário de volta para o Login
                val intent = Intent(this@UpdatePasswordActivity, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                Log.e("UpdatePasswordError", "Erro ao salvar senha: ${e.message}")
                Toast.makeText(this@UpdatePasswordActivity, "Erro ao atualizar: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}