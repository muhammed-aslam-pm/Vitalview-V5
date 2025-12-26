package com.example.vitalviewv5.data.sdk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
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

    private val dataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val data = intent?.getByteArrayExtra("data")
            data?.let {
                try {
                    // Parse data and get the result via callback
                    BleSDK.DataParsingWithData(it, object : DataListener2301 {
                        override fun dataCallback(dataMap: MutableMap<String, Any>) {
                            Timber.d("Parsed data map: $dataMap")
                            currentDataCallback?.invoke(dataMap)
                        }

                        override fun dataCallback(p0: ByteArray?) {
                            TODO("Not yet implemented")
                        }
                    })
                } catch (e: Exception) {
                    Timber.e(e, "Error parsing BLE data")
                }
            }
        }
    }

    init {
        registerDataReceiver()
    }

    private fun registerDataReceiver() {
        val filter = IntentFilter("BLE_DATA_RECEIVED")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    dataReceiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(dataReceiver, filter)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error registering receiver")
        }
    }

    fun observeData(): Flow<Map<String, Any>> = callbackFlow {
        currentDataCallback = { dataMap ->
            Timber.d("SDK Data received: $dataMap")
            trySend(dataMap)
        }

        awaitClose {
            currentDataCallback = null
            Timber.d("Data observation closed")
        }
    }

    fun sendData(data: ByteArray) {
        Timber.d("Sending data to device: ${data.contentToString()}")
    }

    // SDK Commands - All static methods

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

    fun getDeviceTime(): ByteArray {
        return try {
            BleSDK.GetDeviceTime()
        } catch (e: Exception) {
            Timber.e(e, "Error getting device time")
            byteArrayOf()
        }
    }

    fun setPersonalInfo(age: Int, height: Int, weight: Int, stepLength: Int, gender: Int): ByteArray {
        return try {
            val personalInfo = MyPersonalInfo().apply {
                this.age = age
                this.height = height
                this.weight = weight
                this.stepLength = stepLength
                this.sex = gender
            }
            BleSDK.SetPersonalInfo(personalInfo)
        } catch (e: Exception) {
            Timber.e(e, "Error setting personal info")
            byteArrayOf()
        }
    }

    fun getBatteryLevel(): ByteArray {
        return try {
            BleSDK.GetDeviceBatteryLevel()
        } catch (e: Exception) {
            Timber.e(e, "Error getting battery level")
            byteArrayOf()
        }
    }

    fun getDeviceVersion(): ByteArray {
        return try {
            BleSDK.GetDeviceVersion()
        } catch (e: Exception) {
            Timber.e(e, "Error getting version")
            byteArrayOf()
        }
    }

    fun getMacAddress(): ByteArray {
        return try {
            BleSDK.GetDeviceMacAddress()
        } catch (e: Exception) {
            Timber.e(e, "Error getting MAC address")
            byteArrayOf()
        }
    }

    fun enableRealTimeData(enable: Boolean, tempEnable: Boolean): ByteArray {
        return try {
            BleSDK.RealTimeStep(enable, tempEnable)
        } catch (e: Exception) {
            Timber.e(e, "Error enabling real-time data")
            byteArrayOf()
        }
    }

    fun setDeviceMeasurement(dataType: AutoTestMode, seconds: Long, open: Boolean): ByteArray {
        return try {
            BleSDK.SetDeviceMeasurementWithType(dataType, seconds, open)
        } catch (e: Exception) {
            Timber.e(e, "Error setting device measurement")
            byteArrayOf()
        }
    }

    fun setAutomaticMonitoring(autoHeart: MyAutomaticHRMonitoring, type: AutoMode): ByteArray {
        return try {
            BleSDK.SetAutomaticHRMonitoring(autoHeart, type)
        } catch (e: Exception) {
            Timber.e(e, "Error setting automatic monitoring")
            byteArrayOf()
        }
    }

    fun getAutomaticSettings(type: AutoMode): ByteArray {
        return try {
            BleSDK.GetAutomatic(type)
        } catch (e: Exception) {
            Timber.e(e, "Error getting automatic settings")
            byteArrayOf()
        }
    }

    fun getHeartRateHistory(mode: Byte, lastDate: String?): ByteArray {
        return try {
            BleSDK.GetStaticHRWithMode(mode, lastDate ?: "")
        } catch (e: Exception) {
            Timber.e(e, "Error getting heart rate history")
            byteArrayOf()
        }
    }

    fun getDynamicHeartRateHistory(mode: Byte, lastDate: String?): ByteArray {
        return try {
            BleSDK.GetDynamicHRWithMode(mode, lastDate ?: "")
        } catch (e: Exception) {
            Timber.e(e, "Error getting dynamic heart rate history")
            byteArrayOf()
        }
    }

    fun getBloodOxygenHistory(mode: Byte, lastDate: String?): ByteArray {
        return try {
            BleSDK.Oxygen_data(mode, lastDate ?: "")
        } catch (e: Exception) {
            Timber.e(e, "Error getting blood oxygen history")
            byteArrayOf()
        }
    }

    fun getHRVHistory(mode: Byte, lastDate: String?): ByteArray {
        return try {
            BleSDK.GetHRVDataWithMode(mode, lastDate ?: "")
        } catch (e: Exception) {
            Timber.e(e, "Error getting HRV history")
            byteArrayOf()
        }
    }

    fun getSleepHistory(mode: Byte, lastDate: String?): ByteArray {
        return try {
            BleSDK.GetDetailSleepDataWithMode(mode, lastDate ?: "")
        } catch (e: Exception) {
            Timber.e(e, "Error getting sleep history")
            byteArrayOf()
        }
    }

    fun getDetailedSleepData(mode: Byte, lastDate: String?): ByteArray {
        return try {
            BleSDK.getObtainDetailedSleepData(mode, lastDate ?: "")
        } catch (e: Exception) {
            Timber.e(e, "Error getting detailed sleep data")
            byteArrayOf()
        }
    }

    fun getStepHistory(mode: Byte, lastDate: String?): ByteArray {
        return try {
            BleSDK.GetTotalActivityDataWithMode(mode, lastDate ?: "")
        } catch (e: Exception) {
            Timber.e(e, "Error getting step history")
            byteArrayOf()
        }
    }

    fun getDetailedStepData(mode: Byte, lastDate: String?): ByteArray {
        return try {
            BleSDK.GetDetailActivityDataWithMode(mode, lastDate ?: "")
        } catch (e: Exception) {
            Timber.e(e, "Error getting detailed step data")
            byteArrayOf()
        }
    }

    fun getTemperatureHistory(mode: Byte, lastDate: String?): ByteArray {
        return try {
            BleSDK.GetTemperature_historyData(mode, lastDate ?: "")
        } catch (e: Exception) {
            Timber.e(e, "Error getting temperature history")
            byteArrayOf()
        }
    }

    fun getActivityModeData(mode: Byte): ByteArray {
        return try {
            BleSDK.GetActivityModeDataWithMode(mode)
        } catch (e: Exception) {
            Timber.e(e, "Error getting activity mode data")
            byteArrayOf()
        }
    }

    fun findActivityMode(): ByteArray {
        return try {
            BleSDK.FindActivityMode()
        } catch (e: Exception) {
            Timber.e(e, "Error finding activity mode")
            byteArrayOf()
        }
    }

    fun enterActivityMode(time: Int, activityMode: Int, workMode: Int): ByteArray {
        return try {
            BleSDK.EnterActivityMode(time, activityMode, workMode)
        } catch (e: Exception) {
            Timber.e(e, "Error entering activity mode")
            byteArrayOf()
        }
    }

    fun getRealTimeTemperature(): ByteArray {
        return try {
            BleSDK.RealTimeTemperature()
        } catch (e: Exception) {
            Timber.e(e, "Error getting real-time temperature")
            byteArrayOf()
        }
    }

    fun resetDevice(): ByteArray {
        return try {
            BleSDK.Reset()
        } catch (e: Exception) {
            Timber.e(e, "Error resetting device")
            byteArrayOf()
        }
    }

    fun mcuReset(): ByteArray {
        return try {
            BleSDK.MCUReset()
        } catch (e: Exception) {
            Timber.e(e, "Error MCU reset")
            byteArrayOf()
        }
    }

    fun cleanup() {
        try {
            context.unregisterReceiver(dataReceiver)
            currentDataCallback = null
        } catch (e: Exception) {
            Timber.e(e, "Error unregistering receiver")
        }
    }
}
