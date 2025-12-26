package com.example.vitalviewv5.data.local

import androidx.room.TypeConverter
import java.util.Date

class Converters {

    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    // If you need to store lists (for arrays in sleep data, etc.)
    @TypeConverter
    fun fromString(value: String?): List<String>? {
        return value?.split(",")?.map { it.trim() }
    }

    @TypeConverter
    fun fromList(list: List<String>?): String? {
        return list?.joinToString(",")
    }

    // For integer arrays (like sleep quality data)
    @TypeConverter
    fun fromIntString(value: String?): List<Int>? {
        return value?.split(",")?.mapNotNull { it.trim().toIntOrNull() }
    }

    @TypeConverter
    fun fromIntList(list: List<Int>?): String? {
        return list?.joinToString(",")
    }
}
