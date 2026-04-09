package com.example.hesapyonetimi.data.mapper

import com.example.hesapyonetimi.data.local.entity.*
import com.example.hesapyonetimi.domain.model.*

// Transaction Mappers
fun TransactionEntity.toDomain(category: CategoryEntity? = null): Transaction {
    return Transaction(
        id = id,
        amount = amount,
        categoryId = categoryId,
        categoryName = category?.name ?: "",
        categoryIcon = category?.icon ?: "",
        categoryColor = category?.color ?: "",
        description = description,
        date = date,
        isIncome = isIncome,
        createdAt = createdAt,
        walletId = walletId
    )
}

fun Transaction.toEntity(): TransactionEntity {
    return TransactionEntity(
        id = id,
        amount = amount,
        categoryId = categoryId,
        description = description,
        date = date,
        isIncome = isIncome,
        createdAt = createdAt,
        updatedAt = System.currentTimeMillis(),
        walletId = walletId
    )
}

// Category Mappers
fun CategoryEntity.toDomain(): Category {
    return Category(
        id = id,
        name = name,
        icon = icon,
        color = color,
        isIncome = isIncome,
        isDefault = isDefault
    )
}

fun Category.toEntity(): CategoryEntity {
    return CategoryEntity(
        id = id,
        name = name,
        icon = icon,
        color = color,
        isIncome = isIncome,
        isDefault = isDefault
    )
}

// Budget Mappers
fun BudgetEntity.toDomain(category: CategoryEntity? = null, spentAmount: Double = 0.0): Budget {
    return Budget(
        id = id,
        categoryId = categoryId,
        categoryName = category?.name ?: "",
        categoryIcon = category?.icon ?: "",
        categoryColor = category?.color ?: "",
        limitAmount = limitAmount,
        spentAmount = spentAmount,
        yearMonth = yearMonth
    )
}

fun Budget.toEntity(): BudgetEntity {
    return BudgetEntity(
        id = id,
        categoryId = categoryId,
        limitAmount = limitAmount,
        yearMonth = yearMonth,
        updatedAt = System.currentTimeMillis()
    )
}

// Reminder Mappers
fun ReminderEntity.toDomain(category: CategoryEntity? = null): Reminder {
    val now = System.currentTimeMillis()
    val daysUntil = ((dueDate - now) / (1000 * 60 * 60 * 24)).toInt()
    return Reminder(
        id = id,
        title = title,
        amount = amount,
        dueDate = dueDate,
        categoryId = categoryId,
        categoryName = category?.name ?: "",
        categoryIcon = category?.icon ?: "",
        isPaid = isPaid,
        isRecurring = isRecurring,
        recurringType = recurringType,
        daysUntilDue = daysUntil,
        totalDonem = totalDonem,
        donemIndex = donemIndex
    )
}

fun Reminder.toEntity(): ReminderEntity {
    return ReminderEntity(
        id = id,
        title = title,
        amount = amount,
        dueDate = dueDate,
        categoryId = categoryId,
        isPaid = isPaid,
        isRecurring = isRecurring,
        recurringType = recurringType,
        totalDonem = totalDonem,
        donemIndex = donemIndex,
        updatedAt = System.currentTimeMillis()
    )
}
