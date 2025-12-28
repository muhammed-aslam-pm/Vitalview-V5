package com.example.vitalviewv5.presentation

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.vitalviewv5.R
import com.example.vitalviewv5.databinding.ActivityMainBinding
import com.example.vitalviewv5.data.ble.BleManager
import com.example.vitalviewv5.presentation.adapter.DeviceAdapter
import com.example.vitalviewv5.presentation.viewmodel.MainViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var deviceAdapter: DeviceAdapter

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            checkBluetoothEnabled()
        } else {
            showPermissionDeniedDialog()
        }
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            viewModel.startScan()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupObservers()
        setupClickListeners()
        checkPermissions()
    }

    private fun setupRecyclerView() {
        deviceAdapter = DeviceAdapter { device ->
            viewModel.stopScan()
            viewModel.connectToDevice(device)
        }

        binding.rvDevices.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = deviceAdapter
        }
    }

    private fun setupObservers() {
        // ✅ Proper lifecycle-aware collection
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Scan results
                launch {
                    viewModel.scanResults.collect { results ->
                        deviceAdapter.submitList(results)
                        binding.tvNoDevices.visibility = if (results.isEmpty())
                            View.VISIBLE else View.GONE
                    }
                }

                // Connection state
                launch {
                    viewModel.connectionState.collect { state ->
                        updateConnectionUI(state)
                    }
                }

                // ✅ Heart Rate - This is crucial!
                launch {
                    viewModel.heartRate.collect { data ->
                        Timber.d("🔄 UI updating heart rate: $data")
                        binding.tvHeartRate.text = data?.heartRate?.toString() ?: "--"
                    }
                }

                // ✅ Blood Oxygen
                launch {
                    viewModel.bloodOxygen.collect { data ->
                        Timber.d("🔄 UI updating SpO2: $data")
                        binding.tvBloodOxygen.text = data?.spo2?.toString() ?: "--"
                    }
                }

                // ✅ Blood Pressure
                launch {
                    viewModel.bloodPressure.collect { data ->
                        Timber.d("🔄 UI updating BP: $data")
                        binding.tvBloodPressure.text = if (data != null) {
                            "${data.systolic}/${data.diastolic}"
                        } else {
                            "--/--"
                        }
                    }
                }

                // ✅ Temperature
                launch {
                    viewModel.temperature.collect { data ->
                        Timber.d("🔄 UI updating temp: $data")
                        binding.tvTemperature.text = data?.temperature?.let {
                            String.format("%.1f°C", it)
                        } ?: "--"
                    }
                }

                // ✅ Steps
                launch {
                    viewModel.steps.collect { data ->
                        Timber.d("🔄 UI updating steps: $data")
                        binding.tvSteps.text = data?.steps?.toString() ?: "0"
                    }
                }

                // ✅ Sleep
                launch {
                    viewModel.sleepDuration.collect { duration ->
                        Timber.d("🔄 UI updating sleep: $duration")
                        binding.tvSleep.text = duration
                    }
                }

                // Battery Level
                launch {
                    viewModel.batteryLevel.collect { level ->
                        binding.tvBatteryLevel.text = if (level >= 0) "Battery: $level%" else "Battery: --%"
                    }
                }

                // Scanning state
                launch {
                    viewModel.isScanning.collect { isScanning ->
                        binding.btnScan.text = if (isScanning) "Stop Scan" else "Find Devices"
                        binding.progressBar.visibility = if (isScanning)
                            View.VISIBLE else View.GONE
                    }
                }

                // Syncing state
                launch {
                    viewModel.isSyncing.collect { isSyncing ->
                        binding.btnSync.isEnabled = !isSyncing
                        binding.btnSync.text = if (isSyncing) "Syncing..." else "Sync All Health Data"
                    }
                }
            }
        }
    }
    private fun setupClickListeners() {
        binding.btnScan.setOnClickListener {
            if (viewModel.isScanning.value) {
                viewModel.stopScan()
            } else {
                checkPermissions()
            }
        }

        binding.btnDisconnect.setOnClickListener {
            viewModel.disconnect()
        }

        binding.btnSync.setOnClickListener {
            viewModel.syncData()
        }

        binding.cardHeartRate.setOnClickListener { openDetail("Heart Rate") }
        binding.cardSpO2.setOnClickListener { openDetail("SpO2") }
        binding.cardBloodPressure.setOnClickListener { openDetail("Blood Pressure") }
        binding.cardTemperature.setOnClickListener { openDetail("Temperature") }
        binding.cardSteps.setOnClickListener { openDetail("Steps") }
        binding.cardSleep.setOnClickListener { openDetail("Sleep") }
    }

    private fun openDetail(metricName: String) {
        val intent = Intent(this, MetricDetailActivity::class.java)
        intent.putExtra("METRIC_NAME", metricName)
        startActivity(intent)
    }

    private fun updateConnectionUI(state: BleManager.ConnectionState) {
        when (state) {
            is BleManager.ConnectionState.Disconnected -> {
                binding.tvConnectionStatus.text = "Disconnected"
                binding.tvConnectionStatus.setBackgroundResource(R.drawable.bg_status_chip) // Reset or set red
                // Ideally create a specific red/green drawable or use tint, but keeping simple for now
                binding.layoutDeviceList.visibility = View.VISIBLE
                binding.layoutHealthData.visibility = View.GONE
                binding.layoutActions.visibility = View.GONE
                binding.tvBatteryLevel.visibility = View.GONE
            }
            is BleManager.ConnectionState.Connecting -> {
                binding.tvConnectionStatus.text = "Connecting..."
            }
            is BleManager.ConnectionState.Connected -> {
                binding.tvConnectionStatus.text = "Connected"
            }
            is BleManager.ConnectionState.Ready -> {
                binding.tvConnectionStatus.text = "Connected" // "Ready" might confuse users, "Connected" is friendlier
                binding.layoutDeviceList.visibility = View.GONE
                binding.layoutHealthData.visibility = View.VISIBLE
                binding.layoutActions.visibility = View.VISIBLE
                binding.tvBatteryLevel.visibility = View.VISIBLE
            }
        }
    }

    private fun checkPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }

        val notGranted = permissions.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isEmpty()) {
            checkBluetoothEnabled()
        } else {
            requestPermissionLauncher.launch(notGranted.toTypedArray())
        }
    }

    private fun checkBluetoothEnabled() {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter?.isEnabled == false) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
        } else {
            viewModel.startScan()
        }
    }

    private fun showPermissionDeniedDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Permissions Required")
            .setMessage("Bluetooth and Location permissions are required to scan for devices.")
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.disconnect()
    }
}
