package com.example.hesapyonetimi.domain.model

data class Category(
    val id: Long = 0,
    val name: String,
    val icon: String,
    val color: String,
    val isIncome: Boolean,
    val isDefault: Boolean = false,
    val parentId: Long? = null,
    val isLocked: Boolean = false
)
