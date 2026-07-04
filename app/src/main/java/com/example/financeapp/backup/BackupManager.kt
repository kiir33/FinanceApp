package com.example.financeapp.backup

import android.content.Context
import android.net.Uri
import com.example.financeapp.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class BackupManager(private val context: Context) {

    suspend fun exportDatabase(db: AppDatabase, destUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. Checkpoint the WAL file into the main SQLite database file
            val writableDb = db.openHelper.writableDatabase
            writableDb.query("PRAGMA wal_checkpoint(FULL)").use { cursor ->
                if (cursor.moveToFirst()) {
                    // Checkpoint results can be logged or checked if needed
                }
            }

            // 2. Locate the database file
            val dbFile = context.getDatabasePath("finance_database.db")
            if (!dbFile.exists()) {
                return@withContext Result.failure(Exception("Database file does not exist."))
            }

            // 3. Copy to destination Uri via ContentResolver
            context.contentResolver.openOutputStream(destUri)?.use { outputStream ->
                FileInputStream(dbFile).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: return@withContext Result.failure(Exception("Failed to open destination stream."))

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importDatabase(db: AppDatabase, sourceUri: Uri, onDatabaseRecreated: () -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. Close current database
            db.close()

            // 2. Locate the database file and WAL files
            val dbFile = context.getDatabasePath("finance_database.db")
            val walFile = File(dbFile.path + "-wal")
            val shmFile = File(dbFile.path + "-shm")

            // 3. Copy from source Uri to the database file path
            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                FileOutputStream(dbFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: return@withContext Result.failure(Exception("Failed to open source stream."))

            // 4. Delete WAL and SHM files to ensure no old journal conflicts with the imported DB
            if (walFile.exists()) walFile.delete()
            if (shmFile.exists()) shmFile.delete()

            // 5. Reinitialize the database
            withContext(Dispatchers.Main) {
                onDatabaseRecreated()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
