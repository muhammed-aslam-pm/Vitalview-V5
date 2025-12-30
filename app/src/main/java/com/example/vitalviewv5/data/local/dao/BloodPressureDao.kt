package com.example.vitalviewv5.data.local.dao

import androidx.room.*
import com.example.vitalviewv5.data.local.entity.BloodPressureEntity
import kotlinx.coroutines.flow.Flow
@Dao
interface BloodPressureDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bloodPressure: BloodPressureEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(bloodPressureList: List<BloodPressureEntity>)

    @Query("SELECT * FROM blood_pressure ORDER BY timestamp DESC LIMIT 1")
    fun getLatest(): Flow<BloodPressureEntity?>

    @Query("SELECT * FROM blood_pressure WHERE date = :date ORDER BY timestamp DESC")
    fun getByDate(date: String): Flow<List<BloodPressureEntity>>

    @Query("SELECT * FROM blood_pressure ORDER BY timestamp DESC")
    fun getAll(): Flow<List<BloodPressureEntity>>

    @Query("SELECT * FROM blood_pressure ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<BloodPressureEntity>>
}