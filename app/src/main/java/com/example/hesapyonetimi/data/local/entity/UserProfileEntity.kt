package com.example.hesapyonetimi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1, // tek satırlık tablo
    val displayName: String = "Kullanıcı",
    val avatarEmoji: String = "👤",
    val monthlyBudgetLimit: Double = 0.0,
    val themeMode: String = "SYSTEM", // LIGHT, DARK, SYSTEM
    val createdAt: Long = System.currentTimeMillis()
)
