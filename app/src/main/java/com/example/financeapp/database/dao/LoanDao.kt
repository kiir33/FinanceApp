package com.example.financeapp.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.financeapp.database.entities.Loan
import com.example.financeapp.database.entities.LoanRepayment
import kotlinx.coroutines.flow.Flow

@Dao
interface LoanDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLoan(loan: Loan): Long

    @Update
    suspend fun updateLoan(loan: Loan)

    @Query("SELECT * FROM loans WHERE clientId = :clientId ORDER BY createdAt DESC")
    fun getLoansByClientId(clientId: Long): Flow<List<Loan>>

    @Query("SELECT * FROM loans ORDER BY loanDate DESC")
    fun getAllLoans(): Flow<List<Loan>>

    @Query("SELECT COUNT(*) FROM loans WHERE guarantorId = :guarantorId AND status = 'ACTIVE'")
    suspend fun getActiveLoansGuaranteedCount(guarantorId: Long): Int

    @Query("SELECT COUNT(*) FROM loans WHERE clientId = :clientId AND status = 'ACTIVE'")
    suspend fun getActiveLoansCount(clientId: Long): Int

    @Query("SELECT SUM(interestPaid) FROM loan_repayments")
    fun getTotalInterestEarned(): Flow<Double?>

    @Query("SELECT * FROM loans WHERE id = :loanId LIMIT 1")
    suspend fun getLoanById(loanId: Long): Loan?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRepayment(repayment: LoanRepayment): Long

    @Query("SELECT * FROM loan_repayments WHERE loanId = :loanId ORDER BY timestamp DESC")
    fun getRepaymentsByLoanId(loanId: Long): Flow<List<LoanRepayment>>

    @Query("SELECT SUM(outstandingBalance) FROM loans WHERE status = 'ACTIVE'")
    fun getTotalOutstandingLoans(): Flow<Double?>

    // Core member-only operations (no treasury sync)
    @Transaction
    suspend fun repayLoanMemberOnly(loanId: Long, amount: Double, principalPaid: Double, interestPaid: Double, timestamp: Long = System.currentTimeMillis()): Boolean {
        val loan = getLoanById(loanId) ?: return false
        val newPrincipalOutstanding = loan.outstandingBalance - principalPaid
        val status = if (newPrincipalOutstanding <= 0.0) "PAID_OFF" else "ACTIVE"
        val updatedLoan = loan.copy(
            outstandingBalance = if (newPrincipalOutstanding < 0.0) 0.0 else newPrincipalOutstanding,
            status = status
        )
        updateLoan(updatedLoan)
        insertRepayment(LoanRepayment(loanId = loanId, amount = amount, principalPaid = principalPaid, interestPaid = interestPaid, timestamp = timestamp))
        return true
    }

    @Query("SELECT * FROM loans WHERE status = 'ACTIVE' ORDER BY loanDate DESC")
    fun getActiveLoans(): Flow<List<Loan>>

    @Query("SELECT * FROM loan_repayments ORDER BY timestamp ASC")
    fun getAllRepayments(): Flow<List<LoanRepayment>>
}
