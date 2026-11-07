package com.example.financeapp.auth

import android.app.Application
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.viewModelScope
import com.example.financeapp.database.AppDatabase
import com.example.financeapp.database.dao.AuditLogDao
import com.example.financeapp.database.dao.UserDao
import com.example.financeapp.database.entities.AuditLog
import com.example.financeapp.database.entities.Client
import com.example.financeapp.database.entities.User
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthService private constructor(
    private val application: Application? = null,
    val db: AppDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    val userDao: UserDao = db.userDao()
    val auditLogDao: AuditLogDao = db.auditLogDao()
    val clientDao = db.clientDao()

    companion object {
        @Volatile
        private var INSTANCE: AuthService? = null

        fun getInstance(application: Application): AuthService {
            return INSTANCE ?: synchronized(this) {
                val instance = AuthService(
                    application = application,
                    db = AppDatabase.getDatabase(application)
                )
                INSTANCE = instance
                instance
            }
        }

        // For testing
        fun resetInstance() {
            INSTANCE = null
        }

        // For testing with mock database
        fun getInstanceForTest(db: AppDatabase): AuthService {
            return AuthService(
                application = null,
                db = db
            )
        }
    }

    // --- Public API ---

    /**
     * Authenticate with flexible identifier: username, member ID, or phone number.
     * Runs off the main thread.
     */
    suspend fun login(loginId: String, password: String): AuthResult {
        return withContext(ioDispatcher) {
            val user = userDao.findUserByLoginIdentifier(loginId)
            if (user == null) {
                return@withContext AuthResult.Failure.UserNotFound()
            }

            if (!user.isActive) {
                return@withContext AuthResult.Failure.AccountInactive()
            }

            // Check lockout
            val now = System.currentTimeMillis()
            if (user.lockedUntil != null && user.lockedUntil!! > now) {
                val remainingMs = user.lockedUntil!! - now
                val remainingMinutes = (remainingMs / 60000).toInt() + 1
                return@withContext AuthResult.Failure.AccountLocked(
                    remainingMinutes = remainingMinutes,
                    message = "Account locked. Try again in $remainingMinutes minute(s)."
                )
            }

            // Verify password
            val passwordValid = PasswordHasher.verifyPassword(password, user.salt, user.passwordHash)
            if (!passwordValid) {
                // Increment failed attempts
                val newFailedAttempts = user.failedAttempts + 1
                var updatedUser = user.copy(failedAttempts = newFailedAttempts)
                
                // Lock after 5 failures
                if (newFailedAttempts >= 5) {
                    val lockUntil = now + 15 * 60 * 1000 // 15 minutes
                    updatedUser = updatedUser.copy(lockedUntil = lockUntil)
                    userDao.updateUser(updatedUser)
                    
                    // Audit log for lockout
                    auditLogDao.insertAuditLog(AuditLog(
                        userId = user.id,
                        username = user.username,
                        action = "ACCOUNT_LOCKED",
                        details = "Account locked after 5 consecutive failed login attempts"
                    ))
                    
                    return@withContext AuthResult.Failure.AccountLocked(
                        remainingMinutes = 15,
                        message = "Account locked after 5 failed attempts. Try again in 15 minutes."
                    )
                } else {
                    userDao.updateUser(updatedUser)
                    return@withContext AuthResult.Failure.InvalidCredentials(
                        message = "Invalid credentials. ${5 - newFailedAttempts} attempts remaining before lockout."
                    )
                }
            }

            // Success - reset failed attempts and lockout
            val updatedUser = user.copy(
                failedAttempts = 0,
                lockedUntil = null,
                lastLoginAt = now
            )
            userDao.updateUser(updatedUser)

            // Start session
            val session = UserSession(
                userId = updatedUser.id,
                username = updatedUser.username,
                role = updatedUser.role,
                clientId = updatedUser.clientId
            )
            SessionManager.startSession(session)

            // Audit log for successful login
            auditLogDao.insertAuditLog(AuditLog(
                userId = updatedUser.id,
                username = updatedUser.username,
                action = "USER_LOGIN",
                details = "Successful login via ${identifyLoginType(loginId)}"
            ))

            AuthResult.Success(session)
        }
    }

    /**
     * Change password with validation.
     * Enforces minimum 8 characters with at least one letter and one number.
     */
    suspend fun changePassword(userId: Long, oldPassword: String, newPassword: String): AuthResult {
        return withContext(ioDispatcher) {
            val user = userDao.getUserById(userId)
            if (user == null) {
                return@withContext AuthResult.PasswordChangeFailure.UnknownError("User not found")
            }

            // Verify old password
            val oldPasswordValid = PasswordHasher.verifyPassword(oldPassword, user.salt, user.passwordHash)
            if (!oldPasswordValid) {
                return@withContext AuthResult.PasswordChangeFailure.InvalidOldPassword
            }

            // Check if new password is same as old
            // Using existing salt to detect identical password (same salt → same hash)
            val newPasswordHash = PasswordHasher.hashPassword(newPassword, user.salt)
            if (newPasswordHash == user.passwordHash) {
                return@withContext AuthResult.PasswordChangeFailure.SameAsOldPassword
            }

            // Validate new password strength
            val validationError = validatePasswordStrength(newPassword)
            if (validationError != null) {
                return@withContext AuthResult.PasswordChangeFailure.WeakNewPassword(validationError)
            }

            // Generate new salt and hash
            val newSalt = PasswordHasher.generateSalt()
            val finalNewPasswordHash = PasswordHasher.hashPassword(newPassword, newSalt)

            // Update user
            val updatedUser = user.copy(
                passwordHash = finalNewPasswordHash,
                salt = newSalt,
                mustChangePassword = false
            )
            userDao.updateUser(updatedUser)

            // Audit log
            auditLogDao.insertAuditLog(AuditLog(
                userId = user.id,
                username = user.username,
                action = "PASSWORD_CHANGED",
                details = "Password changed successfully"
            ))

            AuthResult.PasswordChangeSuccess
        }
    }

    /**
     * Check if this is the first run (no users exist).
     */
    suspend fun isFirstRun(): AuthResult {
        return withContext(ioDispatcher) {
            val count = userDao.getUserCount()
            AuthResult.FirstRunStatus(count == 0)
        }
    }

    /**
     * Create initial admin account on first run.
     */
    suspend fun createInitialAdmin(
        username: String,
        password: String,
        fullName: String,
        phoneNumber: String
    ): AuthResult {
        return withContext(ioDispatcher) {
            val count = userDao.getUserCount()
            if (count > 0) {
                return@withContext AuthResult.AdminCreationFailure.UserAlreadyExists
            }

            // Validate inputs
            if (username.isBlank() || password.isBlank() || fullName.isBlank() || phoneNumber.isBlank()) {
                return@withContext AuthResult.AdminCreationFailure.ValidationError("All fields are required")
            }

            val validationError = validatePasswordStrength(password)
            if (validationError != null) {
                return@withContext AuthResult.AdminCreationFailure.ValidationError(validationError)
            }

            // Check username uniqueness
            val usernameExists = userDao.usernameExists(username)
            if (usernameExists) {
                return@withContext AuthResult.AdminCreationFailure.ValidationError("Username already exists")
            }

            // Create admin user (without client record - admins are not members)
            val salt = PasswordHasher.generateSalt()
            val passwordHash = PasswordHasher.hashPassword(password, salt)

            val adminUser = User(
                username = username,
                passwordHash = passwordHash,
                salt = salt,
                role = SessionManager.ROLE_ADMIN,
                clientId = null, // Admin has no client ID - not a member
                phoneNumber = phoneNumber.filter { it.isDigit() },
                isActive = true,
                failedAttempts = 0,
                lockedUntil = null,
                mustChangePassword = false,
                createdAt = System.currentTimeMillis(),
                lastLoginAt = null
            )

            val userId = userDao.insertUser(adminUser)

            // Audit log
            auditLogDao.insertAuditLog(AuditLog(
                userId = userId,
                username = username,
                action = "ADMIN_INITIALIZED",
                details = "Initial admin account created"
            ))

            val createdUser = adminUser.copy(id = userId)
            AuthResult.AdminCreated(createdUser)
        }
    }

    // --- Private Helpers ---

    private fun validatePasswordStrength(password: String): String? {
        if (password.length < 8) {
            return "Password must be at least 8 characters"
        }
        if (!password.any { it.isLetter() }) {
            return "Password must contain at least one letter"
        }
        if (!password.any { it.isDigit() }) {
            return "Password must contain at least one number"
        }
        return null
    }

    // Heuristic: <=10 digits = member ID, >10 digits = phone number, else username
    // Note: This may misclassify very long member IDs or short phone numbers
    private fun identifyLoginType(loginId: String): String {
        return if (loginId.all { it.isDigit() }) {
            if (loginId.length <= 10) "member ID" else "phone number"
        } else "username"
    }
}