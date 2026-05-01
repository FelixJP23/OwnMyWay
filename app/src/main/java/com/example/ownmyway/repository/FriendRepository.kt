package com.example.ownmyway.repository

import android.util.Log
import com.example.ownmyway.model.*
import com.example.ownmyway.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FriendRepository {

    // Busca pedidos pendentes com detalhes do perfil do remetente
    suspend fun getIncomingRequestsWithDetails(): List<IncomingRequestDetail> = withContext(Dispatchers.IO) {
        val myId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: return@withContext emptyList()
        return@withContext try {
            // 1. Busca amizades onde sou o receiver e o status é pending
            val requests = SupabaseClient.client.postgrest["friendships"]
                .select {
                    filter {
                        eq("receiver_id", myId)
                        eq("status", "pending")
                    }
                }.decodeList<Friendship>()

            if (requests.isEmpty()) return@withContext emptyList()

            // 2. Extrai IDs únicos dos remetentes
            val senderIds = requests.map { it.sender_id }.distinct()

            // 3. Busca os perfis desses remetentes
            val profiles = SupabaseClient.client.postgrest["profiles"]
                .select {
                    filter { isIn("id", senderIds) }
                }.decodeList<UserDetail>()

            // 4. Combina os dados
            requests.mapNotNull { req ->
                val profile = profiles.find { it.id == req.sender_id }
                if (profile != null) IncomingRequestDetail(req, profile) else null
            }
        } catch (e: Exception) {
            Log.e("Repository", "Erro getIncomingWithDetails: ${e.message}")
            emptyList()
        }
    }

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

    suspend fun checkPendingRequests(myId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val count = SupabaseClient.client.postgrest["friendships"]
                .select {
                    filter {
                        eq("receiver_id", myId)
                        eq("status", "pending")
                    }
                }.decodeList<Friendship>()
            count.isNotEmpty()
        } catch (e: Exception) { false }
    }

    suspend fun searchUsersByUsername(query: String): List<UserDetail> = withContext(Dispatchers.IO) {
        val myId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: return@withContext emptyList()
        try {
            SupabaseClient.client.postgrest["profiles"]
                .select {
                    filter {
                        // Busca parcial (contém o texto) ignorando maiúsculas/minúsculas
                        ilike("username", "%$query%")
                        neq("id", myId)
                    }
                }.decodeList<UserDetail>()
        } catch (e: Exception) {
            Log.e("Repository", "Erro na busca: ${e.message}")
            emptyList()
        }
    }
    suspend fun getUsersPaginated(limit: Int, offset: Int): List<UserDetail> = withContext(Dispatchers.IO) {
        val myId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: return@withContext emptyList()
        try {
            SupabaseClient.client.postgrest["profiles"]
                .select {
                    filter { neq("id", myId) }
                    range(offset.toLong(), (offset + limit - 1).toLong())
                }.decodeList<UserDetail>()
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getMyFriends(): List<UserDetail> = withContext(Dispatchers.IO) {
        val myId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: return@withContext emptyList()
        try {
            val friendships = SupabaseClient.client.postgrest["friendships"]
                .select {
                    filter {
                        eq("status", "accepted")
                        or { eq("sender_id", myId); eq("receiver_id", myId) }
                    }
                }.decodeList<Friendship>()
            val friendIds = friendships.map { if (it.sender_id == myId) it.receiver_id else it.sender_id }
            if (friendIds.isEmpty()) return@withContext emptyList()
            SupabaseClient.client.postgrest["profiles"].select { filter { isIn("id", friendIds) } }.decodeList<UserDetail>()
        } catch (e: Exception) { emptyList() }
    }

    suspend fun sendFriendRequest(targetId: String) = withContext(Dispatchers.IO) {
        val myId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: throw Exception("Não autenticado")
        val request = Friendship(sender_id = myId, receiver_id = targetId, status = "pending")
        SupabaseClient.client.postgrest["friendships"].insert(request)
    }

    suspend fun getMyProfile(): UserDetail? = withContext(Dispatchers.IO) {
        val myId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: return@withContext null
        try {
            SupabaseClient.client.postgrest["profiles"].select { filter { eq("id", myId) } }.decodeSingle<UserDetail>()
        } catch (e: Exception) { null }
    }
}