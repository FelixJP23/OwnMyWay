package com.example.ownmyway

import kotlinx.serialization.Serializable

@Serializable
data class SavedRoute(
    val id: String? = null,
    val user_id: String? = null,
    val name: String,
    val description: String? = null,
    val stops_json: String,
    val total_cost: Int = 0,
    val stop_count: Int = 0,
    val created_at: String? = null
)
