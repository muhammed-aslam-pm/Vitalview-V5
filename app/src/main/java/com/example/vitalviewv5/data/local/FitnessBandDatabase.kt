package com.example.vitalviewv5.data.local
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.vitalviewv5.data.local.dao.BloodOxygenDao
import com.example.vitalviewv5.data.local.dao.BloodPressureDao
import com.example.vitalviewv5.data.local.dao.HeartRateDao
import com.example.vitalviewv5.data.local.dao.SleepDataDao
import com.example.vitalviewv5.data.local.dao.StepDataDao
import com.example.vitalviewv5.data.local.dao.TemperatureDao

import com.example.vitalviewv5.data.local.entity.BloodOxygenEntity
import com.example.vitalviewv5.data.local.entity.BloodPressureEntity
import com.example.vitalviewv5.data.local.entity.HeartRateEntity
import com.example.vitalviewv5.data.local.entity.SleepDataEntity
import com.example.vitalviewv5.data.local.entity.StepDataEntity
import com.example.vitalviewv5.data.local.entity.TemperatureEntity

@Database(
    entities = [
        HeartRateEntity::class,
        BloodOxygenEntity::class,
        BloodPressureEntity::class,
        StepDataEntity::class,
        SleepDataEntity::class,
        TemperatureEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class FitnessBandDatabase : RoomDatabase() {
    abstract fun heartRateDao(): HeartRateDao
    abstract fun bloodOxygenDao(): BloodOxygenDao
    abstract fun bloodPressureDao(): BloodPressureDao
    abstract fun stepDataDao(): StepDataDao
    abstract fun sleepDataDao(): SleepDataDao
    abstract fun temperatureDao(): TemperatureDao
}
