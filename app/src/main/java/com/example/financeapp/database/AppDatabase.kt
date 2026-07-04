package com.example.financeapp.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.financeapp.database.dao.ClientDao
import com.example.financeapp.database.dao.LoanDao
import com.example.financeapp.database.dao.SavingsDao
import com.example.financeapp.database.entities.Client
import com.example.financeapp.database.entities.Loan
import com.example.financeapp.database.entities.LoanRepayment
import com.example.financeapp.database.entities.SavingsAccount
import com.example.financeapp.database.entities.SavingsTransaction

@Database(
    entities = [
        Client::class,
        SavingsAccount::class,
        SavingsTransaction::class,
        Loan::class,
        LoanRepayment::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun clientDao(): ClientDao
    abstract fun savingsDao(): SavingsDao
    abstract fun loanDao(): LoanDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "finance_database.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
