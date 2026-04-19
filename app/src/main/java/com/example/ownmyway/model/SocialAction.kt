package com.example.ownmyway.model

data class SocialAction(
    val friendName: String,
    val actionText: String,
    val destination: String,
    val timeAgo: String,
    val profileImageRes: Int // Usaremos ícones padrão por enquanto
)
