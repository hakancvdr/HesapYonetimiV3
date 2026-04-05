package com.example.hesapyonetimi.data.local.converters

import androidx.room.TypeConverter
import com.example.hesapyonetimi.data.local.entity.RecurringType

class Converters {
    
    @TypeConverter
    fun fromRecurringType(value: RecurringType?): String? {
        return value?.name
    }
    
    @TypeConverter
    fun toRecurringType(value: String?): RecurringType? {
        return value?.let { RecurringType.valueOf(it) }
    }
}
