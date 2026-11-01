package com.example.financeapp.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.example.financeapp.database.AppDatabase
import com.example.financeapp.database.entities.AuditLog
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.io.OutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

sealed interface BackupResult {
    data object Success : BackupResult
    data class Failure(val message: String, val exception: Throwable? = null) : BackupResult {
        override fun toString(): String = "Failure: $message${exception?.let { " (${it.message})" } ?: ""}"
    }
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
            val savingsTransactions = savingsAccounts.flatMap { account ->
                db.savingsDao().getTransactionsByAccountId(account.id).first()
            }
            val loans = db.loanDao().getAllLoans().first()
            val loanRepayments = loans.flatMap { loan ->
                db.loanDao().getRepaymentsByLoanId(loan.id).first()
            }
            val users = db.userDao().getAllUsers().first()
            val groupAccount = db.groupAccountDao().getGroupAccountById()
            val groupTransactions = db.groupTransactionDao().getAllTransactions().first()
            val auditLogs = db.auditLogDao().getAllAuditLogs().first()

            val jsonObj = buildJsonObject {
                put("version", 1)
                put("exportedAt", System.currentTimeMillis())
                put("clients", buildJsonArray { clients.forEach { add(addClient(it)) } })
                put("savingsAccounts", buildJsonArray { savingsAccounts.forEach { add(addSavingsAccount(it)) } })
                put("savingsTransactions", buildJsonArray { savingsTransactions.forEach { add(addSavingsTransaction(it)) } })
                put("loans", buildJsonArray { loans.forEach { add(addLoan(it)) } })
                put("loanRepayments", buildJsonArray { loanRepayments.forEach { add(addLoanRepayment(it)) } })
                put("users", buildJsonArray { users.forEach { add(addUser(it)) } })
                if (groupAccount != null) {
                    put("groupAccount", addGroupAccount(groupAccount))
                }
                put("groupTransactions", buildJsonArray { groupTransactions.forEach { add(addGroupTransaction(it)) } })
                put("auditLogs", buildJsonArray { auditLogs.forEach { add(addAuditLog(it)) } })
            }
            json.encodeToString(JsonObject.serializer(), jsonObj)
        }
    }

    // JSON serialization helpers
    private fun addClient(c: Client): JsonObject = buildJsonObject {
        put("id", c.id)
        put("clientId", c.clientId)
        put("name", c.name)
        put("phoneNumber", c.phoneNumber)
        put("address", c.address)
        put("createdAt", c.createdAt)
    }

    private fun addSavingsAccount(s: SavingsAccount): JsonObject = buildJsonObject {
        put("id", s.id)
        put("clientId", s.clientId)
        put("accountNumber", s.accountNumber)
        put("balance", s.balance)
        put("createdAt", s.createdAt)
    }

    private fun addSavingsTransaction(t: SavingsTransaction): JsonObject = buildJsonObject {
        put("id", t.id)
        put("accountId", t.accountId)
        put("amount", t.amount)
        put("type", t.type)
        put("description", t.description)
        put("transactionDate", t.transactionDate)
        put("timestamp", t.timestamp)
    }

    private fun addLoan(l: Loan): JsonObject = buildJsonObject {
        put("id", l.id)
        put("clientId", l.clientId)
        if (l.guarantorId != null) put("guarantorId", l.guarantorId)
        put("amount", l.amount)
        put("interestRate", l.interestRate)
        put("tenureMonths", l.tenureMonths)
        put("outstandingBalance", l.outstandingBalance)
        put("status", l.status)
        put("loanDate", l.loanDate)
        put("createdAt", l.createdAt)
    }

    private fun addLoanRepayment(r: LoanRepayment): JsonObject = buildJsonObject {
        put("id", r.id)
        put("loanId", r.loanId)
        put("amount", r.amount)
        put("principalPaid", r.principalPaid)
        put("interestPaid", r.interestPaid)
        put("timestamp", r.timestamp)
    }

    private fun addUser(u: User): JsonObject = buildJsonObject {
        put("id", u.id)
        put("username", u.username)
        put("passwordHash", u.passwordHash)
        put("salt", u.salt)
        put("role", u.role)
        if (u.clientId != null) put("clientId", u.clientId)
        put("isActive", u.isActive)
        put("failedAttempts", u.failedAttempts)
        if (u.lockedUntil != null) put("lockedUntil", u.lockedUntil)
        put("mustChangePassword", u.mustChangePassword)
        put("createdAt", u.createdAt)
        if (u.lastLoginAt != null) put("lastLoginAt", u.lastLoginAt)
    }

    private fun addGroupAccount(g: GroupAccount): JsonObject = buildJsonObject {
        put("id", g.id)
        put("name", g.name)
        put("cashBalance", g.cashBalance)
        put("bankBalance", g.bankBalance)
        if (g.bankName != null) put("bankName", g.bankName)
        if (g.bankAccountNumber != null) put("bankAccountNumber", g.bankAccountNumber)
        put("lastUpdated", g.lastUpdated)
    }

    private fun addGroupTransaction(gt: GroupTransaction): JsonObject = buildJsonObject {
        put("id", gt.id)
        put("type", gt.type)
        put("amount", gt.amount)
        put("channel", gt.channel)
        put("description", gt.description)
        if (gt.relatedClientId != null) put("relatedClientId", gt.relatedClientId)
        if (gt.relatedLoanId != null) put("relatedLoanId", gt.relatedLoanId)
        if (gt.relatedSavingsAccountId != null) put("relatedSavingsAccountId", gt.relatedSavingsAccountId)
        if (gt.recordedByUserId != null) put("recordedByUserId", gt.recordedByUserId)
        put("transactionDate", gt.transactionDate)
        put("createdAt", gt.createdAt)
    }

    private fun addAuditLog(al: AuditLog): JsonObject = buildJsonObject {
        put("id", al.id)
        put("userId", al.userId)
        put("username", al.username)
        put("action", al.action)
        put("details", al.details)
        put("timestamp", al.timestamp)
    }

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
                    json.decodeFromString(JsonObject.serializer(), jsonString)
                } catch (e: SerializationException) {
                    return@withContext BackupResult.Failure("Invalid backup file format: ${e.message}", e)
                }

                // 3. Validate version
                val version = jsonObj["version"]?.jsonPrimitive?.int ?: 0
                if (version != 1) {
                    return@withContext BackupResult.Failure("Unsupported backup version: $version")
                }

                // 4. Restore in single transaction
                db.withTransaction {
                    db.clearAllTables()

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

    // Restore methods
    private suspend fun restoreClients(db: AppDatabase, json: JsonObject) {
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

    private suspend fun restoreSavingsAccounts(db: AppDatabase, json: JsonObject) {
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

    private suspend fun restoreSavingsTransactions(db: AppDatabase, json: JsonObject) {
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

    private suspend fun restoreLoans(db: AppDatabase, json: JsonObject) {
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

    private suspend fun restoreLoanRepayments(db: AppDatabase, json: JsonObject) {
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

    private suspend fun restoreUsers(db: AppDatabase, json: JsonObject) {
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

    private suspend fun restoreGroupAccount(db: AppDatabase, json: JsonObject) {
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

    private suspend fun restoreGroupTransactions(db: AppDatabase, json: JsonObject) {
        val arr = json["groupTransactions"]?.jsonArray ?: return
        for (elem in arr) {
            val obj = elem.jsonObject
            val typeStr = obj["type"]?.jsonPrimitive?.content ?: GroupTxType.EXPENSE.name
            val type = try { GroupTxType.valueOf(typeStr) } catch (e: Exception) { GroupTxType.EXPENSE }
            val tx = GroupTransaction(
                id = obj["id"]?.jsonPrimitive?.long ?: 0,
                type = typeStr,
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

    private suspend fun restoreAuditLogs(db: AppDatabase, json: JsonObject) {
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

    private suspend fun verifyIntegrity(db: AppDatabase): IntegrityResult {
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
        val savingsFromTx = db.savingsDao().getAllSavingsAccounts().first().sumOf { account: SavingsAccount ->
            db.savingsDao().getTransactionsByAccountId(account.id).first().sumOf { tx: SavingsTransaction -> tx.amount }
        }
        if (abs(totalSavingsBalance - savingsFromTx) > 0.01) {
            errors.add("Savings balance mismatch: accounts sum=$totalSavingsBalance, transactions sum=$savingsFromTx")
        }

        // Verify group account exists
        val groupAccount = db.groupAccountDao().getGroupAccountById()
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
                val downloadData = when (val downloadResult = driveClient.downloadBackup(fileId)) {
                    is GoogleDriveClient.DriveResult.Success -> downloadResult.data
                    is GoogleDriveClient.DriveResult.Failure -> return@withContext BackupResult.Failure("Download failed: ${downloadResult.message}", downloadResult.exception)
                }

                // 2. Decrypt and restore using existing import logic
                val inputStream = ByteArrayInputStream(downloadData)
                val jsonString = BackupCryptoEngine.decryptPayload(inputStream, passphrase)

                // 3. Parse and validate JSON
                val jsonObj = try {
                    json.decodeFromString(JsonObject.serializer(), jsonString)
                } catch (e: SerializationException) {
                    return@withContext BackupResult.Failure("Invalid backup file format: ${e.message}", e)
                }

                // 4. Validate version
                val version = jsonObj["version"]?.jsonPrimitive?.int ?: 0
                if (version != 1) {
                    return@withContext BackupResult.Failure("Unsupported backup version: $version")
                }

                // 5. Restore in single transaction
                db.withTransaction {
                    db.clearAllTables()
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