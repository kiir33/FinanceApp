package com.example.financeapp.ui.main

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.financeapp.auth.SessionManager
import com.example.financeapp.backup.BackupManager
import com.example.financeapp.data.BankRepository
import com.example.financeapp.database.AppDatabase
import com.example.financeapp.database.entities.Client
import com.example.financeapp.database.entities.Loan
import com.example.financeapp.database.entities.LoanRepayment
import com.example.financeapp.database.entities.SavingsAccount
import com.example.financeapp.database.entities.SavingsTransaction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BankViewModel(application: Application) : AndroidViewModel(application) {

    private var db = AppDatabase.getDatabase(application)
    private var repository = BankRepository(db)
    private val backupManager = BackupManager(application)

    // Dashboard info
    val clientCount: StateFlow<Int> = repository.clientCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val totalSavings: StateFlow<Double> = repository.totalSavings.map { it ?: 0.0 }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)
    val totalOutstandingLoans: StateFlow<Double> = repository.totalOutstandingLoans.map { it ?: 0.0 }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)
    val allClients: StateFlow<List<Client>> = repository.allClients.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSavingsWithClients: StateFlow<List<SavingsWithClient>> = combine(
        repository.getAllSavingsAccounts(),
        repository.allClients
    ) { accounts, clients ->
        accounts.map { account ->
            SavingsWithClient(account, clients.find { it.id == account.clientId })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allLoansWithClients: StateFlow<List<LoanWithClient>> = combine(
        repository.allLoans,
        repository.allClients
    ) { loans, clients ->
        loans.map { loan ->
            LoanWithClient(
                loan = loan,
                client = clients.find { it.id == loan.clientId },
                guarantor = clients.find { it.id == loan.guarantorId }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalInterestEarned: StateFlow<Double> = repository.totalInterestEarned
        .map { it ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Client search
    val searchQuery = MutableStateFlow("")
    @OptIn(ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<Client>> = searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                repository.allClients
            } else {
                repository.searchClients(query)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Selected client detail state
    val selectedClientId = MutableStateFlow<Long?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedClient: StateFlow<Client?> = selectedClientId
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else repository.allClients.map { list -> list.find { it.id == id } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedClientSavingsAccount: StateFlow<SavingsAccount?> = selectedClientId
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else repository.getSavingsAccountForClient(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedClientSavingsTransactions: StateFlow<List<SavingsTransaction>> = selectedClientSavingsAccount
        .flatMapLatest { account ->
            if (account == null) flowOf(emptyList())
            else repository.getSavingsTransactions(account.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedClientLoans: StateFlow<List<Loan>> = selectedClientId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else repository.getLoansForClient(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Selected loan details
    val selectedLoanId = MutableStateFlow<Long?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedLoan: StateFlow<Loan?> = selectedLoanId
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else repository.getLoansForClient(selectedClientId.value ?: 0L).map { list -> list.find { it.id == id } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedLoanRepayments: StateFlow<List<com.example.financeapp.database.entities.LoanRepayment>> = selectedLoanId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else repository.getLoanRepayments(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Backup & Restore Events
    private val _backupRestoreEvent = MutableSharedFlow<BackupRestoreResult>()
    val backupRestoreEvent: SharedFlow<BackupRestoreResult> = _backupRestoreEvent.asSharedFlow()

    private val currentUserId: Long
        get() = SessionManager.currentSession.value?.userId ?: 1L // fallback for initial admin

    fun createClient(name: String, phoneNumber: String, address: String, clientId: Int) {
        viewModelScope.launch {
            val generatedId = repository.insertClient(
                Client(clientId = clientId, name = name, phoneNumber = phoneNumber, address = address)
            )
            // Create a default savings account for the new client
            repository.insertSavingsAccount(
                SavingsAccount(clientId = generatedId, accountNumber = "SAV-${100000 + generatedId}")
            )
        }
    }

    fun depositSavings(accountId: Long, amount: Double, description: String, transactionDate: Long = System.currentTimeMillis(), channel: String = "CASH") {
        viewModelScope.launch {
            val success = repository.performDeposit(
                accountId = accountId,
                amount = amount,
                description = description,
                transactionDate = transactionDate,
                recordedByUserId = currentUserId,
                channel = channel
            )
            if (!success) {
                // Handle error - could emit to a UI event flow
            }
        }
    }

    fun withdrawSavings(
        accountId: Long,
        amount: Double,
        description: String,
        transactionDate: Long = System.currentTimeMillis(),
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val success = repository.performWithdrawal(
                accountId = accountId,
                amount = amount,
                description = description,
                recordedByUserId = currentUserId,
                transactionDate = transactionDate
            )
            if (success) onSuccess() else onError("Insufficient member balance or treasury cash balance.")
        }
    }

    fun disburseLoan(
        clientId: Long,
        guarantorId: Long?,
        amount: Double,
        interestRate: Double,
        tenureMonths: Int,
        loanDate: Long = System.currentTimeMillis(),
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            // Rule 3: A member serving as a guarantor cannot borrow.
            val isServingAsGuarantor = repository.getActiveLoansGuaranteedCount(clientId) > 0
            if (isServingAsGuarantor) {
                onError("Borrower is currently serving as a guarantor for an active loan and cannot borrow.")
                return@launch
            }

            if (guarantorId != null) {
                if (clientId == guarantorId) {
                    onError("Borrower cannot be their own guarantor.")
                    return@launch
                }
                // Rule 2: A member can be a guarantor for at most one active loan.
                val guarantorGuaranteedCount = repository.getActiveLoansGuaranteedCount(guarantorId)
                if (guarantorGuaranteedCount > 0) {
                    onError("Selected guarantor is already guaranteeing another active loan.")
                    return@launch
                }
            }

            // The 80% savings cap and treasury balance are validated atomically inside
            // BankRepository.disburseLoan() so concurrent disbursements cannot bypass them.

            // outstandingBalance tracks principal remaining only.
            // Interest is accrued daily and computed at display time based on today's date.
            val success = repository.disburseLoan(
                Loan(
                    clientId = clientId,
                    guarantorId = guarantorId,
                    amount = amount,
                    interestRate = interestRate,
                    tenureMonths = tenureMonths,
                    outstandingBalance = amount,
                    loanDate = loanDate
                ),
                recordedByUserId = currentUserId
            )
            if (success) onSuccess() else onError("Failed to disburse loan - savings limit or treasury funds insufficient.")
        }
    }

    fun repayLoan(
        loanId: Long,
        principalAmount: Double,
        interestAmount: Double,
        repaymentDate: Long = System.currentTimeMillis(),
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val loan = repository.getLoanById(loanId)
            if (loan == null) {
                onError("Loan not found.")
                return@launch
            }
            if (principalAmount > loan.outstandingBalance) {
                onError("Principal repayment exceeds outstanding balance.")
                return@launch
            }

            val totalPayment = principalAmount + interestAmount
            val success = repository.repayLoan(
                loanId = loanId,
                amount = totalPayment,
                principalPaid = principalAmount,
                interestPaid = interestAmount,
                timestamp = repaymentDate,
                recordedByUserId = currentUserId
            )
            if (success) onSuccess() else onError("Failed to register repayment.")
        }
    }

    // Treasury operations
    fun transferCashToBank(amount: Double, description: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val success = repository.transferCashToBank(amount, description, currentUserId)
            if (success) onSuccess() else onError("Insufficient cash balance.")
        }
    }

    fun transferBankToCash(amount: Double, description: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val success = repository.transferBankToCash(amount, description, currentUserId)
            if (success) onSuccess() else onError("Insufficient bank balance.")
        }
    }

    fun recordExpense(amount: Double, description: String, channel: String = "CASH", onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val success = repository.recordExpense(amount, description, currentUserId, channel)
            if (success) onSuccess() else onError("Insufficient ${channel.toLowerCase()} balance.")
        }
    }

    fun recordInterestIncome(amount: Double, description: String, channel: String = "BANK", onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val success = repository.recordInterestIncome(amount, description, currentUserId, channel)
            if (success) onSuccess() else onError("Failed to record interest income.")
        }
    }

    fun exportDatabase(destUri: Uri) {
        viewModelScope.launch {
            val result = backupManager.exportDatabase(db, destUri)
            if (result.isSuccess) {
                _backupRestoreEvent.emit(BackupRestoreResult.BackupSuccess)
            } else {
                _backupRestoreEvent.emit(BackupRestoreResult.Failure(result.exceptionOrNull()?.message ?: "Unknown error"))
            }
        }
    }

    fun importDatabase(sourceUri: Uri) {
        viewModelScope.launch {
            val result = backupManager.importDatabase(db, sourceUri) {
                // Re-open DB
                db = AppDatabase.getDatabase(getApplication())
                repository = BankRepository(db)
            }
            if (result.isSuccess) {
                _backupRestoreEvent.emit(BackupRestoreResult.RestoreSuccess)
            } else {
                _backupRestoreEvent.emit(BackupRestoreResult.Failure(result.exceptionOrNull()?.message ?: "Unknown error"))
            }
        }
    }

    fun checkClientIdUnique(clientId: Int, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val client = repository.getClientByClientId(clientId)
            onResult(client == null)
        }
    }

    fun getLoanRepayments(loanId: Long): Flow<List<LoanRepayment>> {
        return repository.getLoanRepayments(loanId)
    }

    fun getGuarantorForLoan(guarantorId: Long?): Client? {
        return if (guarantorId != null) repository.getClientById(guarantorId) else null
    }
}

data class SavingsWithClient(
    val account: SavingsAccount,
    val client: Client?
)

data class LoanWithClient(
    val loan: Loan,
    val client: Client?,
    val guarantor: Client?
)

sealed interface BackupRestoreResult {
    data object BackupSuccess : BackupRestoreResult
    data object RestoreSuccess : BackupRestoreResult
    data class Failure(val message: String) : BackupRestoreResult
}