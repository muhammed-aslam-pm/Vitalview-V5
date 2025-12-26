package com.example.vitalviewv5.domain.repository

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import com.example.vitalviewv5.data.ble.BleManager
import com.example.vitalviewv5.domain.model.*
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

    fun disconnect()
}
