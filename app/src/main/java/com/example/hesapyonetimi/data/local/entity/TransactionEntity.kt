package com.example.hesapyonetimi.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("categoryId"), Index("date")]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val amount: Double,
    
    val categoryId: Long,
    
    val description: String,
    
    val date: Long, // Timestamp (milliseconds)
    
    val isIncome: Boolean,
    
    val createdAt: Long = System.currentTimeMillis(),
    
    val updatedAt: Long = System.currentTimeMillis(),
    
    // Opsiyonel: Tekrarlayan işlem ID'si (null ise tek seferlik)
    val recurringTransactionId: Long? = null,

    // Cüzdan bağlantısı (null = varsayılan)
    val walletId: Long? = null,

    // İşlem etiketleri (virgülle ayrılmış)
    val tags: String = "",

    // Tekrarlayan işlem bilgileri
    val isRecurring: Boolean = false,
    val recurringDays: Int = 30
)
