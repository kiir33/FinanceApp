package com.example.financeapp.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.financeapp.database.entities.AuditLog
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: AuditLog): Long

    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC")
    fun getAllAuditLogs(): Flow<List<AuditLog>>

    @Query("SELECT * FROM audit_logs WHERE userId = :userId ORDER BY timestamp DESC")
    fun getAuditLogsByUserId(userId: Long): Flow<List<AuditLog>>

    @Query("SELECT * FROM audit_logs WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp DESC")
    fun getAuditLogsBetween(from: Long, to: Long): Flow<List<AuditLog>>

    @Query("SELECT * FROM audit_logs WHERE action = :action ORDER BY timestamp DESC")
    fun getAuditLogsByAction(action: String): Flow<List<AuditLog>>

    @Query("SELECT COUNT(*) FROM audit_logs")
    suspend fun getAuditLogCount(): Int
}