package com.example.vitalviewv5.data.local.dao

import androidx.room.*
import com.example.vitalviewv5.data.local.entity.HeartRateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HeartRateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(heartRate: HeartRateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(heartRates: List<HeartRateEntity>)

    @Query("SELECT * FROM heart_rate ORDER BY timestamp DESC LIMIT 1")
    fun getLatest(): Flow<HeartRateEntity?>

    @Query("SELECT * FROM heart_rate WHERE date = :date ORDER BY timestamp DESC")
    fun getByDate(date: String): Flow<List<HeartRateEntity>>

    @Query("SELECT * FROM heart_rate ORDER BY timestamp DESC")
    fun getAll(): Flow<List<HeartRateEntity>>

    @Query("SELECT * FROM heart_rate ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<HeartRateEntity>>

    @Query("SELECT * FROM heart_rate WHERE timestamp >= :start AND timestamp <= :end ORDER BY timestamp ASC")
    fun getByRange(start: Long, end: Long): Flow<List<HeartRateEntity>>

    @Query("DELETE FROM heart_rate")
    suspend fun deleteAll()
}