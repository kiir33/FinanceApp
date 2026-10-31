package com.example.financeapp.data

import androidx.room.withTransaction
import com.example.financeapp.database.AppDatabase
import com.example.financeapp.database.dao.GroupAccountDao
import com.example.financeapp.database.dao.GroupTransactionDao
import com.example.financeapp.database.entities.AuditLog
import com.example.financeapp.database.entities.Client
import com.example.financeapp.database.entities.GroupAccount
import com.example.financeapp.database.entities.GroupTransaction
import com.example.financeapp.database.entities.GroupTxType
import com.example.financeapp.database.entities.Loan
import com.example.financeapp.database.entities.LoanRepayment
import com.example.financeapp.database.entities.SavingsAccount
import com.example.financeapp.database.entities.SavingsTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class BankRepository(private val db: AppDatabase) {
    private val clientDao = db.clientDao()
    private val savingsDao = db.savingsDao()
    private val loanDao = db.loanDao()
    private val groupAccountDao = db.groupAccountDao()
    private val groupTransactionDao = db.groupTransactionDao()
    private val userDao = db.userDao()
    private val auditLogDao = db.auditLogDao()

    val allClients: Flow<List<Client>> = clientDao.getAllClients()
    val groupAccount: Flow<GroupAccount?> = groupAccountDao.observeGroupAccount()
    val recentGroupTransactions: Flow<List<GroupTransaction>> = groupTransactionDao.getRecentTransactions(50)
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
        return clientDao.getClientById(clientId.toLong())
    }

    fun searchClients(query: String): Flow<List<Client>> {
        return clientDao.searchClients("%$query%")
    }

    private suspend fun writeAuditLog(
        action: String,
        recordedByUserId: Long,
        details: String,
        timestamp: Long = System.currentTimeMillis()
    ) {
        val username = userDao.getUserById(recordedByUserId)?.username ?: "system"
        auditLogDao.insertAuditLog(
            AuditLog(
                userId = recordedByUserId,
                username = username,
                action = action,
                details = details,
                timestamp = timestamp
            )
        )
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

    // Savings Transactions with Treasury Sync
    suspend fun performDeposit(
        accountId: Long,
        amount: Double,
        description: String,
        transactionDate: Long = System.currentTimeMillis(),
        recordedByUserId: Long,
        channel: String = "CASH"
    ): Boolean {
        return withContext(Dispatchers.IO) {
            db.withTransaction {
                // 1. Member deposit
                val account = savingsDao.getAccountById(accountId) ?: return@withTransaction false
                val newBalance = account.balance + amount
                savingsDao.updateAccount(account.copy(balance = newBalance))
                savingsDao.insertTransaction(SavingsTransaction(
                    accountId = accountId,
                    amount = amount,
                    type = "DEPOSIT",
                    description = description,
                    transactionDate = transactionDate
                ))

                // 2. Treasury sync
                val groupAccount = groupAccountDao.getGroupAccountById() ?: return@withTransaction false
                val updatedGroupAccount = groupAccount.copy(
                    cashBalance = if (channel == "CASH") groupAccount.cashBalance + amount else groupAccount.cashBalance,
                    bankBalance = if (channel == "BANK") groupAccount.bankBalance + amount else groupAccount.bankBalance,
                    lastUpdated = transactionDate
                )
                groupAccountDao.updateGroupAccount(updatedGroupAccount)

                groupTransactionDao.insertTransaction(GroupTransaction(
                    type = GroupTxType.SAVINGS_DEPOSIT.name,
                    amount = amount,
                    channel = channel,
                    description = description,
                    relatedClientId = account.clientId,
                    relatedSavingsAccountId = accountId,
                    recordedByUserId = recordedByUserId,
                    transactionDate = transactionDate
                ))

                true
            }
        }
    }

    suspend fun performWithdrawal(
        accountId: Long,
        amount: Double,
        description: String,
        recordedByUserId: Long,
        transactionDate: Long = System.currentTimeMillis()
    ): Boolean {
        return withContext(Dispatchers.IO) {
            db.withTransaction {
                // 1. Member withdrawal (check balance)
                val account = savingsDao.getAccountById(accountId) ?: return@withTransaction false
                if (account.balance < amount) return@withTransaction false

                val newBalance = account.balance - amount
                savingsDao.updateAccount(account.copy(balance = newBalance))
                savingsDao.insertTransaction(SavingsTransaction(
                    accountId = accountId,
                    amount = -amount,
                    type = "WITHDRAWAL",
                    description = description,
                    transactionDate = transactionDate
                ))

                // 2. Treasury sync - check treasury cash balance
                val groupAccount = groupAccountDao.getGroupAccountById() ?: return@withTransaction false
                if (groupAccount.cashBalance < amount) return@withTransaction false

                val updatedGroupAccount = groupAccount.copy(
                    cashBalance = groupAccount.cashBalance - amount,
                    lastUpdated = transactionDate
                )
                groupAccountDao.updateGroupAccount(updatedGroupAccount)

                groupTransactionDao.insertTransaction(GroupTransaction(
                    type = GroupTxType.SAVINGS_WITHDRAWAL.name,
                    amount = amount,
                    channel = "CASH",
                    description = description,
                    relatedClientId = account.clientId,
                    relatedSavingsAccountId = accountId,
                    recordedByUserId = recordedByUserId,
                    transactionDate = transactionDate
                ))

                true
            }
        }
    }

    fun getSavingsTransactions(accountId: Long): Flow<List<SavingsTransaction>> {
        return savingsDao.getTransactionsByAccountId(accountId)
    }

    // Loans with Treasury Sync
    suspend fun disburseLoan(
        loan: Loan,
        recordedByUserId: Long
    ): Boolean {
        return withContext(Dispatchers.IO) {
            db.withTransaction {
                // 1. Insert loan
                val loanId = loanDao.insertLoan(loan)

                // 2. Savings Cap Check (80% of Borrower + Guarantor savings combined).
                // Executed inside the transaction so concurrent disbursements cannot bypass it.
                val borrowerSavings = savingsDao.getAccountByClientId(loan.clientId).first()?.balance ?: 0.0
                val guarantorSavings = loan.guarantorId
                    ?.let { savingsDao.getAccountByClientId(it).first()?.balance }
                    ?: 0.0
                val maxLoanAmount = (borrowerSavings + guarantorSavings) * 0.8

                if (loan.amount > maxLoanAmount) {
                    return@withTransaction false
                }

                // 3. Treasury sync - check treasury cash balance
                val groupAccount = groupAccountDao.getGroupAccountById() ?: return@withTransaction false
                if (groupAccount.cashBalance < loan.amount) return@withTransaction false

                val updatedGroupAccount = groupAccount.copy(
                    cashBalance = groupAccount.cashBalance - loan.amount,
                    lastUpdated = loan.loanDate
                )
                groupAccountDao.updateGroupAccount(updatedGroupAccount)

                groupTransactionDao.insertTransaction(GroupTransaction(
                    type = GroupTxType.LOAN_DISBURSEMENT.name,
                    amount = loan.amount,
                    channel = "CASH",
                    description = "Loan disbursed to member ${loan.clientId}",
                    relatedClientId = loan.clientId,
                    relatedLoanId = loanId,
                    recordedByUserId = recordedByUserId,
                    transactionDate = loan.loanDate
                ))

                true
            }
        }
    }

    fun getLoansForClient(clientId: Long): Flow<List<Loan>> {
        return loanDao.getLoansByClientId(clientId)
    }

    suspend fun getLoanById(loanId: Long): Loan? {
        return loanDao.getLoanById(loanId)
    }

    // Repayments with Treasury Sync
    suspend fun repayLoan(
        loanId: Long,
        amount: Double,
        principalPaid: Double,
        interestPaid: Double,
        timestamp: Long = System.currentTimeMillis(),
        recordedByUserId: Long
    ): Boolean {
        return withContext(Dispatchers.IO) {
            db.withTransaction {
                // 1. Member repayment
                val loan = loanDao.getLoanById(loanId) ?: return@withTransaction false
                val newPrincipalOutstanding = loan.outstandingBalance - principalPaid
                val status = if (newPrincipalOutstanding <= 0.0) "PAID_OFF" else "ACTIVE"
                val updatedLoan = loan.copy(
                    outstandingBalance = if (newPrincipalOutstanding < 0.0) 0.0 else newPrincipalOutstanding,
                    status = status
                )
                loanDao.updateLoan(updatedLoan)
                loanDao.insertRepayment(LoanRepayment(
                    loanId = loanId,
                    amount = amount,
                    principalPaid = principalPaid,
                    interestPaid = interestPaid,
                    timestamp = timestamp
                ))

                // 2. Treasury sync
                val groupAccount = groupAccountDao.getGroupAccountById() ?: return@withTransaction false
                val updatedGroupAccount = groupAccount.copy(
                    cashBalance = groupAccount.cashBalance + amount,
                    lastUpdated = timestamp
                )
                groupAccountDao.updateGroupAccount(updatedGroupAccount)

                groupTransactionDao.insertTransaction(GroupTransaction(
                    type = GroupTxType.LOAN_REPAYMENT.name,
                    amount = amount,
                    channel = "CASH",
                    description = "Loan repayment for loan $loanId (Principal: $principalPaid, Interest: $interestPaid)",
                    relatedClientId = loan.clientId,
                    relatedLoanId = loanId,
                    recordedByUserId = recordedByUserId,
                    transactionDate = timestamp
                ))

                true
            }
        }
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

    // Treasury operations for manual transfers (Cash <-> Bank, Expenses, Interest Income)
    suspend fun transferCashToBank(amount: Double, description: String, recordedByUserId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            db.withTransaction {
                val timestamp = System.currentTimeMillis()
                val groupAccount = groupAccountDao.getGroupAccountById() ?: return@withTransaction false
                if (groupAccount.cashBalance < amount) return@withTransaction false

                val updatedGroupAccount = groupAccount.copy(
                    cashBalance = groupAccount.cashBalance - amount,
                    bankBalance = groupAccount.bankBalance + amount,
                    lastUpdated = timestamp
                )
                groupAccountDao.updateGroupAccount(updatedGroupAccount)

                groupTransactionDao.insertTransaction(GroupTransaction(
                    type = GroupTxType.BANK_DEPOSIT.name,
                    amount = amount,
                    channel = "CASH",
                    description = description,
                    recordedByUserId = recordedByUserId,
                    transactionDate = timestamp
                ))

                writeAuditLog(
                    action = "TRANSFER_CASH_TO_BANK",
                    recordedByUserId = recordedByUserId,
                    details = "Transferred Rs. $amount from cash to bank: $description",
                    timestamp = timestamp
                )

                true
            }
        }
    }

    suspend fun transferBankToCash(amount: Double, description: String, recordedByUserId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            db.withTransaction {
                val timestamp = System.currentTimeMillis()
                val groupAccount = groupAccountDao.getGroupAccountById() ?: return@withTransaction false
                if (groupAccount.bankBalance < amount) return@withTransaction false

                val updatedGroupAccount = groupAccount.copy(
                    cashBalance = groupAccount.cashBalance + amount,
                    bankBalance = groupAccount.bankBalance - amount,
                    lastUpdated = timestamp
                )
                groupAccountDao.updateGroupAccount(updatedGroupAccount)

                groupTransactionDao.insertTransaction(GroupTransaction(
                    type = GroupTxType.BANK_WITHDRAWAL.name,
                    amount = amount,
                    channel = "BANK",
                    description = description,
                    recordedByUserId = recordedByUserId,
                    transactionDate = timestamp
                ))

                writeAuditLog(
                    action = "TRANSFER_BANK_TO_CASH",
                    recordedByUserId = recordedByUserId,
                    details = "Transferred Rs. $amount from bank to cash: $description",
                    timestamp = timestamp
                )

                true
            }
        }
    }

    suspend fun recordExpense(amount: Double, description: String, recordedByUserId: Long, channel: String = "CASH"): Boolean {
        return withContext(Dispatchers.IO) {
            db.withTransaction {
                val timestamp = System.currentTimeMillis()
                val groupAccount = groupAccountDao.getGroupAccountById() ?: return@withTransaction false
                val balance = if (channel == "CASH") groupAccount.cashBalance else groupAccount.bankBalance
                if (balance < amount) return@withTransaction false

                val updatedGroupAccount = if (channel == "CASH") {
                    groupAccount.copy(
                        cashBalance = groupAccount.cashBalance - amount,
                        lastUpdated = timestamp
                    )
                } else {
                    groupAccount.copy(
                        bankBalance = groupAccount.bankBalance - amount,
                        lastUpdated = timestamp
                    )
                }
                groupAccountDao.updateGroupAccount(updatedGroupAccount)

                groupTransactionDao.insertTransaction(GroupTransaction(
                    type = GroupTxType.EXPENSE.name,
                    amount = amount,
                    channel = channel,
                    description = description,
                    recordedByUserId = recordedByUserId,
                    transactionDate = timestamp
                ))

                writeAuditLog(
                    action = "RECORD_EXPENSE",
                    recordedByUserId = recordedByUserId,
                    details = "Recorded Rs. $amount expense from $channel: $description",
                    timestamp = timestamp
                )

                true
            }
        }
    }

    suspend fun recordInterestIncome(amount: Double, description: String, recordedByUserId: Long, channel: String = "BANK"): Boolean {
        return withContext(Dispatchers.IO) {
            db.withTransaction {
                val timestamp = System.currentTimeMillis()
                val groupAccount = groupAccountDao.getGroupAccountById() ?: return@withTransaction false
                val updatedGroupAccount = if (channel == "CASH") {
                    groupAccount.copy(
                        cashBalance = groupAccount.cashBalance + amount,
                        lastUpdated = timestamp
                    )
                } else {
                    groupAccount.copy(
                        bankBalance = groupAccount.bankBalance + amount,
                        lastUpdated = timestamp
                    )
                }
                groupAccountDao.updateGroupAccount(updatedGroupAccount)

                groupTransactionDao.insertTransaction(GroupTransaction(
                    type = GroupTxType.INTEREST_INCOME.name,
                    amount = amount,
                    channel = channel,
                    description = description,
                    recordedByUserId = recordedByUserId,
                    transactionDate = timestamp
                ))

                writeAuditLog(
                    action = "RECORD_INTEREST_INCOME",
                    recordedByUserId = recordedByUserId,
                    details = "Recorded Rs. $amount interest income to $channel: $description",
                    timestamp = timestamp
                )

                true
            }
        }
    }

    suspend fun updateGroupAccountBankInfo(bankName: String, bankAccountNumber: String): Boolean {
        return withContext(Dispatchers.IO) {
            db.withTransaction {
                val groupAccount = groupAccountDao.getGroupAccountById() ?: return@withTransaction false
                val updatedGroupAccount = groupAccount.copy(
                    bankName = bankName,
                    bankAccountNumber = bankAccountNumber,
                    lastUpdated = System.currentTimeMillis()
                )
                groupAccountDao.updateGroupAccount(updatedGroupAccount)
                writeAuditLog(
                    action = "UPDATE_BANK_INFO",
                    recordedByUserId = 1,
                    details = "Updated bank info: $bankName, $bankAccountNumber"
                )
                true
            }
        }
    }
}