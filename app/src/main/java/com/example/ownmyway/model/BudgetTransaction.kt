package com.example.ownmyway.model

import kotlinx.serialization.Serializable

@Serializable
data class BudgetTransaction(
    val amount: Double,
    val isAddition: Boolean,
    val description: String,
    val timestamp: Long = System.currentTimeMillis()
)
