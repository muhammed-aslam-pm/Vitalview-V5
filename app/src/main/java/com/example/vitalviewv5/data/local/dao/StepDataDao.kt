package com.example.vitalviewv5.data.local.dao

import androidx.room.*
import com.example.vitalviewv5.data.local.entity.StepDataEntity
import kotlinx.coroutines.flow.Flow
@Dao
interface StepDataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(stepData: StepDataEntity)

    @Query("SELECT * FROM steps ORDER BY timestamp DESC LIMIT 1")
    fun getLatest(): Flow<StepDataEntity?>

    @Query("SELECT * FROM steps WHERE date = :date")
    fun getByDate(date: String): Flow<StepDataEntity?>

    @Query("SELECT * FROM steps ORDER BY timestamp DESC LIMIT 7")
    fun getLastWeek(): Flow<List<StepDataEntity>>

    @Query("SELECT * FROM steps ORDER BY timestamp DESC")
    fun getAll(): Flow<List<StepDataEntity>>

    @Query("SELECT * FROM steps ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<StepDataEntity>>
}