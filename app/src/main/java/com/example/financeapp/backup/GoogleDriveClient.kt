package com.example.financeapp.backup

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.FileList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Collections
import java.util.concurrent.TimeUnit

/**
 * Google Drive client for appDataFolder backup operations.
 * Uses Drive REST API v3 with DRIVE_APPDATA scope for private app storage.
 */
class GoogleDriveClient(private val context: Context) {

    companion object {
        private const val TAG = "GoogleDriveClient"
        private const val REQUEST_CODE_SIGN_IN = 9001
    }

    private val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
        .requestEmail()
        .build()

    private val googleSignInClient: GoogleSignInClient = GoogleSignIn.getClient(context, signInOptions)

    private var driveService: Drive? = null
    private var currentAccount: GoogleSignInAccount? = null

    /**
     * Result wrapper for Drive operations.
     */
    sealed interface DriveResult<out T> {
        data class Success<out T>(val data: T) : DriveResult<T>
        data class Failure(val message: String, val exception: Throwable? = null) : DriveResult<Nothing>
            override fun toString(): String = "Failure: $message${exception?.let { " (${it.message})" } ?: ""}"
    }

    /**
     * Drive file metadata.
     */
    data class DriveFile(
        val id: String,
        val name: String,
        val size: Long,
        val createdTime: Long,
        val modifiedTime: Long
    )

    /**
     * Initiates Google Sign-In flow.
     * Call this from an Activity and handle result in onActivityResult.
     */
    fun signIn(activity: Activity): Task<GoogleSignInAccount> {
        val signInIntent = googleSignInClient.signInIntent
        activity.startActivityForResult(signInIntent, REQUEST_CODE_SIGN_IN)
        // Note: The actual result is delivered via onActivityResult
        // The caller should use GoogleSignIn.getSignedInAccountFromIntent(intent)
        return GoogleSignIn.getSignedInAccountFromIntent(signInIntent)
    }

    /**
     * Handles the sign-in result from onActivityResult.
     */
    fun handleSignInResult(result: Task<GoogleSignInAccount>): DriveResult<GoogleSignInAccount> {
        return try {
            val account = result.getResult(ApiException::class.java)
            currentAccount = account
            initializeDriveService(account)
            DriveResult.Success(account)
        } catch (e: ApiException) {
            Log.e(TAG, "Sign-in failed: ${e.statusCode}", e)
            DriveResult.Failure("Sign-in failed: ${getAuthErrorMessage(e.statusCode)}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Sign-in error", e)
            DriveResult.Failure("Sign-in error: ${e.message}", e)
        }
    }

    /**
     * Signs out the current user and clears credentials.
     */
    suspend fun signOut(): DriveResult<Unit> = withContext(Dispatchers.IO) {
        try {
            googleSignInClient.signOut().await()
            currentAccount = null
            driveService = null
            DriveResult.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Sign-out failed", e)
            DriveResult.Failure("Sign-out failed: ${e.message}", e)
        }
    }

    /**
     * Gets the last signed-in account without prompting.
     */
    fun getLastSignedInAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    /**
     * Checks if user is currently signed in.
     */
    fun isSignedIn(): Boolean {
        return getLastSignedInAccount() != null
    }

    /**
     * Gets the current signed-in account email.
     */
    fun getCurrentAccountEmail(): String? {
        return currentAccount?.email ?: getLastSignedInAccount()?.email
    }

    /**
     * Initializes the Drive service with the given account.
     */
    private fun initializeDriveService(account: GoogleSignInAccount) {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            Collections.singleton(DriveScopes.DRIVE_APPDATA)
        )
        credential.selectedAccount = account.account
        driveService = Drive.Builder(AndroidHttp.newCompatibleTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("FinanceApp")
            .build()
    }

    /**
     * Ensures Drive service is initialized.
     */
    private fun ensureDriveService(): DriveResult<Drive> {
        val account = currentAccount ?: getLastSignedInAccount()
            ?: return DriveResult.Failure("Not signed in. Please sign in first.")
        
        if (driveService == null) {
            initializeDriveService(account)
        }
        return DriveResult.Success(driveService!!)
    }

