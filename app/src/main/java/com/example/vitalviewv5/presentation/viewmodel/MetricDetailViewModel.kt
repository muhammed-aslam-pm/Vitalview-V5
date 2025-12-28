package com.example.vitalviewv5.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vitalviewv5.domain.repository.IFitnessBandRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Locale

import javax.inject.Inject
import com.example.vitalviewv5.domain.model.SpotMeasurementType
import com.example.vitalviewv5.util.Resource

@HiltViewModel
class MetricDetailViewModel @Inject constructor(
    private val repository: IFitnessBandRepository
) : ViewModel() {

    // Simple data class for graph and list
    data class HistoryPoint(
        val value: Float,
        val timestamp: Long,
        val label: String // Date string
    )

    private val _historyData = MutableStateFlow<List<HistoryPoint>>(emptyList())
    val historyData: StateFlow<List<HistoryPoint>> = _historyData.asStateFlow()

    private val _metricName = MutableStateFlow("")
    val metricName: StateFlow<String> = _metricName.asStateFlow()
    
    // Average or latest value
    private val _summaryValue = MutableStateFlow("")
    val summaryValue: StateFlow<String> = _summaryValue.asStateFlow()
    
    // Measurement State
    private val _isMeasuring = MutableStateFlow(false)
    val isMeasuring: StateFlow<Boolean> = _isMeasuring.asStateFlow()

    private val _measureMessage = MutableStateFlow<String?>(null)
    val measureMessage: StateFlow<String?> = _measureMessage.asStateFlow()

    fun loadMetric(metricType: String) {
        _metricName.value = metricType
        
        viewModelScope.launch {
            when (metricType) {
                "Heart Rate" -> loadHeartRate()
                "SpO2" -> loadSpO2()
                "Blood Pressure" -> loadBloodPressure()
                "Temperature" -> loadTemperature()
                "Steps" -> loadSteps()
                "Sleep" -> loadSleep()
            }
        }
    }

    fun startMeasurement() {
        val type = when (_metricName.value) {
            "Heart Rate" -> SpotMeasurementType.HEART_RATE
            "SpO2" -> SpotMeasurementType.SPO2
            else -> return
        }

        viewModelScope.launch {
            _isMeasuring.value = true
            _measureMessage.value = null
            
            val result = repository.startSpotMeasurement(type)
            when (result) {
                is Resource.Success -> _measureMessage.value = "Measurement started successfully"
                is Resource.Error -> _measureMessage.value = "Failed: ${result.message}"
                else -> {}
            }
            // Reset measuring state after a delay or immediately if just a trigger
            kotlinx.coroutines.delay(2000)
            _isMeasuring.value = false
        }
    }

    private suspend fun loadHeartRate() {
        repository.getHeartRateHistory().collect { list ->
            val points = list.sortedBy { it.timestamp }.map { 
                HistoryPoint(it.heartRate.toFloat(), it.timestamp, it.date)
            }
            _historyData.value = points
            if (points.isNotEmpty()) {
                val avg = points.map { it.value }.average().toInt()
                _summaryValue.value = "$avg BPM (Avg)"
            }
        }
    }

    private suspend fun loadSpO2() {
        repository.getBloodOxygenHistory().collect { list ->
             val points = list.sortedBy { it.timestamp }.map { 
                HistoryPoint(it.spo2.toFloat(), it.timestamp, it.date)
            }
            _historyData.value = points
             if (points.isNotEmpty()) {
                val avg = points.map { it.value }.average().toInt()
                _summaryValue.value = "$avg % (Avg)"
            }
        }
    }
    
    private suspend fun loadBloodPressure() {
        // For BP graph, let's just show Systolic for now or maybe we can improve later
        repository.getBloodPressureHistory().collect { list ->
             val points = list.sortedBy { it.timestamp }.map { 
                // Using Systolic for graph visualization
                HistoryPoint(it.systolic.toFloat(), it.timestamp, "${it.systolic}/${it.diastolic} - ${it.date}")
            }
            _historyData.value = points
            if (points.isNotEmpty()) {
                val last = list.maxByOrNull { it.timestamp }
                _summaryValue.value = "${last?.systolic}/${last?.diastolic} mmHg (Latest)"
            }
        }
    }

    private suspend fun loadTemperature() {
        repository.getTemperatureHistory().collect { list ->
            val points = list.sortedBy { it.timestamp }.map { 
                HistoryPoint(it.temperature, it.timestamp, it.date)
            }
            _historyData.value = points
             if (points.isNotEmpty()) {
                val avg = points.map { it.value }.average()
                _summaryValue.value = String.format("%.1f °C (Avg)", avg)
            }
        }
    }

    private suspend fun loadSteps() {
        repository.getStepsHistory().collect { list ->
            // Steps might be accumulated, but let's just show raw history entries
            val points = list.sortedBy { it.timestamp }.map { 
                HistoryPoint(it.steps.toFloat(), it.timestamp, it.date)
            }
            _historyData.value = points
             if (points.isNotEmpty()) {
                val total = points.sumOf { it.value.toDouble() }.toInt()
                _summaryValue.value = "$total Steps (Total)"
            }
        }
    }

    private suspend fun loadSleep() {
        repository.getSleepHistory().collect { list ->
            // For Graph: Map Sleep Levels to values 
            // 0: Deep, 1: Light, 2: Awake, 3: REM
            // We can visualize this on the Y-axis.
            val points = list.sortedBy { it.timestamp }.map { 
                val yVal = when(it.sleepLevel) {
                    com.example.vitalviewv5.domain.model.SleepLevel.DEEP_SLEEP -> 0f
                    com.example.vitalviewv5.domain.model.SleepLevel.LIGHT_SLEEP -> 1f
                    com.example.vitalviewv5.domain.model.SleepLevel.REM -> 2f
                    com.example.vitalviewv5.domain.model.SleepLevel.AWAKE -> 3f
                }
                HistoryPoint(yVal, it.timestamp, "${it.sleepLevel} - ${it.date}")
            }
            _historyData.value = points
             if (points.isNotEmpty()) {
                 // Calculate total sleep duration (count of records * unit length?)
                 // Assuming 1 record is 1 unit (e.g. 1 minute or 5 minutes?) 
                 // Actually the records are usually per minute if using mode 1
                 // Let's just show count of records as minutes approx for now for Summary
                 val totalMinutes = list.size // This is rough approximation as records might be 5 mins
                 val hours = list.size / 60
                 val mins = list.size % 60
                 _summaryValue.value = "${hours}h ${mins}m (Approx)"
            }
        }
    }
}
