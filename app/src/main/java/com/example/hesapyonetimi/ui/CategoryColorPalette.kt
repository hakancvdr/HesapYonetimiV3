package com.example.hesapyonetimi.ui

object CategoryColorPalette {
    // Controlled palette for consistency across app.
    val hex: List<String> = listOf(
        "#2E7D32", // green
        "#00897B", // teal
        "#1565C0", // blue
        "#5E35B1", // deep purple
        "#8E24AA", // purple
        "#C2185B", // pink
        "#D32F2F", // red
        "#F57C00", // orange
        "#F9A825", // amber
        "#6D4C41", // brown
        "#546E7A", // blue grey
        "#263238"  // near-black
    )

    fun normalize(hexColor: String?): String? {
        val c = hexColor?.trim().orEmpty()
        if (c.isBlank()) return null
        if (!c.startsWith("#")) return "#$c"
        return c
    }

    fun closestOrDefault(hexColor: String?, defaultColor: String = hex.first()): String {
        val normalized = normalize(hexColor) ?: return defaultColor
        return if (hex.any { it.equals(normalized, ignoreCase = true) }) normalized else defaultColor
    }
}

