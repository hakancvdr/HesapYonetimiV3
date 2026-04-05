package com.example.hesapyonetimi.domain.model

data class Budget(
    val id: Long = 0,
    val categoryId: Long,
    val categoryName: String = "",
    val categoryIcon: String = "",
    val categoryColor: String = "",
    val limitAmount: Double,
    val spentAmount: Double = 0.0,
    val yearMonth: String
) {
    val remainingAmount: Double
        get() = limitAmount - spentAmount
    
    val percentageUsed: Float
        get() = if (limitAmount > 0) (spentAmount / limitAmount * 100).toFloat() else 0f
    
    val isOverBudget: Boolean
        get() = spentAmount > limitAmount
}
