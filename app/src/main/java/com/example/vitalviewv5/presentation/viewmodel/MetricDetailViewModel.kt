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

    fun loadMetric(metricType: String) {
        _metricName.value = metricType
        
        viewModelScope.launch {
            when (metricType) {
                "Heart Rate" -> loadHeartRate()
                "SpO2" -> loadSpO2()
                "Blood Pressure" -> loadBloodPressure()
                "Temperature" -> loadTemperature()
                "Steps" -> loadSteps()
            }
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
}
