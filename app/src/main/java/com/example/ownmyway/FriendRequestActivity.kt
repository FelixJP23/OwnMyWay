package com.example.ownmyway

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ownmyway.repository.FriendRepository
import kotlinx.coroutines.launch

class FriendRequestActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_friend_request)

        val senderId = intent.getStringExtra("SENDER_ID")
        val cardDecision = findViewById<CardView>(R.id.cardDecision)

        // 1. Lógica para quando vem de uma notificação
        if (!senderId.isNullOrEmpty()) {
            cardDecision.visibility = View.VISIBLE
            setupDecisionButtons(senderId)
        } else {
            cardDecision.visibility = View.GONE
        }

        // 2. Configurar Lista de Usuários Disponíveis
        setupUserList()
    }

    private fun setupDecisionButtons(senderId: String) {
        findViewById<Button>(R.id.btnAccept).setOnClickListener {
            handleResponse(senderId, true)
        }
        findViewById<Button>(R.id.btnDecline).setOnClickListener {
            handleResponse(senderId, false)
        }
    }

    private fun handleResponse(senderId: String, accept: Boolean) {
        lifecycleScope.launch {
            try {
                FriendRepository.respondToFriendRequest(senderId, accept)
                Toast.makeText(this@FriendRequestActivity, "Sucesso!", Toast.LENGTH_SHORT).show()
                finish() // Volta para o mapa
            } catch (e: Exception) {
                Log.e("FriendRequest", "Erro ao responder: ${e.message}")
            }
        }
    }

    private fun setupUserList() {
        val rv = findViewById<RecyclerView>(R.id.rvAvailableUsers)
        rv.layoutManager = LinearLayoutManager(this)

        lifecycleScope.launch {
            try {
                val users = FriendRepository.getAllUsers()
                // Nota: Você precisará criar a classe UserAdapter
                rv.adapter = UserAdapter(users) { selectedUser ->
                    sendNewRequest(selectedUser.id)
                }
            } catch (e: Exception) {
                Log.e("FriendRequest", "Erro ao carregar lista: ${e.message}")
            }
        }
    }

    private fun sendNewRequest(userId: String) {
        lifecycleScope.launch {
            try {
                FriendRepository.sendFriendRequest(userId)
                Toast.makeText(this@FriendRequestActivity, "Convite enviado!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@FriendRequestActivity, "Falha ao enviar convite", Toast.LENGTH_SHORT).show()
            }
        }
    }
}