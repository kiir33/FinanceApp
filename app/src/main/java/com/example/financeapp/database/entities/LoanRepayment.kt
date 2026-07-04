package com.example.financeapp.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "loan_repayments",
    foreignKeys = [
        ForeignKey(
            entity = Loan::class,
            parentColumns = ["id"],
            childColumns = ["loanId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("loanId")]
)
data class LoanRepayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val loanId: Long,
    val amount: Double,
    val principalPaid: Double,
    val interestPaid: Double,
    val timestamp: Long = System.currentTimeMillis()
)
