package com.example.vitalviewv5.data.ble

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleManager @Inject constructor(
    private val context: Context
) {
    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var bluetoothLeScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

    private var bluetoothGatt: BluetoothGatt? = null
    private var characteristicWrite: BluetoothGattCharacteristic? = null
    private var characteristicNotify: BluetoothGattCharacteristic? = null

    // UUIDs - Update these based on your device
    companion object {
        private const val SERVICE_UUID = "0000fff0-0000-1000-8000-00805f9b34fb"
        private const val CHARACTERISTIC_WRITE_UUID = "0000fff1-0000-1000-8000-00805f9b34fb"
        private const val CHARACTERISTIC_NOTIFY_UUID = "0000fff2-0000-1000-8000-00805f9b34fb"
        private val CLIENT_CHARACTERISTIC_CONFIG =
            java.util.UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(
                        context, Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun scanForDevices(): Flow<ScanResult> = callbackFlow {
        if (!hasBluetoothPermissions()) {
            close(SecurityException("Missing Bluetooth permissions"))
            return@callbackFlow
        }

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                trySend(result)
            }

            override fun onScanFailed(errorCode: Int) {
                Timber.e("Scan failed with error: $errorCode")
                close(Exception("Scan failed: $errorCode"))
            }
        }

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            bluetoothLeScanner?.startScan(null, scanSettings, scanCallback)
            Timber.d("BLE scan started")
        } catch (e: SecurityException) {
            close(e)
        }

        awaitClose {
            try {
                bluetoothLeScanner?.stopScan(scanCallback)
                Timber.d("BLE scan stopped")
            } catch (e: SecurityException) {
                Timber.e(e, "Error stopping scan")
            }
        }
    }

    fun connectToDevice(
        device: BluetoothDevice,
        onDataReceived: (ByteArray) -> Unit
    ): Flow<ConnectionState> = callbackFlow {
        if (!hasBluetoothPermissions()) {
            close(SecurityException("Missing Bluetooth permissions"))
            return@callbackFlow
        }

        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int
            ) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Timber.d("Connected to GATT server")
                        trySend(ConnectionState.Connected)
                        try {
                            gatt.discoverServices()
                        } catch (e: SecurityException) {
                            Timber.e(e, "Error discovering services")
                        }
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Timber.d("Disconnected from GATT server")
                        trySend(ConnectionState.Disconnected)
                        close()
                    }
                    BluetoothProfile.STATE_CONNECTING -> {
                        trySend(ConnectionState.Connecting)
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Timber.d("Services discovered")

                    val service = gatt.getService(java.util.UUID.fromString(SERVICE_UUID))
                    characteristicWrite = service?.getCharacteristic(
                        java.util.UUID.fromString(CHARACTERISTIC_WRITE_UUID)
                    )
                    characteristicNotify = service?.getCharacteristic(
                        java.util.UUID.fromString(CHARACTERISTIC_NOTIFY_UUID)
                    )

                    characteristicNotify?.let { characteristic ->
                        try {
                            gatt.setCharacteristicNotification(characteristic, true)
                            val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
                            descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(descriptor)

                            trySend(ConnectionState.Ready)
                        } catch (e: SecurityException) {
                            Timber.e(e, "Error enabling notifications")
                        }
                    }
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                val data = characteristic.value
                Timber.d("Data received: ${data.contentToString()}")
                onDataReceived(data)
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Timber.d("Characteristic write successful")
                } else {
                    Timber.e("Characteristic write failed: $status")
                }
            }
        }

        try {
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
            trySend(ConnectionState.Connecting)
        } catch (e: SecurityException) {
            close(e)
        }

        awaitClose {
            disconnect()
        }
    }

    fun writeData(data: ByteArray): Boolean {
        if (!hasBluetoothPermissions()) return false

        return try {
            characteristicWrite?.let { characteristic ->
                characteristic.value = data
                bluetoothGatt?.writeCharacteristic(characteristic) ?: false
            } ?: false
        } catch (e: SecurityException) {
            Timber.e(e, "Error writing data")
            false
        }
    }

    fun disconnect() {
        if (!hasBluetoothPermissions()) return

        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
            bluetoothGatt = null
            Timber.d("Disconnected from device")
        } catch (e: SecurityException) {
            Timber.e(e, "Error disconnecting")
        }
    }

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
        object Ready : ConnectionState()
    }
}
