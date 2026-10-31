package com.example.financeapp.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "group_accounts")
data class GroupAccount(
    @PrimaryKey val id: Long = 1,     // Fixed single row ID
    val name: String = "Group Treasury",
    val cashBalance: Double = 0.0,    // Cash on hand in treasury box
    val bankBalance: Double = 0.0,    // Balance in commercial bank account
    val bankName: String = "",
    val bankAccountNumber: String = "",
    val lastUpdated: Long = System.currentTimeMillis()
)
