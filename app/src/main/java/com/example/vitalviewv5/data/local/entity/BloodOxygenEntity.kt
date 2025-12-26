package com.example.vitalviewv5.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey



@Entity(tableName = "blood_oxygen")
data class BloodOxygenEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val spo2: Int,
    val date: String
)
