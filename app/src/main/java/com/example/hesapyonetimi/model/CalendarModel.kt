package com.example.hesapyonetimi.model

data class CalendarModel(
    val dayName: String,    // Pzt, Sal...
    val dayNumber: String,  // 02, 03...
    var isSelected: Boolean = false, // Tıklanan günü parlatalım diye
    var hasData: Boolean? = null // Veri olan günler için indicator
)