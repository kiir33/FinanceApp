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

        // Migration from version 7 to 8: Add unique indices for phoneNumber and (clientId, name) on clients table
        // This migration handles existing duplicates by keeping the first occurrence and removing duplicates
        // Also normalizes phone numbers by removing + and spaces
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Step 1: Create a new table with the desired schema including unique indices
                db.execSQL("""
                    CREATE TABLE `clients_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `clientId` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `phoneNumber` TEXT NOT NULL,
                        `address` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                """.trimIndent())
                
                // Step 2: Create unique indices on the new table
                db.execSQL("CREATE UNIQUE INDEX `index_clients_new_clientId` ON `clients_new` (`clientId`)")
                db.execSQL("CREATE UNIQUE INDEX `index_clients_new_phoneNumber` ON `clients_new` (`phoneNumber`)")
                db.execSQL("CREATE UNIQUE INDEX `index_clients_new_clientId_name` ON `clients_new` (`clientId`, `name`)")
                
                // Step 3: Copy data from old table to new table, handling duplicates
                // For phoneNumber: normalize by removing + and spaces, keep first occurrence
                // For clientId + name: keep first occurrence
                // We use a CTE to identify rows to keep (first occurrence of each unique combination)
                // Also normalize phoneNumber when storing
                db.execSQL("""
                    INSERT INTO `clients_new` (`id`, `clientId`, `name`, `phoneNumber`, `address`, `createdAt`)
                    SELECT `id`, `clientId`, `name`, REPLACE(REPLACE(`phoneNumber`, '+', ''), ' ', ''), `address`, `createdAt`
                    FROM (
                        SELECT *,
                            ROW_NUMBER() OVER (
                                PARTITION BY REPLACE(REPLACE(`phoneNumber`, '+', ''), ' ', '')
                                ORDER BY `id`
                            ) as phone_rn,
                            ROW_NUMBER() OVER (
                                PARTITION BY `clientId`, `name`
                                ORDER BY `id`
                            ) as name_id_rn
                        FROM `clients`
                    )
                    WHERE phone_rn = 1 AND name_id_rn = 1
                """.trimIndent())
                
                // Step 4: Drop the old table
                db.execSQL("DROP TABLE `clients`")
                
                // Step 5: Rename new table to original name
                db.execSQL("ALTER TABLE `clients_new` RENAME TO `clients`")
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
