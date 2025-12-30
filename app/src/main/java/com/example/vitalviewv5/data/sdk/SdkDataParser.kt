package com.example.vitalviewv5.data.sdk

import com.example.vitalviewv5.data.local.entity.*
import com.example.vitalviewv5.domain.model.SleepLevel
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

object SdkDataParser {

    private val dateFormat = SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.getDefault())
    private val dateFormatNoColon = SimpleDateFormat("yyyy.MM.dd HHmmss", Locale.getDefault())
    private val dateFormatShort = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
    
    private val hyphenDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    private fun parseTimestamp(dateStr: String): Long? {
        if (dateStr.isEmpty()) return null
        
        // Normalize: The band sometimes sends "2025.08.19 15:12:18" or "2025-08-19 15:12:18"
        val normalized = dateStr.trim()
        
        return try {
            if (normalized.contains("-")) {
                hyphenDateFormat.parse(normalized)?.time
            } else {
                dateFormat.parse(normalized)?.time
            }
        } catch (e: Exception) {
            try {
                // Secondary fallback for formats without spaces or with slight variations
                val withDots = normalized.replace("-", ".")
                dateFormat.parse(withDots)?.time
            } catch (e2: Exception) {
                try {
                    // Try without space if it's packed
                    val withSpace = normalized.replaceFirst(Regex("(\\d{4}[.-]\\d{2}[.-]\\d{2})(\\d{2}:\\d{2}:\\d{2})"), "$1 $2")
                    if (withSpace.contains("-")) hyphenDateFormat.parse(withSpace)?.time else dateFormat.parse(withSpace)?.time
                } catch (e3: Exception) {
                    Timber.w("⚠️ Failed to parse date: $dateStr")
                    null
                }
            }
        }
    }

    fun parseBloodOxygenData(dataMap: Map<String, Any>): List<BloodOxygenEntity> {
        return try {
            Timber.d("▶ parseBloodOxygenData STARTED")
            val result = mutableListOf<BloodOxygenEntity>()

            val dicData = dataMap["dicData"]
            Timber.d("dicData type: ${dicData?.javaClass?.name}")
            Timber.d("dicData value: $dicData")

            if (dicData == null) {
                Timber.e("❌ dicData is NULL")
                return emptyList()
            }

            // Handle List of Maps
            val dataList = when (dicData) {
                is List<*> -> {
                    Timber.d("📋 dicData is a List with ${dicData.size} items")
                    dicData.mapIndexedNotNull { index, item ->
                        when (item) {
                            is Map<*, *> -> {
                                try {
                                    // Try direct cast first
                                    @Suppress("UNCHECKED_CAST")
                                    val directCast = item as? Map<String, Any>
                                    if (directCast != null) {
                                        Timber.d("✅ Item #$index: Direct cast successful, keys=${directCast.keys}")
                                        directCast
                                    } else {
                                        // Convert keys to strings, preserve value types
                                        val converted = item.entries.associate { (k, v) ->
                                            val key = k?.toString() ?: ""
                                            val value = v ?: ""
                                            key to value
                                        }
                                        Timber.d("✅ Item #$index: Converted Map, keys=${converted.keys}")
                                        converted
                                    }
                                } catch (e: Exception) {
                                    Timber.e(e, "❌ Error converting item #$index: $item")
                                    null
                                }
                            }
                            else -> {
                                Timber.w("⚠️ List item #$index is not a Map: ${item?.javaClass?.name}, value=$item")
                                null
                            }
                        }
                    }
                }
                else -> {
                    // Fallback to string parsing for backward compatibility
                    val dicDataStr = dicData.toString()
                    val dates = mutableListOf<String>()
                    val spo2Values = mutableListOf<Int>()

                    // Extract all "date=..." values
                    var startIndex = 0
                    while (true) {
                        val dateIndex = dicDataStr.indexOf("date=", startIndex)
                        if (dateIndex == -1) break

                        val valueStart = dateIndex + 5
                        val valueEnd = dicDataStr.indexOf(",", valueStart).let {
                            if (it == -1) dicDataStr.indexOf("}", valueStart) else it
                        }

                        if (valueEnd != -1) {
                            val dateValue = dicDataStr.substring(valueStart, valueEnd).trim()
                            dates.add(dateValue)
                        }

                        startIndex = valueEnd
                    }

                    // Extract all "Blood_oxygen=..." or "Bloodoxygen=..." values
                    startIndex = 0
                    val spo2Key = if (dicDataStr.contains("Blood_oxygen=")) "Blood_oxygen=" else "Bloodoxygen="
                    while (true) {
                        val spo2Index = dicDataStr.indexOf(spo2Key, startIndex)
                        if (spo2Index == -1) break

                        val valueStart = spo2Index + spo2Key.length
                        val valueEnd = dicDataStr.indexOf(",", valueStart).let {
                            if (it == -1) dicDataStr.indexOf("}", valueStart) else it
                        }

                        if (valueEnd != -1) {
                            val spo2Str = dicDataStr.substring(valueStart, valueEnd).trim()
                            spo2Str.toIntOrNull()?.let { spo2Values.add(it) }
                        }

                        startIndex = valueEnd
                    }

                    val minSize = minOf(dates.size, spo2Values.size)
                    for (i in 0 until minSize) {
                        try {
                            val dateStr = dates[i].replace(" ", "")
                            val timestamp = parseTimestamp(dateStr)

                            if (timestamp != null) {
                                result.add(
                                    BloodOxygenEntity(
                                        timestamp = timestamp,
                                        spo2 = spo2Values[i],
                                        date = dateFormatShort.format(Date(timestamp))
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "❌ Error parsing blood oxygen entry #$i")
                        }
                    }
                    return result
                }
            }

            // Process List of Maps
            Timber.d("🔄 Processing ${dataList.size} items from List")
            for ((index, item) in dataList.withIndex()) {
                try {
                    Timber.d("📦 Item #$index: $item (type: ${item?.javaClass?.name})")
                    Timber.d("📦 Item keys: ${item?.keys}")
                    
                    val dateStr = (item["date"] as? String)?.trim()
                        ?: (item["date"]?.toString()?.trim())
                    
                    if (dateStr == null || dateStr.isEmpty()) {
                        Timber.w("⚠️ No date found in item #$index: $item")
                        continue
                    }
                    
                    val spo2 = (item["Blood_oxygen"] as? Number)?.toInt()
                        ?: (item["Blood_oxygen"]?.toString()?.toIntOrNull())
                        ?: (item["Bloodoxygen"] as? Number)?.toInt()
                        ?: (item["Bloodoxygen"]?.toString()?.toIntOrNull())
                    
                    if (spo2 == null) {
                        Timber.w("⚠️ No Blood_oxygen found in item #$index: $item")
                        continue
                    }

                    Timber.d("📊 Item #$index: date=$dateStr, SpO2=$spo2")
                    val timestamp = parseTimestamp(dateStr)
                    if (timestamp != null) {
                        result.add(
                            BloodOxygenEntity(
                                timestamp = timestamp,
                                spo2 = spo2,
                                date = dateFormatShort.format(Date(timestamp))
                            )
                        )
                        Timber.d("✓ Created entity #$index: SpO2=$spo2 at $dateStr")
                    } else {
                        Timber.w("⚠️ Failed to parse timestamp for date: $dateStr")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "❌ Error parsing blood oxygen entry #$index: $item")
                }
            }

            Timber.d("✅ Successfully parsed ${result.size} blood oxygen records")
            result
        } catch (e: Exception) {
            Timber.e(e, "💥 FATAL ERROR in parseBloodOxygenData")
            emptyList()
        }
    }

    fun parseSleepData(dataMap: Map<String, Any>): List<SleepDataEntity> {
        return try {
            Timber.d("▶ parseSleepData STARTED")
            val result = mutableListOf<SleepDataEntity>()

            val dicData = dataMap["dicData"]
            if (dicData == null) return emptyList()

            // Sleep parsing is often complex because it comes as a List of Maps
            val dataList = when (dicData) {
                is List<*> -> dicData
                else -> return emptyList()
            }

            for (item in dataList) {
                if (item !is Map<*, *>) continue
                
                // Get Date
                val dateStr = (item["date"] as? String)?.trim() ?: continue
                val startTime = parseTimestamp(dateStr) ?: continue
                
                // Get Unit Length
                val sleepUnitLength = (item["sleepUnitLength"] as? Number)?.toInt() 
                    ?: (item["sleepUnitLength"]?.toString()?.toIntOrNull()) ?: 1
                
                // Get Array String
                val arrayStr = (item["arraySleepQuality"] as? String)?.trim() ?: continue
                val values = arrayStr.split(" ").mapNotNull { it.toIntOrNull() }
                
                // Parse each value
                var currentTime = startTime
                // If unit=1, each value is 1 min. If unit=5, each value is 5 min.
                val intervalMillis = sleepUnitLength * 60 * 1000L 

                for (value in values) {
                    val levelCode = if (sleepUnitLength == 1) {
                         when (value.toFloat()) {
                            1f -> 0 // Deep
                            2f -> 1 // Light
                            3f -> 3 // REM
                            else -> 2 // Awake
                        }
                    } else {
                        val v = value.toFloat()
                        when {
                            v >= 0 && v <= 2f -> 0 // Deep
                            v > 2f && v <= 8f -> 1 // Light
                            v > 8f && v <= 20f -> 3 // REM
                            else -> 2 // Awake
                        }
                    }
                    
                    val levelStr = when (levelCode) {
                        0 -> "DEEP_SLEEP"
                        1 -> "LIGHT_SLEEP"
                        3 -> "REM"
                        else -> "AWAKE"
                    }

                    result.add(
                        SleepDataEntity(
                            timestamp = currentTime,
                            date = dateFormatShort.format(Date(currentTime)),
                            sleepValue = value,
                            sleepLevel = levelStr
                        )
                    )
                    
                    currentTime += intervalMillis
                }
            }
            
            Timber.d("✅ Successfully parsed ${result.size} sleep records")
            result
        } catch (e: Exception) {
            Timber.e(e, "❌ Error parsing sleep data")
            emptyList()
        }
    }

    fun parseHeartRateData(dataMap: Map<String, Any>): List<HeartRateEntity> {
        return try {
            Timber.d("▶ parseHeartRateData STARTED")
            val result = mutableListOf<HeartRateEntity>()

            val dicData = dataMap["dicData"]
            Timber.d("dicData type: ${dicData?.javaClass?.name}")
            Timber.d("dicData value: $dicData")

            if (dicData == null) {
                Timber.e("❌ dicData is NULL")
                return emptyList()
            }

            // Handle List of Maps
            val dataList = when (dicData) {
                is List<*> -> {
                    Timber.d("📋 dicData is a List with ${dicData.size} items")
                    dicData.mapIndexedNotNull { index, item ->
                        when (item) {
                            is Map<*, *> -> {
                                try {
                                    // Try direct cast first
                                    @Suppress("UNCHECKED_CAST")
                                    val directCast = item as? Map<String, Any>
                                    if (directCast != null) {
                                        Timber.d("✅ Item #$index: Direct cast successful, keys=${directCast.keys}")
                                        directCast
                                    } else {
                                        // Convert keys to strings, preserve value types
                                        val converted = item.entries.associate { (k, v) ->
                                            val key = k?.toString() ?: ""
                                            val value = v ?: ""
                                            key to value
                                        }
                                        Timber.d("✅ Item #$index: Converted Map, keys=${converted.keys}")
                                        converted
                                    }
                                } catch (e: Exception) {
                                    Timber.e(e, "❌ Error converting item #$index: $item")
                                    null
                                }
                            }
                            else -> {
                                Timber.w("⚠️ List item #$index is not a Map: ${item?.javaClass?.name}, value=$item")
                                null
                            }
                        }
                    }
                }
                else -> {
                    // Fallback to string parsing for backward compatibility
                    val dicDataStr = dicData.toString()
                    val dates = mutableListOf<String>()
                    val heartRates = mutableListOf<Int>()

                    // Extract dates
                    var startIndex = 0
                    while (true) {
                        val dateIndex = dicDataStr.indexOf("date=", startIndex)
                        if (dateIndex == -1) break

                        val valueStart = dateIndex + 5
                        val valueEnd = dicDataStr.indexOf(",", valueStart).let {
                            if (it == -1) dicDataStr.indexOf("}", valueStart) else it
                        }

                        if (valueEnd != -1) {
                            dates.add(dicDataStr.substring(valueStart, valueEnd).trim())
                        }
                        startIndex = valueEnd
                    }

                    // Extract heart rates (onceHeartValue or heartRate)
                    startIndex = 0
                    val hrKey = if (dicDataStr.contains("onceHeartValue=")) "onceHeartValue=" else "heartRate="
                    while (true) {
                        val hrIndex = dicDataStr.indexOf(hrKey, startIndex)
                        if (hrIndex == -1) break

                        val valueStart = hrIndex + hrKey.length
                        val valueEnd = dicDataStr.indexOf(",", valueStart).let {
                            if (it == -1) dicDataStr.indexOf("}", valueStart) else it
                        }

                        if (valueEnd != -1) {
                            dicDataStr.substring(valueStart, valueEnd).trim().toIntOrNull()?.let {
                                if (it > 0) heartRates.add(it) // Filter out 0 values
                            }
                        }
                        startIndex = valueEnd
                    }

                    val minSize = minOf(dates.size, heartRates.size)
                    for (i in 0 until minSize) {
                        try {
                            val dateStr = dates[i].trim()
                            val timestamp = parseTimestamp(dateStr)

                            if (timestamp != null) {
                                result.add(
                                    HeartRateEntity(
                                        timestamp = timestamp,
                                        heartRate = heartRates[i],
                                        date = dateFormatShort.format(Date(timestamp))
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "❌ Error parsing heart rate entry #$i")
                        }
                    }
                    return result
                }
            }

            // Process List of Maps
            Timber.d("🔄 Processing ${dataList.size} items from List")
            for ((index, item) in dataList.withIndex()) {
                try {
                    Timber.d("📦 Item #$index: $item (type: ${item?.javaClass?.name})")
                    Timber.d("📦 Item keys: ${item?.keys}")

                    val dateStr = (item["date"] as? String)?.trim()
                        ?: (item["date"]?.toString()?.trim())

                    if (dateStr == null || dateStr.isEmpty()) {
                        Timber.w("⚠️ No date found in item #$index: $item")
                        continue
                    }

                    val heartRate = (item["onceHeartValue"] as? Number)?.toInt()
                        ?: (item["onceHeartValue"]?.toString()?.toIntOrNull())
                        ?: (item["heartRate"] as? Number)?.toInt()
                        ?: (item["heartRate"]?.toString()?.toIntOrNull())

                    if (heartRate == null || heartRate <= 0) {
                        Timber.w("⚠️ No valid heart rate found in item #$index: $item")
                        continue
                    }

                    Timber.d("📊 Item #$index: date=$dateStr, HR=$heartRate")
                    val timestamp = parseTimestamp(dateStr)
                    if (timestamp != null) {
                        result.add(
                            HeartRateEntity(
                                timestamp = timestamp,
                                heartRate = heartRate,
                                date = dateFormatShort.format(Date(timestamp))
                            )
                        )
                        Timber.d("✓ Created entity #$index: HR=$heartRate at $dateStr")
                    } else {
                        Timber.w("⚠️ Failed to parse timestamp for date: $dateStr")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "❌ Error parsing heart rate entry #$index: $item")
                }
            }

            Timber.d("✅ Successfully parsed ${result.size} heart rate records")
            result
        } catch (e: Exception) {
            Timber.e(e, "💥 FATAL ERROR in parseHeartRateData")
            emptyList()
        }
    }

    fun parseBloodPressureData(dataMap: Map<String, Any>): List<BloodPressureEntity> {
        return try {
            Timber.d("▶ parseBloodPressureData STARTED")
            val result = mutableListOf<BloodPressureEntity>()

            val dicData = dataMap["dicData"]
            if (dicData == null) {
                Timber.e("❌ dicData is NULL")
                return emptyList()
            }

            // Handle List of Maps
            val dataList = when (dicData) {
                is List<*> -> {
                    Timber.d("📋 dicData is a List with ${dicData.size} items")
                    dicData.mapIndexedNotNull { index, item ->
                        when (item) {
                            is Map<*, *> -> {
                                try {
                                    // Try direct cast first
                                    @Suppress("UNCHECKED_CAST")
                                    val directCast = item as? Map<String, Any>
                                    if (directCast != null) {
                                        Timber.d("✅ Item #$index: Direct cast successful, keys=${directCast.keys}")
                                        directCast
                                    } else {
                                        // Convert keys to strings, preserve value types
                                        val converted = item.entries.associate { (k, v) ->
                                            val key = k?.toString() ?: ""
                                            val value = v ?: ""
                                            key to value
                                        }
                                        Timber.d("✅ Item #$index: Converted Map, keys=${converted.keys}")
                                        converted
                                    }
                                } catch (e: Exception) {
                                    Timber.e(e, "❌ Error converting item #$index: $item")
                                    null
                                }
                            }
                            else -> {
                                Timber.w("⚠️ List item #$index is not a Map: ${item?.javaClass?.name}, value=$item")
                                null
                            }
                        }
                    }
                }
                else -> {
                    // Fallback to string parsing for backward compatibility
                    val dicDataStr = dicData.toString()
                    val dates = mutableListOf<String>()
                    val systolicList = mutableListOf<Int>()
                    val diastolicList = mutableListOf<Int>()
                    val heartRates = mutableListOf<Int>()

                    // Extract dates
                    var startIndex = 0
                    while (true) {
                        val dateIndex = dicDataStr.indexOf("date=", startIndex)
                        if (dateIndex == -1) break

                        val valueStart = dateIndex + 5
                        val valueEnd = dicDataStr.indexOf(",", valueStart).let {
                            if (it == -1) dicDataStr.indexOf("}", valueStart) else it
                        }

                        if (valueEnd != -1) {
                            dates.add(dicDataStr.substring(valueStart, valueEnd).trim())
                        }
                        startIndex = valueEnd
                    }

                    // Extract systolic (highBP)
                    startIndex = 0
                    while (true) {
                        val bpIndex = dicDataStr.indexOf("highBP=", startIndex)
                        if (bpIndex == -1) break

                        val valueStart = bpIndex + 7
                        val valueEnd = dicDataStr.indexOf(",", valueStart).let {
                            if (it == -1) dicDataStr.indexOf("}", valueStart) else it
                        }

                        if (valueEnd != -1) {
                            dicDataStr.substring(valueStart, valueEnd).trim().toIntOrNull()?.let {
                                if (it > 0) systolicList.add(it)
                            }
                        }
                        startIndex = valueEnd
                    }

                    // Extract diastolic (lowBP)
                    startIndex = 0
                    while (true) {
                        val bpIndex = dicDataStr.indexOf("lowBP=", startIndex)
                        if (bpIndex == -1) break

                        val valueStart = bpIndex + 6
                        val valueEnd = dicDataStr.indexOf(",", valueStart).let {
                            if (it == -1) dicDataStr.indexOf("}", valueStart) else it
                        }

                        if (valueEnd != -1) {
                            dicDataStr.substring(valueStart, valueEnd).trim().toIntOrNull()?.let {
                                if (it > 0) diastolicList.add(it)
                            }
                        }
                        startIndex = valueEnd
                    }

                    // Extract heart rate
                    startIndex = 0
                    while (true) {
                        val hrIndex = dicDataStr.indexOf("heartRate=", startIndex)
                        if (hrIndex == -1) break

                        val valueStart = hrIndex + 10
                        val valueEnd = dicDataStr.indexOf(",", valueStart).let {
                            if (it == -1) dicDataStr.indexOf("}", valueStart) else it
                        }

                        if (valueEnd != -1) {
                            dicDataStr.substring(valueStart, valueEnd).trim().toIntOrNull()?.let {
                                heartRates.add(it) // Can be 0
                            }
                        }
                        startIndex = valueEnd
                    }

                    val minSize = minOf(dates.size, systolicList.size, diastolicList.size)
                    for (i in 0 until minSize) {
                        try {
                            val dateStr = dates[i].replace(" ", "")
                            val timestamp = parseTimestamp(dateStr)
                            val hr = if (i < heartRates.size) heartRates[i] else 0

                            if (timestamp != null) {
                                result.add(
                                    BloodPressureEntity(
                                        timestamp = timestamp,
                                        systolic = systolicList[i],
                                        diastolic = diastolicList[i],
                                        heartRate = hr,
                                        date = dateFormatShort.format(Date(timestamp))
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "❌ Error parsing BP entry #$i")
                        }
                    }
                    return result
                }
            }

            // Process List of Maps
            Timber.d("🔄 Processing ${dataList.size} items from List")
            for ((index, item) in dataList.withIndex()) {
                try {
                    Timber.d("📦 Item #$index: $item (type: ${item?.javaClass?.name})")
                    Timber.d("📦 Item keys: ${item?.keys}")
                    
                    val dateStr = (item["date"] as? String)?.trim()
                        ?: (item["date"]?.toString()?.trim())
                    
                    if (dateStr == null || dateStr.isEmpty()) {
                        Timber.w("⚠️ No date found in item #$index: $item")
                        continue
                    }
                    
                    val systolic = (item["highBP"] as? Number)?.toInt()
                        ?: (item["highBP"]?.toString()?.toIntOrNull())
                    
                    if (systolic == null) {
                        Timber.w("⚠️ No highBP found in item #$index: $item")
                        continue
                    }
                    
                    val diastolic = (item["lowBP"] as? Number)?.toInt()
                        ?: (item["lowBP"]?.toString()?.toIntOrNull())
                    
                    if (diastolic == null) {
                        Timber.w("⚠️ No lowBP found in item #$index: $item")
                        continue
                    }
                    
                    val heartRate = (item["heartRate"] as? Number)?.toInt()
                        ?: (item["heartRate"]?.toString()?.toIntOrNull())
                        ?: 0

                    Timber.d("📊 Item #$index: date=$dateStr, BP=$systolic/$diastolic, HR=$heartRate")
                    val timestamp = parseTimestamp(dateStr)
                    if (timestamp != null && systolic > 0 && diastolic > 0) {
                        result.add(
                            BloodPressureEntity(
                                timestamp = timestamp,
                                systolic = systolic,
                                diastolic = diastolic,
                                heartRate = heartRate,
                                date = dateFormatShort.format(Date(timestamp))
                            )
                        )
                        Timber.d("✓ Created entity #$index: BP=$systolic/$diastolic at $dateStr")
                    } else {
                        Timber.w("⚠️ Failed to parse or invalid BP values: timestamp=$timestamp, sys=$systolic, dia=$diastolic")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "❌ Error parsing BP entry #$index: $item")
                }
            }

            Timber.d("✅ Successfully parsed ${result.size} blood pressure records")
            result
        } catch (e: Exception) {
            Timber.e(e, "💥 FATAL ERROR in parseBloodPressureData")
            emptyList()
        }
    }

    fun parseStepData(dataMap: Map<String, Any>): List<StepDataEntity> {
        return try {
            Timber.d("▶ parseStepData STARTED")
            val result = mutableListOf<StepDataEntity>()

            val dicData = dataMap["dicData"]
            Timber.d("dicData type: ${dicData?.javaClass?.name}")
            Timber.d("dicData value: $dicData")

            if (dicData == null) {
                Timber.e("❌ dicData is NULL")
                return emptyList()
            }

            // Handle List of Maps
            val dataList = when (dicData) {
                is List<*> -> {
                    Timber.d("📋 dicData is a List with ${dicData.size} items")
                    dicData.mapIndexedNotNull { index, item ->
                        when (item) {
                            is Map<*, *> -> {
                                try {
                                    // Try direct cast first
                                    @Suppress("UNCHECKED_CAST")
                                    val directCast = item as? Map<String, Any>
                                    if (directCast != null) {
                                        Timber.d("✅ Item #$index: Direct cast successful, keys=${directCast.keys}")
                                        directCast
                                    } else {
                                        // Convert keys to strings, preserve value types
                                        val converted = item.entries.associate { (k, v) ->
                                            val key = k?.toString() ?: ""
                                            val value = v ?: ""
                                            key to value
                                        }
                                        Timber.d("✅ Item #$index: Converted Map, keys=${converted.keys}")
                                        converted
                                    }
                                } catch (e: Exception) {
                                    Timber.e(e, "❌ Error converting item #$index: $item")
                                    null
                                }
                            }
                            else -> {
                                Timber.w("⚠️ List item #$index is not a Map: ${item?.javaClass?.name}, value=$item")
                                null
                            }
                        }
                    }
                }
                else -> {
                    // Fallback to string parsing for backward compatibility
                    val dicDataStr = dicData.toString()
                    val dates = mutableListOf<String>()
                    val steps = mutableListOf<Int>()
                    val distances = mutableListOf<Float>()
                    val calories = mutableListOf<Float>()

                    // Extract dates
                    var startIndex = 0
                    while (true) {
                        val dateIndex = dicDataStr.indexOf("date=", startIndex)
                        if (dateIndex == -1) break

                        val valueStart = dateIndex + 5
                        val valueEnd = dicDataStr.indexOf(",", valueStart).let {
                            if (it == -1) dicDataStr.indexOf("}", valueStart) else it
                        }

                        if (valueEnd != -1) {
                            dates.add(dicDataStr.substring(valueStart, valueEnd).trim())
                        }
                        startIndex = valueEnd
                    }

                    // Extract steps (allstep or step)
                    startIndex = 0
                    val stepKey = if (dicDataStr.contains("allstep=")) "allstep=" else "step="
                    while (true) {
                        val stepIndex = dicDataStr.indexOf(stepKey, startIndex)
                        if (stepIndex == -1) break

                        val valueStart = stepIndex + stepKey.length
                        val valueEnd = dicDataStr.indexOf(",", valueStart).let {
                            if (it == -1) dicDataStr.indexOf("}", valueStart) else it
                        }

                        if (valueEnd != -1) {
                            dicDataStr.substring(valueStart, valueEnd).trim().toIntOrNull()?.let {
                                steps.add(it)
                            }
                        }
                        startIndex = valueEnd
                    }

                    // Extract distances
                    startIndex = 0
                    while (true) {
                        val distIndex = dicDataStr.indexOf("distance=", startIndex)
                        if (distIndex == -1) break

                        val valueStart = distIndex + 9
                        val valueEnd = dicDataStr.indexOf(",", valueStart).let {
                            if (it == -1) dicDataStr.indexOf("}", valueStart) else it
                        }

                        if (valueEnd != -1) {
                            dicDataStr.substring(valueStart, valueEnd).trim().toFloatOrNull()?.let {
                                distances.add(it)
                            }
                        }
                        startIndex = valueEnd
                    }

                    // Extract calories
                    startIndex = 0
                    val calorieKey = if (dicDataStr.contains("calorie=")) "calorie=" else "calories="
                    while (true) {
                        val calIndex = dicDataStr.indexOf(calorieKey, startIndex)
                        if (calIndex == -1) break

                        val valueStart = calIndex + calorieKey.length
                        val valueEnd = dicDataStr.indexOf(",", valueStart).let {
                            if (it == -1) dicDataStr.indexOf("}", valueStart) else it
                        }

                        if (valueEnd != -1) {
                            dicDataStr.substring(valueStart, valueEnd).trim().toFloatOrNull()?.let {
                                calories.add(it)
                            }
                        }
                        startIndex = valueEnd
                    }

                    val minSize = minOf(dates.size, steps.size)
                    for (i in 0 until minSize) {
                        try {
                            val dateStr = dates[i].trim()
                            val timestamp = parseTimestamp(dateStr)

                            if (timestamp != null) {
                                result.add(
                                    StepDataEntity(
                                        timestamp = timestamp,
                                        steps = steps[i],
                                        distance = if (i < distances.size) distances[i] else 0f,
                                        calories = if (i < calories.size) calories[i] else 0f,
                                        date = dateFormatShort.format(Date(timestamp))
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "❌ Error parsing step entry #$i")
                        }
                    }
                    return result
                }
            }

            // Process List of Maps
            Timber.d("🔄 Processing ${dataList.size} items from List")
            for ((index, item) in dataList.withIndex()) {
                try {
                    Timber.d("📦 Item #$index: $item (type: ${item?.javaClass?.name})")
                    Timber.d("📦 Item keys: ${item?.keys}")

                    val dateStr = (item["date"] as? String)?.trim()
                        ?: (item["date"]?.toString()?.trim())

                    if (dateStr == null || dateStr.isEmpty()) {
                        Timber.w("⚠️ No date found in item #$index: $item")
                        continue
                    }

                    val steps = (item["allstep"] as? Number)?.toInt()
                        ?: (item["allstep"]?.toString()?.toIntOrNull())
                        ?: (item["step"] as? Number)?.toInt()
                        ?: (item["step"]?.toString()?.toIntOrNull())
                        ?: 0

                    val distance = (item["distance"] as? Number)?.toFloat()
                        ?: (item["distance"]?.toString()?.toFloatOrNull())
                        ?: 0f

                    val calories = (item["calorie"] as? Number)?.toFloat()
                        ?: (item["calorie"]?.toString()?.toFloatOrNull())
                        ?: (item["calories"] as? Number)?.toFloat()
                        ?: (item["calories"]?.toString()?.toFloatOrNull())
                        ?: 0f

                    Timber.d("📊 Item #$index: date=$dateStr, steps=$steps, distance=$distance, calories=$calories")
                    val timestamp = parseTimestamp(dateStr)
                    if (timestamp != null) {
                        result.add(
                            StepDataEntity(
                                timestamp = timestamp,
                                steps = steps,
                                distance = distance,
                                calories = calories,
                                date = dateFormatShort.format(Date(timestamp))
                            )
                        )
                        Timber.d("✓ Created entity #$index: steps=$steps at $dateStr")
                    } else {
                        Timber.w("⚠️ Failed to parse timestamp for date: $dateStr")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "❌ Error parsing step entry #$index: $item")
                }
            }

            Timber.d("✅ Successfully parsed ${result.size} step records")
            result
        } catch (e: Exception) {
            Timber.e(e, "💥 FATAL ERROR in parseStepData")
            emptyList()
        }
    }




    fun parseTemperatureData(dataMap: Map<String, Any>): List<TemperatureEntity> {
        return try {
            Timber.d("▶ parseTemperatureData STARTED")
            val result = mutableListOf<TemperatureEntity>()

            val dicData = dataMap["dicData"]
            Timber.d("dicData type: ${dicData?.javaClass?.name}")
            Timber.d("dicData value: $dicData")

            if (dicData == null) {
                Timber.e("❌ dicData is NULL")
                return emptyList()
            }

            // Handle List of Maps
            val dataList = when (dicData) {
                is List<*> -> {
                    Timber.d("📋 dicData is a List with ${dicData.size} items")
                    dicData.mapIndexedNotNull { index, item ->
                        when (item) {
                            is Map<*, *> -> {
                                try {
                                    // Try direct cast first
                                    @Suppress("UNCHECKED_CAST")
                                    val directCast = item as? Map<String, Any>
                                    if (directCast != null) {
                                        Timber.d("✅ Item #$index: Direct cast successful, keys=${directCast.keys}")
                                        directCast
                                    } else {
                                        // Convert keys to strings, preserve value types
                                        val converted = item.entries.associate { (k, v) ->
                                            val key = k?.toString() ?: ""
                                            val value = v ?: ""
                                            key to value
                                        }
                                        Timber.d("✅ Item #$index: Converted Map, keys=${converted.keys}")
                                        converted
                                    }
                                } catch (e: Exception) {
                                    Timber.e(e, "❌ Error converting item #$index: $item")
                                    null
                                }
                            }
                            else -> {
                                Timber.w("⚠️ List item #$index is not a Map: ${item?.javaClass?.name}, value=$item")
                                null
                            }
                        }
                    }
                }
                else -> {
                    // Fallback to string parsing for backward compatibility
                    val dicDataStr = dicData.toString()
                    val dates = mutableListOf<String>()
                    val temps = mutableListOf<Float>()

                    // Extract dates
                    var startIndex = 0
                    while (true) {
                        val dateIndex = dicDataStr.indexOf("date=", startIndex)
                        if (dateIndex == -1) break

                        val valueStart = dateIndex + 5
                        val valueEnd = dicDataStr.indexOf(",", valueStart).let {
                            if (it == -1) dicDataStr.indexOf("}", valueStart) else it
                        }

                        if (valueEnd != -1) {
                            dates.add(dicDataStr.substring(valueStart, valueEnd).trim())
                        }
                        startIndex = valueEnd
                    }

                    // Extract temperatures
                    startIndex = 0
                    while (true) {
                        val tempIndex = dicDataStr.indexOf("temperature=", startIndex)
                        if (tempIndex == -1) break

                        val valueStart = tempIndex + 12
                        val valueEnd = dicDataStr.indexOf(",", valueStart).let {
                            if (it == -1) dicDataStr.indexOf("}", valueStart) else it
                        }

                        if (valueEnd != -1) {
                            dicDataStr.substring(valueStart, valueEnd).trim().toFloatOrNull()?.let {
                                temps.add(it)
                            }
                        }
                        startIndex = valueEnd
                    }

                    val minSize = minOf(dates.size, temps.size)
                    for (i in 0 until minSize) {
                        try {
                            val dateStr = dates[i].trim()
                            val timestamp = parseTimestamp(dateStr)

                            if (timestamp != null) {
                                result.add(
                                    TemperatureEntity(
                                        timestamp = timestamp,
                                        temperature = temps[i],
                                        date = dateFormatShort.format(Date(timestamp))
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "❌ Error parsing temp entry #$i")
                        }
                    }
                    return result
                }
            }

            // Process List of Maps
            Timber.d("🔄 Processing ${dataList.size} items from List")
            for ((index, item) in dataList.withIndex()) {
                try {
                    Timber.d("📦 Item #$index: $item (type: ${item?.javaClass?.name})")
                    Timber.d("📦 Item keys: ${item?.keys}")

                    val dateStr = (item["date"] as? String)?.trim()
                        ?: (item["date"]?.toString()?.trim())

                    if (dateStr == null || dateStr.isEmpty()) {
                        Timber.w("⚠️ No date found in item #$index: $item")
                        continue
                    }

                    val temperature = (item["temperature"] as? Number)?.toFloat()
                        ?: (item["temperature"]?.toString()?.toFloatOrNull())

                    if (temperature == null) {
                        Timber.w("⚠️ No temperature found in item #$index: $item")
                        continue
                    }

                    Timber.d("📊 Item #$index: date=$dateStr, temp=$temperature")
                    val timestamp = parseTimestamp(dateStr)
                    if (timestamp != null) {
                        result.add(
                            TemperatureEntity(
                                timestamp = timestamp,
                                temperature = temperature,
                                date = dateFormatShort.format(Date(timestamp))
                            )
                        )
                        Timber.d("✓ Created entity #$index: temp=$temperature at $dateStr")
                    } else {
                        Timber.w("⚠️ Failed to parse timestamp for date: $dateStr")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "❌ Error parsing temp entry #$index: $item")
                }
            }

            Timber.d("✅ Successfully parsed ${result.size} temperature records")
            result
        } catch (e: Exception) {
            Timber.e(e, "💥 FATAL ERROR in parseTemperatureData")
            emptyList()
        }
    }
}
