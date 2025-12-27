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
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.flowOn
@Singleton
class FitnessBandRepository @Inject constructor(
    private val bleManager: BleManager,
    private val sdkWrapper: FitnessBandSdkWrapper,
    private val database: FitnessBandDatabase
) : IFitnessBandRepository {

    // ✅ Scope for repository operations
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionState = MutableStateFlow<BleManager.ConnectionState>(
        BleManager.ConnectionState.Disconnected
    )

    override val connectionState: StateFlow<BleManager.ConnectionState> =
        _connectionState.asStateFlow()

    init {
        observeSdkData()
        Timber.d("FitnessBandRepository initialized")
    }

    // ✅ FIX: Launch coroutine to handle suspend functions
    private fun observeSdkData() {
        sdkWrapper.observeData()
            .onEach { dataMap ->
                Timber.d("📊 Repository received data from SDK: $dataMap")
                // ✅ Launch coroutine for suspend function with exception handling
                repositoryScope.launch {
                    try {
                        processIncomingData(dataMap)
                    } catch (e: Exception) {
                        Timber.e(e, "❌❌❌ CRITICAL ERROR in processIncomingData: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }
            .catch { e ->
                Timber.e(e, "❌ Error observing SDK data")
            }
            .launchIn(repositoryScope)
    }

    private suspend fun processIncomingData(dataMap: Map<String, Any>) {
        val dataType = (dataMap["dataType"] as? Number)?.toInt() ?: return
        val dataEnd = dataMap["dataEnd"] as? Boolean ?: false

        Timber.d("🔄 Processing data type: $dataType, dataEnd: $dataEnd")

        try {
            when (dataType) {
                23 -> processRealTimeData(dataMap) // Real-time data
                28 -> saveHeartRateData(dataMap) // Heart rate history
                68 -> saveBloodOxygenData(dataMap) // Blood oxygen history
                42 -> saveBloodPressureData(dataMap) // HRV/BP history
                26 -> saveSleepData(dataMap) // Sleep history
                24 -> saveStepData(dataMap) // Step history
                59 -> saveTemperatureData(dataMap) // Temperature history
                9 -> processBatteryData(dataMap) // Battery level
                11 -> processVersionData(dataMap) // Firmware version
                10 -> processMacAddress(dataMap) // MAC address
                else -> Timber.d("⚠️ Unknown data type: $dataType")
            }
        } catch (e: Exception) {
            Timber.e(e, "❌ Error processing data type $dataType")
        }
    }

    private suspend fun processRealTimeData(dataMap: Map<String, Any>) {
        @Suppress("UNCHECKED_CAST")
        val dicData = dataMap["dicData"] as? Map<String, Any> ?: return

        val heartRate = (dicData["heartRate"] as? Number)?.toInt() ?: 0
        val bloodOxygen = (dicData["Blood_oxygen"] as? Number)?.toInt() ?: 0
        val temperature = (dicData["TempData"] as? Number)?.toFloat() ?: 0f
        val steps = (dicData["step"] as? Number)?.toInt() ?: 0

        Timber.d("💓 Real-time: HR=$heartRate, SpO2=$bloodOxygen, Temp=$temperature, Steps=$steps")
    }

    private suspend fun saveHeartRateData(dataMap: Map<String, Any>) {
        try {
            val entities = SdkDataParser.parseHeartRateData(dataMap)
            if (entities.isNotEmpty()) {
                database.heartRateDao().insertAll(entities)
                Timber.d("✅ Saved ${entities.size} heart rate records")
            } else {
                Timber.w("⚠️ No heart rate entities to save")
            }
        } catch (e: Exception) {
            Timber.e(e, "❌ Error saving heart rate data")
        }
    }

    private suspend fun saveBloodOxygenData(dataMap: Map<String, Any>) {
        try {
            val entities = SdkDataParser.parseBloodOxygenData(dataMap)
            if (entities.isNotEmpty()) {
                database.bloodOxygenDao().insertAll(entities)
                Timber.d("✅ Saved ${entities.size} blood oxygen records")
            } else {
                Timber.w("⚠️ No blood oxygen entities to save")
            }
        } catch (e: Exception) {
            Timber.e(e, "❌ Error saving blood oxygen data")
        }
    }

    private suspend fun saveBloodPressureData(dataMap: Map<String, Any>) {
        try {
            val entities = SdkDataParser.parseBloodPressureData(dataMap)
            if (entities.isNotEmpty()) {
                database.bloodPressureDao().insertAll(entities)
                Timber.d("✅ Saved ${entities.size} blood pressure records")
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
            if (entities.isNotEmpty()) {
                database.sleepDataDao().insertAll(entities)
                Timber.d("✅ Saved ${entities.size} sleep records")
            } else {
                Timber.w("⚠️ No sleep entities to save")
            }
        } catch (e: Exception) {
            Timber.e(e, "❌ Error saving sleep data")
        }
    }

    private suspend fun saveStepData(dataMap: Map<String, Any>) {
        try {
            val entities = SdkDataParser.parseStepData(dataMap)
            if (entities.isNotEmpty()) {
                entities.forEach { database.stepDataDao().insert(it) }
                Timber.d("✅ Saved ${entities.size} step records")
            } else {
                Timber.w("⚠️ No step entities to save")
            }
        } catch (e: Exception) {
            Timber.e(e, "❌ Error saving step data")
        }
    }

    private suspend fun saveTemperatureData(dataMap: Map<String, Any>) {
        try {
            val entities = SdkDataParser.parseTemperatureData(dataMap)
            if (entities.isNotEmpty()) {
                database.temperatureDao().insertAll(entities)
                Timber.d("✅ Saved ${entities.size} temperature records")
            } else {
                Timber.w("⚠️ No temperature entities to save")
            }
        } catch (e: Exception) {
            Timber.e(e, "❌ Error saving temperature data")
        }
    }

    private fun processBatteryData(dataMap: Map<String, Any>) {
        @Suppress("UNCHECKED_CAST")
        val dicData = dataMap["dicData"] as? Map<String, Any> ?: return
        val batteryLevel = (dicData["batteryLevel"] as? Number)?.toInt() ?: 0
        Timber.d("🔋 Battery level: $batteryLevel%")
    }

    private fun processVersionData(dataMap: Map<String, Any>) {
        @Suppress("UNCHECKED_CAST")
        val dicData = dataMap["dicData"] as? Map<String, Any> ?: return
        val version = dicData["deviceVersion"] as? String ?: ""
        Timber.d("📱 Firmware version: $version")
    }

    private fun processMacAddress(dataMap: Map<String, Any>) {
        @Suppress("UNCHECKED_CAST")
        val dicData = dataMap["dicData"] as? Map<String, Any> ?: return
        val macAddress = dicData["macAddress"] as? String ?: ""
        Timber.d("📍 MAC address: $macAddress")
    }

    // Public API methods
    override fun scanForDevices(): Flow<Resource<List<android.bluetooth.le.ScanResult>>> = flow {
        emit(Resource.Loading())

        bleManager.scanForDevices()
            .catch { e ->
                emit(Resource.Error(e.message ?: "Scan failed"))
            }
            .collect { scanResult ->
                emit(Resource.Success(listOf(scanResult)))
            }
    }

    override fun connectToDevice(
        device: android.bluetooth.BluetoothDevice
    ): Flow<Resource<BleManager.ConnectionState>> = flow {
        emit(Resource.Loading())
        try {
            bleManager.connectToDevice(device) { data ->
                Timber.d("📥 Received data from device: ${data.contentToString()}")
                sdkWrapper.processRawData(data)
            }.collect { state ->
                _connectionState.value = state
                emit(Resource.Success(state))

                if (state == BleManager.ConnectionState.Ready) {
                    Timber.d("✅ Device is ready, initializing...")
                    initializeDevice()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "❌ Connection error")
            _connectionState.value = BleManager.ConnectionState.Disconnected
            emit(Resource.Error(e.message ?: "Connection failed"))
        }
    }

    private fun initializeDevice() {
        // Set device time
        bleManager.writeData(sdkWrapper.setDeviceTime())

        // Get device info
        bleManager.writeData(sdkWrapper.getBatteryLevel())
        bleManager.writeData(sdkWrapper.getDeviceVersion())
        bleManager.writeData(sdkWrapper.getMacAddress())

        // Enable real-time data
        bleManager.writeData(sdkWrapper.enableRealTimeData(true, true))
    }

    override fun syncHistoricalData(): Flow<Resource<Boolean>> = flow {
        emit(Resource.Loading())

        try {
            Timber.d("🔄 Starting historical data sync...")

            // Sync heart rate
            bleManager.writeData(sdkWrapper.getHeartRateHistory(0, null))
            kotlinx.coroutines.delay(1000)

            // Sync blood oxygen
            bleManager.writeData(sdkWrapper.getBloodOxygenHistory(0, null))
            kotlinx.coroutines.delay(1000)

            // Sync HRV/BP
            bleManager.writeData(sdkWrapper.getHRVHistory(0, null))
            kotlinx.coroutines.delay(1000)

            // Sync sleep
            bleManager.writeData(sdkWrapper.getSleepHistory(0, null))
            kotlinx.coroutines.delay(1000)

            // Sync steps
            bleManager.writeData(sdkWrapper.getStepHistory(0, null))
            kotlinx.coroutines.delay(1000)

            // Sync temperature
            bleManager.writeData(sdkWrapper.getTemperatureHistory(0, null))

            Timber.d("✅ Historical data sync completed")
            emit(Resource.Success(true))
        } catch (e: Exception) {
            Timber.e(e, "❌ Sync failed")
            emit(Resource.Error(e.message ?: "Sync failed"))
        }
    }

    override fun getLatestHeartRate(): Flow<HeartRateData?> {
        return database.heartRateDao().getLatest()
            .map { entity ->
                entity?.let {
                    Timber.d("💙 Repository emitting HR: ${it.heartRate} at ${it.date}")
                    HeartRateData(
                        timestamp = it.timestamp,
                        heartRate = it.heartRate,
                        date = it.date
                    )
                } ?: run {
                    Timber.d("💙 Repository: No heart rate data")
                    null
                }
            }
            .flowOn(Dispatchers.IO) // ✅ Ensure DB queries run on IO dispatcher
    }

    override fun getLatestBloodOxygen(): Flow<BloodOxygenData?> {
        return database.bloodOxygenDao().getLatest()
            .map { entity ->
                entity?.let {
                    Timber.d("💙 Repository emitting SpO2: ${it.spo2} at ${it.date}")
                    BloodOxygenData(
                        timestamp = it.timestamp,
                        spo2 = it.spo2,
                        date = it.date
                    )
                } ?: run {
                    Timber.d("💙 Repository: No SpO2 data")
                    null
                }
            }
            .flowOn(Dispatchers.IO)
    }

    override fun getLatestBloodPressure(): Flow<BloodPressureData?> {
        return database.bloodPressureDao().getLatest()
            .map { entity ->
                entity?.let {
                    Timber.d("💙 Repository emitting BP: ${it.systolic}/${it.diastolic} at ${it.date}")
                    BloodPressureData(
                        timestamp = it.timestamp,
                        systolic = it.systolic,
                        diastolic = it.diastolic,
                        heartRate = it.heartRate,
                        date = it.date
                    )
                } ?: run {
                    Timber.d("💙 Repository: No BP data")
                    null
                }
            }
            .flowOn(Dispatchers.IO)
    }

    override fun getLatestTemperature(): Flow<TemperatureData?> {
        return database.temperatureDao().getLatest()
            .map { entity ->
                entity?.let {
                    Timber.d("💙 Repository emitting Temp: ${it.temperature}°C at ${it.date}")
                    TemperatureData(
                        timestamp = it.timestamp,
                        temperature = it.temperature,
                        date = it.date
                    )
                } ?: run {
                    Timber.d("💙 Repository: No temp data")
                    null
                }
            }
            .flowOn(Dispatchers.IO)
    }

    override fun getLatestSteps(): Flow<StepData?> {
        return database.stepDataDao().getLatest()
            .map { entity ->
                entity?.let {
                    Timber.d("💙 Repository emitting Steps: ${it.steps} at ${it.date}")
                    StepData(
                        timestamp = it.timestamp,
                        steps = it.steps,
                        distance = it.distance,
                        calories = it.calories,
                        date = it.date
                    )
                } ?: run {
                    Timber.d("💙 Repository: No steps data")
                    null
                }
            }
            .flowOn(Dispatchers.IO)
    }

    override fun disconnect() {
        bleManager.disconnect()
        _connectionState.value = BleManager.ConnectionState.Disconnected
        Timber.d("🔌 Disconnected from device")
    }
}