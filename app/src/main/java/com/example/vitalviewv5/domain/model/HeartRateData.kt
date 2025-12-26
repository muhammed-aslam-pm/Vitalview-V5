package com.example.vitalviewv5.domain.model

data class HeartRateData(
    val timestamp: Long,
    val heartRate: Int,
    val date: String
)

data class BloodOxygenData(
    val timestamp: Long,
    val spo2: Int,
    val date: String
)

data class BloodPressureData(
    val timestamp: Long,
    val systolic: Int,  // High BP
    val diastolic: Int, // Low BP
    val heartRate: Int,
    val date: String
)

data class TemperatureData(
    val timestamp: Long,
    val temperature: Float,
    val date: String
)

data class StepData(
    val timestamp: Long,
    val steps: Int,
    val distance: Float,
    val calories: Float,
    val date: String
)

data class SleepData(
    val timestamp: Long,
    val date: String,
    val sleepValue: Int,
    val sleepLevel: SleepLevel
)

enum class SleepLevel {
    DEEP_SLEEP,
    LIGHT_SLEEP,
    AWAKE,
    REM
}

data class DeviceInfo(
    val name: String,
    val macAddress: String,
    val batteryLevel: Int,
    val firmwareVersion: String,
    val isConnected: Boolean
)
