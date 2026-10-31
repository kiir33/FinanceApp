package com.example.financeapp.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UserSession(
    val userId: Long,
    val username: String,
    val role: String,
    val clientId: Long?,
    val tokenIssuedAt: Long = System.currentTimeMillis()
)

object SessionManager {
    const val ROLE_ADMIN = "ADMIN"
    const val ROLE_MEMBER = "MEMBER"

    private const val IDLE_TIMEOUT_MS = 5 * 60 * 1000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _currentSession = MutableStateFlow<UserSession?>(null)
    val currentSession: StateFlow<UserSession?> = _currentSession.asStateFlow()

    private var autoLogoutJob: Job? = null

    @Synchronized
    fun startSession(session: UserSession) {
        cancelAutoLogout()
        _currentSession.value = session
    }

    @Synchronized
    fun clearSession() {
        cancelAutoLogout()
        _currentSession.value = null
    }

    fun isLoggedIn(): Boolean = _currentSession.value != null

    fun isAdmin(): Boolean = _currentSession.value?.role == ROLE_ADMIN

    fun isMember(): Boolean = _currentSession.value?.role == ROLE_MEMBER

    fun getCurrentClientId(): Long? = _currentSession.value?.clientId

    @Synchronized
    fun onActivityPaused() {
        val session = _currentSession.value ?: return
        cancelAutoLogout()
        autoLogoutJob = scope.launch {
            delay(IDLE_TIMEOUT_MS)
            if (_currentSession.value == session) {
                clearSession()
            }
        }
    }

    @Synchronized
    fun onActivityResumed() {
        cancelAutoLogout()
    }

    private fun cancelAutoLogout() {
        autoLogoutJob?.cancel()
        autoLogoutJob = null
    }
}
