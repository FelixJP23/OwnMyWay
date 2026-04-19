package com.example.ownmyway.repository

import com.example.ownmyway.SupabaseClient
import com.example.ownmyway.model.Friendship
import com.example.ownmyway.model.UserDetail
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FriendRepository {

    // Buscar todos os usuários cadastrados (exceto o logado)
    suspend fun getAllUsers(): List<UserDetail> = withContext(Dispatchers.IO) {
        val myId = SupabaseClient.client.auth.currentUserOrNull()?.id
        SupabaseClient.client.postgrest["profiles"]
            .select()
            .decodeList<UserDetail>()
            .filter { it.id != myId }
    }

    // Enviar pedido de amizade
    suspend fun sendFriendRequest(targetUserId: String) = withContext(Dispatchers.IO) {
        val myId = SupabaseClient.client.auth.currentUserOrNull()?.id
            ?: throw Exception("Usuário não logado")

        val friendship = Friendship(
            sender_id = myId,
            receiver_id = targetUserId,
            status = "pending"
        )

        SupabaseClient.client.postgrest["friendships"].insert(friendship)
    }

    // Responder (Aceitar/Recusar) pedido
    suspend fun respondToFriendRequest(senderId: String, accept: Boolean) = withContext(Dispatchers.IO) {
        val myId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: return@withContext

        if (accept) {
            SupabaseClient.client.postgrest["friendships"].update({
                set("status", "accepted")
            }) {
                filter {
                    eq("sender_id", senderId)
                    eq("receiver_id", myId)
                }
            }
        } else {
            SupabaseClient.client.postgrest["friendships"].delete {
                filter {
                    eq("sender_id", senderId)
                    eq("receiver_id", myId)
                }
            }
        }
    }
}