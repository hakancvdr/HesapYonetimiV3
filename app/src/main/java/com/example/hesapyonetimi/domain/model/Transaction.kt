package com.example.hesapyonetimi.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Transaction(
    val id: Long = 0,
    val amount: Double,
    val categoryId: Long,
    val categoryName: String = "",
    val categoryIcon: String = "",
    val categoryColor: String = "",
    val description: String,
    val date: Long,
    val isIncome: Boolean,
    val createdAt: Long = System.currentTimeMillis(),
    val walletId: Long? = null,
    val walletName: String? = null,
    val tags: String = ""
) : Parcelable
