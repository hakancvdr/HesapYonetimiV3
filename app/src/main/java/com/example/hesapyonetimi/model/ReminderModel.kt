package com.example.hesapyonetimi.model

data class ReminderModel(
    val title: String,      // Örn: Netflix
    val amount: String,     // Örn: ₺ 159.90
    val dueDate: String,    // Örn: 15 Nisan
    val iconRes: Int        // İkonun kimliği
)