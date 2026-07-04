package com.example.financeapp

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object Dashboard : NavKey

@Serializable
data object ClientList : NavKey

@Serializable
data class ClientDetail(val clientId: Long) : NavKey

@Serializable
data object BackupRestore : NavKey

@Serializable
data object SavingsList : NavKey

@Serializable
data object LoansList : NavKey

@Serializable
data class LoanDetail(val loanId: Long) : NavKey
