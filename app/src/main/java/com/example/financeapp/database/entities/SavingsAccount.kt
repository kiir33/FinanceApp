package com.example.financeapp.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "savings_accounts",
    foreignKeys = [
        ForeignKey(
            entity = Client::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("clientId")]
)
data class SavingsAccount(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientId: Long,
    val accountNumber: String,
    val balance: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis()
)
