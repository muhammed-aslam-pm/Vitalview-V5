package com.example.vitalviewv5.data.local.dao

import androidx.room.*
import com.example.vitalviewv5.data.local.entity.TemperatureEntity
import kotlinx.coroutines.flow.Flow
@Dao
interface TemperatureDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(temperature: TemperatureEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(temperatures: List<TemperatureEntity>)

    @Query("SELECT * FROM temperature ORDER BY timestamp DESC LIMIT 1")
    fun getLatest(): Flow<TemperatureEntity?>

    @Query("SELECT * FROM temperature WHERE date = :date ORDER BY timestamp DESC")
    fun getByDate(date: String): Flow<List<TemperatureEntity>>
}