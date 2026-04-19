package com.example.ownmyway.repository

import com.example.ownmyway.SupabaseClient
import com.example.ownmyway.model.Friendship
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.auth.auth

object FriendRepository {

    suspend fun sendFriendRequest(targetUserId: String) {
        // Usa o cliente que você configurou no SupabaseClient.kt
        val myId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: return

        val request = Friendship(
            sender_id = myId,
            receiver_id = targetUserId
        )

        // Insere na tabela 'friendships' que criamos no banco
        SupabaseClient.client.postgrest["friendships"].insert(request)
    }
}