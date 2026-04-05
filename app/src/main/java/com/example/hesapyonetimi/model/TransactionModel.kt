package com.example.hesapyonetimi.model

data class TransactionModel(
    val title: String,
    val category: String,
    val amount: String,
    val isIncome: Boolean // Gelir mi Gider mi? (Renk belirlemek için)
)