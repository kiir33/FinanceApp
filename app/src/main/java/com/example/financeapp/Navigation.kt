package com.example.financeapp

import android.app.Application
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavController
import androidx.navigation3.runtime.NavDestination
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.financeapp.auth.AuthService
import com.example.financeapp.auth.SessionManager
import com.example.financeapp.database.AppDatabase
import com.example.financeapp.ui.main.BankViewModel
import com.example.financeapp.ui.screens.AdminSetupScreen
import com.example.financeapp.ui.screens.BackupRestoreScreen
import com.example.financeapp.ui.screens.ClientDetailScreen
import com.example.financeapp.ui.screens.ClientListScreen
import com.example.financeapp.ui.screens.DashboardScreen
import com.example.financeapp.ui.screens.LoginScreen
import com.example.financeapp.ui.screens.MemberDashboardScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun MainNavigation(application: Application) {
    val backStack = rememberNavBackStack(Login) // Will be overridden by auth logic
    val viewModel: BankViewModel = viewModel()
    val authService = AuthService.getInstance(application)

    // Observe session state for auto-redirect
    val currentSession by SessionManager.currentSession.collectAsStateWithLifecycle()

    // Determine initial destination based on auth state
    val initialDestination = remember {
        mutableStateOf<Any?>(null)
    }

    // Initialize navigation on first composition
    androidx.compose.runtime.LaunchedEffect(Unit) {
        // Check if first run (no users exist)
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            val isFirstRun = authService.isFirstRun().getOrElse { true }
            val session = SessionManager.currentSession.value

            val destination = when {
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
    androidx.compose.runtime.LaunchedEffect(currentSession) {
        val session = currentSession
        if (session == null) {
            // Session cleared - redirect to Login
            backStack.clearAndAdd(Login)
        } else {
            // Session started - redirect based on role
            val destination = when (session.role) {
                SessionManager.ROLE_ADMIN -> Dashboard
                SessionManager.ROLE_MEMBER -> MemberDashboard
                else -> Login
            }
            backStack.clearAndAdd(destination)
        }
    }

    // Prevent back navigation to auth screens
    val onBackPress = { backStack: NavController ->
        val current = backStack.currentBackStackEntry?.destination
        val isAuthScreen = current is Login || current is AdminSetup
        val canGoBack = backStack.currentBackStackEntry?.previousBackStackEntry != null

        if (isAuthScreen) {
            // Don't allow back from auth screens - could exit app or stay
            false
        } else if (canGoBack) {
            backStack.removeLastOrNull()
            true
        } else {
            false
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

            // Admin-only screens (role guard applied at navigation level)
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
            entry<BackupRestore> {
                BackupRestoreScreen(
                    onBack = { backStack.removeLastOrNull() },
                    viewModel = viewModel,
                    modifier = Modifier.safeDrawingPadding()
                )
            }
            entry<SavingsList> {
                com.example.financeapp.ui.screens.SavingsListScreen(
                    onNavigate = { key -> backStack.add(key) },
                    onBack = { backStack.removeLastOrNull() },
                    viewModel = viewModel,
                    modifier = Modifier.safeDrawingPadding()
                )
            }
            entry<LoansList> {
                com.example.financeapp.ui.screens.LoansListScreen(
                    onNavigate = { key -> backStack.add(key) },
                    onBack = { backStack.removeLastOrNull() },
                    viewModel = viewModel,
                    modifier = Modifier.safeDrawingPadding()
                )
            }
            entry<LoanDetail> { key ->
                com.example.financeapp.ui.screens.LoanDetailScreen(
                    loanId = key.loanId,
                    onBack = { backStack.removeLastOrNull() },
                    viewModel = viewModel,
                    modifier = Modifier.safeDrawingPadding()
                )
            }

            // Placeholder entries for future screens (issues #9, #11, #13, #15)
            entry<GroupAccountView> {
                PlaceholderScreen(title = "Group Account", onBack = { backStack.removeLastOrNull() })
            }
            entry<ReportsDashboard> {
                PlaceholderScreen(title = "Reports Dashboard", onBack = { backStack.removeLastOrNull() })
            }
            entry<CsvImportView> {
                PlaceholderScreen(title = "CSV Import", onBack = { backStack.removeLastOrNull() })
            }
            entry<UserManagement> {
                PlaceholderScreen(title = "User Management", onBack = { backStack.removeLastOrNull() })
            }
            entry<AuditLogView> {
                PlaceholderScreen(title = "Audit Log", onBack = { backStack.removeLastOrNull() })
            }
        }
    )
}

@Composable
private fun PlaceholderScreen(title: String, onBack: () -> Unit) {
    import androidx.compose.material3.Scaffold
    import androidx.compose.material3.TopAppBar
    import androidx.compose.material3.TopAppBarDefaults
    import androidx.compose.material3.Text
    import androidx.compose.material3.Button
    import androidx.compose.material3.Icon
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.filled.ArrowBack
    import androidx.compose.runtime.Composable
    import androidx.compose.foundation.layout.fillMaxSize
    import androidx.compose.foundation.layout.padding
    import androidx.compose.foundation.layout.Column
    import androidx.compose.foundation.layout.Arrangement
    import androidx.compose.foundation.layout.Spacer
    import androidx.compose.ui.Alignment
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.unit.dp
    import androidx.compose.ui.unit.sp
    import androidx.compose.material3.Card
    import androidx.compose.material3.CardDefaults
    import androidx.compose.runtime.getValue
    import androidx.compose.runtime.mutableStateOf
    import androidx.compose.runtime.remember

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
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
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$title\n(Coming in later phase)",
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}