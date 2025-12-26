package com.example.vitalviewv5.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vitalviewv5.data.ble.BleManager
import com.example.vitalviewv5.domain.model.*
import com.example.vitalviewv5.domain.repository.IFitnessBandRepository
import com.example.vitalviewv5.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: IFitnessBandRepository
) : ViewModel() {

    private val _scanResults = MutableStateFlow<List<android.bluetooth.le.ScanResult>>(emptyList())
    val scanResults: StateFlow<List<android.bluetooth.le.ScanResult>> = _scanResults.asStateFlow()

    private val _connectionState = MutableStateFlow<BleManager.ConnectionState>(
        BleManager.ConnectionState.Disconnected
    )
    val connectionState: StateFlow<BleManager.ConnectionState> = _connectionState.asStateFlow()

    private val _heartRate = MutableStateFlow<HeartRateData?>(null)
    val heartRate: StateFlow<HeartRateData?> = _heartRate.asStateFlow()

    private val _bloodOxygen = MutableStateFlow<BloodOxygenData?>(null)
    val bloodOxygen: StateFlow<BloodOxygenData?> = _bloodOxygen.asStateFlow()

    private val _bloodPressure = MutableStateFlow<BloodPressureData?>(null)
    val bloodPressure: StateFlow<BloodPressureData?> = _bloodPressure.asStateFlow()

    private val _temperature = MutableStateFlow<TemperatureData?>(null)
    val temperature: StateFlow<TemperatureData?> = _temperature.asStateFlow()

    private val _steps = MutableStateFlow<StepData?>(null)
    val steps: StateFlow<StepData?> = _steps.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    init {
        observeConnectionState()
        observeHealthData()
    }

    private fun observeConnectionState() {
        viewModelScope.launch {
            repository.connectionState.collect { state ->
                _connectionState.value = state
            }
        }
    }

    private fun observeHealthData() {
        viewModelScope.launch {
            repository.getLatestHeartRate().collect { data ->
                _heartRate.value = data
            }
        }

        viewModelScope.launch {
            repository.getLatestBloodOxygen().collect { data ->
                _bloodOxygen.value = data
            }
        }

        viewModelScope.launch {
            repository.getLatestBloodPressure().collect { data ->
                _bloodPressure.value = data
            }
        }

        viewModelScope.launch {
            repository.getLatestTemperature().collect { data ->
                _temperature.value = data
            }
        }

        viewModelScope.launch {
            repository.getLatestSteps().collect { data ->
                _steps.value = data
            }
        }
    }

    fun startScan() {
        viewModelScope.launch {
            _isScanning.value = true
            _scanResults.value = emptyList()

            repository.scanForDevices().collect { resource ->
                when (resource) {
                    is Resource.Success -> {
                        resource.data?.let { results ->
                            val currentList = _scanResults.value.toMutableList()
                            results.forEach { newResult ->
                                val existingIndex = currentList.indexOfFirst {
                                    it.device.address == newResult.device.address
                                }
                                if (existingIndex >= 0) {
                                    currentList[existingIndex] = newResult
                                } else {
                                    currentList.add(newResult)
                                }
                            }
                            _scanResults.value = currentList
                        }
                    }
                    is Resource.Error -> {
                        _isScanning.value = false
                    }
                    is Resource.Loading -> {}
                }
            }
        }
    }

    fun stopScan() {
        _isScanning.value = false
    }

    fun connectToDevice(device: android.bluetooth.BluetoothDevice) {
        viewModelScope.launch {
            repository.connectToDevice(device).collect { resource ->
                when (resource) {
                    is Resource.Success -> {
                        // Connection state is already updated through observeConnectionState()
                    }
                    is Resource.Error -> {
                        // Handle error
                    }
                    is Resource.Loading -> {}
                }
            }
        }
    }

    fun syncData() {
        viewModelScope.launch {
            _isSyncing.value = true
            repository.syncHistoricalData().collect { resource ->
                when (resource) {
                    is Resource.Success -> {
                        _isSyncing.value = false
                    }
                    is Resource.Error -> {
                        _isSyncing.value = false
                    }
                    is Resource.Loading -> {}
                }
            }
        }
    }

    fun disconnect() {
        repository.disconnect()
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }
}
