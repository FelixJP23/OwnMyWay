package com.example.ownmyway

import android.content.Context
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SharedPreferencesSessionManager(context: Context) : SessionManager {

    private val sharedPreferences = context.getSharedPreferences("ownmyway_sessions", Context.MODE_PRIVATE)

    // Salva a sessão no armazenamento local sempre que o usuário faz login
    override suspend fun saveSession(session: UserSession) {
        val sessionJson = Json.encodeToString(session)
        sharedPreferences.edit().putString("current_session", sessionJson).apply()
    }

    // Carrega a sessão salva quando o app abre
    override suspend fun loadSession(): UserSession? {
        val sessionJson = sharedPreferences.getString("current_session", null) ?: return null
        return try {
            Json.decodeFromString<UserSession>(sessionJson)
        } catch (e: Exception) {
            null
        }
    }

    // Limpa os dados do aparelho quando o usuário clica em "Sair" (Logout)
    override suspend fun deleteSession() {
        sharedPreferences.edit().remove("current_session").apply()
    }
}