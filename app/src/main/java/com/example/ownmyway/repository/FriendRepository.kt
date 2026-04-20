package com.example.ownmyway.repository

import com.example.ownmyway.model.Friendship
import com.example.ownmyway.model.UserDetail
import com.example.ownmyway.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FriendRepository {

    // 1. Checa se há pedidos pendentes (Para a bolinha vermelha)
    suspend fun checkPendingRequests(myId: String): Boolean = withContext(Dispatchers.IO) {
        val requests = SupabaseClient.client.postgrest["friendships"]
            .select {
                filter {
                    eq("receiver_id", myId)
                    eq("status", "pending")
                }
            }.decodeList<Friendship>()
        return@withContext requests.isNotEmpty()
    }

    // 2. Traz a lista de quem me enviou convite
    suspend fun getIncomingRequests(): List<Friendship> = withContext(Dispatchers.IO) {
        val myId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: return@withContext emptyList()
        SupabaseClient.client.postgrest["friendships"]
            .select {
                filter {
                    eq("receiver_id", myId)
                    eq("status", "pending")
                }
            }.decodeList<Friendship>()
    }

    // 3. Busca usuários pelo nome para enviar convite
    suspend fun searchUsers(query: String): List<UserDetail> = withContext(Dispatchers.IO) {
        val myId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: return@withContext emptyList()
        SupabaseClient.client.postgrest["profiles"]
            .select {
                filter {
                    ilike("full_name", "%$query%")
                    neq("id", myId)
                }
            }.decodeList<UserDetail>()
    }

    // 4. Envia o pedido de amizade
    suspend fun sendFriendRequest(targetId: String) = withContext(Dispatchers.IO) {
        val myId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: throw Exception("Não autenticado")
        val request = Friendship(sender_id = myId, receiver_id = targetId, status = "pending")
        SupabaseClient.client.postgrest["friendships"].insert(request)
    }

    // 5. Aceita ou Recusa o pedido
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