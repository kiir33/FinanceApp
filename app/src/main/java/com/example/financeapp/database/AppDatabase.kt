package com.example.financeapp.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.financeapp.database.dao.AuditLogDao
import com.example.financeapp.database.dao.ClientDao
import com.example.financeapp.database.dao.GroupAccountDao
import com.example.financeapp.database.dao.GroupTransactionDao
import com.example.financeapp.database.dao.LoanDao
import com.example.financeapp.database.dao.SavingsDao
import com.example.financeapp.database.dao.UserDao
import com.example.financeapp.database.entities.AuditLog
import com.example.financeapp.database.entities.Client
import com.example.financeapp.database.entities.GroupAccount
import com.example.financeapp.database.entities.GroupTransaction
import com.example.financeapp.database.entities.Loan
import com.example.financeapp.database.entities.LoanRepayment
import com.example.financeapp.database.entities.SavingsAccount
import com.example.financeapp.database.entities.SavingsTransaction
import com.example.financeapp.database.entities.User

@Database(
    entities = [
        Client::class,
        SavingsAccount::class,
        SavingsTransaction::class,
        Loan::class,
        LoanRepayment::class,
        User::class,
        GroupAccount::class,
        GroupTransaction::class,
        AuditLog::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun clientDao(): ClientDao
    abstract fun savingsDao(): SavingsDao
    abstract fun loanDao(): LoanDao
    abstract fun userDao(): UserDao
    abstract fun groupAccountDao(): GroupAccountDao
    abstract fun groupTransactionDao(): GroupTransactionDao
    abstract fun auditLogDao(): AuditLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Migration from version 6 to 7: add users, group_accounts, group_transactions, audit_logs tables
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create users table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `users` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `username` TEXT NOT NULL,
                        `passwordHash` TEXT NOT NULL,
                        `salt` TEXT NOT NULL,
                        `role` TEXT NOT NULL,
                        `clientId` INTEGER,
                        `isActive` INTEGER NOT NULL DEFAULT 1,
                        `failedAttempts` INTEGER NOT NULL DEFAULT 0,
                        `lockedUntil` INTEGER,
                        `mustChangePassword` INTEGER NOT NULL DEFAULT 1,
                        `createdAt` INTEGER NOT NULL,
                        `lastLoginAt` INTEGER,
                        FOREIGN KEY(`clientId`) REFERENCES `clients`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_users_username` ON `users` (`username`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_users_clientId` ON `users` (`clientId`)")

                // 2. Create group_accounts table and initialize default row
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `group_accounts` (
                        `id` INTEGER PRIMARY KEY NOT NULL,
                        `name` TEXT NOT NULL,
                        `cashBalance` REAL NOT NULL,
                        `bankBalance` REAL NOT NULL,
                        `bankName` TEXT NOT NULL,
                        `bankAccountNumber` TEXT NOT NULL,
                        `lastUpdated` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT OR IGNORE INTO `group_accounts` (`id`, `name`, `cashBalance`, `bankBalance`, `bankName`, `bankAccountNumber`, `lastUpdated`)
                    VALUES (1, 'Main Group Treasury', 0.0, 0.0, '', '', strftime('%s','now')*1000)
                """.trimIndent())

                // 3. Create group_transactions table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `group_transactions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `type` TEXT NOT NULL,
                        `amount` REAL NOT NULL,
                        `channel` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `relatedClientId` INTEGER,
                        `relatedLoanId` INTEGER,
                        `relatedSavingsAccountId` INTEGER,
                        `recordedByUserId` INTEGER NOT NULL,
                        `transactionDate` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_transactions_transactionDate` ON `group_transactions` (`transactionDate`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_transactions_relatedClientId` ON `group_transactions` (`relatedClientId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_transactions_relatedLoanId` ON `group_transactions` (`relatedLoanId`)")

                // 4. Create audit_logs table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `audit_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `userId` INTEGER NOT NULL,
                        `username` TEXT NOT NULL,
                        `action` TEXT NOT NULL,
                        `details` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_logs_timestamp` ON `audit_logs` (`timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_logs_userId` ON `audit_logs` (`userId`)")
            }
        }

        // Migration from version 7 to 8: Remove admin client records and set admin clientId to null
        // Admins should not be members and should not appear in the member list
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Set admin users' clientId to NULL (admins are not members)
                db.execSQL("UPDATE `users` SET `clientId` = NULL WHERE `role` = 'ADMIN'")

                // 2. Delete client records that were linked to admin users AND have no financial data
                // This ensures admins don't appear in the member list (ClientListScreen)
                // We only delete clients that:
                // - Were linked to an admin user (via the old clientId in users table before we nulled it)
                // - Have no savings accounts
                // - Have no loans
                // - Have no savings transactions
                // - Have no loan repayments
                db.execSQL("""
                    DELETE FROM `clients`
                    WHERE `id` IN (
                        SELECT u.`clientId` FROM `users` u
                        WHERE u.`role` = 'ADMIN' AND u.`clientId` IS NOT NULL
                    )
                    AND `id` NOT IN (SELECT DISTINCT `clientId` FROM `savings_accounts` WHERE `clientId` IS NOT NULL)
                    AND `id` NOT IN (SELECT DISTINCT `clientId` FROM `loans` WHERE `clientId` IS NOT NULL)
                    AND `id` NOT IN (SELECT DISTINCT `clientId` FROM `savings_transactions` WHERE `clientId` IS NOT NULL)
                    AND `id` NOT IN (SELECT DISTINCT `guarantorId` FROM `loans` WHERE `guarantorId` IS NOT NULL)
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "finance_database.db"
                )
                    .addMigrations(MIGRATION_6_7, MIGRATION_7_8)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}