    /**
     * Uploads a backup file to Drive appDataFolder.
     */
    suspend fun uploadBackup(
        fileName: String,
        inputStream: InputStream,
        mimeType: String = "application/octet-stream"
    ): DriveResult<DriveFile> = withContext(Dispatchers.IO) {
        val serviceResult = ensureDriveService()
        if (serviceResult is DriveResult.Failure) {
            return@withContext serviceResult
        }
        val service = serviceResult.data

        try {
            val fileMetadata = File()
                .setName(fileName)
                .setParents(listOf("appDataFolder"))
                .setMimeType(mimeType)

            // Read input stream to byte array for MediaHttpUploader
            val byteArray = inputStream.readBytes()
            val mediaContent = com.google.api.client.http.ByteArrayContent(mimeType, byteArray)

            val uploadedFile = service.files()
                .create(fileMetadata, mediaContent)
                .setFields("id,name,size,createdTime,modifiedTime")
                .execute()

            val driveFile = DriveFile(
                id = uploadedFile.id,
                name = uploadedFile.name!!,
                size = uploadedFile.size ?: byteArray.size.toLong(),
                createdTime = uploadedFile.createdTime?.value ?: System.currentTimeMillis(),
                modifiedTime = uploadedFile.modifiedTime?.value ?: System.currentTimeMillis()
            )

            Log.i(TAG, "Uploaded backup: ${driveFile.name} (${driveFile.id})")
            DriveResult.Success(driveFile)

        } catch (e: IOException) {
            Log.e(TAG, "Upload failed", e)
            DriveResult.Failure("Upload failed: ${getNetworkErrorMessage(e)}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Upload error", e)
            DriveResult.Failure("Upload error: ${e.message}", e)
        }
    }

    /**
     * Lists recent backup files in appDataFolder.
     */
    suspend fun listRecentBackups(maxResults: Int = 5): DriveResult<List<DriveFile>> = withContext(Dispatchers.IO) {
        val serviceResult = ensureDriveService()
        if (serviceResult is DriveResult.Failure) {
            return@withContext serviceResult
        }
        val service = serviceResult.data

        try {
            val fileList: FileList = service.files()
                .list()
                .setSpaces("appDataFolder")
                .setFields("files(id,name,size,createdTime,modifiedTime)")
                .setOrderBy("modifiedTime desc")
                .setPageSize(maxResults)
                .execute()

            val files = fileList.files ?: emptyList()
            val driveFiles = files.map { file ->
                DriveFile(
                    id = file.id!!,
                    name = file.name!!,
                    size = file.size ?: 0,
                    createdTime = file.createdTime?.value ?: 0,
                    modifiedTime = file.modifiedTime?.value ?: 0
                )
            }

            Log.i(TAG, "Listed ${driveFiles.size} recent backups")
            DriveResult.Success(driveFiles)

        } catch (e: IOException) {
            Log.e(TAG, "List failed", e)
            DriveResult.Failure("Failed to list backups: ${getNetworkErrorMessage(e)}", e)
        } catch (e: Exception) {
            Log.e(TAG, "List error", e)
            DriveResult.Failure("List error: ${e.message}", e)
        }
    }

    /**
     * Downloads a backup file from Drive.
     */
    suspend fun downloadBackup(fileId: String): DriveResult<ByteArray> = withContext(Dispatchers.IO) {
        val serviceResult = ensureDriveService()
        if (serviceResult is DriveResult.Failure) {
            return@withContext serviceResult
        }
        val service = serviceResult.data

        try {
            val outputStream = ByteArrayOutputStream()
            service.files()
                .get(fileId)
                .executeMediaAndDownloadTo(outputStream)

            val bytes = outputStream.toByteArray()
            Log.i(TAG, "Downloaded backup: $fileId (${bytes.size} bytes)")
            DriveResult.Success(bytes)

        } catch (e: IOException) {
            Log.e(TAG, "Download failed", e)
            DriveResult.Failure("Download failed: ${getNetworkErrorMessage(e)}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Download error", e)
            DriveResult.Failure("Download error: ${e.message}", e)
        }
    }

