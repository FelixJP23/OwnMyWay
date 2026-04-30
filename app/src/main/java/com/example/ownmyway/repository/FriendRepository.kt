package com.example.ownmyway.repository

import android.util.Log
import com.example.ownmyway.model.Friendship
import com.example.ownmyway.model.UserDetail
import com.example.ownmyway.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FriendRepository {

    // 1. Checa se há pedidos pendentes (Para o badge na MainActivity)
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

    // 2. Lista de pedidos recebidos (Ainda pendentes)
    suspend fun getIncomingRequests(): List<Friendship> = withContext(Dispatchers.IO) {
        val myId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: return@withContext emptyList()
        return@withContext try {
            SupabaseClient.client.postgrest["friendships"]
                .select {
                    filter {
                        eq("receiver_id", myId)
                        eq("status", "pending")
                    }
                }.decodeList<Friendship>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // 3. NOVO: Busca usuários pelo @handle (Identificador Único)
    suspend fun searchUsersByHandle(handle: String): List<UserDetail> = withContext(Dispatchers.IO) {
        val myId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: return@withContext emptyList()
        return@withContext try {
            SupabaseClient.client.postgrest["profiles"]
                .select {
                    filter {
                        // ilike ignora maiúsculas/minúsculas. Buscamos o handle exato.
                        ilike("handle", handle)
                        neq("id", myId) // Não mostrar a si mesmo
                    }
                }.decodeList<UserDetail>()
        } catch (e: Exception) {
            Log.e("Repository", "Erro searchByHandle: ${e.message}")
            emptyList()
        }
    }

    // 4. NOVO: Busca paginada (10 usuários por vez)
    suspend fun getUsersPaginated(limit: Int, offset: Int): List<UserDetail> = withContext(Dispatchers.IO) {
        val myId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: return@withContext emptyList()
        return@withContext try {
            SupabaseClient.client.postgrest["profiles"]
                .select {
                    filter { neq("id", myId) }
                    // range define o início e fim da busca (ex: 0 a 9, 10 a 19...)
                    range(offset.toLong(), (offset + limit - 1).toLong())
                }.decodeList<UserDetail>()
        } catch (e: Exception) {
            Log.e("Repository", "Erro paginação: ${e.message}")
            emptyList()
        }
    }

    // 5. NOVO: Traz a lista de perfis dos seus amigos (status = accepted)
    suspend fun getMyFriends(): List<UserDetail> = withContext(Dispatchers.IO) {
        val myId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: return@withContext emptyList()
        return@withContext try {
            // Passo A: Pega os IDs de quem é seu amigo na tabela friendships
            val friendships = SupabaseClient.client.postgrest["friendships"]
                .select {
                    filter {
                        eq("status", "accepted")
                        or {
                            eq("sender_id", myId)
                            eq("receiver_id", myId)
                        }
                    }
                }.decodeList<Friendship>()

            // Passo B: Extrai os IDs dos amigos (quem não sou eu)
            val friendIds = friendships.map {
                if (it.sender_id == myId) it.receiver_id else it.sender_id
            }

            if (friendIds.isEmpty()) return@withContext emptyList()

            // Passo C: Busca os detalhes (nomes) desses IDs na tabela profiles
            SupabaseClient.client.postgrest["profiles"]
                .select {
                    filter {
                        isIn("id", friendIds)
                    }
                }.decodeList<UserDetail>()
        } catch (e: Exception) {
            Log.e("Repository", "Erro getMyFriends: ${e.message}")
            emptyList()
        }
    }

    // 6. Envia o pedido de amizade
    suspend fun sendFriendRequest(targetId: String) = withContext(Dispatchers.IO) {
        val myId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: throw Exception("Não autenticado")
        val request = Friendship(sender_id = myId, receiver_id = targetId, status = "pending")
        SupabaseClient.client.postgrest["friendships"].insert(request)
    }

    // 7. Responde ao pedido
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

    suspend fun getMyProfile(): UserDetail? = withContext(Dispatchers.IO) {
        val myId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: return@withContext null
        return@withContext try {
            SupabaseClient.client.postgrest["profiles"]
                .select { filter { eq("id", myId) } }
                .decodeSingle<UserDetail>()
        } catch (e: Exception) {
            null
        }
    }
}