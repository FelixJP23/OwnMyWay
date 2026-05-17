package com.example.ownmyway

import android.content.Context
import com.example.ownmyway.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.storage.Storage

object SupabaseClient {
    lateinit var client: io.github.jan.supabase.SupabaseClient

    fun init(context: Context) {
        if (::client.isInitialized) return

        client = createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_KEY
        ) {
            install(Postgrest)

            install(Auth) {
                // Instancia o gerenciador customizado usando as dependências que já funcionam
                sessionManager = SharedPreferencesSessionManager(context)
            }

            install(Storage)
        }
    }
}