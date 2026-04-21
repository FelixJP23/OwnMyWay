package com.example.ownmyway.repository

import android.util.Log
import com.example.ownmyway.model.Friendship
import com.example.ownmyway.model.UserDetail
import com.example.ownmyway.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FriendRepository {

    // 1. Checa se há pedidos pendentes (Para a bolinha vermelha na Main)
    suspend fun checkPendingRequests(myId: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val requests = SupabaseClient.client.postgrest["friendships"]
                .select {
                    filter {
                        eq("receiver_id", myId)
                        eq("status", "pending")
                    }
                }.decodeList<Friendship>()
            requests.isNotEmpty()
        } catch (e: Exception) {
            Log.e("Repository", "Erro checkPending: ${e.message}")
            false
        }
    }

    // 2. Traz a lista de pedidos recebidos para a tela de gerenciamento
    suspend fun getIncomingRequests(): List<Friendship> = withContext(Dispatchers.IO) {
        val myId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: return@withContext emptyList()

        Log.d("FRIEND_DEBUG", "Meu ID autenticado: $myId")
        return@withContext try {
            SupabaseClient.client.postgrest["friendships"]
                .select {
                    filter {
                        eq("receiver_id", myId)
                        eq("status", "pending")
                    }
                }.decodeList<Friendship>()
        } catch (e: Exception) {
            Log.e("Repository", "Erro getIncoming: ${e.message}")
            emptyList()
        }
    }

    // 3. Busca usuários para enviar novo convite
    suspend fun searchUsers(query: String): List<UserDetail> = withContext(Dispatchers.IO) {
        val myId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: return@withContext emptyList()
        return@withContext try {
            SupabaseClient.client.postgrest["profiles"]
                .select {
                    filter {
                        ilike("full_name", "%$query%")
                        neq("id", myId)
                    }
                }.decodeList<UserDetail>()
        } catch (e: Exception) {
            Log.e("Repository", "Erro searchUsers: ${e.message}")
            emptyList()
        }
    }

    // 4. Envia o pedido de amizade
    suspend fun sendFriendRequest(targetId: String) = withContext(Dispatchers.IO) {
        val myId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: throw Exception("Não autenticado")
        val request = Friendship(sender_id = myId, receiver_id = targetId, status = "pending")
        SupabaseClient.client.postgrest["friendships"].insert(request)
    }

    // 5. Aceita ou Recusa o pedido (Importante: filtra por quem enviou e quem recebeu)
    suspend fun respondToFriendRequest(senderId: String, accept: Boolean) = withContext(Dispatchers.IO) {
        val myId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: throw Exception("Não logado")
        val newStatus = if (accept) "accepted" else "rejected"

        SupabaseClient.client.postgrest["friendships"].update(
            { set("status", newStatus) }
        ) {
            filter {
                eq("sender_id", senderId)
                eq("receiver_id", myId)
            }
        }
    }
}