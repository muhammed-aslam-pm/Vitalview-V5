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
import kotlinx.coroutines.launch

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
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.metricName.collectLatest { name ->
                binding.tvTitle.text = name
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
    }
}