    /**
     * Downloads a backup file as InputStream for streaming.
     */
    suspend fun downloadBackupAsStream(fileId: String): DriveResult<InputStream> = withContext(Dispatchers.IO) {
        val serviceResult = ensureDriveService()
        if (serviceResult is DriveResult.Failure) {
            return@withContext serviceResult
        }
        val service = serviceResult.data

        try {
            val inputStream = service.files()
                .get(fileId)
                .executeMediaAsInputStream()

            Log.i(TAG, "Opened download stream for: $fileId")
            DriveResult.Success(inputStream)

        } catch (e: IOException) {
            Log.e(TAG, "Download stream failed", e)
            DriveResult.Failure("Download failed: ${getNetworkErrorMessage(e)}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Download stream error", e)
            DriveResult.Failure("Download error: ${e.message}", e)
        }
    }

    /**
     * Deletes a backup file from Drive.
     */
    suspend fun deleteBackup(fileId: String): DriveResult<Unit> = withContext(Dispatchers.IO) {
        val serviceResult = ensureDriveService()
        if (serviceResult is DriveResult.Failure) {
            return@withContext serviceResult
        }
        val service = serviceResult.data

        try {
            service.files().delete(fileId).execute()
            Log.i(TAG, "Deleted backup: $fileId")
            DriveResult.Success(Unit)
        } catch (e: IOException) {
            Log.e(TAG, "Delete failed", e)
            DriveResult.Failure("Delete failed: ${getNetworkErrorMessage(e)}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Delete error", e)
            DriveResult.Failure("Delete error: ${e.message}", e)
        }
    }

    /**
     * Cleans up old backups beyond the retention limit.
     */
    suspend fun cleanupOldBackups(keepCount: Int = 5): DriveResult<Int> = withContext(Dispatchers.IO) {
        val listResult = listRecentBackups(keepCount + 10) // Get more to find old ones
        if (listResult is DriveResult.Failure) {
            return@withContext DriveResult.Failure(listResult.message, listResult.exception)
        }

        val files = listResult.data
        if (files.size <= keepCount) {
            return@withContext DriveResult.Success(0)
        }

        val toDelete = files.drop(keepCount)
        var deletedCount = 0

        for (file in toDelete) {
            val deleteResult = deleteBackup(file.id)
            if (deleteResult is DriveResult.Success) {
                deletedCount++
            } else {
                Log.w(TAG, "Failed to delete old backup: ${file.id} - ${deleteResult.message}")
            }
        }

        DriveResult.Success(deletedCount)
    }

    /**
     * Converts Google Sign-In status code to user-friendly message.
     */
    private fun getAuthErrorMessage(statusCode: Int): String {
        return when (statusCode) {
            com.google.android.gms.common.api.CommonStatusCodes.SIGN_IN_REQUIRED -> "Sign in required"
            com.google.android.gms.common.api.CommonStatusCodes.NETWORK_ERROR -> "Network error. Check your connection."
            com.google.android.gms.common.api.CommonStatusCodes.RESOLUTION_REQUIRED -> "Additional steps required to sign in"
            com.google.android.gms.common.api.CommonStatusCodes.CANCELED -> "Sign in cancelled"
            com.google.android.gms.common.api.CommonStatusCodes.TIMEOUT -> "Sign in timed out"
            com.google.android.gms.common.api.CommonStatusCodes.API_NOT_CONNECTED -> "Google Play Services not available"
            com.google.android.gms.common.api.CommonStatusCodes.DEVELOPER_ERROR -> "Configuration error. Contact support."
            else -> "Sign in failed (code: $statusCode)"
        }
    }

    /**
     * Converts network IOException to user-friendly message.
     */
    private fun getNetworkErrorMessage(e: IOException): String {
        val message = e.message?.lowercase() ?: ""
        return when {
            message.contains("unknownhost") || message.contains("network") -> "No internet connection. Please check your network."
            message.contains("timeout") -> "Request timed out. Please try again."
            message.contains("401") || message.contains("unauthorized") -> "Sign in expired. Please sign in again."
            message.contains("403") || message.contains("forbidden") -> "Permission denied. Check Drive access."
            message.contains("404") -> "Backup file not found."
            message.contains("429") || message.contains("quota") -> "Drive quota exceeded. Delete old backups."
            message.contains("500") || message.contains("503") -> "Drive service temporarily unavailable. Try later."
            else -> "Network error: ${e.message}"
        }
    }
}