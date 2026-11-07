package com.example.financeapp.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "clients",
    indices = [
        Index(value = ["clientId"], unique = true),
        Index(value = ["phoneNumber"], unique = true),
        Index(value = ["clientId", "name"], unique = true)
    ]
)
data class Client(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientId: Int,
    val name: String,
    val phoneNumber: String,
    val address: String,
    val createdAt: Long = System.currentTimeMillis()
)
