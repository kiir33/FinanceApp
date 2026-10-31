package com.example.financeapp.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "users",
    foreignKeys = [
        ForeignKey(
            entity = Client::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["username"], unique = true),
        Index(value = ["clientId"], unique = true)
    ]
)
data class User(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,             // Unique login handle
    val passwordHash: String,         // Argon2id / PBKDF2 hash
    val salt: String,                 // 16-byte hex-encoded random salt
    val role: String,                 // "ADMIN" or "MEMBER"
    val clientId: Long? = null,       // Linked Client ID (null for pure admins)
    val isActive: Boolean = true,
    val failedAttempts: Int = 0,      // Max 5 before temporary lock
    val lockedUntil: Long? = null,    // Lockout epoch timestamp (millis)
    val mustChangePassword: Boolean = true, // Force change on first login
    val createdAt: Long = System.currentTimeMillis(),
    val lastLoginAt: Long? = null
)
