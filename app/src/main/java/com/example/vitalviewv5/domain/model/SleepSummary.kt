package com.example.vitalviewv5.domain.model

data class SleepSummary(
    val date: String,
    val sleepScore: Int,
    val totalSleepTimeMinutes: Int,
    val inBedDurationMinutes: Int,
    val deepSleepMinutes: Int,
    val lightSleepMinutes: Int,
    val remSleepMinutes: Int,
    val awakeMinutes: Int,
    val deepSleepPercentage: Int,
    val lightSleepPercentage: Int,
    val remSleepPercentage: Int,
    val awakePercentage: Int,
    val sleepEfficiency: Int, // Percentage
    val sleepLatency: Int, // Minutes
    val sleepDebt: Int, // Minutes (compared to goal, e.g. 8h)
    val startTime: Long,
    val endTime: Long,
    val stages: List<SleepData>, // Individual stage segments
    val correlatedHeartRate: List<HeartRateData> = emptyList(),
    val correlatedSpO2: List<BloodOxygenData> = emptyList()
)
