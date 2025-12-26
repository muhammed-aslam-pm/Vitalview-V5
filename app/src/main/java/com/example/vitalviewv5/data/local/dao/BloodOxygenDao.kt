package com.example.vitalviewv5.data.local.dao

import androidx.room.*
import com.example.vitalviewv5.data.local.entity.BloodOxygenEntity
import kotlinx.coroutines.flow.Flow
@Dao
interface BloodOxygenDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bloodOxygen: BloodOxygenEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(bloodOxygenList: List<BloodOxygenEntity>)

    @Query("SELECT * FROM blood_oxygen ORDER BY timestamp DESC LIMIT 1")
    fun getLatest(): Flow<BloodOxygenEntity?>

    @Query("SELECT * FROM blood_oxygen WHERE date = :date ORDER BY timestamp DESC")
    fun getByDate(date: String): Flow<List<BloodOxygenEntity>>

    @Query("SELECT * FROM blood_oxygen ORDER BY timestamp DESC")
    fun getAll(): Flow<List<BloodOxygenEntity>>
}
