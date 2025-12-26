package com.example.vitalviewv5.di

import android.content.Context
import androidx.room.Room
import com.example.vitalviewv5.data.ble.BleManager
import com.example.vitalviewv5.data.local.FitnessBandDatabase
import com.example.vitalviewv5.data.repository.FitnessBandRepository
import com.example.vitalviewv5.data.sdk.FitnessBandSdkWrapper
import com.example.vitalviewv5.domain.repository.IFitnessBandRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FitnessBandDatabase {
        return Room.databaseBuilder(
            context,
            FitnessBandDatabase::class.java,
            "fitness_band_db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideBleManager(@ApplicationContext context: Context): BleManager {
        return BleManager(context)
    }

    @Provides
    @Singleton
    fun provideSdkWrapper(@ApplicationContext context: Context): FitnessBandSdkWrapper {
        return FitnessBandSdkWrapper(context)
    }

    @Provides
    @Singleton
    fun provideRepository(
        bleManager: BleManager,
        sdkWrapper: FitnessBandSdkWrapper,
        database: FitnessBandDatabase
    ): IFitnessBandRepository {
        return FitnessBandRepository(bleManager, sdkWrapper, database)
    }
}
