package com.example.financeapp.data

import com.example.financeapp.database.AppDatabase
import com.example.financeapp.database.entities.Client
import com.example.financeapp.database.entities.Loan
import com.example.financeapp.database.entities.LoanRepayment
import com.example.financeapp.database.entities.SavingsAccount
import com.example.financeapp.database.entities.SavingsTransaction
import kotlinx.coroutines.flow.Flow

class BankRepository(private val db: AppDatabase) {
    private val clientDao = db.clientDao()
    private val savingsDao = db.savingsDao()
    private val loanDao = db.loanDao()

    val allClients: Flow<List<Client>> = clientDao.getAllClients()
    val totalSavings: Flow<Double?> = savingsDao.getTotalSavings()
    val totalOutstandingLoans: Flow<Double?> = loanDao.getTotalOutstandingLoans()
    val allLoans: Flow<List<Loan>> = loanDao.getAllLoans()
    val totalInterestEarned: Flow<Double?> = loanDao.getTotalInterestEarned()
    val clientCount: Flow<Int> = clientDao.getClientCount()

    suspend fun insertClient(client: Client): Long {
        return clientDao.insertClient(client)
    }

    suspend fun updateClient(client: Client) {
        clientDao.updateClient(client)
    }

    suspend fun getClientById(id: Long): Client? {
        return clientDao.getClientById(id)
    }

    suspend fun getClientByClientId(clientId: Int): Client? {
        return clientDao.getClientByClientId(clientId)
    }

    fun searchClients(query: String): Flow<List<Client>> {
        return clientDao.searchClients("%$query%")
    }

    // Savings Account
    suspend fun insertSavingsAccount(account: SavingsAccount): Long {
        return savingsDao.insertAccount(account)
    }

    fun getSavingsAccountForClient(clientId: Long): Flow<SavingsAccount?> {
        return savingsDao.getAccountByClientId(clientId)
    }

    suspend fun getSavingsAccountById(accountId: Long): SavingsAccount? {
        return savingsDao.getAccountById(accountId)
    }

    // Savings Transactions
    suspend fun performDeposit(accountId: Long, amount: Double, description: String, transactionDate: Long = System.currentTimeMillis()) {
        savingsDao.performDeposit(accountId, amount, description, transactionDate)
    }

    suspend fun performWithdrawal(accountId: Long, amount: Double, description: String): Boolean {
        return savingsDao.performWithdrawal(accountId, amount, description)
    }

    fun getSavingsTransactions(accountId: Long): Flow<List<SavingsTransaction>> {
        return savingsDao.getTransactionsByAccountId(accountId)
    }

    // Loans
    suspend fun disburseLoan(loan: Loan): Long {
        return loanDao.insertLoan(loan)
    }

    fun getLoansForClient(clientId: Long): Flow<List<Loan>> {
        return loanDao.getLoansByClientId(clientId)
    }

    suspend fun getLoanById(loanId: Long): Loan? {
        return loanDao.getLoanById(loanId)
    }

    // Repayments
    suspend fun repayLoan(loanId: Long, amount: Double, principalPaid: Double, interestPaid: Double, timestamp: Long = System.currentTimeMillis()): Boolean {
        return loanDao.repayLoan(loanId, amount, principalPaid, interestPaid, timestamp)
    }

    fun getLoanRepayments(loanId: Long): Flow<List<LoanRepayment>> {
        return loanDao.getRepaymentsByLoanId(loanId)
    }

    fun getAllSavingsAccounts(): Flow<List<SavingsAccount>> {
        return savingsDao.getAllSavingsAccounts()
    }

    suspend fun getActiveLoansGuaranteedCount(guarantorId: Long): Int {
        return loanDao.getActiveLoansGuaranteedCount(guarantorId)
    }

    suspend fun getActiveLoansCount(clientId: Long): Int {
        return loanDao.getActiveLoansCount(clientId)
    }
}
