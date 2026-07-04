package com.example.financeapp.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "savings_transactions",
    foreignKeys = [
        ForeignKey(
            entity = SavingsAccount::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("accountId")]
)
data class SavingsTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val amount: Double,
    val type: String, // "DEPOSIT" or "WITHDRAWAL"
    val description: String,
    val transactionDate: Long = System.currentTimeMillis(),
    val timestamp: Long = System.currentTimeMillis() // Record creation date
)
