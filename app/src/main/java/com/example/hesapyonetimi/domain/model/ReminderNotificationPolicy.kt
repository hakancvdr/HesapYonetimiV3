package com.example.hesapyonetimi.domain.model

/**
 * Hatırlatıcı bildirim zamanı. Eski kayıtlar [LEGACY_MULTI] ile önceki çoklu alarm davranışını korur.
 */
enum class ReminderNotificationPolicy {
    LEGACY_MULTI,
    DUE_DAY,
    ONE_DAY_BEFORE,
    TWO_DAYS_BEFORE,
    ONE_WEEK_BEFORE;

    companion object {
        fun fromStored(name: String?): ReminderNotificationPolicy {
            if (name.isNullOrBlank()) return LEGACY_MULTI
            return entries.find { it.name == name } ?: LEGACY_MULTI
        }
    }
}
