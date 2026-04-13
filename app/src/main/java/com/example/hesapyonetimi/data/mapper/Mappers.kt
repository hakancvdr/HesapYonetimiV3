package com.example.hesapyonetimi.data.mapper

import com.example.hesapyonetimi.data.local.entity.*
import com.example.hesapyonetimi.domain.model.*
import com.example.hesapyonetimi.domain.model.ReminderNotificationPolicy

private fun normalizeCategoryIcon(raw: String): String {
    val s = raw.trim()
    return when (s) {
        "🛒" -> "shopping_cart"
        "🚗" -> "directions_car"
        "📄" -> "receipt_long"
        "🎮" -> "sports_esports"
        "⚕️", "🏥" -> "medical_services"
        "📚" -> "school"
        "👕" -> "checkroom"
        "🍽️", "🍔" -> "restaurant"
        "🏠" -> "home"
        "📦" -> "inventory_2"
        "💰" -> "payments"
        "💻" -> "laptop_mac"
        "📈" -> "trending_up"
        "🎁" -> "redeem"
        "💵" -> "payments"
        "💳" -> "credit_card"
        "☕" -> "local_cafe"
        "🎬" -> "movie"
        "✈️", "🛫" -> "flight"
        "🐾" -> "pets"
        "⚡" -> "bolt"
        "🛍️" -> "shopping_bag"
        "🎵" -> "music_note"
        "📱" -> "smartphone"
        "🧾" -> "receipt_long"
        "⛽" -> "local_gas_station"
        "🚌" -> "directions_bus"
        "🚕" -> "local_taxi"
        "🏋️", "💪" -> "fitness_center"
        "🧹" -> "cleaning_services"
        "👶" -> "child_care"
        "🐕", "🐱" -> "pets"
        else -> s
    }
}

/** Public so UI can map legacy emoji / odd stored values before ligature render. */
fun normalizeStoredCategoryIcon(raw: String): String = normalizeCategoryIcon(raw)

// Transaction Mappers
fun TransactionEntity.toDomain(category: CategoryEntity? = null): Transaction {
    return Transaction(
        id = id,
        amount = amount,
        categoryId = categoryId,
        categoryName = category?.name ?: "",
        categoryIcon = normalizeCategoryIcon(category?.icon ?: ""),
        categoryColor = category?.color ?: "",
        description = description,
        date = date,
        isIncome = isIncome,
        createdAt = createdAt,
        walletId = walletId,
        tags = tags
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
        walletId = walletId,
        tags = tags,
        isRecurring = false,
        recurringDays = 30
    )
}

// Category Mappers
fun CategoryEntity.toDomain(): Category {
    return Category(
        id = id,
        name = name,
        icon = normalizeCategoryIcon(icon),
        color = color,
        isIncome = isIncome,
        isDefault = isDefault,
        parentId = parentId,
        isLocked = isLocked
    )
}

fun Category.toEntity(): CategoryEntity {
    return CategoryEntity(
        id = id,
        name = name,
        icon = icon,
        color = color,
        isIncome = isIncome,
        isDefault = isDefault,
        parentId = parentId,
        isLocked = isLocked
    )
}

// Budget Mappers
fun BudgetEntity.toDomain(category: CategoryEntity? = null, spentAmount: Double = 0.0): Budget {
    return Budget(
        id = id,
        categoryId = categoryId,
        categoryName = category?.name ?: "",
        categoryIcon = normalizeCategoryIcon(category?.icon ?: ""),
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
        categoryIcon = normalizeCategoryIcon(category?.icon ?: ""),
        isPaid = isPaid,
        paidAt = paidAt,
        isRecurring = isRecurring,
        recurringType = recurringType,
        daysUntilDue = daysUntil,
        totalDonem = totalDonem,
        donemIndex = donemIndex,
        notificationPolicy = ReminderNotificationPolicy.fromStored(notificationPolicy)
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
        paidAt = paidAt,
        isRecurring = isRecurring,
        recurringType = recurringType,
        totalDonem = totalDonem,
        donemIndex = donemIndex,
        updatedAt = System.currentTimeMillis(),
        notificationPolicy = notificationPolicy.name
    )
}
