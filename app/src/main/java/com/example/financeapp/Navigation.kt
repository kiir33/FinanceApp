package com.example.financeapp

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.financeapp.auth.AuthResult
import com.example.financeapp.auth.AuthService
import com.example.financeapp.auth.SessionManager
import com.example.financeapp.ui.main.BankViewModel
import com.example.financeapp.ui.screens.AdminSetupScreen
import com.example.financeapp.ui.screens.BackupRestoreScreen
import com.example.financeapp.ui.screens.ClientDetailScreen
import com.example.financeapp.ui.screens.ClientListScreen
import com.example.financeapp.ui.screens.CsvImportScreen
import com.example.financeapp.ui.screens.DashboardScreen
import com.example.financeapp.ui.screens.GroupAccountScreen
import com.example.financeapp.ui.screens.LoanDetailScreen
import com.example.financeapp.ui.screens.LoansListScreen
import com.example.financeapp.ui.screens.LoginScreen
import com.example.financeapp.ui.screens.MemberDashboardScreen
import com.example.financeapp.ui.screens.ReportsDashboardScreen
import com.example.financeapp.ui.screens.SavingsListScreen
import com.example.financeapp.ui.screens.UserManagementScreen
import com.example.financeapp.ui.screens.SettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private fun <T> MutableList<T>.clearAndAdd(item: T) {
    clear()
    add(item)
}

@Composable
fun MainNavigation(application: Application) {
    val backStack = rememberNavBackStack(Login as NavKey)
    val viewModel: BankViewModel = viewModel()
    val authService = AuthService.getInstance(application)

    // Observe session state for auto-redirect
    val currentSession by SessionManager.currentSession.collectAsStateWithLifecycle()

    // Determine initial destination based on auth state
    val initialDestination = remember {
        mutableStateOf<NavKey?>(null)
    }

    // Initialize navigation on first composition
    LaunchedEffect(Unit) {
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            val result = authService.isFirstRun()
            val isFirstRun = (result as? AuthResult.FirstRunStatus)?.isFirstRun ?: true
            val session = SessionManager.currentSession.value

            val destination: NavKey = when {
                isFirstRun -> AdminSetup
                session == null -> Login
                session.role == SessionManager.ROLE_ADMIN -> Dashboard
                session.role == SessionManager.ROLE_MEMBER -> MemberDashboard
                else -> Login
            }

            initialDestination.value = destination
            backStack.clearAndAdd(destination)
        }
    }

    // React to session changes (logout, auto-logout)
    LaunchedEffect(currentSession) {
        val session = currentSession
        if (session == null) {
            // Session cleared - redirect to Login
            backStack.clearAndAdd(Login)
        } else {
            // Session started - redirect based on role
            val destination: NavKey = when (session.role) {
                SessionManager.ROLE_ADMIN -> Dashboard
                SessionManager.ROLE_MEMBER -> MemberDashboard
                else -> Login
            }
            backStack.clearAndAdd(destination)
        }
    }

    // Prevent back navigation to auth screens
    val onBackPress: () -> Unit = {
        val current = backStack.lastOrNull()
        val isAuthScreen = current is Login || current is AdminSetup
        val canGoBack = backStack.size > 1

        if (!isAuthScreen && canGoBack) {
            backStack.removeLastOrNull()
        }
    }

    NavDisplay(
        backStack = backStack,
        onBack = onBackPress,
        entryProvider = entryProvider {
            // Auth screens (no role guard needed)
            entry<Login> {
                LoginScreen(
                    onLoginSuccess = { /* Handled by session observer */ },
                    modifier = Modifier.safeDrawingPadding()
                )
            }
            entry<AdminSetup> {
                AdminSetupScreen(
                    onSetupComplete = { /* Handled by session observer */ },
                    modifier = Modifier.safeDrawingPadding()
                )
            }

            // Member-only screen
            entry<MemberDashboard> {
                MemberDashboardScreen(
                    onNavigate = { key -> backStack.add(key) },
                    onBack = { backStack.removeLastOrNull() },
                    modifier = Modifier.safeDrawingPadding()
                )
            }

            // Admin-only screens
            entry<Dashboard> {
                DashboardScreen(
                    onNavigate = { key -> backStack.add(key) },
                    viewModel = viewModel,
                    modifier = Modifier.safeDrawingPadding()
                )
            }
            entry<ClientList> {
                ClientListScreen(
                    onNavigate = { key -> backStack.add(key) },
                    onBack = { backStack.removeLastOrNull() },
                    viewModel = viewModel,
                    modifier = Modifier.safeDrawingPadding()
                )
            }
            entry<ClientDetail> { key ->
                ClientDetailScreen(
                    clientId = key.clientId,
                    onNavigate = { k -> backStack.add(k) },
                    onBack = { backStack.removeLastOrNull() },
                    viewModel = viewModel,
                    modifier = Modifier.safeDrawingPadding()
                )
            }
            entry<SavingsList> {
                SavingsListScreen(
                    onNavigate = { key -> backStack.add(key) },
                    onBack = { backStack.removeLastOrNull() },
                    viewModel = viewModel,
                    modifier = Modifier.safeDrawingPadding()
                )
            }
            entry<LoansList> {
                LoansListScreen(
                    onNavigate = { key -> backStack.add(key) },
                    onBack = { backStack.removeLastOrNull() },
                    viewModel = viewModel,
                    modifier = Modifier.safeDrawingPadding()
                )
            }
            entry<LoanDetail> { key ->
                LoanDetailScreen(
                    loanId = key.loanId,
                    onBack = { backStack.removeLastOrNull() },
                    viewModel = viewModel,
                    modifier = Modifier.safeDrawingPadding()
                )
            }

            entry<GroupAccountView> {
                GroupAccountScreen(
                    onNavigate = { key -> backStack.add(key) },
                    onBack = { backStack.removeLastOrNull() },
                    modifier = Modifier.safeDrawingPadding()
                )
            }
            entry<ReportsDashboard> {
                ReportsDashboardScreen(
                    onNavigate = { key -> backStack.add(key) },
                    onBack = { backStack.removeLastOrNull() },
                    viewModel = viewModel,
                    modifier = Modifier.safeDrawingPadding()
                )
            }
            entry<CsvImportView> {
                CsvImportScreen(
                    onNavigate = { key -> backStack.add(key) },
                    onBack = { backStack.removeLastOrNull() },
                    viewModel = viewModel,
                    modifier = Modifier.safeDrawingPadding()
                )
            }
            entry<UserManagement> {
                UserManagementScreen(
                    onNavigate = { key -> backStack.add(key) },
                    onBack = { backStack.removeLastOrNull() },
                    modifier = Modifier.safeDrawingPadding()
                )
            }
            entry<Settings> {
                SettingsScreen(
                    onNavigate = { key -> backStack.add(key) },
                    onBack = { backStack.removeLastOrNull() },
                    modifier = Modifier.safeDrawingPadding()
                )
            }
            entry<BackupRestore> {
                BackupRestoreScreen(
                    onNavigate = { key -> backStack.add(key) },
                    onBack = { backStack.removeLastOrNull() },
                    viewModel = viewModel,
                    modifier = Modifier.safeDrawingPadding()
                )
            }
            entry<AuditLogView> {
                PlaceholderScreen(title = "Audit Log", onBack = { backStack.removeLastOrNull() })
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceholderScreen(title: String, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().width(400.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$title\n(Coming in later phase)",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}
