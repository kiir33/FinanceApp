package com.example.financeapp.importer

import com.example.financeapp.auth.PasswordHasher
import com.example.financeapp.database.entities.Client
import com.example.financeapp.database.entities.Loan
import com.example.financeapp.database.entities.SavingsAccount
import com.example.financeapp.database.entities.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

// Data classes for CSV parsing
data class MemberRow(
    val memberId: Int,
    val fullName: String,
    val phoneNumber: String,
    val address: String
)

data class SavingsRow(
    val memberId: Int,
    val accountNumber: String,
    val openingBalance: Double
)

data class LoanRow(
    val memberId: Int,
    val guarantorMemberId: Int?,
    val disbursedAmount: Double,
    val interestRate: Double,
    val tenureMonths: Int,
    val disbursementDate: Long,
    val currentPrincipalOutstanding: Double
)

data class ValidationError(
    val row: Int,
    val column: String,
    val reason: String
)

data class DryRunResult(
    val isValid: Boolean,
    val errors: List<ValidationError>,
    val preview: ImportPreview?
)

data class ImportPreview(
    val memberCount: Int,
    val totalSavingsBalance: Double,
    val totalLoanAmount: Double,
    val memberUsernames: Map<Int, String> // memberId -> generated username
)

data class ImportResult(
    val success: Boolean,
    val message: String,
    val importedMembers: Int = 0,
    val importedSavings: Int = 0,
    val importedLoans: Int = 0,
    val credentialsSheet: String? = null
)

