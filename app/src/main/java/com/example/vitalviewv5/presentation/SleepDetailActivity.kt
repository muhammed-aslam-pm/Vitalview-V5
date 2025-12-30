package com.example.vitalviewv5.presentation

import android.os.Bundle
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.vitalviewv5.databinding.ActivitySleepDetailBinding
import com.example.vitalviewv5.presentation.viewmodel.SleepDetailViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class SleepDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySleepDetailBinding
    private val viewModel: SleepDetailViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySleepDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Sleep Detail"

        setupObservers()
        setupListeners()
    }

    private fun setupListeners() {
        binding.btnPrevDate.setOnClickListener { viewModel.changeDate(-1) }
        binding.btnNextDate.setOnClickListener { viewModel.changeDate(1) }
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.selectedDate.collect { date ->
                        binding.tvSelectedDate.text = formatDate(date)
                    }
                }

                launch {
                    viewModel.sleepSummary.collect { summary ->
                        if (summary != null) {
                            binding.tvSleepScore.text = summary.sleepScore.toString()
                            binding.tvSleepQuality.text = when {
                                summary.sleepScore >= 80 -> "Excellent"
                                summary.sleepScore >= 60 -> "Normal"
                                else -> "Poor"
                            }
                            
                            binding.tvTotalSleepTime.text = formatMinutes(summary.totalSleepTimeMinutes)
                            binding.tvInBedDuration.text = formatMinutes(summary.inBedDurationMinutes)
                            
                            binding.sleepChartView.setSleepData(summary.stages)
                            
                            val startStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(summary.startTime))
                            val endStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(summary.endTime))
                            binding.tvSleepRange.text = "$startStr - $endStr"

                            // Stages Breakdown
                            binding.tvDeepTime.text = "${summary.deepSleepMinutes} m"
                            binding.pbDeep.progress = summary.deepSleepPercentage
                            
                            binding.tvLightTime.text = formatMinutes(summary.lightSleepMinutes)
                            binding.pbLight.progress = summary.lightSleepPercentage
                            
                            binding.tvRemTime.text = "${summary.remSleepMinutes} m"
                            binding.pbRem.progress = summary.remSleepPercentage
                            
                            binding.tvAwakeTime.text = "${summary.awakeMinutes} m"
                            binding.pbAwake.progress = summary.awakePercentage

                            // Overview
                            binding.tvEfficiency.text = "${summary.sleepEfficiency}%"
                            binding.tvLatency.text = "${summary.sleepLatency} m"
                            binding.tvDebt.text = formatMinutes(summary.sleepDebt)

                            // HR Correlation
                            val avgHr = if (summary.correlatedHeartRate.isNotEmpty()) {
                                summary.correlatedHeartRate.map { it.heartRate }.average().toInt()
                            } else 60
                            binding.tvAvgHeartRate.text = "$avgHr bpm (Avg)"
                        } else {
                            // Handle empty state
                            binding.tvSleepScore.text = "--"
                            binding.tvSleepQuality.text = "No Data"
                        }
                    }
                }
            }
        }
    }

    private fun formatDate(dateStr: String): String {
        return try {
            val date = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).parse(dateStr)
            SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(date!!)
        } catch (e: Exception) {
            dateStr
        }
    }

    private fun formatMinutes(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
