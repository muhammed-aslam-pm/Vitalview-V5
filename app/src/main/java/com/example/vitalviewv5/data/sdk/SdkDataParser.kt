package com.example.vitalviewv5.data.sdk

import com.example.vitalviewv5.data.local.entity.*
import com.example.vitalviewv5.domain.model.SleepLevel
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

object SdkDataParser {

    private val dateFormat = SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.getDefault())
    private val dateFormatShort = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())

    // ✅ IMPROVED: Better extraction that handles the actual data format
    private fun extractValues(dataMap: Map<String, Any>, key: String): List<String> {
        val dicData = dataMap["dicData"]
        val values = mutableListOf<String>()

        when (dicData) {
            is List<*> -> {
                // Handle list format: dicData=[{date=..., key=...}, ...]
                dicData.forEach { item ->
                    if (item is Map<*, *>) {
                        val value = item[key]
                        if (value != null) {
                            values.add(value.toString())
                        }
                    }
                }
            }
            is Map<*, *> -> {
                // Handle single map format: dicData={key=value}
                val value = dicData[key]
                if (value != null) {
                    values.add(value.toString())
                }
            }
            else -> {
                // Fallback to string parsing
                val dicDataStr = dicData.toString()
                var index = 0
                while (true) {
                    index = dicDataStr.indexOf("$key=", index)
                    if (index == -1) break

                    val startIndex = index + key.length + 1
                    var endIndex = dicDataStr.indexOf(",", startIndex)
                    if (endIndex == -1) {
                        endIndex = dicDataStr.indexOf("}", startIndex)
                    }

                    if (endIndex != -1) {
                        val value = dicDataStr.substring(startIndex, endIndex).trim()
                        values.add(value)
                    }
                    index = endIndex
                }
            }
        }

        Timber.d("📦 Extracted ${values.size} values for key '$key': $values")
        return values
    }

    fun parseHeartRateData(dataMap: Map<String, Any>): List<HeartRateEntity> {
        val result = mutableListOf<HeartRateEntity>()

        try {
            Timber.d("🔍 Parsing heart rate data: $dataMap")

            val dates = extractValues(dataMap, "date")
            val heartRates = extractValues(dataMap, "onceHeartValue")

            Timber.d("📅 Found ${dates.size} dates and ${heartRates.size} heart rates")

            dates.forEachIndexed { index, dateStr ->
                if (index < heartRates.size) {
                    try {
                        val hr = heartRates[index].toIntOrNull()
                        if (hr == null || hr == 0) {
                            Timber.w("⚠️ Invalid heart rate value: ${heartRates[index]}")
                            return@forEachIndexed
                        }

                        val timestamp = dateFormat.parse(dateStr)?.time ?: System.currentTimeMillis()
                        val entity = HeartRateEntity(
                            timestamp = timestamp,
                            heartRate = hr,
                            date = dateFormatShort.format(Date(timestamp))
                        )
                        result.add(entity)
                        Timber.d("✅ Parsed HR: $hr at $dateStr")
                    } catch (e: Exception) {
                        Timber.e(e, "❌ Error parsing heart rate at index $index: $dateStr")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "❌ Error in parseHeartRateData")
        }

        Timber.d("✅ Parsed ${result.size} heart rate records")
        return result
    }

    fun parseBloodOxygenData(dataMap: Map<String, Any>): List<BloodOxygenEntity> {
        val result = mutableListOf<BloodOxygenEntity>()

        try {
            Timber.d("🔍 Parsing blood oxygen data: $dataMap")

            val dates = extractValues(dataMap, "date")
            // ✅ FIXED: Changed from "Bloodoxygen" to "Blood_oxygen" (with underscore)
            val spo2Values = extractValues(dataMap, "Blood_oxygen")

            Timber.d("📅 Found ${dates.size} dates and ${spo2Values.size} SpO2 values")

            dates.forEachIndexed { index, dateStr ->
                if (index < spo2Values.size) {
                    try {
                        val spo2 = spo2Values[index].toIntOrNull()
                        if (spo2 == null || spo2 == 0) {
                            Timber.w("⚠️ Invalid SpO2 value: ${spo2Values[index]}")
                            return@forEachIndexed
                        }

                        val timestamp = dateFormat.parse(dateStr)?.time ?: System.currentTimeMillis()
                        val entity = BloodOxygenEntity(
                            timestamp = timestamp,
                            spo2 = spo2,
                            date = dateFormatShort.format(Date(timestamp))
                        )
                        result.add(entity)
                        Timber.d("✅ Parsed SpO2: $spo2 at $dateStr")
                    } catch (e: Exception) {
                        Timber.e(e, "❌ Error parsing blood oxygen at index $index: $dateStr")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "❌ Error in parseBloodOxygenData")
        }

        Timber.d("✅ Parsed ${result.size} blood oxygen records")
        return result
    }

    fun parseBloodPressureData(dataMap: Map<String, Any>): List<BloodPressureEntity> {
        val result = mutableListOf<BloodPressureEntity>()

        try {
            Timber.d("🔍 Parsing blood pressure data: $dataMap")

            val dates = extractValues(dataMap, "date")
            val highBPs = extractValues(dataMap, "highBP")
            val lowBPs = extractValues(dataMap, "lowBP")
            val heartRates = extractValues(dataMap, "heartRate")

            Timber.d("📅 Found ${dates.size} dates, ${highBPs.size} systolic, ${lowBPs.size} diastolic")

            dates.forEachIndexed { index, dateStr ->
                if (index < highBPs.size && index < lowBPs.size) {
                    try {
                        val systolic = highBPs[index].toIntOrNull()
                        val diastolic = lowBPs[index].toIntOrNull()

                        if (systolic == null || diastolic == null || systolic == 0 || diastolic == 0) {
                            Timber.w("⚠️ Invalid BP values: $systolic/$diastolic")
                            return@forEachIndexed
                        }

                        val hr = if (index < heartRates.size) heartRates[index].toIntOrNull() ?: 0 else 0

                        val timestamp = dateFormat.parse(dateStr)?.time ?: System.currentTimeMillis()
                        val entity = BloodPressureEntity(
                            timestamp = timestamp,
                            systolic = systolic,
                            diastolic = diastolic,
                            heartRate = hr,
                            date = dateFormatShort.format(Date(timestamp))
                        )
                        result.add(entity)
                        Timber.d("✅ Parsed BP: $systolic/$diastolic (HR: $hr) at $dateStr")
                    } catch (e: Exception) {
                        Timber.e(e, "❌ Error parsing blood pressure at index $index: $dateStr")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "❌ Error in parseBloodPressureData")
        }

        Timber.d("✅ Parsed ${result.size} blood pressure records")
        return result
    }

    fun parseSleepData(dataMap: Map<String, Any>): List<SleepDataEntity> {
        val result = mutableListOf<SleepDataEntity>()

        try {
            Timber.d("🔍 Parsing sleep data: $dataMap")

            // Sleep data comes as a list
            @Suppress("UNCHECKED_CAST")
            val dicData = dataMap["dicData"] as? List<Map<String, Any>> ?: return result

            dicData.forEach { sleepMap ->
                val dateStr = sleepMap["date"] as? String ?: return@forEach
                val sleepUnitLength = (sleepMap["sleepUnitLength"] as? Number)?.toInt() ?: 1
                val arraySleepQuality = sleepMap["arraySleepQuality"] as? String ?: return@forEach

                val sleepValues = arraySleepQuality.trim().split(" ")
                    .mapNotNull { it.toIntOrNull() }

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

                    timestamp += sleepUnitLength * 60 * 1000L
                }

                Timber.d("✅ Parsed ${sleepValues.size} sleep values for $dateStr")
            }
        } catch (e: Exception) {
            Timber.e(e, "❌ Error in parseSleepData")
        }

        Timber.d("✅ Parsed ${result.size} sleep records")
        return result
    }

    fun parseStepData(dataMap: Map<String, Any>): List<StepDataEntity> {
        val result = mutableListOf<StepDataEntity>()

        try {
            Timber.d("🔍 Parsing step data: $dataMap")

            val dates = extractValues(dataMap, "date")
            val steps = extractValues(dataMap, "step")
            val distances = extractValues(dataMap, "distance")
            val calories = extractValues(dataMap, "calories")

            Timber.d("📅 Found ${dates.size} dates, ${steps.size} steps")

            dates.forEachIndexed { index, dateStr ->
                if (index < steps.size) {
                    try {
                        val stepCount = steps[index].toIntOrNull() ?: 0
                        val distance = if (index < distances.size) distances[index].toFloatOrNull() ?: 0f else 0f
                        val calorie = if (index < calories.size) calories[index].toFloatOrNull() ?: 0f else 0f

                        val timestamp = dateFormatShort.parse(dateStr)?.time ?: System.currentTimeMillis()
                        val entity = StepDataEntity(
                            timestamp = timestamp,
                            steps = stepCount,
                            distance = distance,
                            calories = calorie,
                            date = dateStr
                        )
                        result.add(entity)
                        Timber.d("✅ Parsed steps: $stepCount at $dateStr")
                    } catch (e: Exception) {
                        Timber.e(e, "❌ Error parsing step data at index $index: $dateStr")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "❌ Error in parseStepData")
        }

        Timber.d("✅ Parsed ${result.size} step records")
        return result
    }

    fun parseTemperatureData(dataMap: Map<String, Any>): List<TemperatureEntity> {
        val result = mutableListOf<TemperatureEntity>()

        try {
            Timber.d("🔍 Parsing temperature data: $dataMap")

            val dates = extractValues(dataMap, "date")
            val temps = extractValues(dataMap, "temperature")

            Timber.d("📅 Found ${dates.size} dates and ${temps.size} temperatures")

            dates.forEachIndexed { index, dateStr ->
                if (index < temps.size) {
                    try {
                        val temp = temps[index].toFloatOrNull()
                        if (temp == null || temp == 0f) {
                            Timber.w("⚠️ Invalid temperature value: ${temps[index]}")
                            return@forEachIndexed
                        }

                        val timestamp = dateFormat.parse(dateStr)?.time ?: System.currentTimeMillis()
                        val entity = TemperatureEntity(
                            timestamp = timestamp,
                            temperature = temp,
                            date = dateFormatShort.format(Date(timestamp))
                        )
                        result.add(entity)
                        Timber.d("✅ Parsed temp: $temp at $dateStr")
                    } catch (e: Exception) {
                        Timber.e(e, "❌ Error parsing temperature at index $index: $dateStr")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "❌ Error in parseTemperatureData")
        }

        Timber.d("✅ Parsed ${result.size} temperature records")
        return result
    }

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