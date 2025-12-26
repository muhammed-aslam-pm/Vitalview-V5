package com.example.vitalviewv5.data.sdk


import com.example.vitalviewv5.data.local.entity.BloodOxygenEntity
import com.example.vitalviewv5.data.local.entity.BloodPressureEntity
import com.example.vitalviewv5.data.local.entity.HeartRateEntity
import com.example.vitalviewv5.data.local.entity.SleepDataEntity
import com.example.vitalviewv5.data.local.entity.StepDataEntity
import com.example.vitalviewv5.data.local.entity.TemperatureEntity
import com.example.vitalviewv5.domain.model.SleepLevel
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

object SdkDataParser {

    private val dateFormat = SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.getDefault())
    private val dateFormatShort = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())

    fun parseHeartRateData(dataMap: Map<String, Any>): List<HeartRateEntity> {
        val result = mutableListOf<HeartRateEntity>()

        @Suppress("UNCHECKED_CAST")
        val dicData = dataMap["dicData"] as? List<Map<String, Any>> ?: return result

        dicData.forEach { item ->
            val dateStr = item["date"] as? String ?: return@forEach
            val heartRate = (item["onceHeartValue"] as? Number)?.toInt() ?: return@forEach

            try {
                val timestamp = dateFormat.parse(dateStr)?.time ?: System.currentTimeMillis()
                result.add(
                    HeartRateEntity(
                        timestamp = timestamp,
                        heartRate = heartRate,
                        date = dateFormatShort.format(Date(timestamp))
                    )
                )
            } catch (e: Exception) {
                Timber.e(e, "Error parsing heart rate data")
            }
        }

        return result
    }

    fun parseBloodOxygenData(dataMap: Map<String, Any>): List<BloodOxygenEntity> {
        val result = mutableListOf<BloodOxygenEntity>()

        @Suppress("UNCHECKED_CAST")
        val dicData = dataMap["dicData"] as? List<Map<String, Any>> ?: return result

        dicData.forEach { item ->
            val dateStr = item["date"] as? String ?: return@forEach
            val spo2 = (item["Blood_oxygen"] as? Number)?.toInt() ?: return@forEach

            try {
                val timestamp = dateFormat.parse(dateStr)?.time ?: System.currentTimeMillis()
                result.add(
                    BloodOxygenEntity(
                        timestamp = timestamp,
                        spo2 = spo2,
                        date = dateFormatShort.format(Date(timestamp))
                    )
                )
            } catch (e: Exception) {
                Timber.e(e, "Error parsing blood oxygen data")
            }
        }

        return result
    }

    fun parseBloodPressureData(dataMap: Map<String, Any>): List<BloodPressureEntity> {
        val result = mutableListOf<BloodPressureEntity>()

        @Suppress("UNCHECKED_CAST")
        val dicData = dataMap["dicData"] as? List<Map<String, Any>> ?: return result

        dicData.forEach { item ->
            val dateStr = item["date"] as? String ?: return@forEach
            val highBP = (item["highBP"] as? Number)?.toInt() ?: return@forEach
            val lowBP = (item["lowBP"] as? Number)?.toInt() ?: return@forEach
            val heartRate = (item["heartRate"] as? Number)?.toInt() ?: 0

            try {
                val timestamp = dateFormat.parse(dateStr)?.time ?: System.currentTimeMillis()
                result.add(
                    BloodPressureEntity(
                        timestamp = timestamp,
                        systolic = highBP,
                        diastolic = lowBP,
                        heartRate = heartRate,
                        date = dateFormatShort.format(Date(timestamp))
                    )
                )
            } catch (e: Exception) {
                Timber.e(e, "Error parsing blood pressure data")
            }
        }

        return result
    }

    fun parseSleepData(dataMap: Map<String, Any>): List<SleepDataEntity> {
        val result = mutableListOf<SleepDataEntity>()

        @Suppress("UNCHECKED_CAST")
        val dicData = dataMap["dicData"] as? List<Map<String, Any>> ?: return result

        dicData.forEach { item ->
            val dateStr = item["date"] as? String ?: return@forEach
            val sleepUnitLength = (item["sleepUnitLength"] as? Number)?.toInt() ?: 1
            val arraySleepQuality = item["arraySleepQuality"] as? String ?: return@forEach

            val sleepValues = arraySleepQuality.trim().split(" ")
                .mapNotNull { it.toIntOrNull() }

            try {
                var timestamp = dateFormat.parse(dateStr)?.time ?: System.currentTimeMillis()

                sleepValues.forEach { value ->
                    val sleepLevel = if (sleepUnitLength == 1) {
                        getSleepLevelOneMinute(value.toFloat())
                    } else {
                        getSleepLevelFiveMinute(value.toFloat() / 5f)
                    }

                    result.add(
                        SleepDataEntity(
                            timestamp = timestamp,
                            date = dateStr,
                            sleepValue = value,
                            sleepLevel = sleepLevel.name
                        )
                    )

                    timestamp += sleepUnitLength * 60 * 1000L // Increment by minutes
                }
            } catch (e: Exception) {
                Timber.e(e, "Error parsing sleep data")
            }
        }

        return result
    }

    fun parseStepData(dataMap: Map<String, Any>): List<StepDataEntity> {
        val result = mutableListOf<StepDataEntity>()

        @Suppress("UNCHECKED_CAST")
        val dicData = dataMap["dicData"] as? List<Map<String, Any>> ?: return result

        dicData.forEach { item ->
            val dateStr = item["date"] as? String ?: return@forEach
            val steps = (item["step"] as? Number)?.toInt() ?: 0
            val distance = (item["distance"] as? Number)?.toFloat() ?: 0f
            val calories = (item["calories"] as? Number)?.toFloat() ?: 0f

            try {
                val timestamp = dateFormatShort.parse(dateStr)?.time ?: System.currentTimeMillis()
                result.add(
                    StepDataEntity(
                        timestamp = timestamp,
                        steps = steps,
                        distance = distance,
                        calories = calories,
                        date = dateStr
                    )
                )
            } catch (e: Exception) {
                Timber.e(e, "Error parsing step data")
            }
        }

        return result
    }

    fun parseTemperatureData(dataMap: Map<String, Any>): List<TemperatureEntity> {
        val result = mutableListOf<TemperatureEntity>()

        @Suppress("UNCHECKED_CAST")
        val dicData = dataMap["dicData"] as? List<Map<String, Any>> ?: return result

        dicData.forEach { item ->
            val dateStr = item["date"] as? String ?: return@forEach
            val temp = (item["temperature"] as? Number)?.toFloat() ?: return@forEach

            try {
                val timestamp = dateFormat.parse(dateStr)?.time ?: System.currentTimeMillis()
                result.add(
                    TemperatureEntity(
                        timestamp = timestamp,
                        temperature = temp,
                        date = dateFormatShort.format(Date(timestamp))
                    )
                )
            } catch (e: Exception) {
                Timber.e(e, "Error parsing temperature data")
            }
        }

        return result
    }

    // Sleep level calculation based on SDK documentation
    private fun getSleepLevelOneMinute(data: Float): SleepLevel {
        return when (data) {
            1f -> SleepLevel.DEEP_SLEEP
            2f -> SleepLevel.LIGHT_SLEEP
            3f -> SleepLevel.REM
            else -> SleepLevel.AWAKE
        }
    }

    private fun getSleepLevelFiveMinute(data: Float): SleepLevel {
        return when {
            data in 0f..2f -> SleepLevel.DEEP_SLEEP
            data > 2f && data <= 8f -> SleepLevel.LIGHT_SLEEP
            data > 8f && data <= 20f -> SleepLevel.REM
            else -> SleepLevel.AWAKE
        }
    }
}
