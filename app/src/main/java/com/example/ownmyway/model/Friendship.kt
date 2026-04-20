package com.example.ownmyway.model

import kotlinx.serialization.Serializable

@Serializable
data class Friendship(
    val id: String? = null,
    val sender_id: String,
    val receiver_id: String,
    val status: String = "pending",
    val created_at: String? = null,
)