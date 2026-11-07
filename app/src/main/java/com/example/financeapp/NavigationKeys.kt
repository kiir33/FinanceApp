package com.example.financeapp

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object Login : NavKey

@Serializable
data object AdminSetup : NavKey

@Serializable
data object MemberDashboard : NavKey

@Serializable
data object Dashboard : NavKey

@Serializable
data object ClientList : NavKey

@Serializable
data class ClientDetail(val clientId: Long) : NavKey

@Serializable
data object SavingsList : NavKey

@Serializable
data object LoansList : NavKey

@Serializable
data class LoanDetail(val loanId: Long) : NavKey

@Serializable
data object GroupAccountView : NavKey

@Serializable
data object ReportsDashboard : NavKey

@Serializable
data object CsvImportView : NavKey

@Serializable
data object BackupRestore : NavKey

@Serializable
data object UserManagement : NavKey

@Serializable
data object AuditLogView : NavKey

@Serializable
data object Settings : NavKey