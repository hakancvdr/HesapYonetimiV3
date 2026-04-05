package com.example.hesapyonetimi

/**
 * LEGACY MODEL - Geriye uyumluluk için
 */
data class Hatirlatici(
    val id: Int,
    val baslik: String,
    val tutar: Double,
    val tarihSaatMs: Long,
    val durum: Int,
    var isDone: Boolean = durum == 1
)
