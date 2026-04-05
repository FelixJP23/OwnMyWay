package com.example.ownmyway.network

import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val id: String,
    val full_name: String? = null,
    val onboarding_completed: Boolean = false,
    val budget_level: String? = null,
    val travel_pace: String? = null,
    val interests: List<String>? = null,
    val preferred_transport: String? = null
)
