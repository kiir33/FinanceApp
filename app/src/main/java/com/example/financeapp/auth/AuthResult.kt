package com.example.financeapp.auth

import com.example.financeapp.database.entities.User

sealed interface AuthResult {
    data class Success(val session: UserSession) : AuthResult
    sealed interface Failure : AuthResult {
        data class InvalidCredentials(val message: String = "Invalid username/ID/phone or password") : Failure
        data class AccountLocked(val remainingMinutes: Int, val message: String) : Failure
        data class UserNotFound(val message: String = "No account found with that identifier") : Failure
        data class AccountInactive(val message: String = "This account has been deactivated") : Failure
        data class UnknownError(val message: String) : Failure
    }
    data class PasswordChangeSuccess : AuthResult
    sealed interface PasswordChangeFailure : AuthResult {
        data class InvalidOldPassword : PasswordChangeFailure
        data class WeakNewPassword(val message: String) : PasswordChangeFailure
        data class SameAsOldPassword : PasswordChangeFailure
        data class UnknownError(val message: String) : PasswordChangeFailure
    }
    data class FirstRunStatus(val isFirstRun: Boolean) : AuthResult
    data class AdminCreated(val user: User) : AuthResult
    sealed interface AdminCreationFailure : AuthResult {
        data class ValidationError(val message: String) : AdminCreationFailure
        data class UserAlreadyExists : AdminCreationFailure
        data class UnknownError(val message: String) : AdminCreationFailure
    }
}