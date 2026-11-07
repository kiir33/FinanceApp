package com.example.financeapp.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.financeapp.database.entities.AppSettings

@Dao
interface AppSettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetting(setting: AppSettings)

    @Query("SELECT value FROM app_settings WHERE key = :key LIMIT 1")
    suspend fun getSettingValue(key: String): String?

    @Query("DELETE FROM app_settings WHERE key = :key")
    suspend fun deleteSetting(key: String)

    @Query("SELECT * FROM app_settings")
    suspend fun getAllSettings(): List<AppSettings>
}