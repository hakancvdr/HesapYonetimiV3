package com.example.hesapyonetimi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wallets")
data class WalletEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val icon: String = "💳",
    val color: String = "#0099CC",
    val type: String = "BANK",  // BANK, CASH, CRYPTO, OTHER
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
