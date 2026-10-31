package com.example.financeapp.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "group_transactions",
    indices = [
        Index("transactionDate"),
        Index("relatedClientId"),
        Index("relatedLoanId")
    ]
)
data class GroupTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,                 // See GroupTxType enum below
    val amount: Double,               // Always positive decimal
    val channel: String,              // "CASH" or "BANK"
    val description: String,
    val relatedClientId: Long? = null,
    val relatedLoanId: Long? = null,
    val relatedSavingsAccountId: Long? = null,
    val recordedByUserId: Long,
    val transactionDate: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)

enum class GroupTxType {
    SAVINGS_DEPOSIT,      // Inflow (+)
    SAVINGS_WITHDRAWAL,   // Outflow (-)
    LOAN_DISBURSEMENT,    // Outflow (-)
    LOAN_REPAYMENT,       // Inflow (+) (splits into principal + interest)
    INTEREST_INCOME,      // Income earned from bank deposit or penalties
    EXPENSE,              // Outflow: stationery, refreshments, audit fees
    BANK_DEPOSIT,         // Transfer: Cash -> Bank (Net 0)
    BANK_WITHDRAWAL       // Transfer: Bank -> Cash (Net 0)
}

@composable
fun GroupTxType.displayName: String = when (this) {
    SAVINGS_DEPOSIT -> "Savings Deposit"
    SAVINGS_WITHDRAWAL -> "Savings Withdrawal"
    LOAN_DISBURSEMENT -> "Loan Disbursement"
    LOAN_REPAYMENT -> "Loan Repayment"
    INTEREST_INCOME -> "Interest Income"
    EXPENSE -> "Expense"
    BANK_DEPOSIT -> "Cash → Bank Transfer"
    BANK_WITHDRAWAL -> "Bank → Cash Transfer"
}
