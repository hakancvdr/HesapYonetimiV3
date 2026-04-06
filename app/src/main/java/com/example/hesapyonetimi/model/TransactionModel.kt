package com.example.hesapyonetimi.model

data class TransactionModel(
    val id: Long = 0,
    val title: String,
    val category: String,
    val amount: String,
    val isIncome: Boolean,
    val time: String = "",
    val transaction: com.example.hesapyonetimi.domain.model.Transaction? = null
)
