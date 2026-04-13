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

    /** Alt kategori ise parent category id (null = üst kategori). */
    val parentId: Long? = null,

    /** Sistem kilitli kategori (örn: "Diğer") */
    val isLocked: Boolean = false,
    
    val createdAt: Long = System.currentTimeMillis()
)
