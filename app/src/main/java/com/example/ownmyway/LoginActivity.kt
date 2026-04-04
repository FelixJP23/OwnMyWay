package com.example.ownmyway

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val btnEnter = findViewById<Button>(R.id.btnEnter)

        btnEnter.setOnClickListener {
            Toast.makeText(this, "Fazendo login...", Toast.LENGTH_SHORT).show()
        }
    }
}
