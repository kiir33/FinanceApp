package com.example.financeapp.data

import com.example.financeapp.database.AppDatabase
import com.example.financeapp.database.entities.Client
import com.example.financeapp.database.entities.GroupAccount
import com.example.financeapp.database.entities.GroupTransaction
import com.example.financeapp.database.entities.Loan
import com.example.financeapp.database.entities.SavingsAccount
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

class BankRepositoryTest {

    private lateinit var mockDb: AppDatabase
    private lateinit var mockClientDao: com.example.financeapp.database.dao.ClientDao
    private lateinit var mockSavingsDao: com.example.financeapp.database.dao.SavingsDao
    private lateinit var mockLoanDao: com.example.financeapp.database.dao.LoanDao
    private lateinit var mockGroupAccountDao: com.example.financeapp.database.dao.GroupAccountDao
    private lateinit var mockGroupTransactionDao: com.example.financeapp.database.dao.GroupTransactionDao
    private lateinit var repository: BankRepository

    @Before
    fun setup() {
        mockDb = mock(AppDatabase::class.java)
        mockClientDao = mock(com.example.financeapp.database.dao.ClientDao::class.java)
        mockSavingsDao = mock(com.example.financeapp.database.dao.SavingsDao::class.java)
        mockLoanDao = mock(com.example.financeapp.database.dao.LoanDao::class.java)
        mockGroupAccountDao = mock(com.example.financeapp.database.dao.GroupAccountDao::class.java)
        mockGroupTransactionDao = mock(com.example.financeapp.database.dao.GroupTransactionDao::class.java)

        `when`(mockDb.clientDao()).thenReturn(mockClientDao)
        `when`(mockDb.savingsDao()).thenReturn(mockSavingsDao)
        `when`(mockDb.loanDao()).thenReturn(mockLoanDao)
        `when`(mockDb.groupAccountDao()).thenReturn(mockGroupAccountDao)
        `when`(mockDb.groupTransactionDao()).thenReturn(mockGroupTransactionDao)

        repository = BankRepository(mockDb)
    }

    @Test
    fun `disburseLoan rejects when exceeding 80% savings cap`() {
        // Borrower savings: 50,000
        // Guarantor savings: 50,000
        // Combined: 100,000
        // 80% cap: 80,000
        // Requested loan: 90,000 (exceeds cap)

        val borrower = Client(1, 101, "Borrower", "9800000001", "Address", System.currentTimeMillis())
        val guarantor = Client(2, 102, "Guarantor", "9800000002", "Address", System.currentTimeMillis())

        val borrowerSavings = SavingsAccount(1, 1, "SAV-100101", 50000.0, System.currentTimeMillis())
        val guarantorSavings = SavingsAccount(2, 2, "SAV-100102", 50000.0, System.currentTimeMillis())

        val groupAccount = GroupAccount(1, "Treasury", 100000.0, 0.0, "", "", System.currentTimeMillis())

        `when`(mockClientDao.getClientById(1L)).thenReturn(borrower)
        `when`(mockClientDao.getClientById(2L)).thenReturn(guarantor)
        `when`(mockSavingsDao.getSavingsAccountForClient(1L)).thenReturn(borrowerSavings)
        `when`(mockSavingsDao.getSavingsAccountForClient(2L)).thenReturn(guarantorSavings)
        `when`(mockGroupAccountDao.getGroupAccountById()).thenReturn(java.util.Optional.of(groupAccount))
        `when`(mockLoanDao.getActiveLoansGuaranteedCount(2L)).thenReturn(0)

        val loan = Loan(
            clientId = 1,
            guarantorId = 2,
            amount = 90000.0,
            interestRate = 12.0,
            tenureMonths = 12,
            outstandingBalance = 90000.0,
            loanDate = System.currentTimeMillis()
        )

        val result = repository.disburseLoan(loan, 1L)

        assertFalse("Loan should be rejected when exceeding 80% cap", result)
    }

    @Test
    fun `disburseLoan accepts when within 80% savings cap`() {
        // Borrower savings: 50,000
        // Guarantor savings: 50,000
        // Combined: 100,000
        // 80% cap: 80,000
        // Requested loan: 70,000 (within cap)

        val borrower = Client(1, 101, "Borrower", "9800000001", "Address", System.currentTimeMillis())
        val guarantor = Client(2, 102, "Guarantor", "9800000002", "Address", System.currentTimeMillis())

        val borrowerSavings = SavingsAccount(1, 1, "SAV-100101", 50000.0, System.currentTimeMillis())
        val guarantorSavings = SavingsAccount(2, 2, "SAV-100102", 50000.0, System.currentTimeMillis())

        val groupAccount = GroupAccount(1, "Treasury", 100000.0, 0.0, "", "", System.currentTimeMillis())

        `when`(mockClientDao.getClientById(1L)).thenReturn(borrower)
        `when`(mockClientDao.getClientById(2L)).thenReturn(guarantor)
        `when`(mockSavingsDao.getSavingsAccountForClient(1L)).thenReturn(borrowerSavings)
        `when`(mockSavingsDao.getSavingsAccountForClient(2L)).thenReturn(guarantorSavings)
        `when`(mockGroupAccountDao.getGroupAccountById()).thenReturn(java.util.Optional.of(groupAccount))
        `when`(mockLoanDao.getActiveLoansGuaranteedCount(2L)).thenReturn(0)
        `when`(mockLoanDao.insertLoan(any())).thenReturn(1L)
        `when`(mockGroupAccountDao.updateCashBalance(anyDouble())).thenReturn(1)
        `when`(mockGroupTransactionDao.insertTransaction(any())).thenReturn(1L)

        val loan = Loan(
            clientId = 1,
            guarantorId = 2,
            amount = 70000.0,
            interestRate = 12.0,
            tenureMonths = 12,
            outstandingBalance = 70000.0,
            loanDate = System.currentTimeMillis()
        )

        val result = repository.disburseLoan(loan, 1L)

        assertTrue("Loan should be accepted when within 80% cap", result)
    }

