package com.example.financeapp.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.financeapp.database.dao.AuditLogDao
import com.example.financeapp.database.dao.AppSettingsDao
import com.example.financeapp.database.dao.ClientDao
import com.example.financeapp.database.dao.GroupAccountDao
import com.example.financeapp.database.dao.GroupTransactionDao
import com.example.financeapp.database.dao.LoanDao
import com.example.financeapp.database.dao.SavingsDao
import com.example.financeapp.database.dao.UserDao
import com.example.financeapp.database.entities.AppSettings
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
        AuditLog::class,
        AppSettings::class
    ],
    version = 10,
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
    abstract fun appSettingsDao(): AppSettingsDao

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

        // Migration from version 7 to 8: Remove admin client records and set admin clientId to null.
        // Admins should not be members and should not appear in the member list.
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Users now carry their own phone number. Admins have no Client row, so this is
                //    the only place their phone can live (and it keeps phone login working).
                db.execSQL("ALTER TABLE `users` ADD COLUMN `phoneNumber` TEXT")

                // 2. Preserve the admin's phone number before the linked Client row is removed.
                db.execSQL("""
                    UPDATE `users`
                    SET `phoneNumber` = (
                        SELECT `phoneNumber` FROM `clients`
                        WHERE `clients`.`id` = `users`.`clientId`
                    )
                    WHERE `role` = 'ADMIN'
                      AND `clientId` IS NOT NULL
                      AND `phoneNumber` IS NULL
                """.trimIndent())

                // 3. Delete the Client row linked to admin users, but only when it has no financial
                //    data attached. Run BEFORE clearing users.clientId so the link is still visible.
                //    NOTE: savings_transactions has no clientId column (it links via accountId), so
                //    the savings_accounts guard below is what protects transaction history.
                db.execSQL("""
                    DELETE FROM `clients`
                    WHERE `id` IN (
                        SELECT `clientId` FROM `users`
                        WHERE `role` = 'ADMIN' AND `clientId` IS NOT NULL
                    )
                    AND `id` NOT IN (SELECT DISTINCT `clientId` FROM `savings_accounts` WHERE `clientId` IS NOT NULL)
                    AND `id` NOT IN (SELECT DISTINCT `clientId` FROM `loans` WHERE `clientId` IS NOT NULL)
                    AND `id` NOT IN (SELECT DISTINCT `guarantorId` FROM `loans` WHERE `guarantorId` IS NOT NULL)
                """.trimIndent())

                // 4. Safety net: admins must never be linked to a member record. (Deleting the
                //    client above already triggers ON DELETE SET NULL when FK enforcement is on.)
                db.execSQL("UPDATE `users` SET `clientId` = NULL WHERE `role` = 'ADMIN'")
            }
        }

        // Migration from version 8 to 9: enforce unique phone numbers and unique (clientId, name).
        // Implemented in-place (no table recreate) so all financial data and foreign keys survive.
        // NOTE: deliberately avoids window functions (ROW_NUMBER) because they are unavailable on
        // SQLite < 3.25, which is what Android bundles below API 30 (minSdk is 24).
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Normalize stored phone numbers to digits only. Mirrors normalizePhoneNumber()
                //    used by all Kotlin call sites (SQLite has no regex, so strip known separators).
                db.execSQL("""
                    UPDATE `clients`
                    SET `phoneNumber` = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
                        `phoneNumber`, '+', ''), ' ', ''), '-', ''), '(', ''), ')', ''), '.', ''), '/', ''), '#', ''), '*', ''), '&', '')
                """.trimIndent())

                // 2. De-duplicate non-destructively: keep the earliest row per phone number and
                //    append '#<row id>' to the others so no member/transaction data is dropped.
                db.execSQL("""
                    UPDATE `clients`
                    SET `phoneNumber` = `phoneNumber` || '#' || `id`
                    WHERE `id` NOT IN (SELECT MIN(`id`) FROM `clients` GROUP BY `phoneNumber`)
                """.trimIndent())

                // 3. Add the unique indices declared on the Client entity. `clientId` already has a
                //    unique index from earlier versions, so only these two are new.
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_clients_phoneNumber` ON `clients` (`phoneNumber`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_clients_clientId_name` ON `clients` (`clientId`, `name`)")
            }
        }

        // Migration from version 9 to 10: Add app_settings table for configurable settings
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `app_settings` (
                        `key` TEXT PRIMARY KEY NOT NULL,
                        `value` TEXT NOT NULL
                    )
                """.trimIndent())

                // Insert default loan_to_savings_ratio setting (80%)
                db.execSQL("""
                    INSERT OR IGNORE INTO `app_settings` (`key`, `value`)
                    VALUES ('loan_to_savings_ratio', '0.8')
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
                    .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Fresh installs are created directly at the current version, so the
                            // migration above never runs. Seed the default ratio explicitly.
                            db.execSQL("""
                                INSERT OR IGNORE INTO `app_settings` (`key`, `value`)
                                VALUES ('loan_to_savings_ratio', '0.8')
                            """.trimIndent())
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}