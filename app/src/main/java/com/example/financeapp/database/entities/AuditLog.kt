package com.example.financeapp.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "audit_logs",
    indices = [Index("timestamp"), Index("userId")]
)
data class AuditLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val username: String,
    val action: String,               // e.g. "USER_LOGIN", "DISBURSE_LOAN", "CSV_EXPORT"
    val details: String,              // Human-readable summary or JSON details
    val timestamp: Long = System.currentTimeMillis()
)
