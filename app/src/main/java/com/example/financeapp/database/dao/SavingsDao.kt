package com.example.financeapp.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.financeapp.database.entities.SavingsAccount
import com.example.financeapp.database.entities.SavingsTransaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SavingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: SavingsAccount): Long

    @Update
    suspend fun updateAccount(account: SavingsAccount)

    @Query("SELECT * FROM savings_accounts WHERE clientId = :clientId LIMIT 1")
    fun getAccountByClientId(clientId: Long): Flow<SavingsAccount?>

    @Query("SELECT * FROM savings_accounts WHERE id = :accountId LIMIT 1")
    suspend fun getAccountById(accountId: Long): SavingsAccount?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: SavingsTransaction): Long

    @Query("SELECT * FROM savings_transactions WHERE accountId = :accountId ORDER BY timestamp DESC")
    fun getTransactionsByAccountId(accountId: Long): Flow<List<SavingsTransaction>>

    @Query("SELECT * FROM savings_accounts ORDER BY id ASC")
    fun getAllSavingsAccounts(): Flow<List<SavingsAccount>>

    @Query("SELECT SUM(balance) FROM savings_accounts")
    fun getTotalSavings(): Flow<Double?>

    @Transaction
    suspend fun performDeposit(accountId: Long, amount: Double, description: String, transactionDate: Long = System.currentTimeMillis()) {
        val account = getAccountById(accountId) ?: return
        val newBalance = account.balance + amount
        updateAccount(account.copy(balance = newBalance))
        insertTransaction(SavingsTransaction(accountId = accountId, amount = amount, type = "DEPOSIT", description = description, transactionDate = transactionDate))
    }

    @Transaction
    suspend fun performWithdrawal(accountId: Long, amount: Double, description: String): Boolean {
        val account = getAccountById(accountId) ?: return false
        if (account.balance < amount) return false
        val newBalance = account.balance - amount
        updateAccount(account.copy(balance = newBalance))
        insertTransaction(SavingsTransaction(accountId = accountId, amount = -amount, type = "WITHDRAWAL", description = description))
        return true
    }
}