class CsvImporter(
    private val clientDao: com.example.financeapp.database.dao.ClientDao,
    private val savingsDao: com.example.financeapp.database.dao.SavingsDao,
    private val loanDao: com.example.financeapp.database.dao.LoanDao,
    private val userDao: com.example.financeapp.database.dao.UserDao,
    private val db: com.example.financeapp.database.AppDatabase
) {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    // --- Public API ---

    /**
     * Perform dry run validation on all three CSV files.
     * Returns detailed row-level errors or a preview summary.
     */
    suspend fun dryRun(
        membersCsv: InputStream,
        savingsCsv: InputStream?,
        loansCsv: InputStream?
    ): DryRunResult {
        return withContext(Dispatchers.IO) {
            val allErrors = mutableListOf<ValidationError>()
            var memberRows = emptyList<MemberRow>()
            var savingsRows = emptyList<SavingsRow>()
            var loanRows = emptyList<LoanRow>()

            // Parse and validate members (required)
            val memberResult = parseAndValidateMembers(membersCsv)
            allErrors.addAll(memberResult.errors)
            memberRows = memberResult.rows

            // Parse and validate savings (optional)
            if (savingsCsv != null) {
                val savingsResult = parseAndValidateSavings(savingsCsv, memberRows.map { it.memberId }.toSet())
                allErrors.addAll(savingsResult.errors)
                savingsRows = savingsResult.rows
            }

            // Parse and validate loans (optional)
            if (loansCsv != null) {
                val loanResult = parseAndValidateLoans(loansCsv, memberRows.map { it.memberId }.toSet())
                allErrors.addAll(loanResult.errors)
                loanRows = loanResult.rows
            }

            // Cross-validation: check FK references
            allErrors.addAll(crossValidate(memberRows, savingsRows, loanRows))

            if (allErrors.isNotEmpty()) {
                DryRunResult(false, allErrors, null)
            } else {
                // Generate preview with usernames
                val existingUsernames = userDao.getAllUsers().first().map { it.username }.toSet()
                val memberUsernames = generateUsernamesForPreview(memberRows, existingUsernames)
                val totalSavings = savingsRows.sumOf { it.openingBalance }
                val totalLoans = loanRows.sumOf { it.disbursedAmount }

                val preview = ImportPreview(
                    memberCount = memberRows.size,
                    totalSavingsBalance = totalSavings,
                    totalLoanAmount = totalLoans,
                    memberUsernames = memberUsernames
                )
                DryRunResult(true, emptyList(), preview)
            }
        }
    }

    /**
     * Commit the import after successful dry run.
     * All-or-nothing transaction.
     */
    suspend fun commit(
        membersCsv: InputStream,
        savingsCsv: InputStream?,
        loansCsv: InputStream?
    ): ImportResult {
        return withContext(Dispatchers.IO) {
            // Re-parse (could optimize by passing parsed data, but safer to re-validate)
            val memberResult = parseAndValidateMembers(membersCsv)
            if (memberResult.errors.isNotEmpty()) {
                return@withContext ImportResult(false, "Member validation failed: ${memberResult.errors.joinToString { "${it.row}:${it.column} - ${it.reason}" }}")
            }

            var savingsRows = emptyList<SavingsRow>()
            if (savingsCsv != null) {
                val savingsResult = parseAndValidateSavings(savingsCsv, memberResult.rows.map { it.memberId }.toSet())
                if (savingsResult.errors.isNotEmpty()) {
                    return@withContext ImportResult(false, "Savings validation failed: ${savingsResult.errors.joinToString { "${it.row}:${it.column} - ${it.reason}" }}")
                }
                savingsRows = savingsResult.rows
            }

            var loanRows = emptyList<LoanRow>()
            if (loansCsv != null) {
                val loanResult = parseAndValidateLoans(loansCsv, memberResult.rows.map { it.memberId }.toSet())
                if (loanResult.errors.isNotEmpty()) {
                    return@withContext ImportResult(false, "Loan validation failed: ${loanResult.errors.joinToString { "${it.row}:${it.column} - ${it.reason}" }}")
                }
                loanRows = loanResult.rows
            }

            // Cross-validate
            val crossErrors = crossValidate(memberResult.rows, savingsRows, loanRows)
            if (crossErrors.isNotEmpty()) {
                return@withContext ImportResult(false, "Cross-validation failed: ${crossErrors.joinToString { "${it.row}:${it.column} - ${it.reason}" }}")
            }

            // Execute import in single transaction
            return@withContext db.runInTransaction {
                val existingUsernames = userDao.getAllUsers().first().map { it.username }.toMutableSet()
                val memberIdToDbId = mutableMapOf<Int, Long>()
                val memberIdToUsername = mutableMapOf<Int, String>()
                val credentialsRows = mutableListOf<String>()

                // 1. Insert Clients
                for (row in memberResult.rows) {
                    val client = Client(
                        clientId = row.memberId,
                        name = row.fullName,
                        phoneNumber = row.phoneNumber,
                        address = row.address
                    )
                    val dbId = clientDao.insertClient(client)
                    memberIdToDbId[row.memberId] = dbId
                }

                // 2. Create User logins with generated usernames and temp passwords
                for (row in memberResult.rows) {
                    val dbId = memberIdToDbId[row.memberId]!!
                    val baseUsername = generateBaseUsername(row.fullName)
                    val username = generateUniqueUsername(baseUsername, row.memberId, existingUsernames)
                    existingUsernames.add(username)
                    memberIdToUsername[row.memberId] = username

                    val tempPassword = row.phoneNumber.replace(Regex("[^0-9]"), "")
                    val salt = PasswordHasher.generateSalt()
                    val passwordHash = PasswordHasher.hashPassword(tempPassword, salt)

                    val user = User(
                        username = username,
                        passwordHash = passwordHash,
                        salt = salt,
                        role = "MEMBER",
                        clientId = dbId,
                        isActive = true,
                        failedAttempts = 0,
                        lockedUntil = null,
                        mustChangePassword = true,
                        createdAt = System.currentTimeMillis(),
                        lastLoginAt = null
                    )
                    userDao.insertUser(user)

                    // Add to credentials sheet
                    credentialsRows.add("${row.memberId},${row.fullName},$username,${row.phoneNumber},$tempPassword")
                }

                // 3. Insert Savings Accounts + Opening Transactions
                for (row in savingsRows) {
                    val dbId = memberIdToDbId[row.memberId] ?: continue
                    val account = SavingsAccount(
                        clientId = dbId,
                        accountNumber = row.accountNumber,
                        balance = row.openingBalance,
                        createdAt = System.currentTimeMillis()
                    )
                    val accountId = savingsDao.insertAccount(account)

                    // Opening transaction
                    if (row.openingBalance > 0) {
                        savingsDao.insertTransaction(com.example.financeapp.database.entities.SavingsTransaction(
                            accountId = accountId,
                            amount = row.openingBalance,
                            type = "DEPOSIT",
                            description = "Opening balance (CSV import)",
                            transactionDate = System.currentTimeMillis()
                        ))
                    }
                }

                // 4. Insert Loans (reconcile outstanding balance)
                for (row in loanRows) {
                    val dbId = memberIdToDbId[row.memberId] ?: continue
                    var guarantorDbId: Long? = null
                    if (row.guarantorMemberId != null) {
                        guarantorDbId = memberIdToDbId[row.guarantorMemberId!!]
                    }

                    val loan = Loan(
                        clientId = dbId,
                        guarantorId = guarantorDbId,
                        amount = row.disbursedAmount,
                        interestRate = row.interestRate,
                        tenureMonths = row.tenureMonths,
                        outstandingBalance = row.currentPrincipalOutstanding, // Use imported outstanding balance
                        status = if (row.currentPrincipalOutstanding <= 0) "PAID_OFF" else "ACTIVE",
                        loanDate = row.disbursementDate,
                        createdAt = System.currentTimeMillis()
                    )
                    loanDao.insertLoan(loan)
                }

                // Generate credentials sheet CSV
                val credentialsSheet = generateCredentialsSheet(credentialsRows)

                // Audit log for CSV import
                db.auditLogDao().insertAuditLog(AuditLog(
                    userId = 1, // TODO: get actual admin user ID from context
                    username = "admin",
                    action = "CSV_IMPORT",
                    details = """Bulk import: ${memberResult.rows.size} members, ${savingsRows.size} savings, ${loanRows.size} loans"""
                ))

                ImportResult(
                    success = true,
                    message = "Successfully imported ${memberResult.rows.size} members, ${savingsRows.size} savings accounts, ${loanRows.size} loans",
                    importedMembers = memberResult.rows.size,
                    importedSavings = savingsRows.size,
                    importedLoans = loanRows.size,
                    credentialsSheet = credentialsSheet
                )
            }
        }
    }

    // --- Template Downloads ---

    fun getMembersTemplate(): String {
        return "Member_ID,Full_Name,Phone_Number,Address\n101,John Doe,9800000001,Kathmandu\n102,Jane Smith,9800000002,Pokhara\n"
    }

    fun getSavingsTemplate(): String {
        return "Member_ID,Account_Number,Opening_Balance\n101,SAV-100101,15000.00\n102,SAV-100102,25000.00\n"
    }

    fun getLoansTemplate(): String {
        return "Member_ID,Guarantor_Member_ID,Disbursed_Amount,Interest_Rate,Tenure_Months,Disbursement_Date_YYYY_MM_DD,Current_Principal_Outstanding\n101,102,50000,12.0,12,2026-01-15,40000.00\n"
    }

    // --- Private Parsing & Validation ---

    private data class ParseResult<T>(
        val rows: List<T>,
        val errors: List<ValidationError>
    )

    private fun parseAndValidateMembers(input: InputStream): ParseResult<MemberRow> {
        val rows = mutableListOf<MemberRow>()
        val errors = mutableListOf<ValidationError>()
        val seenMemberIds = mutableSetOf<Int>()
        val reader = BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8))
        var lineNumber = 0
        var isHeader = true

        reader.use {
            it.forEachLine { line ->
                lineNumber++
                if (isHeader) {
                    isHeader = false
                    // Validate headers
                    val headers = line.split(",").map { it.trim() }
                    val expected = listOf("Member_ID", "Full_Name", "Phone_Number", "Address")
                    if (headers != expected) {
                        errors.add(ValidationError(0, "headers", "Invalid headers. Expected: ${expected.joinToString(",")}"))
                    }
                    return@forEachLine
                }

                if (line.trim().isEmpty()) return@forEachLine

                val cols = parseCsvLine(line)
                if (cols.size < 4) {
                    errors.add(ValidationError(lineNumber, "all", "Expected 4 columns, got ${cols.size}"))
                    return@forEachLine
                }

                // Member_ID
                val memberId = cols[0].trim().toIntOrNull()
                if (memberId == null) {
                    errors.add(ValidationError(lineNumber, "Member_ID", "Must be a valid integer"))
                } else if (memberId in seenMemberIds) {
                    errors.add(ValidationError(lineNumber, "Member_ID", "Duplicate member ID"))
                } else if (memberId <= 0) {
                    errors.add(ValidationError(lineNumber, "Member_ID", "Must be positive"))
                } else {
                    seenMemberIds.add(memberId)
                }

                // Full_Name
                val fullName = cols[1].trim()
                if (fullName.isBlank()) {
                    errors.add(ValidationError(lineNumber, "Full_Name", "Cannot be empty"))
                }

                // Phone_Number
                val phoneNumber = cols[2].trim()
                val cleanPhone = phoneNumber.replace(Regex("[^0-9]"), "")
                if (cleanPhone.isEmpty() || cleanPhone.length !in 7..15) {
                    errors.add(ValidationError(lineNumber, "Phone_Number", "Invalid phone number (7-15 digits)"))
                }

                // Address
                val address = cols[3].trim()
                if (address.isBlank()) {
                    errors.add(ValidationError(lineNumber, "Address", "Cannot be empty"))
                }

                if (errors.none { it.row == lineNumber }) {
                    rows.add(MemberRow(memberId!!, fullName, phoneNumber, address))
                }
            }
        }
        ParseResult(rows, errors)
    }

    private fun parseAndValidateSavings(input: InputStream, validMemberIds: Set<Int>): ParseResult<SavingsRow> {
        val rows = mutableListOf<SavingsRow>()
        val errors = mutableListOf<ValidationError>()
        val seenAccountNumbers = mutableSetOf<String>()
        val seenMemberIds = mutableSetOf<Int>()
        val reader = BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8))
        var lineNumber = 0
        var isHeader = true

        reader.use {
            it.forEachLine { line ->
                lineNumber++
                if (isHeader) {
                    isHeader = false
                    val headers = line.split(",").map { it.trim() }
                    val expected = listOf("Member_ID", "Account_Number", "Opening_Balance")
                    if (headers != expected) {
                        errors.add(ValidationError(0, "headers", "Invalid headers. Expected: ${expected.joinToString(",")}"))
                    }
                    return@forEachLine
                }

                if (line.trim().isEmpty()) return@forEachLine

                val cols = parseCsvLine(line)
                if (cols.size < 3) {
                    errors.add(ValidationError(lineNumber, "all", "Expected 3 columns, got ${cols.size}"))
                    return@forEachLine
                }

                // Member_ID
                val memberId = cols[0].trim().toIntOrNull()
                if (memberId == null) {
                    errors.add(ValidationError(lineNumber, "Member_ID", "Must be a valid integer"))
                } else if (memberId !in validMemberIds) {
                    errors.add(ValidationError(lineNumber, "Member_ID", "Member ID not found in members file"))
                } else if (memberId in seenMemberIds) {
                    errors.add(ValidationError(lineNumber, "Member_ID", "Duplicate savings account for member"))
                } else {
                    seenMemberIds.add(memberId)
                }

                // Account_Number
                val accountNumber = cols[1].trim()
                if (accountNumber.isBlank()) {
                    errors.add(ValidationError(lineNumber, "Account_Number", "Cannot be empty"))
                } else if (accountNumber in seenAccountNumbers) {
                    errors.add(ValidationError(lineNumber, "Account_Number", "Duplicate account number"))
                } else {
                    seenAccountNumbers.add(accountNumber)
                }

                // Opening_Balance
                val openingBalance = cols[2].trim().toDoubleOrNull()
                if (openingBalance == null) {
                    errors.add(ValidationError(lineNumber, "Opening_Balance", "Must be a valid number"))
                } else if (openingBalance < 0) {
                    errors.add(ValidationError(lineNumber, "Opening_Balance", "Cannot be negative"))
                }

                if (errors.none { it.row == lineNumber }) {
                    rows.add(SavingsRow(memberId!!, accountNumber, openingBalance!!))
                }
            }
        }
        ParseResult(rows, errors)
    }

    private fun parseAndValidateLoans(input: InputStream, validMemberIds: Set<Int>): ParseResult<LoanRow> {
        val rows = mutableListOf<LoanRow>()
        val errors = mutableListOf<ValidationError>()
        val reader = BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8))
        var lineNumber = 0
        var isHeader = true

        reader.use {
            it.forEachLine { line ->
                lineNumber++
                if (isHeader) {
                    isHeader = false
                    val headers = line.split(",").map { it.trim() }
                    val expected = listOf("Member_ID", "Guarantor_Member_ID", "Disbursed_Amount", "Interest_Rate", "Tenure_Months", "Disbursement_Date_YYYY_MM_DD", "Current_Principal_Outstanding")
                    if (headers != expected) {
                        errors.add(ValidationError(0, "headers", "Invalid headers. Expected: ${expected.joinToString(",")}"))
                    }
                    return@forEachLine
                }

                if (line.trim().isEmpty()) return@forEachLine

                val cols = parseCsvLine(line)
                if (cols.size < 7) {
                    errors.add(ValidationError(lineNumber, "all", "Expected 7 columns, got ${cols.size}"))
                    return@forEachLine
                }

                // Member_ID
                val memberId = cols[0].trim().toIntOrNull()
                if (memberId == null || memberId !in validMemberIds) {
                    errors.add(ValidationError(lineNumber, "Member_ID", "Member ID not found in members file"))
                }

                // Guarantor_Member_ID (optional)
                val guarantorMemberId = cols[1].trim().toIntOrNull()
                if (guarantorMemberId != null) {
                    if (guarantorMemberId == memberId) {
                        errors.add(ValidationError(lineNumber, "Guarantor_Member_ID", "Borrower cannot be their own guarantor"))
                    } else if (guarantorMemberId !in validMemberIds) {
                        errors.add(ValidationError(lineNumber, "Guarantor_Member_ID", "Guarantor member ID not found in members file"))
                    }
                }

                // Disbursed_Amount
                val disbursedAmount = cols[2].trim().toDoubleOrNull()
                if (disbursedAmount == null || disbursedAmount <= 0) {
                    errors.add(ValidationError(lineNumber, "Disbursed_Amount", "Must be a positive number"))
                }

                // Interest_Rate
                val interestRate = cols[3].trim().toDoubleOrNull()
                if (interestRate == null || interestRate < 0 || interestRate > 100) {
                    errors.add(ValidationError(lineNumber, "Interest_Rate", "Must be 0-100"))
                }

                // Tenure_Months
                val tenureMonths = cols[4].trim().toIntOrNull()
                if (tenureMonths == null || tenureMonths <= 0 || tenureMonths > 600) {
                    errors.add(ValidationError(lineNumber, "Tenure_Months", "Must be 1-600 months"))
                }

                // Disbursement_Date_YYYY_MM_DD
                var disbursementDate: Long = System.currentTimeMillis()
                try {
                    val date = LocalDate.parse(cols[5].trim(), dateFormatter)
                    disbursementDate = date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
                } catch (e: Exception) {
                    errors.add(ValidationError(lineNumber, "Disbursement_Date_YYYY_MM_DD", "Invalid date format (use YYYY-MM-DD)"))
                }

                // Current_Principal_Outstanding
                val currentPrincipalOutstanding = cols[6].trim().toDoubleOrNull()
                if (currentPrincipalOutstanding == null || currentPrincipalOutstanding < 0) {
                    errors.add(ValidationError(lineNumber, "Current_Principal_Outstanding", "Must be non-negative number"))
                } else if (currentPrincipalOutstanding > (disbursedAmount ?: Double.MAX_VALUE)) {
                    errors.add(ValidationError(lineNumber, "Current_Principal_Outstanding", "Cannot exceed disbursed amount"))
                }

                if (errors.none { it.row == lineNumber }) {
                    rows.add(LoanRow(
                        memberId = memberId!!,
                        guarantorMemberId = guarantorMemberId,
                        disbursedAmount = disbursedAmount!!,
                        interestRate = interestRate!!,
                        tenureMonths = tenureMonths!!,
                        disbursementDate = disbursementDate,
                        currentPrincipalOutstanding = currentPrincipalOutstanding!!
                    ))
                }
            }
        }
        ParseResult(rows, errors)
    }

    private fun crossValidate(members: List<MemberRow>, savings: List<SavingsRow>, loans: List<LoanRow>): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        val memberIds = members.map { it.memberId }.toSet()

        // Check guarantor references in loans
        for (loan in loans) {
            if (loan.guarantorMemberId != null && loan.guarantorMemberId !in memberIds) {
                errors.add(ValidationError(-1, "Guarantor_Member_ID", "Loan references unknown guarantor member ID \${loan.guarantorMemberId}"))
            }
        }

        // Check savings member IDs
        for (saving in savings) {
            if (saving.memberId !in memberIds) {
                errors.add(ValidationError(-1, "Member_ID", "Savings references unknown member ID \${saving.memberId}"))
            }
        }

        // Cross-validation: Guarantor can only guarantee 1 active loan
        val guarantorCounts = mutableMapOf<Int, Int>()
        for (loan in loans) {
            if (loan.guarantorMemberId != null) {
                val count = guarantorCounts.getOrDefault(loan.guarantorMemberId!!, 0) + 1
                guarantorCounts[loan.guarantorMemberId!!] = count
                if (count > 1) {
                    errors.add(ValidationError(-1, "Guarantor_Member_ID", "Guarantor member ID \${loan.guarantorMemberId} cannot guarantee more than 1 loan in this import"))
                }
            }
        }

        // Cross-validation: Borrower cannot have active loan in this import
        val borrowerCounts = mutableMapOf<Int, Int>()
        for (loan in loans) {
            val count = borrowerCounts.getOrDefault(loan.memberId, 0) + 1
            borrowerCounts[loan.memberId] = count
            if (count > 1) {
                errors.add(ValidationError(-1, "Member_ID", "Borrower member ID \${loan.memberId} has multiple active loans in this import"))
            }
        }

        return errors
    }.toSet()

        // Check guarantor references in loans
        for (loan in loans) {
            if (loan.guarantorMemberId != null && loan.guarantorMemberId !in memberIds) {
                errors.add(ValidationError(-1, "Guarantor_Member_ID", "Loan references unknown guarantor member ID ${loan.guarantorMemberId}"))
            }
        }

        // Check savings member IDs
        for (saving in savings) {
            if (saving.memberId !in memberIds) {
                errors.add(ValidationError(-1, "Member_ID", "Savings references unknown member ID ${saving.memberId}"))
            }
        }

        return errors
    }

    // --- Username Generation (Section 8.3.A) ---

    private fun generateBaseUsername(fullName: String): String {
        val slug = fullName.trim()
            .lowercase(Locale.getDefault())
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .takeIf { it.isNotEmpty() } ?: "member"
        return slug
    }

    private fun generateUniqueUsername(baseUsername: String, memberId: Int, existingUsernames: Set<String>): String {
        return if (!existingUsernames.contains(baseUsername)) {
            baseUsername
        } else {
            "${baseUsername}_$memberId"
        }
    }

    private fun generateUsernamesForPreview(members: List<MemberRow>, existingUsernames: Set<String>): Map<Int, String> {
        val result = mutableMapOf<Int, String>()
        val used = existingUsernames.toMutableSet()
        for (member in members) {
            val base = generateBaseUsername(member.fullName)
            val username = generateUniqueUsername(base, member.memberId, used)
            used.add(username)
            result[member.memberId] = username
        }
        return result
    }

    private fun generateCredentialsSheet(rows: List<String>): String {
        val header = "Member_ID,Full_Name,Username,Phone_Number,Temporary_Password\n"
        return header + rows.joinToString("\n") + "\n"
    }

    private fun parseCsvLine(line: String): List<String> {
        // Simple CSV parser handling quoted fields
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    sb.append('"')
                    i += 2
                }
                c == '"' -> {
                    inQuotes = !inQuotes
                    i++
                }
                c == ',' && !inQuotes -> {
                    result.add(sb.toString())
                    sb.setLength(0)
                    i++
                }
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        result.add(sb.toString())
        return result
    }
}