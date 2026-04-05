package com.example.hesapyonetimi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val name: String,
    
    val icon: String, // Icon resource name veya emoji
    
    val color: String, // Hex color (#FF5722)
    
    val isIncome: Boolean, // Gelir kategorisi mi, gider mi?
    
    val isDefault: Boolean = false, // Varsayılan kategori mi? (silinemesin)
    
    val createdAt: Long = System.currentTimeMillis()
)
