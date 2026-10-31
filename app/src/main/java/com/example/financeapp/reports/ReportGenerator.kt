package com.example.financeapp.reports

import android.content.Context
import com.example.financeapp.database.AppDatabase
import com.example.financeapp.database.entities.Client
import com.example.financeapp.database.entities.GroupAccount
import com.example.financeapp.database.entities.GroupTransaction
import com.example.financeapp.database.entities.GroupTxType
import com.example.financeapp.database.entities.Loan
import com.example.financeapp.database.entities.LoanRepayment
import com.example.financeapp.database.entities.SavingsAccount
import com.example.financeapp.database.entities.SavingsTransaction
import com.example.financeapp.database.entities.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportGenerator {

    private const val DATE_PATTERN = "yyyy-MM-dd"
    private const val DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss"
    private const val AMOUNT_PATTERN = "%.2f"
    private const val UTF8_BOM = "\uFEFF"

    private fun formatAmount(amount: Double): String =
        String.format(Locale.US, AMOUNT_PATTERN, amount)

    private fun formatDate(epochMillis: Long, pattern: String = DATE_PATTERN): String =
        SimpleDateFormat(pattern, Locale.US).format(Date(epochMillis))

    private fun escapeCsv(value: String): String {
        val needsQuoting = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuoting) "\"${value.replace("\"", "\"\"")}\"" else value
    }

    @Suppress("UNUSED_PARAMETER")
    fun generateMemberDirectory(context: Context): String {
        return withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            val clients = db.clientDao().getAllClientsBlocking()
            val loans = db.loanDao().getAllLoansBlocking()

            val loanCountByClient = loans.groupingBy { it.clientId }.eachCount()
            val guaranteedLoanCountByClient = loans.filter { it.guarantorId != null }
                .groupingBy { it.guarantorId!! }.eachCount()

            val rows = mutableListOf<List<String>>()
            rows.add(listOf(
                escapeCsv("Member_ID"),
                escapeCsv("Full_Name"),
                escapeCsv("Phone_Number"),
                escapeCsv("Address"),
                escapeCsv("Registration_Date"),
                escapeCsv("Active_Loans_Count"),
                escapeCsv("Has_Guaranteed_Active_Loan")
            ))

            for (client in clients) {
                val savingsAccount = db.savingsDao().getAccountByClientIdBlocking(client.id)
                val activeLoans = loanCountByClient[client.id] ?: 0
                val hasGuaranteed = (guaranteedLoanCountByClient[client.id] ?: 0) > 0

                rows.add(listOf(
                    escapeCsv(client.clientId.toString()),
                    escapeCsv(client.name),
                    escapeCsv(client.phoneNumber),
                    escapeCsv(client.address),
                    escapeCsv(formatDate(savingsAccount?.createdAt ?: client.createdAt ?: System.currentTimeMillis())),
                    escapeCsv(activeLoans.toString()),
                    escapeCsv(if (hasGuaranteed) "Yes" else "No")
                ))
            }

            buildCsvString(rows)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun generateAllMembersSavingsSummary(context: Context): String {
        return withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            val clients = db.clientDao().getAllClientsBlocking()
            val savingsAccounts = db.savingsDao().getAllSavingsAccountsBlocking()
            val savingsTransactions = db.savingsDao().getAllTransactionsBlocking()

            val transactionsByAccount = savingsTransactions.groupingBy { it.accountId }.eachCount()
            val totalDepositedByAccount = savingsTransactions.filter { it.type == "DEPOSIT" }
                .groupingBy { it.accountId }.fold(0.0) { acc, tx -> acc + tx.amount }
            val totalWithdrawnByAccount = savingsTransactions.filter { it.type == "WITHDRAWAL" }
                .groupingBy { it.accountId }.fold(0.0) { acc, tx -> acc + tx.amount.abs() }
            val lastTxDateByAccount = savingsTransactions.groupingBy { it.accountId }
                .maxBy { it.transactionDate }?.transactionDate

            val accountByClient = savingsAccounts.associateBy { it.clientId }

            val rows = mutableListOf<List<String>>()
            rows.add(listOf(
                escapeCsv("Member_ID"),
                escapeCsv("Full_Name"),
                escapeCsv("Account_Number"),
                escapeCsv("Total_Deposited"),
                escapeCsv("Total_Withdrawn"),
                escapeCsv("Current_Balance"),
                escapeCsv("Last_Transaction_Date")
            ))

            for (client in clients) {
                val account = accountByClient[client.id]
                val deposited = account?.let { totalDepositedByAccount[it.id] ?: 0.0 } ?: 0.0
                val withdrawn = account?.let { totalWithdrawnByAccount[it.id] ?: 0.0 } ?: 0.0
                val balance = account?.balance ?: 0.0
                val lastTxDate = account?.let { lastTxDateByAccount[it.id] }

                rows.add(listOf(
                    escapeCsv(client.clientId.toString()),
                    escapeCsv(client.name),
                    escapeCsv(account?.accountNumber ?: ""),
                    escapeCsv(formatAmount(deposited)),
                    escapeCsv(formatAmount(withdrawn)),
                    escapeCsv(formatAmount(balance)),
                    escapeCsv(lastTxDate?.let { formatDate(it) } ?: "")
                ))
            }

            buildCsvString(rows)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun generateSavingsPassbookTransactions(context: Context, fromDate: Long, toDate: Long): String {
        return withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            val transactions = if (fromDate > 0 && toDate > 0 && fromDate <= toDate) {
                db.savingsDao().getTransactionsBetweenBlocking(fromDate, toDate)
            } else {
                db.savingsDao().getAllTransactionsBlocking()
            }

            val accounts = db.savingsDao().getAllSavingsAccountsBlocking().associateBy { it.id }
            val clients = db.clientDao().getAllClientsBlocking().associateBy { it.id }

            val rows = mutableListOf<List<String>>()
            rows.add(listOf(
                escapeCsv("Transaction_ID"),
                escapeCsv("Date"),
                escapeCsv("Member_ID"),
                escapeCsv("Member_Name"),
                escapeCsv("Account_Number"),
                escapeCsv("Transaction_Type"),
                escapeCsv("Deposit_Amount"),
                escapeCsv("Withdrawal_Amount"),
                escapeCsv("Remarks")
            ))

            for (tx in transactions) {
                val account = accounts[tx.accountId]
                val client = account?.let { clients[it.clientId] }
                val isDeposit = tx.type == "DEPOSIT"

                rows.add(listOf(
                    escapeCsv(tx.id.toString()),
                    escapeCsv(formatDate(tx.transactionDate)),
                    escapeCsv(account?.clientId.toString() ?: ""),
                    escapeCsv(client?.name ?: ""),
                    escapeCsv(account?.accountNumber ?: ""),
                    escapeCsv(if (isDeposit) "DEPOSIT" else "WITHDRAWAL"),
                    escapeCsv(if (isDeposit) formatAmount(tx.amount.abs()) else ""),
                    escapeCsv(if (!isDeposit) formatAmount(tx.amount.abs()) else ""),
                    escapeCsv(tx.description)
                ))
            }

            buildCsvString(rows)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun generateAllLoansMasterLedger(context: Context): String {
        return withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            val loans = db.loanDao().getAllLoansBlocking()
            val clients = db.clientDao().getAllClientsBlocking().associateBy { it.id }
            val repayments = db.loanDao().getAllRepaymentsBlocking()

            val totalPrincipalPaidByLoan = repayments.groupingBy { it.loanId }
                .fold(0.0) { acc, r -> acc + r.principalPaid }
            val totalInterestPaidByLoan = repayments.groupingBy { it.loanId }
                .fold(0.0) { acc, r -> acc + r.interestPaid }

            val rows = mutableListOf<List<String>>()
            rows.add(listOf(
                escapeCsv("Loan_ID"),
                escapeCsv("Member_ID"),
                escapeCsv("Borrower_Name"),
                escapeCsv("Guarantor_ID"),
                escapeCsv("Guarantor_Name"),
                escapeCsv("Principal_Disbursed"),
                escapeCsv("Annual_Flat_Rate_%"),
                escapeCsv("Tenure_Months"),
                escapeCsv("Disbursement_Date"),
                escapeCsv("Maturity_Date"),
                escapeCsv("Principal_Repaid"),
                escapeCsv("Interest_Repaid"),
                escapeCsv("Principal_Outstanding"),
                escapeCsv("Loan_Status")
            ))

            for (loan in loans) {
                val borrower = clients[loan.clientId]
                val guarantor = loan.guarantorId?.let { clients[it] }
                val principalPaid = totalPrincipalPaidByLoan[loan.id] ?: 0.0
                val interestPaid = totalInterestPaidByLoan[loan.id] ?: 0.0
                val outstanding = loan.currentOutstandingBalance()

                rows.add(listOf(
                    escapeCsv(loan.id.toString()),
                    escapeCsv(borrower?.clientId.toString() ?: ""),
                    escapeCsv(borrower?.name ?: ""),
                    escapeCsv(guarantor?.clientId.toString() ?: ""),
                    escapeCsv(guarantor?.name ?: ""),
                    escapeCsv(formatAmount(loan.amount)),
                    escapeCsv(String.format(Locale.US, "%.1f", loan.interestRate)),
                    escapeCsv(loan.tenureMonths.toString()),
                    escapeCsv(formatDate(loan.loanDate)),
                    escapeCsv(formatDate(loan.maturityDateMillis())),
                    escapeCsv(formatAmount(principalPaid)),
                    escapeCsv(formatAmount(interestPaid)),
                    escapeCsv(formatAmount(outstanding)),
                    escapeCsv(loan.status)
                ))
            }

            buildCsvString(rows)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun generateActiveLoansAgingReport(context: Context): String {
        return withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            val activeLoans = db.loanDao().getActiveLoansBlocking()
            val clients = db.clientDao().getAllClientsBlocking().associateBy { it.id }
            val repayments = db.loanDao().getAllRepaymentsBlocking()

            val totalInterestPaidByLoan = repayments.filter { r ->
                activeLoans.any { it.id == r.loanId }
            }.groupingBy { it.loanId }.fold(0.0) { acc, r -> acc + r.interestPaid }

            val rows = mutableListOf<List<String>>()
            rows.add(listOf(
                escapeCsv("Loan_ID"),
                escapeCsv("Member_ID"),
                escapeCsv("Borrower_Name"),
                escapeCsv("Phone_Number"),
                escapeCsv("Original_Amount"),
                escapeCsv("Principal_Outstanding"),
                escapeCsv("Accrued_Interest_Due"),
                escapeCsv("Total_Payoff_Amount"),
                escapeCsv("Months_Elapsed"),
                escapeCsv("Tenure_Months"),
                escapeCsv("Days_Until_Maturity_Or_Overdue"),
                escapeCsv("Guarantor_Name"),
                escapeCsv("Guarantor_Phone")
            ))

            val now = System.currentTimeMillis()

            for (loan in activeLoans) {
                val borrower = clients[loan.clientId]
                val guarantor = loan.guarantorId?.let { clients[it] }
                val monthsElapsed = loan.monthsElapsed(now)
                val monthlyInterest = loan.amount * (loan.interestRate / 100.0) / 12.0
                val accruedInterest = monthlyInterest * monthsElapsed
                val interestPaid = totalInterestPaidByLoan[loan.id] ?: 0.0
                val interestDue = (accruedInterest - interestPaid).coerceAtLeast(0.0)
                val totalPayoff = loan.outstandingBalance + interestDue

                val maturityDate = loan.maturityDateMillis()
                val daysUntil = ((maturityDate - now) / (24 * 60 * 60 * 1000)).toInt()

                rows.add(listOf(
                    escapeCsv(loan.id.toString()),
                    escapeCsv(borrower?.clientId.toString() ?: ""),
                    escapeCsv(borrower?.name ?: ""),
                    escapeCsv(borrower?.phoneNumber ?: ""),
                    escapeCsv(formatAmount(loan.amount)),
                    escapeCsv(formatAmount(loan.outstandingBalance)),
                    escapeCsv(formatAmount(interestDue)),
                    escapeCsv(formatAmount(totalPayoff)),
                    escapeCsv(monthsElapsed.toString()),
                    escapeCsv(loan.tenureMonths.toString()),
                    escapeCsv(daysUntil.toString()),
                    escapeCsv(guarantor?.name ?: ""),
                    escapeCsv(guarantor?.phoneNumber ?: "")
                ))
            }

            buildCsvString(rows)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun generateLoanRepaymentsHistory(context: Context): String {
        return withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            val repayments = db.loanDao().getAllRepaymentsBlocking()
            val loans = db.loanDao().getAllLoansBlocking().associateBy { it.id }
            val clients = db.clientDao().getAllClientsBlocking().associateBy { it.id }

            val rows = mutableListOf<List<String>>()
            rows.add(listOf(
                escapeCsv("Repayment_ID"),
                escapeCsv("Payment_Date"),
                escapeCsv("Loan_ID"),
                escapeCsv("Member_ID"),
                escapeCsv("Member_Name"),
                escapeCsv("Total_Amount_Paid"),
                escapeCsv("Principal_Portion"),
                escapeCsv("Interest_Portion"),
                escapeCsv("Remaining_Principal_After_Payment")
            ))

            // Calculate running balance for each loan
            val repaymentsByLoan = repayments.groupingBy { it.loanId }.fold(mutableListOf<LoanRepayment>()) { acc, r ->
                acc.add(r)
                acc
            }

            for (repayment in repayments) {
                val loan = loans[repayment.loanId]
                val borrower = loan?.let { clients[it.clientId] }
                val loanRepayments = repaymentsByLoan[loan.id] ?: emptyList()
                val index = loanRepayments.indexOf(repayment)
                val remainingAfter = if (index >= 0) {
                    val priorRepayments = loanRepayments.take(index + 1)
                    loan?.amount?.minus(priorRepayments.sumOf { it.principalPaid }) ?: 0.0
                } else 0.0

                rows.add(listOf(
                    escapeCsv(repayment.id.toString()),
                    escapeCsv(formatDate(repayment.timestamp, DATE_TIME_PATTERN)),
                    escapeCsv(loan?.id.toString() ?: ""),
                    escapeCsv(borrower?.clientId.toString() ?: ""),
                    escapeCsv(borrower?.name ?: ""),
                    escapeCsv(formatAmount(repayment.amount)),
                    escapeCsv(formatAmount(repayment.principalPaid)),
                    escapeCsv(formatAmount(repayment.interestPaid)),
                    escapeCsv(formatAmount(remainingAfter.coerceAtLeast(0.0)))
                ))
            }

            buildCsvString(rows)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun generateGroupBalanceSheet(context: Context): String {
        return withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            val groupAccount = db.groupAccountDao().getGroupAccountByIdBlocking() ?: GroupAccount()
            val totalSavings = db.savingsDao().getTotalSavingsBlocking() ?: 0.0
            val totalLoansOutstanding = db.loanDao().getTotalOutstandingLoansBlocking() ?: 0.0
            val totalInterestEarned = db.loanDao().getTotalInterestEarnedBlocking() ?: 0.0
            val totalExpenses = db.groupTransactionDao().getExpensesTotalBlocking() ?: 0.0

            val cashInTreasury = groupAccount.cashBalance
            val bankBalance = groupAccount.bankBalance
            val loansReceivable = totalLoansOutstanding
            val totalAssets = cashInTreasury + bankBalance + loansReceivable

            val membersTotalSavings = totalSavings
            val retainedEarnings = totalInterestEarned - totalExpenses
            val totalLiabilitiesEquity = membersTotalSavings + retainedEarnings

            val rows = mutableListOf<List<String>>()
            rows.add(listOf(escapeCsv("ASSETS"), escapeCsv("AMOUNT (NPR)")))
            rows.add(listOf(escapeCsv("Cash in Treasury Box"), escapeCsv(formatAmount(cashInTreasury))))
            rows.add(listOf(escapeCsv("Bank Current Account Balance"), escapeCsv(formatAmount(bankBalance))))
            rows.add(listOf(escapeCsv("Loans Receivable (Principal Outstanding)"), escapeCsv(formatAmount(loansReceivable))))
            rows.add(listOf(escapeCsv("TOTAL ASSETS"), escapeCsv(formatAmount(totalAssets))))
            rows.add(listOf("", ""))
            rows.add(listOf(escapeCsv("LIABILITIES & EQUITY"), escapeCsv("AMOUNT (NPR)")))
            rows.add(listOf(escapeCsv("Members Total Savings Balance (Liabilities)"), escapeCsv(formatAmount(membersTotalSavings))))
            rows.add(listOf(escapeCsv("Retained Earnings (Cumulative Net Profit)"), escapeCsv(formatAmount(retainedEarnings))))
            rows.add(listOf(escapeCsv("TOTAL LIABILITIES & EQUITY"), escapeCsv(formatAmount(totalLiabilitiesEquity))))

            buildCsvString(rows)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun generateIncomeStatementProfitLoss(context: Context): String {
        return withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            val totalInterestEarned = db.loanDao().getTotalInterestEarnedBlocking() ?: 0.0
            val totalExpenses = db.groupTransactionDao().getExpensesTotalBlocking() ?: 0.0
            val otherIncome = db.groupTransactionDao().getInterestIncomeTotalBlocking() ?: 0.0

            val totalRevenue = totalInterestEarned + otherIncome
            val totalExpenditures = totalExpenses
            val netSurplus = totalRevenue - totalExpenditures

            val rows = mutableListOf<List<String>>()
            rows.add(listOf(escapeCsv("REVENUE"), escapeCsv("AMOUNT (NPR)")))
            rows.add(listOf(escapeCsv("Total Loan Interest Earned"), escapeCsv(formatAmount(totalInterestEarned))))
            rows.add(listOf(escapeCsv("Other Group Income"), escapeCsv(formatAmount(otherIncome))))
            rows.add(listOf(escapeCsv("TOTAL REVENUE"), escapeCsv(formatAmount(totalRevenue))))
            rows.add(listOf("", ""))
            rows.add(listOf(escapeCsv("EXPENDITURES"), escapeCsv("AMOUNT (NPR)")))
            rows.add(listOf(escapeCsv("Group Operating Expenses"), escapeCsv(formatAmount(totalExpenses))))
            rows.add(listOf(escapeCsv("TOTAL EXPENDITURES"), escapeCsv(formatAmount(totalExpenditures))))
            rows.add(listOf("", ""))
            rows.add(listOf(escapeCsv("NET SURPLUS / (DEFICIT)"), escapeCsv(formatAmount(netSurplus))))

            buildCsvString(rows)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun generateTreasuryCashFlow(context: Context): String {
        return withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            val transactions = db.groupTransactionDao().getAllTransactionsBlocking()

            val rows = mutableListOf<List<String>>()
            rows.add(listOf(
                escapeCsv("Log_ID"),
                escapeCsv("Date"),
                escapeCsv("Movement_Type"),
                escapeCsv("Channel"),
                escapeCsv("Inflow_Amount"),
                escapeCsv("Outflow_Amount"),
                escapeCsv("Running_Cash_Balance"),
                escapeCsv("Running_Bank_Balance"),
                escapeCsv("Description")
            ))

            var runningCash = 0.0
            var runningBank = 0.0

            for (tx in transactions) {
                val type = GroupTxType.valueOf(tx.type)
                val isInflow = when (type) {
                    GroupTxType.SAVINGS_DEPOSIT, GroupTxType.LOAN_REPAYMENT, GroupTxType.INTEREST_INCOME, GroupTxType.BANK_WITHDRAWAL -> true
                    else -> false
                }

                val inflow = if (isInflow && tx.channel == "CASH") tx.amount else if (isInflow && tx.channel == "BANK") tx.amount else 0.0
                val outflow = if (!isInflow && tx.channel == "CASH") tx.amount else if (!isInflow && tx.channel == "BANK") tx.amount else 0.0

                if (tx.channel == "CASH") {
                    runningCash += (inflow - outflow)
                } else {
                    runningBank += (inflow - outflow)
                }

                rows.add(listOf(
                    escapeCsv(tx.id.toString()),
                    escapeCsv(formatDate(tx.transactionDate)),
                    escapeCsv(type.displayName),
                    escapeCsv(tx.channel),
                    escapeCsv(if (inflow > 0) formatAmount(inflow) else ""),
                    escapeCsv(if (outflow > 0) formatAmount(outflow) else ""),
                    escapeCsv(formatAmount(runningCash)),
                    escapeCsv(formatAmount(runningBank)),
                    escapeCsv(tx.description)
                ))
            }

            buildCsvString(rows)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun generateSystemAuditTrail(context: Context): String {
        return withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            val auditLogs = db.auditLogDao().getAllAuditLogsBlocking()

            val rows = mutableListOf<List<String>>()
            rows.add(listOf(
                escapeCsv("Audit_ID"),
                escapeCsv("Timestamp_ISO"),
                escapeCsv("Admin_Username"),
                escapeCsv("Action_Executed"),
                escapeCsv("Details_Summary")
            ))

            for (log in auditLogs) {
                rows.add(listOf(
                    escapeCsv(log.id.toString()),
                    escapeCsv(formatDate(log.timestamp, "yyyy-MM-dd'T'HH:mm:ss")),
                    escapeCsv(log.username),
                    escapeCsv(log.action),
                    escapeCsv(log.details)
                ))
            }

            buildCsvString(rows)
        }
    }

    private fun buildCsvString(rows: List<List<String>>): String {
        return buildString {
            append(UTF8_BOM)
            rows.forEach { row ->
                append(row.joinToString(","))
                append("\r\n")
            }
        }
    }
}

// Extension functions for blocking DAO calls
private fun com.example.financeapp.database.dao.ClientDao.getAllClientsBlocking(): List<Client> =
    kotlinx.coroutines.runBlocking { getAllClients().first() }

private fun com.example.financeapp.database.dao.SavingsDao.getAllSavingsAccountsBlocking(): List<SavingsAccount> =
    kotlinx.coroutines.runBlocking { getAllSavingsAccounts().first() }

private fun com.example.financeapp.database.dao.SavingsDao.getAllTransactionsBlocking(): List<SavingsTransaction> =
    kotlinx.coroutines.runBlocking { getAllTransactions().first() }

private fun com.example.financeapp.database.dao.SavingsDao.getTransactionsBetweenBlocking(from: Long, to: Long): List<SavingsTransaction> =
    kotlinx.coroutines.runBlocking { getTransactionsBetween(from, to).first() }

private fun com.example.financeapp.database.dao.SavingsDao.getTotalSavingsBlocking(): Double? =
    kotlinx.coroutines.runBlocking { getTotalSavings().first() }

private fun com.example.financeapp.database.dao.LoanDao.getAllLoansBlocking(): List<Loan> =
    kotlinx.coroutines.runBlocking { getAllLoans().first() }

private fun com.example.financeapp.database.dao.LoanDao.getActiveLoansBlocking(): List<Loan> =
    kotlinx.coroutines.runBlocking { getActiveLoans().first() }

private fun com.example.financeapp.database.dao.LoanDao.getAllRepaymentsBlocking(): List<LoanRepayment> =
    kotlinx.coroutines.runBlocking { getAllRepayments().first() }

private fun com.example.financeapp.database.dao.LoanDao.getTotalOutstandingLoansBlocking(): Double? =
    kotlinx.coroutines.runBlocking { getTotalOutstandingLoans().first() }

private fun com.example.financeapp.database.dao.LoanDao.getTotalInterestEarnedBlocking(): Double? =
    kotlinx.coroutines.runBlocking { getTotalInterestEarned().first() }

private fun com.example.financeapp.database.dao.GroupAccountDao.getGroupAccountByIdBlocking(): GroupAccount? =
    kotlinx.coroutines.runBlocking { observeGroupAccount().first() }

private fun com.example.financeapp.database.dao.GroupTransactionDao.getAllTransactionsBlocking(): List<GroupTransaction> =
    kotlinx.coroutines.runBlocking { getAllTransactions().first() }

private fun com.example.financeapp.database.dao.GroupTransactionDao.getExpensesTotalBlocking(): Double? =
    kotlinx.coroutines.runBlocking { getExpensesTotal().first() }

private fun com.example.financeapp.database.dao.GroupTransactionDao.getInterestIncomeTotalBlocking(): Double? =
    kotlinx.coroutines.runBlocking { getInterestIncomeTotal().first() }

private fun com.example.financeapp.database.dao.AuditLogDao.getAllAuditLogsBlocking(): List<com.example.financeapp.database.entities.AuditLog> =
    kotlinx.coroutines.runBlocking { getAllAuditLogs().first() }