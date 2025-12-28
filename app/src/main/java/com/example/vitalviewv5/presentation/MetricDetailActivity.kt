package com.example.vitalviewv5.presentation

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.vitalviewv5.databinding.ActivityMetricDetailBinding
import com.example.vitalviewv5.presentation.adapter.HistoryAdapter
import com.example.vitalviewv5.presentation.viewmodel.MetricDetailViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import android.view.View
import android.widget.Toast

@AndroidEntryPoint
class MetricDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMetricDetailBinding
    private val viewModel: MetricDetailViewModel by viewModels()
    private val historyAdapter = HistoryAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMetricDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val metricName = intent.getStringExtra("METRIC_NAME") ?: "Health Logic"
        
        setupUI()
        setupObservers()
        
        viewModel.loadMetric(metricName)
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressed() }

        binding.rvHistory.apply {
            layoutManager = LinearLayoutManager(this@MetricDetailActivity)
            adapter = historyAdapter
        }

        binding.btnMeasureNow.setOnClickListener {
            viewModel.startMeasurement()
        }
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.metricName.collectLatest { name ->
                binding.tvTitle.text = name
                
                // Show Measure button only for Heart Rate and SpO2
                if (name == "Heart Rate" || name == "SpO2") {
                    binding.layoutAction.visibility = View.VISIBLE
                } else {
                    binding.layoutAction.visibility = View.GONE
                }
            }
        }

        lifecycleScope.launch {
            viewModel.historyData.collectLatest { data ->
                // Update List
                historyAdapter.submitList(data.reversed()) // Show newest first in list
                
                // Update Graph (show oldest first left-to-right)
                val values = data.map { it.value }
                binding.graphView.setData(values)
            }
        }

        lifecycleScope.launch {
            viewModel.summaryValue.collectLatest { value ->
                binding.tvSummaryValue.text = value
            }
        }


        lifecycleScope.launch {
            viewModel.isMeasuring.collectLatest { isMeasuring ->
                binding.btnMeasureNow.isEnabled = !isMeasuring
                binding.btnMeasureNow.text = if (isMeasuring) "Measuring..." else "Measure Now"
                binding.progressBarAction.visibility = if (isMeasuring) View.VISIBLE else View.GONE
            }
        }

        lifecycleScope.launch {
            viewModel.measureMessage.collectLatest { msg ->
                msg?.let {
                    Toast.makeText(this@MetricDetailActivity, it, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