    @Test
    fun `withdrawSavings rejects when insufficient treasury cash balance`() {
        val account = SavingsAccount(1, 1, "SAV-100101", 100000.0, System.currentTimeMillis())
        val groupAccount = GroupAccount(1, "Treasury", 5000.0, 100000.0, "", "", System.currentTimeMillis())

        `when`(mockSavingsDao.getSavingsAccountById(1L)).thenReturn(java.util.Optional.of(account))
        `when`(mockGroupAccountDao.getGroupAccountById()).thenReturn(java.util.Optional.of(groupAccount))

        val result = repository.performWithdrawal(
            accountId = 1,
            amount = 10000.0,
            description = "Test",
            recordedByUserId = 1,
            transactionDate = System.currentTimeMillis()
        )

        assertFalse("Withdrawal should be rejected when insufficient treasury cash", result)
    }

    @Test
    fun `withdrawSavings accepts when sufficient treasury cash balance`() {
        val account = SavingsAccount(1, 1, "SAV-100101", 100000.0, System.currentTimeMillis())
        val groupAccount = GroupAccount(1, "Treasury", 20000.0, 100000.0, "", "", System.currentTimeMillis())

        `when`(mockSavingsDao.getSavingsAccountById(1L)).thenReturn(java.util.Optional.of(account))
        `when`(mockGroupAccountDao.getGroupAccountById()).thenReturn(java.util.Optional.of(groupAccount))
        `when`(mockSavingsDao.updateBalance(anyLong(), anyDouble())).thenReturn(1)
        `when`(mockSavingsDao.insertTransaction(any())).thenReturn(1L)
        `when`(mockGroupAccountDao.updateCashBalance(anyDouble())).thenReturn(1)
        `when`(mockGroupTransactionDao.insertTransaction(any())).thenReturn(1L)

        val result = repository.performWithdrawal(
            accountId = 1,
            amount = 10000.0,
            description = "Test",
            recordedByUserId = 1,
            transactionDate = System.currentTimeMillis()
        )

        assertTrue("Withdrawal should be accepted when sufficient treasury cash", result)
    }

    @Test
    fun `depositSavings updates treasury correctly`() {
        val account = SavingsAccount(1, 1, "SAV-100101", 50000.0, System.currentTimeMillis())
        val groupAccount = GroupAccount(1, "Treasury", 10000.0, 100000.0, "", "", System.currentTimeMillis())

        `when`(mockSavingsDao.getSavingsAccountById(1L)).thenReturn(java.util.Optional.of(account))
        `when`(mockGroupAccountDao.getGroupAccountById()).thenReturn(java.util.Optional.of(groupAccount))
        `when`(mockSavingsDao.updateBalance(anyLong(), anyDouble())).thenReturn(1)
        `when`(mockSavingsDao.insertTransaction(any())).thenReturn(1L)
        `when`(mockGroupAccountDao.updateCashBalance(anyDouble())).thenReturn(1)
        `when`(mockGroupTransactionDao.insertTransaction(any())).thenReturn(1L)

        val result = repository.performDeposit(
            accountId = 1,
            amount = 10000.0,
            description = "Test",
            transactionDate = System.currentTimeMillis(),
            recordedByUserId = 1,
            channel = "CASH"
        )

        assertTrue("Deposit should succeed", result)
    }

    @Test
    fun `disburseLoan rejects when insufficient treasury balance`() {
        val borrower = Client(1, 101, "Borrower", "9800000001", "Address", System.currentTimeMillis())
        val guarantor = Client(2, 102, "Guarantor", "9800000002", "Address", System.currentTimeMillis())

        val borrowerSavings = SavingsAccount(1, 1, "SAV-100101", 100000.0, System.currentTimeMillis())
        val guarantorSavings = SavingsAccount(2, 2, "SAV-100102", 100000.0, System.currentTimeMillis())

        // Treasury only has 50,000 but loan is 80,000 (within 80% cap but exceeds treasury)
        val groupAccount = GroupAccount(1, "Treasury", 50000.0, 0.0, "", "", System.currentTimeMillis())

        `when`(mockClientDao.getClientById(1L)).thenReturn(borrower)
        `when`(mockClientDao.getClientById(2L)).thenReturn(guarantor)
        `when`(mockSavingsDao.getSavingsAccountForClient(1L)).thenReturn(borrowerSavings)
        `when`(mockSavingsDao.getSavingsAccountForClient(2L)).thenReturn(guarantorSavings)
        `when`(mockGroupAccountDao.getGroupAccountById()).thenReturn(java.util.Optional.of(groupAccount))
        `when`(mockLoanDao.getActiveLoansGuaranteedCount(2L)).thenReturn(0)

        val loan = Loan(
            clientId = 1,
            guarantorId = 2,
            amount = 80000.0,
            interestRate = 12.0,
            tenureMonths = 12,
            outstandingBalance = 80000.0,
            loanDate = System.currentTimeMillis()
        )

        val result = repository.disburseLoan(loan, 1L)

        assertFalse("Loan should be rejected when exceeding treasury balance", result)
    }
}