package com.example.vitalviewv5.data.repository

import com.example.vitalviewv5.data.ble.BleManager
import com.example.vitalviewv5.data.local.FitnessBandDatabase
import com.example.vitalviewv5.data.sdk.FitnessBandSdkWrapper
import com.example.vitalviewv5.data.sdk.SdkDataParser
import com.example.vitalviewv5.domain.model.*
import com.example.vitalviewv5.domain.repository.IFitnessBandRepository
import com.example.vitalviewv5.util.Resource
import kotlinx.coroutines.flow.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FitnessBandRepository @Inject constructor(
    private val bleManager: BleManager,
    private val sdkWrapper: FitnessBandSdkWrapper,
    private val database: FitnessBandDatabase
) : IFitnessBandRepository {

    private val _connectionState = MutableStateFlow<BleManager.ConnectionState>(
        BleManager.ConnectionState.Disconnected
    )
    override val connectionState: StateFlow<BleManager.ConnectionState> = _connectionState.asStateFlow()

    init {
        observeSdkData()
    }

    private fun observeSdkData() {
        sdkWrapper.observeData()
            .onEach { dataMap ->
                processIncomingData(dataMap)
            }
            .catch { e ->
                Timber.e(e, "Error observing SDK data")
            }
            .launchIn(kotlinx.coroutines.GlobalScope)
    }

    private suspend fun processIncomingData(dataMap: Map<String, Any>) {
        val dataType = (dataMap["dataType"] as? Number)?.toInt() ?: return
        val dataEnd = dataMap["dataEnd"] as? Boolean ?: false

        Timber.d("Processing data type: $dataType, dataEnd: $dataEnd")

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
            else -> Timber.d("Unknown data type: $dataType")
        }
    }

    private suspend fun processRealTimeData(dataMap: Map<String, Any>) {
        @Suppress("UNCHECKED_CAST")
        val dicData = dataMap["dicData"] as? Map<String, Any> ?: return

        val heartRate = (dicData["heartRate"] as? Number)?.toInt() ?: 0
        val bloodOxygen = (dicData["Blood_oxygen"] as? Number)?.toInt() ?: 0
        val temperature = (dicData["TempData"] as? Number)?.toFloat() ?: 0f
        val steps = (dicData["step"] as? Number)?.toInt() ?: 0

        Timber.d("Real-time: HR=$heartRate, SpO2=$bloodOxygen, Temp=$temperature, Steps=$steps")

        // Emit real-time values through flows if needed
    }

    private suspend fun saveHeartRateData(dataMap: Map<String, Any>) {
        val entities = SdkDataParser.parseHeartRateData(dataMap)
        if (entities.isNotEmpty()) {
            database.heartRateDao().insertAll(entities)
            Timber.d("Saved ${entities.size} heart rate records")
        }
    }

    private suspend fun saveBloodOxygenData(dataMap: Map<String, Any>) {
        val entities = SdkDataParser.parseBloodOxygenData(dataMap)
        if (entities.isNotEmpty()) {
            database.bloodOxygenDao().insertAll(entities)
            Timber.d("Saved ${entities.size} blood oxygen records")
        }
    }

    private suspend fun saveBloodPressureData(dataMap: Map<String, Any>) {
        val entities = SdkDataParser.parseBloodPressureData(dataMap)
        if (entities.isNotEmpty()) {
            database.bloodPressureDao().insertAll(entities)
            Timber.d("Saved ${entities.size} blood pressure records")
        }
    }

    private suspend fun saveSleepData(dataMap: Map<String, Any>) {
        val entities = SdkDataParser.parseSleepData(dataMap)
        if (entities.isNotEmpty()) {
            database.sleepDataDao().insertAll(entities)
            Timber.d("Saved ${entities.size} sleep records")
        }
    }

    private suspend fun saveStepData(dataMap: Map<String, Any>) {
        val entities = SdkDataParser.parseStepData(dataMap)
        if (entities.isNotEmpty()) {
            entities.forEach { database.stepDataDao().insert(it) }
            Timber.d("Saved ${entities.size} step records")
        }
    }

    private suspend fun saveTemperatureData(dataMap: Map<String, Any>) {
        val entities = SdkDataParser.parseTemperatureData(dataMap)
        if (entities.isNotEmpty()) {
            database.temperatureDao().insertAll(entities)
            Timber.d("Saved ${entities.size} temperature records")
        }
    }

    private fun processBatteryData(dataMap: Map<String, Any>) {
        @Suppress("UNCHECKED_CAST")
        val dicData = dataMap["dicData"] as? Map<String, Any> ?: return
        val batteryLevel = (dicData["batteryLevel"] as? Number)?.toInt() ?: 0
        Timber.d("Battery level: $batteryLevel%")
    }

    private fun processVersionData(dataMap: Map<String, Any>) {
        @Suppress("UNCHECKED_CAST")
        val dicData = dataMap["dicData"] as? Map<String, Any> ?: return
        val version = dicData["deviceVersion"] as? String ?: ""
        Timber.d("Firmware version: $version")
    }

    private fun processMacAddress(dataMap: Map<String, Any>) {
        @Suppress("UNCHECKED_CAST")
        val dicData = dataMap["dicData"] as? Map<String, Any> ?: return
        val macAddress = dicData["macAddress"] as? String ?: ""
        Timber.d("MAC address: $macAddress")
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

        bleManager.connectToDevice(device) { data ->
            // Pass data to SDK for processing
            sdkWrapper.sendData(data)
        }.collect { state ->
            _connectionState.value = state
            emit(Resource.Success(state))

            // When connected and ready, initialize device
            if (state == BleManager.ConnectionState.Ready) {
                initializeDevice()
            }
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

            emit(Resource.Success(true))
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "Sync failed"))
        }
    }

    override fun getLatestHeartRate(): Flow<HeartRateData?> {
        return database.heartRateDao().getLatest().map { entity ->
            entity?.let {
                HeartRateData(
                    timestamp = it.timestamp,
                    heartRate = it.heartRate,
                    date = it.date
                )
            }
        }
    }

    override fun getLatestBloodOxygen(): Flow<BloodOxygenData?> {
        return database.bloodOxygenDao().getLatest().map { entity ->
            entity?.let {
                BloodOxygenData(
                    timestamp = it.timestamp,
                    spo2 = it.spo2,
                    date = it.date
                )
            }
        }
    }

    override fun getLatestBloodPressure(): Flow<BloodPressureData?> {
        return database.bloodPressureDao().getLatest().map { entity ->
            entity?.let {
                BloodPressureData(
                    timestamp = it.timestamp,
                    systolic = it.systolic,
                    diastolic = it.diastolic,
                    heartRate = it.heartRate,
                    date = it.date
                )
            }
        }
    }

    override fun getLatestTemperature(): Flow<TemperatureData?> {
        return database.temperatureDao().getLatest().map { entity ->
            entity?.let {
                TemperatureData(
                    timestamp = it.timestamp,
                    temperature = it.temperature,
                    date = it.date
                )
            }
        }
    }

    override fun getLatestSteps(): Flow<StepData?> {
        return database.stepDataDao().getLatest().map { entity ->
            entity?.let {
                StepData(
                    timestamp = it.timestamp,
                    steps = it.steps,
                    distance = it.distance,
                    calories = it.calories,
                    date = it.date
                )
            }
        }
    }

    override fun disconnect() {
        bleManager.disconnect()
        _connectionState.value = BleManager.ConnectionState.Disconnected
    }
}
