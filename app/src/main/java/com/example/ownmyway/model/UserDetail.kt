package com.example.ownmyway.model

import kotlinx.serialization.Serializable
@Serializable
data class UserDetail(
    val id: String,
    val full_name: String? = null,
    val email: String? = null,
    val avatar_url: String? = null,
    val username: String? = null
)