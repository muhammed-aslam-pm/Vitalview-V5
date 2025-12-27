package com.example.vitalviewv5.data.repository

import com.example.vitalviewv5.data.ble.BleManager
import com.example.vitalviewv5.data.local.FitnessBandDatabase
import com.example.vitalviewv5.data.sdk.FitnessBandSdkWrapper
import com.example.vitalviewv5.data.sdk.SdkDataParser
import com.example.vitalviewv5.domain.model.*
import com.example.vitalviewv5.domain.repository.IFitnessBandRepository
import com.example.vitalviewv5.util.Resource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FitnessBandRepository @Inject constructor(
    private val bleManager: BleManager,
    private val sdkWrapper: FitnessBandSdkWrapper,
    private val database: FitnessBandDatabase
) : IFitnessBandRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ✅ NEW: Real-time flows to bypass the database for instant UI updates
    private val _realTimeHeartRate = MutableSharedFlow<HeartRateData>(replay = 1)
    private val _realTimeSpO2 = MutableSharedFlow<BloodOxygenData>(replay = 1)
    private val _realTimeSteps = MutableSharedFlow<StepData>(replay = 1)
    private val _realTimeTemp = MutableSharedFlow<TemperatureData>(replay = 1)
    private val _realTimeBloodPressure = MutableSharedFlow<BloodPressureData>(replay = 1)

    private val _connectionState = MutableStateFlow<BleManager.ConnectionState>(
        BleManager.ConnectionState.Disconnected
    )

    override val connectionState: StateFlow<BleManager.ConnectionState> =
        _connectionState.asStateFlow()

    init {
        observeSdkData()
        Timber.d("FitnessBandRepository initialized")
    }

    private fun observeSdkData() {
        sdkWrapper.observeData()
            .onEach { dataMap ->
                repositoryScope.launch {
                    try {
                        processIncomingData(dataMap)
                    } catch (e: Exception) {
                        Timber.e(e, "❌ Error in processIncomingData")
                    }
                }
            }
            .launchIn(repositoryScope)
    }

    private suspend fun processIncomingData(dataMap: Map<String, Any>) {
        val dataTypeValue = dataMap["dataType"]
        Timber.d("🔍 processIncomingData: dataTypeValue=$dataTypeValue (${dataTypeValue?.javaClass?.name}), dataMap keys=${dataMap.keys}")
        
        val dataType = when (dataTypeValue) {
            is Number -> dataTypeValue.toInt()
            is Int -> dataTypeValue
            is Long -> dataTypeValue.toInt()
            is Byte -> dataTypeValue.toInt()
            is Short -> dataTypeValue.toInt()
            is String -> dataTypeValue.toIntOrNull()
            else -> {
                Timber.w("⚠️ dataType is unexpected type: ${dataTypeValue?.javaClass?.name}, value=$dataTypeValue")
                null
            }
        }
        
        if (dataType == null) {
            Timber.w("⚠️ dataType is null, skipping. Full dataMap: $dataMap")
            return
        }
        
        Timber.d("✅ Extracted dataType=$dataType")

        try {
            when (dataType) {
                23 -> {
                    Timber.d("📥 Processing real-time data (type 23)")
                    processRealTimeData(dataMap)
                }
                28 -> {
                    Timber.d("📥 Processing heart rate data (type 28)")
                    saveHeartRateData(dataMap)
                }
                68 -> {
                    Timber.d("📥 Processing blood oxygen data (type 68)")
                    saveBloodOxygenData(dataMap)
                }
                42 -> {
                    Timber.d("📥 Processing blood pressure data (type 42)")
                    saveBloodPressureData(dataMap)
                }
                26 -> {
                    Timber.d("📥 Processing sleep data (type 26)")
                    saveSleepData(dataMap)
                }
                24 -> {
                    Timber.d("📥 Processing step data (type 24)")
                    saveStepData(dataMap)
                }
                59 -> {
                    Timber.d("📥 Processing temperature data (type 59)")
                    saveTemperatureData(dataMap)
                }
                9 -> {
                    Timber.d("📥 Processing battery data (type 9)")
                    processBatteryData(dataMap)
                }
                11 -> {
                    Timber.d("📥 Processing version data (type 11)")
                    processVersionData(dataMap)
                }
                10 -> {
                    Timber.d("📥 Processing MAC address data (type 10)")
                    processMacAddress(dataMap)
                }
                else -> {
                    Timber.w("⚠️ Unknown data type: $dataType")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "❌ Error processing data type $dataType")
        }
    }

    // ✅ FIXED: Parse and emit real-time data to the UI flows
    private suspend fun processRealTimeData(dataMap: Map<String, Any>) {
        @Suppress("UNCHECKED_CAST")
        val dicData = dataMap["dicData"] as? Map<String, Any> ?: return

        val heartRate = (dicData["heartRate"] as? Number)?.toInt() ?: 0
        val bloodOxygen = (dicData["Blood_oxygen"] as? Number)?.toInt() ?: 0
        val temperature = (dicData["TempData"] as? Number)?.toFloat() ?: 0f
        val steps = (dicData["step"] as? Number)?.toInt() ?: 0

        // Use current time for real-time display
        val timestamp = System.currentTimeMillis()
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

        // 1. Emit Heart Rate
        if (heartRate > 0) {
            _realTimeHeartRate.emit(HeartRateData(timestamp, heartRate, dateStr))
        }

        // 2. Emit SpO2
        if (bloodOxygen > 0) {
            _realTimeSpO2.emit(BloodOxygenData(timestamp, bloodOxygen, dateStr))
        }

        // 3. Emit Temperature
        if (temperature > 0) {
            _realTimeTemp.emit(TemperatureData(timestamp, temperature, dateStr))
        }

        // 4. Emit Steps
        _realTimeSteps.emit(StepData(timestamp, steps, 0f, 0f, dateStr))

        Timber.d("⚡ Real-time emitted: HR=$heartRate, SpO2=$bloodOxygen, Steps=$steps")
    }

    // ... [Keep all your existing saveXData methods (saveHeartRateData, etc.) exactly the same] ...
    private suspend fun saveHeartRateData(dataMap: Map<String, Any>) {
        try {
            Timber.d("💾 saveHeartRateData called")
            val entities = SdkDataParser.parseHeartRateData(dataMap)
            Timber.d("📦 Parsed ${entities.size} heart rate entities")
            if (entities.isNotEmpty()) {
                database.heartRateDao().insertAll(entities)
                Timber.d("✅ Inserted ${entities.size} heart rate records into database")
                // Emit the latest (most recent) value to real-time flow for immediate UI update
                val latestEntity = entities.maxByOrNull { it.timestamp }
                latestEntity?.let {
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val dateStr = dateFormat.format(Date(it.timestamp))
                    _realTimeHeartRate.emit(HeartRateData(it.timestamp, it.heartRate, dateStr))
                    Timber.d("📤 Emitted latest HR to UI: ${it.heartRate} at ${dateStr}")
                }
            } else {
                Timber.w("⚠️ No heart rate entities to save")
            }
        } catch (e: Exception) { 
            Timber.e(e, "❌ Error saving heart rate data")
        }
    }

    private suspend fun saveBloodOxygenData(dataMap: Map<String, Any>) {
        try {
            Timber.d("💾 saveBloodOxygenData called")
            val entities = SdkDataParser.parseBloodOxygenData(dataMap)
            Timber.d("📦 Parsed ${entities.size} blood oxygen entities")
            if (entities.isNotEmpty()) {
                database.bloodOxygenDao().insertAll(entities)
                Timber.d("✅ Inserted ${entities.size} blood oxygen records into database")
                // Emit the latest (most recent) value to real-time flow for immediate UI update
                val latestEntity = entities.maxByOrNull { it.timestamp }
                latestEntity?.let {
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val dateStr = dateFormat.format(Date(it.timestamp))
                    _realTimeSpO2.emit(BloodOxygenData(it.timestamp, it.spo2, dateStr))
                    Timber.d("📤 Emitted latest SpO2 to UI: ${it.spo2} at ${dateStr}")
                }
            } else {
                Timber.w("⚠️ No blood oxygen entities to save")
            }
        } catch (e: Exception) { 
            Timber.e(e, "❌ Error saving blood oxygen data")
        }
    }

    private suspend fun saveBloodPressureData(dataMap: Map<String, Any>) {
        try {
            Timber.d("💾 saveBloodPressureData called")
            val entities = SdkDataParser.parseBloodPressureData(dataMap)
            Timber.d("📦 Parsed ${entities.size} blood pressure entities")
            if (entities.isNotEmpty()) {
                database.bloodPressureDao().insertAll(entities)
                Timber.d("✅ Inserted ${entities.size} blood pressure records into database")
                // Emit the latest (most recent) value to real-time flow for immediate UI update
                val latestEntity = entities.maxByOrNull { it.timestamp }
                latestEntity?.let {
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val dateStr = dateFormat.format(Date(it.timestamp))
                    _realTimeBloodPressure.emit(
                        BloodPressureData(it.timestamp, it.systolic, it.diastolic, it.heartRate, dateStr)
                    )
                    Timber.d("📤 Emitted latest BP to UI: ${it.systolic}/${it.diastolic} at ${dateStr}")
                }
            } else {
                Timber.w("⚠️ No blood pressure entities to save")
            }
        } catch (e: Exception) { 
            Timber.e(e, "❌ Error saving blood pressure data")
        }
    }

    private suspend fun saveSleepData(dataMap: Map<String, Any>) {
        try {
            val entities = SdkDataParser.parseSleepData(dataMap)
            if (entities.isNotEmpty()) database.sleepDataDao().insertAll(entities)
        } catch (e: Exception) { Timber.e(e) }
    }

    private suspend fun saveStepData(dataMap: Map<String, Any>) {
        try {
            Timber.d("💾 saveStepData called")
            val entities = SdkDataParser.parseStepData(dataMap)
            Timber.d("📦 Parsed ${entities.size} step entities")
            if (entities.isNotEmpty()) {
                entities.forEach { database.stepDataDao().insert(it) }
                Timber.d("✅ Inserted ${entities.size} step records into database")
            } else {
                Timber.w("⚠️ No step entities to save")
            }
        } catch (e: Exception) { 
            Timber.e(e, "❌ Error saving step data")
        }
    }

    private suspend fun saveTemperatureData(dataMap: Map<String, Any>) {
        try {
            Timber.d("💾 saveTemperatureData called")
            val entities = SdkDataParser.parseTemperatureData(dataMap)
            Timber.d("📦 Parsed ${entities.size} temperature entities")
            if (entities.isNotEmpty()) {
                database.temperatureDao().insertAll(entities)
                Timber.d("✅ Inserted ${entities.size} temperature records into database")
                // Emit the latest (most recent) value to real-time flow for immediate UI update
                val latestEntity = entities.maxByOrNull { it.timestamp }
                latestEntity?.let {
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val dateStr = dateFormat.format(Date(it.timestamp))
                    _realTimeTemp.emit(TemperatureData(it.timestamp, it.temperature, dateStr))
                    Timber.d("📤 Emitted latest Temp to UI: ${it.temperature} at ${dateStr}")
                }
            } else {
                Timber.w("⚠️ No temperature entities to save")
            }
        } catch (e: Exception) { 
            Timber.e(e, "❌ Error saving temperature data")
        }
    }

    private fun processBatteryData(dataMap: Map<String, Any>) { /* Keep existing */ }
    private fun processVersionData(dataMap: Map<String, Any>) { /* Keep existing */ }
    private fun processMacAddress(dataMap: Map<String, Any>) { /* Keep existing */ }

    // Public API methods

    override fun scanForDevices(): Flow<Resource<List<android.bluetooth.le.ScanResult>>> = flow {
        emit(Resource.Loading())
        bleManager.scanForDevices()
            .catch { e -> emit(Resource.Error(e.message ?: "Scan failed")) }
            .collect { scanResult -> emit(Resource.Success(listOf(scanResult))) }
    }

    override fun connectToDevice(device: android.bluetooth.BluetoothDevice): Flow<Resource<BleManager.ConnectionState>> = flow {
        emit(Resource.Loading())
        try {
            bleManager.connectToDevice(device) { data ->
                sdkWrapper.processRawData(data)
            }.collect { state ->
                _connectionState.value = state
                emit(Resource.Success(state))
                if (state == BleManager.ConnectionState.Ready) initializeDevice()
            }
        } catch (e: Exception) {
            _connectionState.value = BleManager.ConnectionState.Disconnected
            emit(Resource.Error(e.message ?: "Connection failed"))
        }
    }

    private fun initializeDevice() {
        bleManager.writeData(sdkWrapper.setDeviceTime())
        bleManager.writeData(sdkWrapper.enableRealTimeData(true, true)) // Important!
    }

    override fun syncHistoricalData(): Flow<Resource<Boolean>> = flow {
        // ... [Keep existing implementation] ...
        emit(Resource.Loading())
        try {
            bleManager.writeData(sdkWrapper.getHeartRateHistory(0, null))
            kotlinx.coroutines.delay(1000)
            bleManager.writeData(sdkWrapper.getBloodOxygenHistory(0, null))
            kotlinx.coroutines.delay(1000)
            bleManager.writeData(sdkWrapper.getHRVHistory(0, null)) // BP comes here
            kotlinx.coroutines.delay(1000)
            // ... (rest of your sync logic)
            emit(Resource.Success(true))
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "Sync failed"))
        }
    }

    // ✅ FIXED: Merge Database History with Real-Time Data for UI
    override fun getLatestHeartRate(): Flow<HeartRateData?> {
        val dbFlow = database.heartRateDao().getLatest().map { entity ->
            entity?.let {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val dateStr = dateFormat.format(Date(it.timestamp))
                HeartRateData(it.timestamp, it.heartRate, dateStr)
            }
        }
        return merge(dbFlow, _realTimeHeartRate)
            .flowOn(Dispatchers.IO)
    }

    override fun getLatestBloodOxygen(): Flow<BloodOxygenData?> {
        val dbFlow = database.bloodOxygenDao().getLatest().map { entity ->
            entity?.let {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val dateStr = dateFormat.format(Date(it.timestamp))
                BloodOxygenData(it.timestamp, it.spo2, dateStr)
            }
        }
        return merge(dbFlow, _realTimeSpO2)
            .flowOn(Dispatchers.IO)
    }

    override fun getLatestBloodPressure(): Flow<BloodPressureData?> {
        val dbFlow = database.bloodPressureDao().getLatest().map { entity ->
            entity?.let {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val dateStr = dateFormat.format(Date(it.timestamp))
                BloodPressureData(it.timestamp, it.systolic, it.diastolic, it.heartRate, dateStr)
            }
        }
        return merge(dbFlow, _realTimeBloodPressure)
            .flowOn(Dispatchers.IO)
    }

    override fun getLatestTemperature(): Flow<TemperatureData?> {
        val dbFlow = database.temperatureDao().getLatest().map { entity ->
            entity?.let {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val dateStr = dateFormat.format(Date(it.timestamp))
                TemperatureData(it.timestamp, it.temperature, dateStr)
            }
        }
        return merge(dbFlow, _realTimeTemp)
            .flowOn(Dispatchers.IO)
    }

    override fun getLatestSteps(): Flow<StepData?> {
        val dbFlow = database.stepDataDao().getLatest().map { entity ->
            entity?.let {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val dateStr = dateFormat.format(Date(it.timestamp))
                StepData(it.timestamp, it.steps, it.distance, it.calories, dateStr)
            }
        }
        return merge(dbFlow, _realTimeSteps)
            .flowOn(Dispatchers.IO)
    }

    override fun disconnect() {
        bleManager.disconnect()
        _connectionState.value = BleManager.ConnectionState.Disconnected
    }
}