package com.example.financeapp

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.financeapp.ui.main.BankViewModel
import com.example.financeapp.ui.screens.BackupRestoreScreen
import com.example.financeapp.ui.screens.ClientDetailScreen
import com.example.financeapp.ui.screens.ClientListScreen
import com.example.financeapp.ui.screens.DashboardScreen

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Dashboard)
  val viewModel: BankViewModel = viewModel()

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
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
      },
  )
}
