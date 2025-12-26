package com.example.vitalviewv5.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "steps")
data class StepDataEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val steps: Int,
    val distance: Float,
    val calories: Float,
    val date: String
)