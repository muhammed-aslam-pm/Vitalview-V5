package com.example.vitalviewv5.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "heart_rate")
data class HeartRateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val heartRate: Int,
    val date: String
)