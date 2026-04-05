package com.example.hesapyonetimi

/**
 * LEGACY MODEL - Geriye uyumluluk için
 * Eski adapter'lar tarafından kullanılıyor
 */
data class Islem(
    val tutar: Double,
    val kategori: String,
    val aciklama: String,
    val tarihSaat: String,
    val isGelir: Boolean,
    var isDone: Boolean = false
)
