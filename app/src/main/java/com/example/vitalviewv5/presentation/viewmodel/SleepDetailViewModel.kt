package com.example.vitalviewv5.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vitalviewv5.domain.model.SleepSummary
import com.example.vitalviewv5.domain.repository.IFitnessBandRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class SleepDetailViewModel @Inject constructor(
    private val repository: IFitnessBandRepository
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(Date()))
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    val sleepSummary: StateFlow<SleepSummary?> = _selectedDate
        .flatMapLatest { date ->
            repository.getSleepSummary(date)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun changeDate(days: Int) {
        val calendar = Calendar.getInstance()
        val current = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).parse(_selectedDate.value) ?: Date()
        calendar.time = current
        calendar.add(Calendar.DAY_OF_YEAR, days)
        _selectedDate.value = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(calendar.time)
    }
}
