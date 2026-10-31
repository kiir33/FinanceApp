package com.example.financeapp.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.example.financeapp.database.AppDatabase
import com.example.financeapp.database.entities.AuditLog
import com.example.financeapp.database.entities.Client
import com.example.financeapp.database.entities.GroupAccount
import com.example.financeapp.database.entities.GroupTransaction
import com.example.financeapp.database.entities.Loan
import com.example.financeapp.database.entities.LoanRepayment
import com.example.financeapp.database.entities.SavingsAccount
import com.example.financeapp.database.entities.SavingsTransaction
import com.example.financeapp.database.entities.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DecodingException
import kotlinx.serialization.EncodingException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.io.OutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface BackupResult {
    data object Success : BackupResult
    data class Failure(val message: String, val exception: Throwable? = null) : BackupResult
        override fun toString(): String = "Failure: $message${exception?.let { " (${it.message})" } ?: ""}"
}

class BackupManager(private val context: Context) {

    private val json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }

    // --- Export ---

    /**
     * Exports entire database to encrypted .apex file via SAF.
     * Returns BackupResult.
     */
    suspend fun exportDatabase(
        db: AppDatabase,
        destUri: Uri,
        userId: Long,
        username: String,
        passphrase: CharArray
    ): BackupResult {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Dump all tables to JSON
                val jsonString = dumpDatabaseToJson(db)

                // 2. Encrypt to .apex
                context.contentResolver.openOutputStream(destUri)?.use { outputStream ->
                    BackupCryptoEngine.encryptPayload(jsonString, passphrase, outputStream)
                } ?: return@withContext BackupResult.Failure("Failed to open output stream")

                // Audit log for backup
                db.auditLogDao().insertAuditLog(AuditLog(
                    userId = userId,
                    username = username,
                    action = "CSV_EXPORT",
                    details = "Full database backup exported to .apex format"
                ))

                BackupResult.Success
            } catch (e: Exception) {
                BackupResult.Failure("Export failed: ${e.message}", e)
            }
        }
    }

    /**
     * Exports database to JSON string (for testing or direct use).
     */
    suspend fun exportDatabaseToJson(db: AppDatabase): String {
        return withContext(Dispatchers.IO) {
            dumpDatabaseToJson(db)
        }
    }

    private suspend fun dumpDatabaseToJson(db: AppDatabase): String {
        return withContext(Dispatchers.IO) {
            val clients = db.clientDao().getAllClients().first()
            val savingsAccounts = db.savingsDao().getAllSavingsAccounts().first()
            val savingsTransactions = db.savingsDao().getAllSavingsAccounts().first().flatMap { account ->
                db.savingsDao().getTransactionsByAccountId(account.id).first()
            }
            val loans = db.loanDao().getAllLoans().first()
            val loanRepayments = loans.flatMap { loan ->
                db.loanDao().getRepaymentsByLoanId(loan.id).first()
            }
            val users = db.userDao().getAllUsers().first()
            val groupAccount = db.groupAccountDao().getGroupAccountById().first()
            val groupTransactions = db.groupTransactionDao().getAllTransactions().first()
            val auditLogs = db.auditLogDao().getAllAuditLogs().first()

            val jsonObj = jsonObject(
                "version" to 1,
                "exportedAt" to System.currentTimeMillis(),
                "clients" to jsonArray { clients.forEach { c -> addClient(c) } },
                "savingsAccounts" to jsonArray { savingsAccounts.forEach { s -> addSavingsAccount(s) } },
                "savingsTransactions" to jsonArray { savingsTransactions.forEach { t -> addSavingsTransaction(t) } },
                "loans" to jsonArray { loans.forEach { l -> addLoan(l) } },
                "loanRepayments" to jsonArray { loanRepayments.forEach { r -> addLoanRepayment(r) } },
                "users" to jsonArray { users.forEach { u -> addUser(u) } },
                "groupAccount" to groupAccount?.let { addGroupAccount(it) },
                "groupTransactions" to jsonArray { groupTransactions.forEach { gt -> addGroupTransaction(gt) } },
                "auditLogs" to jsonArray { auditLogs.forEach { al -> addAuditLog(al) } }
            )
            json.encodeToString(jsonObj.serializer(), jsonObj)
        }
    }

    // JSON serialization helpers
    private fun addClient(c: Client) = jsonObject(
        "id" to c.id,
        "clientId" to c.clientId,
        "name" to c.name,
        "phoneNumber" to c.phoneNumber,
        "address" to c.address,
        "createdAt" to c.createdAt
    )

    private fun addSavingsAccount(s: SavingsAccount) = jsonObject(
        "id" to s.id,
        "clientId" to s.clientId,
        "accountNumber" to s.accountNumber,
        "balance" to s.balance,
        "createdAt" to s.createdAt
    )

    private fun addSavingsTransaction(t: SavingsTransaction) = jsonObject(
        "id" to t.id,
        "accountId" to t.accountId,
        "amount" to t.amount,
        "type" to t.type,
        "description" to t.description,
        "transactionDate" to t.transactionDate,
        "timestamp" to t.timestamp
    )

    private fun addLoan(l: Loan) = jsonObject(
        "id" to l.id,
        "clientId" to l.clientId,
        "guarantorId" to l.guarantorId,
        "amount" to l.amount,
        "interestRate" to l.interestRate,
        "tenureMonths" to l.tenureMonths,
        "outstandingBalance" to l.outstandingBalance,
        "status" to l.status,
        "loanDate" to l.loanDate,
        "createdAt" to l.createdAt
    )

    private fun addLoanRepayment(r: LoanRepayment) = jsonObject(
        "id" to r.id,
        "loanId" to r.loanId,
        "amount" to r.amount,
        "principalPaid" to r.principalPaid,
        "interestPaid" to r.interestPaid,
        "timestamp" to r.timestamp
    )

    private fun addUser(u: User) = jsonObject(
        "id" to u.id,
        "username" to u.username,
        "passwordHash" to u.passwordHash,
        "salt" to u.salt,
        "role" to u.role,
        "clientId" to u.clientId,
        "isActive" to u.isActive,
        "failedAttempts" to u.failedAttempts,
        "lockedUntil" to u.lockedUntil,
        "mustChangePassword" to u.mustChangePassword,
        "createdAt" to u.createdAt,
        "lastLoginAt" to u.lastLoginAt
    )

    private fun addGroupAccount(g: GroupAccount) = jsonObject(
        "id" to g.id,
        "name" to g.name,
        "cashBalance" to g.cashBalance,
        "bankBalance" to g.bankBalance,
        "bankName" to g.bankName,
        "bankAccountNumber" to g.bankAccountNumber,
        "lastUpdated" to g.lastUpdated
    )

    private fun addGroupTransaction(gt: GroupTransaction) = jsonObject(
        "id" to gt.id,
        "type" to gt.type,
        "amount" to gt.amount,
        "channel" to gt.channel,
        "description" to gt.description,
        "relatedClientId" to gt.relatedClientId,
        "relatedLoanId" to gt.relatedLoanId,
        "relatedSavingsAccountId" to gt.relatedSavingsAccountId,
        "recordedByUserId" to gt.recordedByUserId,
        "transactionDate" to gt.transactionDate,
        "createdAt" to gt.createdAt
    )

    private fun addAuditLog(al: AuditLog) = jsonObject(
        "id" to al.id,
        "userId" to al.userId,
        "username" to al.username,
        "action" to al.action,
        "details" to al.details,
        "timestamp" to al.timestamp
    )

    // --- Import / Restore ---

    /**
     * Restores database from encrypted .apex file via SAF.
     * Wipes current data and repopulates in a single transaction.
     */
    suspend fun importDatabase(
        db: AppDatabase,
        sourceUri: Uri,
        passphrase: CharArray,
        userId: Long,
        username: String,
        onDbReopened: () -> Unit
    ): BackupResult {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Decrypt and parse JSON
                val jsonString = context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                    BackupCryptoEngine.decryptPayload(inputStream, passphrase)
                } ?: return@withContext BackupResult.Failure("Failed to open input stream")

                // 2. Parse and validate JSON
                val jsonObj = try {
                    json.decodeFromString(kotlinx.serialization.json.JsonObject.serializer(), jsonString)
                } catch (e: DecodingException) {
                    return@withContext BackupResult.Failure("Invalid backup file format: ${e.message}", e)
                }

                // 3. Validate version
                val version = jsonObj["version"]?.jsonPrimitive?.int ?: 0
                if (version != 1) {
                    return@withContext BackupResult.Failure("Unsupported backup version: $version")
                }

                // 4. Restore in single transaction
                db.runInTransaction {
                    // Clear all tables in dependency order (children first)
                    clearAllTables(db)

                    // Restore data
                    restoreClients(db, jsonObj)
                    restoreSavingsAccounts(db, jsonObj)
                    restoreSavingsTransactions(db, jsonObj)
                    restoreLoans(db, jsonObj)
                    restoreLoanRepayments(db, jsonObj)
                    restoreUsers(db, jsonObj)
                    restoreGroupAccount(db, jsonObj)
                    restoreGroupTransactions(db, jsonObj)
                    restoreAuditLogs(db, jsonObj)
                }

                // 5. Reopen database
                onDbReopened()

                // 6. Integrity check
                val integrityResult = verifyIntegrity(db)
                if (!integrityResult.isValid) {
                    return@withContext BackupResult.Failure("Integrity check failed: ${integrityResult.errors.joinToString(", ")}")
                }

                // Audit log for restore
                db.auditLogDao().insertAuditLog(AuditLog(
                    userId = userId,
                    username = username,
                    action = "DATABASE_RESTORE",
                    details = "Full database restored from .apex backup"
                ))

                BackupResult.Success
            } catch (e: IllegalArgumentException) {
                BackupResult.Failure("Invalid backup file or wrong passphrase: ${e.message}", e)
            } catch (e: Exception) {
                BackupResult.Failure("Restore failed: ${e.message}", e)
            }
        }
    }

    private fun clearAllTables(db: AppDatabase) {
        // Delete in reverse dependency order
        db.auditLogDao().deleteAll()
        db.groupTransactionDao().deleteAll()
        db.groupAccountDao().deleteAll()
        db.userDao().deleteAll()
        db.loanDao().deleteAllRepayments()
        db.loanDao().deleteAllLoans()
        db.savingsDao().deleteAllTransactions()
        db.savingsDao().deleteAllAccounts()
        db.clientDao().deleteAllClients()
    }

    // Restore methods
    private fun restoreClients(db: AppDatabase, json: kotlinx.serialization.json.JsonObject) {
        val arr = json["clients"]?.jsonArray ?: return
        for (elem in arr) {
            val obj = elem.jsonObject
            val client = Client(
                id = obj["id"]?.jsonPrimitive?.long ?: 0,
                clientId = obj["clientId"]?.jsonPrimitive?.int ?: 0,
                name = obj["name"]?.jsonPrimitive?.content ?: "",
                phoneNumber = obj["phoneNumber"]?.jsonPrimitive?.content ?: "",
                address = obj["address"]?.jsonPrimitive?.content ?: "",
                createdAt = obj["createdAt"]?.jsonPrimitive?.long ?: System.currentTimeMillis()
            )
            db.clientDao().insertClient(client)
        }
    }

    private fun restoreSavingsAccounts(db: AppDatabase, json: kotlinx.serialization.json.JsonObject) {
        val arr = json["savingsAccounts"]?.jsonArray ?: return
        for (elem in arr) {
            val obj = elem.jsonObject
            val account = SavingsAccount(
                id = obj["id"]?.jsonPrimitive?.long ?: 0,
                clientId = obj["clientId"]?.jsonPrimitive?.long ?: 0,
                accountNumber = obj["accountNumber"]?.jsonPrimitive?.content ?: "",
                balance = obj["balance"]?.jsonPrimitive?.double ?: 0.0,
                createdAt = obj["createdAt"]?.jsonPrimitive?.long ?: System.currentTimeMillis()
            )
            db.savingsDao().insertAccount(account)
        }
    }

    private fun restoreSavingsTransactions(db: AppDatabase, json: kotlinx.serialization.json.JsonObject) {
        val arr = json["savingsTransactions"]?.jsonArray ?: return
        for (elem in arr) {
            val obj = elem.jsonObject
            val tx = SavingsTransaction(
                id = obj["id"]?.jsonPrimitive?.long ?: 0,
                accountId = obj["accountId"]?.jsonPrimitive?.long ?: 0,
                amount = obj["amount"]?.jsonPrimitive?.double ?: 0.0,
                type = obj["type"]?.jsonPrimitive?.content ?: "",
                description = obj["description"]?.jsonPrimitive?.content ?: "",
                transactionDate = obj["transactionDate"]?.jsonPrimitive?.long ?: System.currentTimeMillis(),
                timestamp = obj["timestamp"]?.jsonPrimitive?.long ?: System.currentTimeMillis()
            )
            db.savingsDao().insertTransaction(tx)
        }
    }

    private fun restoreLoans(db: AppDatabase, json: kotlinx.serialization.json.JsonObject) {
        val arr = json["loans"]?.jsonArray ?: return
        for (elem in arr) {
            val obj = elem.jsonObject
            val loan = Loan(
                id = obj["id"]?.jsonPrimitive?.long ?: 0,
                clientId = obj["clientId"]?.jsonPrimitive?.long ?: 0,
                guarantorId = obj["guarantorId"]?.jsonPrimitive?.long,
                amount = obj["amount"]?.jsonPrimitive?.double ?: 0.0,
                interestRate = obj["interestRate"]?.jsonPrimitive?.double ?: 0.0,
                tenureMonths = obj["tenureMonths"]?.jsonPrimitive?.int ?: 0,
                outstandingBalance = obj["outstandingBalance"]?.jsonPrimitive?.double ?: 0.0,
                status = obj["status"]?.jsonPrimitive?.content ?: "ACTIVE",
                loanDate = obj["loanDate"]?.jsonPrimitive?.long ?: System.currentTimeMillis(),
                createdAt = obj["createdAt"]?.jsonPrimitive?.long ?: System.currentTimeMillis()
            )
            db.loanDao().insertLoan(loan)
        }
    }

    private fun restoreLoanRepayments(db: AppDatabase, json: kotlinx.serialization.json.JsonObject) {
        val arr = json["loanRepayments"]?.jsonArray ?: return
        for (elem in arr) {
            val obj = elem.jsonObject
            val repayment = LoanRepayment(
                id = obj["id"]?.jsonPrimitive?.long ?: 0,
                loanId = obj["loanId"]?.jsonPrimitive?.long ?: 0,
                amount = obj["amount"]?.jsonPrimitive?.double ?: 0.0,
                principalPaid = obj["principalPaid"]?.jsonPrimitive?.double ?: 0.0,
                interestPaid = obj["interestPaid"]?.jsonPrimitive?.double ?: 0.0,
                timestamp = obj["timestamp"]?.jsonPrimitive?.long ?: System.currentTimeMillis()
            )
            db.loanDao().insertRepayment(repayment)
        }
    }

    private fun restoreUsers(db: AppDatabase, json: kotlinx.serialization.json.JsonObject) {
        val arr = json["users"]?.jsonArray ?: return
        for (elem in arr) {
            val obj = elem.jsonObject
            val user = User(
                id = obj["id"]?.jsonPrimitive?.long ?: 0,
                username = obj["username"]?.jsonPrimitive?.content ?: "",
                passwordHash = obj["passwordHash"]?.jsonPrimitive?.content ?: "",
                salt = obj["salt"]?.jsonPrimitive?.content ?: "",
                role = obj["role"]?.jsonPrimitive?.content ?: "MEMBER",
                clientId = obj["clientId"]?.jsonPrimitive?.long,
                isActive = obj["isActive"]?.jsonPrimitive?.boolean ?: true,
                failedAttempts = obj["failedAttempts"]?.jsonPrimitive?.int ?: 0,
                lockedUntil = obj["lockedUntil"]?.jsonPrimitive?.long,
                mustChangePassword = obj["mustChangePassword"]?.jsonPrimitive?.boolean ?: true,
                createdAt = obj["createdAt"]?.jsonPrimitive?.long ?: System.currentTimeMillis(),
                lastLoginAt = obj["lastLoginAt"]?.jsonPrimitive?.long
            )
            db.userDao().insertUser(user)
        }
    }

    private fun restoreGroupAccount(db: AppDatabase, json: kotlinx.serialization.json.JsonObject) {
        val obj = json["groupAccount"]?.jsonObject ?: return
        val account = GroupAccount(
            id = obj["id"]?.jsonPrimitive?.long ?: 1,
            name = obj["name"]?.jsonPrimitive?.content ?: "Group Treasury",
            cashBalance = obj["cashBalance"]?.jsonPrimitive?.double ?: 0.0,
            bankBalance = obj["bankBalance"]?.jsonPrimitive?.double ?: 0.0,
            bankName = obj["bankName"]?.jsonPrimitive?.content ?: "",
            bankAccountNumber = obj["bankAccountNumber"]?.jsonPrimitive?.content ?: "",
            lastUpdated = obj["lastUpdated"]?.jsonPrimitive?.long ?: System.currentTimeMillis()
        )
        db.groupAccountDao().insertGroupAccount(account)
    }

    private fun restoreGroupTransactions(db: AppDatabase, json: kotlinx.serialization.json.JsonObject) {
        val arr = json["groupTransactions"]?.jsonArray ?: return
        for (elem in arr) {
            val obj = elem.jsonObject
            val tx = GroupTransaction(
                id = obj["id"]?.jsonPrimitive?.long ?: 0,
                type = obj["type"]?.jsonPrimitive?.content ?: "",
                amount = obj["amount"]?.jsonPrimitive?.double ?: 0.0,
                channel = obj["channel"]?.jsonPrimitive?.content ?: "",
                description = obj["description"]?.jsonPrimitive?.content ?: "",
                relatedClientId = obj["relatedClientId"]?.jsonPrimitive?.long,
                relatedLoanId = obj["relatedLoanId"]?.jsonPrimitive?.long,
                relatedSavingsAccountId = obj["relatedSavingsAccountId"]?.jsonPrimitive?.long,
                recordedByUserId = obj["recordedByUserId"]?.jsonPrimitive?.long ?: 0,
                transactionDate = obj["transactionDate"]?.jsonPrimitive?.long ?: System.currentTimeMillis(),
                createdAt = obj["createdAt"]?.jsonPrimitive?.long ?: System.currentTimeMillis()
            )
            db.groupTransactionDao().insertTransaction(tx)
        }
    }

    private fun restoreAuditLogs(db: AppDatabase, json: kotlinx.serialization.json.JsonObject) {
        val arr = json["auditLogs"]?.jsonArray ?: return
        for (elem in arr) {
            val obj = elem.jsonObject
            val log = AuditLog(
                id = obj["id"]?.jsonPrimitive?.long ?: 0,
                userId = obj["userId"]?.jsonPrimitive?.long ?: 0,
                username = obj["username"]?.jsonPrimitive?.content ?: "",
                action = obj["action"]?.jsonPrimitive?.content ?: "",
                details = obj["details"]?.jsonPrimitive?.content ?: "",
                timestamp = obj["timestamp"]?.jsonPrimitive?.long ?: System.currentTimeMillis()
            )
            db.auditLogDao().insertAuditLog(log)
        }
    }

    // --- Integrity Verification ---

    data class IntegrityResult(
        val isValid: Boolean,
        val errors: List<String>,
        val stats: Map<String, Int>
    )

    private fun verifyIntegrity(db: AppDatabase): IntegrityResult {
        val errors = mutableListOf<String>()
        val stats = mutableMapOf<String, Int>()

        // Count rows
        val clientCount = db.clientDao().getAllClients().first().size
        val savingsCount = db.savingsDao().getAllSavingsAccounts().first().size
        val loanCount = db.loanDao().getAllLoans().first().size
        val userCount = db.userDao().getAllUsers().first().size
        val groupTxCount = db.groupTransactionDao().getAllTransactions().first().size
        val auditCount = db.auditLogDao().getAllAuditLogs().first().size

        stats["clients"] = clientCount
        stats["savings_accounts"] = savingsCount
        stats["loans"] = loanCount
        stats["users"] = userCount
        stats["group_transactions"] = groupTxCount
        stats["audit_logs"] = auditCount

        // Sanity checks
        val totalSavingsBalance = db.savingsDao().getTotalSavings().first() ?: 0.0
        val totalOutstandingLoans = db.loanDao().getTotalOutstandingLoans().first() ?: 0.0

        // Verify savings balance matches transaction sum
        val savingsFromTx = db.savingsDao().getAllSavingsAccounts().first().sumOf { account ->
            db.savingsDao().getTransactionsByAccountId(account.id).first().sumOf { it.amount }
        }
        if (Math.abs(totalSavingsBalance - savingsFromTx) > 0.01) {
            errors.add("Savings balance mismatch: accounts sum=$totalSavingsBalance, transactions sum=$savingsFromTx")
        }

        // Verify group account exists
        val groupAccount = db.groupAccountDao().getGroupAccountById().first()
        if (groupAccount == null) {
            errors.add("Group account missing after restore")
        }

        return IntegrityResult(errors.isEmpty(), errors, stats)
    }

    // --- Helper for generating backup filename ---

    companion object {
        fun generateBackupFileName(): String {
            val sdf = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault())
            return "finance_backup_${sdf.format(Date())}.apex"
        }
    }

    // --- Google Drive Integration ---

    private val driveClient = GoogleDriveClient(context)

    /**
     * Checks if user is signed in to Google Drive.
     */
    fun isDriveSignedIn(): Boolean = driveClient.isSignedIn()

    /**
     * Gets the current Drive account email.
     */
    fun getDriveAccountEmail(): String? = driveClient.getCurrentAccountEmail()

    /**
     * Initiates Google Sign-In for Drive.
     * Returns the sign-in intent to be launched by the caller.
     */
    fun getDriveSignInIntent(): android.content.Intent {
        return driveClient.googleSignInClient.signInIntent
    }

    /**
     * Handles the sign-in result from onActivityResult.
     */
    fun handleDriveSignInResult(result: com.google.android.gms.tasks.Task<com.google.android.gms.auth.api.signin.GoogleSignInAccount>): GoogleDriveClient.DriveResult<com.google.android.gms.auth.api.signin.GoogleSignInAccount> {
        return driveClient.handleSignInResult(result)
    }

    /**
     * Signs out from Google Drive.
     */
    suspend fun driveSignOut(): GoogleDriveClient.DriveResult<Unit> = driveClient.signOut()

    /**
     * Uploads encrypted database backup to Google Drive.
     */
    suspend fun uploadBackupToDrive(
        db: AppDatabase,
        passphrase: CharArray,
        userId: Long,
        username: String
    ): GoogleDriveClient.DriveResult<GoogleDriveClient.DriveFile> {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Dump database to JSON
                val jsonString = dumpDatabaseToJson(db)

                // 2. Encrypt to .apex in memory
                val byteOut = ByteArrayOutputStream()
                BackupCryptoEngine.encryptPayload(jsonString, passphrase, byteOut)
                val encryptedBytes = byteOut.toByteArray()

                // 3. Generate filename
                val fileName = generateBackupFileName()

                // 4. Upload to Drive
                val inputStream = ByteArrayInputStream(encryptedBytes)
                val uploadResult = driveClient.uploadBackup(fileName, inputStream)

                // 5. Clean up old backups (keep 5 most recent)
                if (uploadResult is GoogleDriveClient.DriveResult.Success) {
                    driveClient.cleanupOldBackups(5)
                }

                // 6. Audit log
                if (uploadResult is GoogleDriveClient.DriveResult.Success) {
                    db.auditLogDao().insertAuditLog(AuditLog(
                        userId = userId,
                        username = username,
                        action = "DRIVE_BACKUP_UPLOAD",
                        details = "Encrypted backup uploaded to Google Drive: ${uploadResult.data.name}"
                    ))
                }

                uploadResult
            } catch (e: Exception) {
                GoogleDriveClient.DriveResult.Failure("Drive upload failed: ${e.message}", e)
            }
        }
    }

    /**
     * Lists recent backups from Google Drive.
     */
    suspend fun listDriveBackups(maxResults: Int = 5): GoogleDriveClient.DriveResult<List<GoogleDriveClient.DriveFile>> {
        return driveClient.listRecentBackups(maxResults)
    }

    /**
     * Downloads and restores database from Google Drive backup.
     */
    suspend fun restoreFromDrive(
        db: AppDatabase,
        fileId: String,
        passphrase: CharArray,
        userId: Long,
        username: String,
        onDbReopened: () -> Unit
    ): BackupResult {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Download backup from Drive
                val downloadResult = driveClient.downloadBackup(fileId)
                if (downloadResult is GoogleDriveClient.DriveResult.Failure) {
                    return@withContext BackupResult.Failure("Download failed: ${downloadResult.message}", downloadResult.exception)
                }

                // 2. Decrypt and restore using existing import logic
                val inputStream = ByteArrayInputStream(downloadResult.data)
                val jsonString = BackupCryptoEngine.decryptPayload(inputStream, passphrase)

                // 3. Parse and validate JSON
                val jsonObj = try {
                    json.decodeFromString(kotlinx.serialization.json.JsonObject.serializer(), jsonString)
                } catch (e: DecodingException) {
                    return@withContext BackupResult.Failure("Invalid backup file format: ${e.message}", e)
                }

                // 4. Validate version
                val version = jsonObj["version"]?.jsonPrimitive?.int ?: 0
                if (version != 1) {
                    return@withContext BackupResult.Failure("Unsupported backup version: $version")
                }

                // 5. Restore in single transaction
                db.runInTransaction {
                    clearAllTables(db)
                    restoreClients(db, jsonObj)
                    restoreSavingsAccounts(db, jsonObj)
                    restoreSavingsTransactions(db, jsonObj)
                    restoreLoans(db, jsonObj)
                    restoreLoanRepayments(db, jsonObj)
                    restoreUsers(db, jsonObj)
                    restoreGroupAccount(db, jsonObj)
                    restoreGroupTransactions(db, jsonObj)
                    restoreAuditLogs(db, jsonObj)
                }

                // 6. Reopen database
                onDbReopened()

                // 7. Integrity check
                val integrityResult = verifyIntegrity(db)
                if (!integrityResult.isValid) {
                    return@withContext BackupResult.Failure("Integrity check failed: ${integrityResult.errors.joinToString(", ")}")
                }

                // 8. Audit log for restore
                db.auditLogDao().insertAuditLog(AuditLog(
                    userId = userId,
                    username = username,
                    action = "DRIVE_BACKUP_RESTORE",
                    details = "Full database restored from Google Drive backup"
                ))

                BackupResult.Success
            } catch (e: IllegalArgumentException) {
                BackupResult.Failure("Invalid backup file or wrong passphrase: ${e.message}", e)
            } catch (e: Exception) {
                BackupResult.Failure("Drive restore failed: ${e.message}", e)
            }
        }
    }

    /**
     * Deletes a backup from Google Drive.
     */
    suspend fun deleteDriveBackup(fileId: String): GoogleDriveClient.DriveResult<Unit> {
        return driveClient.deleteBackup(fileId)
    }
}