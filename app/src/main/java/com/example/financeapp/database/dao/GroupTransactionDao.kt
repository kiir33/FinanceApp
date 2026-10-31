package com.example.financeapp.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.financeapp.database.entities.GroupTransaction
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupTransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: GroupTransaction): Long

    @Query("SELECT * FROM group_transactions ORDER BY transactionDate DESC")
    fun getAllTransactions(): Flow<List<GroupTransaction>>

    @Query("SELECT * FROM group_transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionById(id: Long): GroupTransaction?

    @Query("SELECT * FROM group_transactions WHERE relatedClientId = :clientId ORDER BY transactionDate DESC")
    fun getTransactionsByClientId(clientId: Long): Flow<List<GroupTransaction>>

    @Query("SELECT * FROM group_transactions WHERE relatedLoanId = :loanId ORDER BY transactionDate DESC")
    fun getTransactionsByLoanId(loanId: Long): Flow<List<GroupTransaction>>

    @Query("SELECT * FROM group_transactions WHERE transactionDate BETWEEN :from AND :to ORDER BY transactionDate DESC")
    fun getTransactionsBetween(from: Long, to: Long): Flow<List<GroupTransaction>>

    @Query("SELECT * FROM group_transactions WHERE type = :type ORDER BY transactionDate DESC")
    fun getTransactionsByType(type: String): Flow<List<GroupTransaction>>

    @Query("SELECT SUM(amount) FROM group_transactions WHERE type = 'EXPENSE'")
    fun getExpensesTotal(): Flow<Double?>

    @Query("SELECT SUM(amount) FROM group_transactions WHERE type = 'INTEREST_INCOME'")
    fun getInterestIncomeTotal(): Flow<Double?>

    @Query("SELECT * FROM group_transactions ORDER BY transactionDate DESC LIMIT :limit")
    fun getRecentTransactions(limit: Int): Flow<List<GroupTransaction>>
}
