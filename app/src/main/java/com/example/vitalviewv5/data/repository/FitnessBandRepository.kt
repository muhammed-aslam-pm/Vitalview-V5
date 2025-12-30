package com.example.vitalviewv5.data.repository

import com.example.vitalviewv5.data.ble.BleManager

import com.example.vitalviewv5.data.local.FitnessBandDatabase
import com.example.vitalviewv5.data.sdk.FitnessBandSdkWrapper
import com.example.vitalviewv5.data.sdk.SdkDataParser
import com.example.vitalviewv5.data.local.entity.*
import com.example.vitalviewv5.domain.model.*
import com.example.vitalviewv5.domain.model.SpotMeasurementType
import com.jstyle.blesdk2436.model.AutoTestMode
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
        val dataFlow = sdkWrapper.observeData().shareIn(repositoryScope, SharingStarted.Eagerly)

        // Flow 1: Control & Historical Data (Process Immediately)
        dataFlow
            .filter { data ->
                val type = extractDataType(data)
                type != 23 && type != null
            }
            .onEach { dataMap ->
                repositoryScope.launch {
                    try {
                        processIncomingData(dataMap)
                    } catch (e: Exception) {
                        Timber.e(e, "❌ Error in processIncomingData (immediate)")
                    }
                }
            }
            .launchIn(repositoryScope)

        // Flow 2: Real-time Data (Debounce to 2s to prevent ANR)
        dataFlow
            .filter { data -> extractDataType(data) == 23 }
            .debounce(2000)
            .onEach { dataMap ->
                repositoryScope.launch {
                    try {
                        processIncomingData(dataMap)
                    } catch (e: Exception) {
                        Timber.e(e, "❌ Error in processIncomingData (debounced)")
                    }
                }
            }
            .launchIn(repositoryScope)
    }

    private fun extractDataType(dataMap: Map<String, Any>): Int? {
        val dataTypeValue = dataMap["dataType"]
        return when (dataTypeValue) {
            is Number -> dataTypeValue.toInt()
            is Int -> dataTypeValue
            is Long -> dataTypeValue.toInt()
            is Byte -> dataTypeValue.toInt()
            is Short -> dataTypeValue.toInt()
            is String -> dataTypeValue.toIntOrNull()
            else -> null
        }
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
    // ✅ FIXED: Parse and emit real-time data to the UI flows
    // ✅ FIXED: Parse and emit real-time data to the UI flows AND save to database for history
    private suspend fun processRealTimeData(dataMap: Map<String, Any>) {
        try {
            val dicData = dataMap["dicData"]

            if (dicData == null) {
                Timber.w("⚠️ Type 23: dicData is null, full map: $dataMap")
                return
            }

            Timber.d("🔍 Real-time dicData: $dicData")
            
            // Safe Map Access
            val data = if (dicData is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                dicData as Map<String, Any>
            } else {
                Timber.w("⚠️ dicData is not a Map: ${dicData::class.java.name}")
                return
            }

            // Extract values safely from Map
            fun getInt(key: String): Int = (data[key] as? Number)?.toInt() 
                ?: (data[key]?.toString()?.toIntOrNull()) ?: 0
                
            fun getFloat(key: String): Float = (data[key] as? Number)?.toFloat()
                ?: (data[key]?.toString()?.toFloatOrNull()) ?: 0f

            val heartRate = getInt("heartRate")
            val bloodOxygen = getInt("Blood_oxygen").takeIf { it > 0 } ?: getInt("Bloodoxygen")
            val temperature = getFloat("TempData")
            val steps = getInt("step").takeIf { it > 0 } ?: getInt("allstep")
            val distance = getFloat("distance")
            val calories = getFloat("calories")
            
            // Check for BP just in case it's sneakily included
            val highBP = getInt("highBP")
            val lowBP = getInt("lowBP")

            val timestamp = System.currentTimeMillis()
            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(Date(timestamp))

            Timber.d("⚡ Parsed real-time: HR=$heartRate, SpO2=$bloodOxygen, Temp=$temperature, Steps=$steps, BP=$highBP/$lowBP, Dist=$distance, Cal=$calories")

            // Emit Heart Rate & Save
            if (heartRate > 0) {
                _realTimeHeartRate.emit(HeartRateData(timestamp, heartRate, dateStr))
                database.heartRateDao().insert(
                    HeartRateEntity(timestamp = timestamp, heartRate = heartRate, date = dateStr)
                )
            }

            // Emit SpO2 & Save
            if (bloodOxygen > 0) {
                _realTimeSpO2.emit(BloodOxygenData(timestamp, bloodOxygen, dateStr))
                database.bloodOxygenDao().insert(
                    BloodOxygenEntity(timestamp = timestamp, spo2 = bloodOxygen, date = dateStr)
                )
            }

            // Emit Temperature & Save
            if (temperature > 0f) {
                _realTimeTemp.emit(TemperatureData(timestamp, temperature, dateStr))
                database.temperatureDao().insert(
                    TemperatureEntity(timestamp = timestamp, temperature = temperature, date = dateStr)
                )
            }

            // Emit Steps & Save
            // Save even if steps are 0 if we want to track intervals? But mostly interest in movement.
            // But real-time might send 0 if idle. Let's save if valid or if we want continuous timeline.
            // For now, save if any value is present.
            if (steps > 0 || distance > 0f || calories > 0f) {
                _realTimeSteps.emit(StepData(timestamp, steps, distance, calories, dateStr))
                database.stepDataDao().insert(
                    StepDataEntity(timestamp = timestamp, steps = steps, distance = distance, calories = calories, date = dateStr)
                )
            }
            
            // Emit BP if available & Save
            if (highBP > 0 && lowBP > 0) {
                 _realTimeBloodPressure.emit(
                    BloodPressureData(timestamp, highBP, lowBP, heartRate, dateStr)
                )
                database.bloodPressureDao().insert(
                    BloodPressureEntity(timestamp = timestamp, systolic = highBP, diastolic = lowBP, heartRate = heartRate, date = dateStr)
                )
                Timber.d("📤 Emitted & Saved Real-time BP: $highBP/$lowBP")
            }

        } catch (e: Exception) {
            Timber.e(e, "❌ Error in processRealTimeData")
        }
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
            Timber.d("💾 saveSleepData called")
            val entities = SdkDataParser.parseSleepData(dataMap)
            Timber.d("📦 Parsed ${entities.size} sleep entities")
            if (entities.isNotEmpty()) {
                database.sleepDataDao().insertAll(entities)
                Timber.d("✅ Inserted ${entities.size} sleep records into database")
            } else {
                Timber.w("⚠️ No sleep entities to save")
            }
        } catch (e: Exception) { 
            Timber.e(e, "❌ Error saving sleep data")
        }
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

    override val batteryLevel = MutableStateFlow(-1)
    
    override suspend fun refreshBatteryLevel() {
        try {
            val cmd = sdkWrapper.getBatteryLevel()
            bleManager.writeData(cmd)
            Timber.d("Requested battery level")
        } catch (e: Exception) {
            Timber.e(e, "Failed to request battery level")
        }
    }

    private fun processBatteryData(dataMap: Map<String, Any>) {
        try {
            val dicData = dataMap["dicData"]
            if (dicData == null) return

            val data = if (dicData is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                dicData as Map<String, Any>
            } else {
                 return
            }

            val level = (data["batteryLevel"] as? Number)?.toInt() 
                ?: (data["batteryLevel"]?.toString()?.toIntOrNull()) 
                ?: -1

            if (level >= 0) {
                 repositoryScope.launch {
                     batteryLevel.emit(level)
                 }
                 Timber.d("🔋 Battery Level updated: $level%")
            }
        } catch (e: Exception) {
             Timber.e(e, "Error parsing battery data")
        }
    }
    
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

    private suspend fun initializeDevice() {
        Timber.d("🔧 Initializing device...")
        bleManager.writeData(sdkWrapper.setDeviceTime())

        kotlinx.coroutines.delay(500) // Wait between commands
        
        refreshBatteryLevel()
        kotlinx.coroutines.delay(500)

        bleManager.writeData(sdkWrapper.enableRealTimeData(true, true))
        Timber.d("✅ Enabled real-time data streaming")
        
        // ✅ NEW: Auto-sync historical data on connection (including Sleep)
        repositoryScope.launch {
            kotlinx.coroutines.delay(2000) // Give it a moment to settle
            Timber.d("🚀 Auto-triggering historical data sync...")
            syncHistoricalData().collect { result ->
                when (result) {
                    is Resource.Success -> Timber.d("✅ Auto-sync completed")
                    is Resource.Error -> Timber.e("❌ Auto-sync failed: ${result.message}")
                    is Resource.Loading -> Timber.d("🔄 Auto-sync in progress...")
                }
            }
        }
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
            bleManager.writeData(sdkWrapper.getSleepHistory(0, null))
            kotlinx.coroutines.delay(1000)
            bleManager.writeData(sdkWrapper.getStepHistory(0, null))
            kotlinx.coroutines.delay(1000)
            bleManager.writeData(sdkWrapper.getTemperatureHistory(0, null))
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

    override fun getHeartRateHistory(): Flow<List<HeartRateData>> {
        return database.heartRateDao().getAll().map { entities ->
            entities.map { 
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(it.timestamp))
                HeartRateData(it.timestamp, it.heartRate, dateStr)
            }
        }.flowOn(Dispatchers.IO)
    }

    override fun getBloodOxygenHistory(): Flow<List<BloodOxygenData>> {
        return database.bloodOxygenDao().getAll().map { entities ->
            entities.map {
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(it.timestamp)) 
                BloodOxygenData(it.timestamp, it.spo2, dateStr)
            }
        }.flowOn(Dispatchers.IO)
    }

    override fun getBloodPressureHistory(): Flow<List<BloodPressureData>> {
         return database.bloodPressureDao().getAll().map { entities ->
            entities.map {
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(it.timestamp))
                BloodPressureData(it.timestamp, it.systolic, it.diastolic, it.heartRate, dateStr)
            }
        }.flowOn(Dispatchers.IO)
    }

    override fun getTemperatureHistory(): Flow<List<TemperatureData>> {
        return database.temperatureDao().getAll().map { entities ->
            entities.map {
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(it.timestamp))
                TemperatureData(it.timestamp, it.temperature, dateStr)
            }
        }.flowOn(Dispatchers.IO)
    }

    override fun getStepsHistory(): Flow<List<StepData>> {
        return database.stepDataDao().getAll().map { entities ->
            entities.map {
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(it.timestamp))
                StepData(it.timestamp, it.steps, it.distance, it.calories, dateStr)
            }
        }.flowOn(Dispatchers.IO)
    }

    override fun getSleepHistory(): Flow<List<SleepData>> {
        return database.sleepDataDao().getRecentSleep().map { entities ->
            entities.map {
                SleepData(it.timestamp, it.date, it.sleepValue, SleepLevel.valueOf(it.sleepLevel))
            }
        }.flowOn(Dispatchers.IO)
    }

    override fun getRecentHeartRateHistory(limit: Int): Flow<List<HeartRateData>> {
        return database.heartRateDao().getRecent(limit).map { entities ->
            entities.map {
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(it.timestamp))
                HeartRateData(it.timestamp, it.heartRate, dateStr)
            }
        }.flowOn(Dispatchers.IO)
    }

    override fun getRecentBloodOxygenHistory(limit: Int): Flow<List<BloodOxygenData>> {
        return database.bloodOxygenDao().getRecent(limit).map { entities ->
            entities.map {
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(it.timestamp))
                BloodOxygenData(it.timestamp, it.spo2, dateStr)
            }
        }.flowOn(Dispatchers.IO)
    }

    override fun getRecentBloodPressureHistory(limit: Int): Flow<List<BloodPressureData>> {
        return database.bloodPressureDao().getRecent(limit).map { entities ->
            entities.map {
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(it.timestamp))
                BloodPressureData(it.timestamp, it.systolic, it.diastolic, it.heartRate, dateStr)
            }
        }.flowOn(Dispatchers.IO)
    }

    override fun getRecentTemperatureHistory(limit: Int): Flow<List<TemperatureData>> {
        return database.temperatureDao().getRecent(limit).map { entities ->
            entities.map {
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(it.timestamp))
                TemperatureData(it.timestamp, it.temperature, dateStr)
            }
        }.flowOn(Dispatchers.IO)
    }

    override fun getRecentStepsHistory(limit: Int): Flow<List<StepData>> {
        return database.stepDataDao().getRecent(limit).map { entities ->
            entities.map {
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(it.timestamp))
                StepData(it.timestamp, it.steps, it.distance, it.calories, dateStr)
            }
        }.flowOn(Dispatchers.IO)
    }

    override fun getRecentSleepHistory(limit: Int): Flow<List<SleepData>> {
        // Sleep already uses getRecentSleep with hardcoded 500, let's keep it or update DAO
        return database.sleepDataDao().getRecentSleep().map { entities ->
            entities.map {
                SleepData(it.timestamp, it.date, it.sleepValue, SleepLevel.valueOf(it.sleepLevel))
            }
        }.flowOn(Dispatchers.IO)
    }
    
    override suspend fun startSpotMeasurement(type: SpotMeasurementType): Resource<Boolean> {
        return try {
            val mode = when(type) {
                SpotMeasurementType.HEART_RATE -> AutoTestMode.AutoHeartRate
                SpotMeasurementType.SPO2 -> AutoTestMode.AutoSpo2
            }
            // Start measurement for 30 seconds
            val cmd = sdkWrapper.setDeviceMeasurementWithType(mode, 30, true)
            bleManager.writeData(cmd)
            Timber.d("🚀 Started spot measurement: $type")
            Resource.Success(true)
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to start spot measurement")
            Resource.Error(e.message ?: "Failed to start measurement")
        }
    }

    override fun disconnect() {
        bleManager.disconnect()
        _connectionState.value = BleManager.ConnectionState.Disconnected
        batteryLevel.value = -1
    }
}