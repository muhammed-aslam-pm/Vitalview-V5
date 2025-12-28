package com.example.vitalviewv5.data.sdk

import android.content.Context
import com.jstyle.blesdk2436.Util.BleSDK
import com.jstyle.blesdk2436.callback.DataListener2301
import com.jstyle.blesdk2436.model.AutoMode
import com.jstyle.blesdk2436.model.AutoTestMode
import com.jstyle.blesdk2436.model.MyAutomaticHRMonitoring
import com.jstyle.blesdk2436.model.MyDeviceTime
import com.jstyle.blesdk2436.model.MyPersonalInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FitnessBandSdkWrapper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var currentDataCallback: ((Map<String, Any>) -> Unit)? = null

    // Persistent listener for SDK callbacks
    private val dataListener = object : DataListener2301 {
        override fun dataCallback(dataMap: MutableMap<String, Any>) {
            Timber.d("✅ SDK Parsed: $dataMap")
            currentDataCallback?.invoke(dataMap)
        }

        override fun dataCallback(byteArray: ByteArray?) {
            Timber.d("⚠️ SDK returned ByteArray: ${byteArray?.contentToString()}")
        }
    }

    init {
        Timber.d("FitnessBandSdkWrapper initialized")
        try {
            val testData = BleSDK.GetDeviceTime()
            Timber.d("✅ SDK ready: ${testData.contentToString()}")
        } catch (e: Exception) {
            Timber.e(e, "❌ SDK init error")
        }
    }

    /**
     * Process raw BLE data directly through the SDK
     */
    fun processRawData(data: ByteArray) {
        try {
            Timber.d("🔄 Processing ${data.size} bytes through SDK")
            BleSDK.DataParsingWithData(data, dataListener)
        } catch (e: Exception) {
            Timber.e(e, "❌ SDK parsing error")
        }
    }

    /**
     * Observe parsed data from SDK
     */
    fun observeData(): Flow<Map<String, Any>> = callbackFlow {
        Timber.d("📡 Starting SDK data observation")

        currentDataCallback = { dataMap ->
            Timber.d("🔄 Forwarding to repository: $dataMap")
            trySend(dataMap)
        }

        awaitClose {
            currentDataCallback = null
            Timber.d("🔌 SDK observation closed")
        }
    }

    // SDK command methods remain the same...
    fun setDeviceTime(): ByteArray {
        return try {
            val calendar = Calendar.getInstance()
            val setTime = MyDeviceTime().apply {
                year = calendar.get(Calendar.YEAR)
                month = calendar.get(Calendar.MONTH) + 1
                day = calendar.get(Calendar.DAY_OF_MONTH)
                hour = calendar.get(Calendar.HOUR_OF_DAY)
                minute = calendar.get(Calendar.MINUTE)
                second = calendar.get(Calendar.SECOND)
            }
            BleSDK.SetDeviceTime(setTime)
        } catch (e: Exception) {
            Timber.e(e, "Error setting device time")
            byteArrayOf()
        }
    }

    fun getBatteryLevel(): ByteArray = try { BleSDK.GetDeviceBatteryLevel() } catch (e: Exception) { Timber.e(e, "Error"); byteArrayOf() }
    fun getDeviceVersion(): ByteArray = try { BleSDK.GetDeviceVersion() } catch (e: Exception) { Timber.e(e, "Error"); byteArrayOf() }
    fun getMacAddress(): ByteArray = try { BleSDK.GetDeviceMacAddress() } catch (e: Exception) { Timber.e(e, "Error"); byteArrayOf() }
    fun enableRealTimeData(enable: Boolean, tempEnable: Boolean): ByteArray = try { BleSDK.RealTimeStep(enable, tempEnable) } catch (e: Exception) { Timber.e(e, "Error"); byteArrayOf() }
    fun getHeartRateHistory(mode: Byte, lastDate: String?): ByteArray = try { BleSDK.GetStaticHRWithMode(mode, lastDate ?: "") } catch (e: Exception) { Timber.e(e, "Error"); byteArrayOf() }
    fun getBloodOxygenHistory(mode: Byte, lastDate: String?): ByteArray = try { BleSDK.Oxygen_data(mode, lastDate ?: "") } catch (e: Exception) { Timber.e(e, "Error"); byteArrayOf() }
    fun getHRVHistory(mode: Byte, lastDate: String?): ByteArray = try { BleSDK.GetHRVDataWithMode(mode, lastDate ?: "") } catch (e: Exception) { Timber.e(e, "Error"); byteArrayOf() }
    fun getSleepHistory(mode: Byte, lastDate: String?): ByteArray = try { BleSDK.GetDetailSleepDataWithMode(mode, lastDate ?: "") } catch (e: Exception) { Timber.e(e, "Error"); byteArrayOf() }
    fun getStepHistory(mode: Byte, lastDate: String?): ByteArray = try { BleSDK.GetTotalActivityDataWithMode(mode, lastDate ?: "") } catch (e: Exception) { Timber.e(e, "Error"); byteArrayOf() }
    fun getTemperatureHistory(mode: Byte, lastDate: String?): ByteArray = try { BleSDK.GetTemperature_historyData(mode, lastDate ?: "") } catch (e: Exception) { Timber.e(e, "Error"); byteArrayOf() }

    
    // Spot Measurement (AutoTestMode)
    fun setDeviceMeasurementWithType(mode: AutoTestMode, seconds: Long, open: Boolean): ByteArray = 
        try { BleSDK.SetDeviceMeasurementWithType(mode, seconds, open) } catch (e: Exception) { Timber.e(e, "Error"); byteArrayOf() }
}
