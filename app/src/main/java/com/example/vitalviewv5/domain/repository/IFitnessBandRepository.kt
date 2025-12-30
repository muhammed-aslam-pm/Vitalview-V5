package com.example.vitalviewv5.domain.repository

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import com.example.vitalviewv5.data.ble.BleManager
import com.example.vitalviewv5.domain.model.*
import com.example.vitalviewv5.domain.model.SpotMeasurementType
import com.example.vitalviewv5.util.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface IFitnessBandRepository {

    val connectionState: StateFlow<BleManager.ConnectionState>

    fun scanForDevices(): Flow<Resource<List<ScanResult>>>

    fun connectToDevice(device: BluetoothDevice): Flow<Resource<BleManager.ConnectionState>>

    fun syncHistoricalData(): Flow<Resource<Boolean>>

    fun getLatestHeartRate(): Flow<HeartRateData?>

    fun getLatestBloodOxygen(): Flow<BloodOxygenData?>

    fun getLatestBloodPressure(): Flow<BloodPressureData?>

    fun getLatestTemperature(): Flow<TemperatureData?>

    fun getLatestSteps(): Flow<StepData?>
    

    
    // History
    fun getHeartRateHistory(): Flow<List<HeartRateData>>
    fun getBloodOxygenHistory(): Flow<List<BloodOxygenData>>
    fun getBloodPressureHistory(): Flow<List<BloodPressureData>>
    fun getTemperatureHistory(): Flow<List<TemperatureData>>
    fun getStepsHistory(): Flow<List<StepData>>
    fun getSleepHistory(): Flow<List<SleepData>>

    // Optimized history
    fun getRecentHeartRateHistory(limit: Int = 500): Flow<List<HeartRateData>>
    fun getRecentBloodOxygenHistory(limit: Int = 500): Flow<List<BloodOxygenData>>
    fun getRecentBloodPressureHistory(limit: Int = 500): Flow<List<BloodPressureData>>
    fun getRecentTemperatureHistory(limit: Int = 500): Flow<List<TemperatureData>>
    fun getRecentStepsHistory(limit: Int = 500): Flow<List<StepData>>
    fun getRecentSleepHistory(limit: Int = 500): Flow<List<SleepData>>

    fun getSleepSummary(date: String): Flow<SleepSummary?>

    suspend fun startSpotMeasurement(type: SpotMeasurementType): Resource<Boolean>

    // Battery
    val batteryLevel: StateFlow<Int>
    suspend fun refreshBatteryLevel()

    fun disconnect()
}
