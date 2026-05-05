package com.example.ownmyway

import android.content.Intent

import android.os.Bundle

import android.widget.Button

import android.widget.Toast

import androidx.appcompat.app.AppCompatActivity

import com.google.android.material.textfield.TextInputEditText

import androidx.lifecycle.lifecycleScope

import kotlinx.coroutines.launch
import io.github.jan.supabase.auth.auth
class UpdatePasswordActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_update_password)

        val btnSave = findViewById<Button>(R.id.btnSaveNewPassword)
        val etNewPassword = findViewById<TextInputEditText>(R.id.etNewPassword)

        btnSave.setOnClickListener {
            val newPassword = etNewPassword.text.toString()

            lifecycleScope.launch {
                try {
                    // O Supabase identifica o usuário automaticamente pelo token que veio no link
                    SupabaseClient.client.auth.updateUser {
                        password = newPassword
                    }
                    Toast.makeText(this@UpdatePasswordActivity, "Senha alterada com sucesso!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@UpdatePasswordActivity, LoginActivity::class.java))
                    finish()
                } catch (e: Exception) {
                    Toast.makeText(this@UpdatePasswordActivity, "Erro ao atualizar: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}