package com.example.ownmyway.model

/**
 * Classe auxiliar para vincular um pedido de amizade aos detalhes
 * visuais do remetente (nome e foto).
 */
data class IncomingRequestDetail(
    val friendship: Friendship,
    val senderDetail: UserDetail
)