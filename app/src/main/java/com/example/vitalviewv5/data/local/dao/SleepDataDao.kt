package com.example.vitalviewv5.data.local.dao

import androidx.room.*
import com.example.vitalviewv5.data.local.entity.SleepDataEntity
import kotlinx.coroutines.flow.Flow
@Dao
interface SleepDataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sleepList: List<SleepDataEntity>)

    @Query("SELECT * FROM sleep WHERE date LIKE :date || '%' ORDER BY timestamp ASC")
    fun getSleepByDate(date: String): Flow<List<SleepDataEntity>>

    @Query("SELECT * FROM sleep ORDER BY timestamp DESC LIMIT 500")
    fun getRecentSleep(): Flow<List<SleepDataEntity>>
}