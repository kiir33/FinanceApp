package com.example.financeapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.AddCard
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.financeapp.R
import com.example.financeapp.auth.AuthService
import com.example.financeapp.auth.SessionManager
import com.example.financeapp.database.entities.AuditLog
import androidx.navigation3.runtime.NavKey
import com.example.financeapp.BackupRestore
import com.example.financeapp.ClientList
import com.example.financeapp.SavingsList
import com.example.financeapp.LoansList
import com.example.financeapp.ui.main.BankViewModel
import com.example.financeapp.database.entities.Client
import com.example.financeapp.database.entities.Loan
import com.example.financeapp.ui.main.SavingsWithClient
import com.example.financeapp.ui.main.LoanWithClient
import com.example.financeapp.theme.AccentRed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.material3.ExperimentalMaterial3Api

@Composable
fun DashboardScreen(
    onNavigate: (NavKey) -> Unit,
    viewModel: BankViewModel,
    modifier: Modifier = Modifier
) {
    val clientCount by viewModel.clientCount.collectAsState()
    val totalSavings by viewModel.totalSavings.collectAsState()
    val totalLoans by viewModel.totalOutstandingLoans.collectAsState()
    val interestEarned by viewModel.totalInterestEarned.collectAsState()
    val savingsList by viewModel.allSavingsWithClients.collectAsState()
    val loansList by viewModel.allLoansWithClients.collectAsState()
    val context = LocalContext.current
    val session by SessionManager.currentSession.collectAsState()
    val scope = rememberCoroutineScope()
    var showAddClientDialog by remember { mutableStateOf(false) }
    var showDepositDialog by remember { mutableStateOf(false) }
    var showRepayDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.dashboard_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            var expanded by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.desc_menu),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.dashboard_backup_restore)) },
                        onClick = {
                            expanded = false
                            onNavigate(BackupRestore)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.dashboard_logout)) },
                        onClick = {
                            expanded = false
                            val currentSession = session
                            if (currentSession != null) {
                                scope.launch {
                                    val application = context.applicationContext as android.app.Application
                                    val authService = AuthService.getInstance(application)
                                    authService.auditLogDao.insertAuditLog(AuditLog(
                                        userId = currentSession.userId,
                                        username = currentSession.username,
                                        action = "USER_LOGOUT",
                                        details = "User logged out"
                                    ))
                                }
                                SessionManager.clearSession()
                            }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Card 1: Total Savings Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .clickable { onNavigate(SavingsList) },
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            )
                        )
                    )
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.dashboard_total_savings),
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = String.format(Locale.getDefault(), "Rs. %,.0f", totalSavings),
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Card 2: Active Loans Card (Alternating background with blue text using theme color scheme)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clickable { onNavigate(LoansList) },
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.dashboard_active_loans_title),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = String.format(Locale.getDefault(), "Rs. %,.0f", totalLoans),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.dashboard_interest_earned_format, String.format(Locale.getDefault(), "Rs. %,.0f", interestEarned)),
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Actions grid
       
        // Member Stats Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigate(ClientList) },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(
                    imageVector = Icons.Default.Group,
                    contentDescription = stringResource(R.string.desc_active_members),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.dashboard_active_client_base),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.dashboard_registered_accounts),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Text(
                    text = "$clientCount",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Quick Actions Section
        Text(
            text = stringResource(R.string.dashboard_quick_actions),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Button 1: Add Member
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(90.dp)
                    .clickable { showAddClientDialog = true },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = stringResource(R.string.desc_add_member),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.dashboard_add_member),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Button 2: Deposit
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(90.dp)
                    .clickable { showDepositDialog = true },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.AddCard,
                        contentDescription = stringResource(R.string.desc_deposit),
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.dashboard_deposit),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Button 3: Loan Payment
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .clickable { showRepayDialog = true },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Savings,
                        contentDescription = stringResource(R.string.desc_loan_payment),
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = stringResource(R.string.dashboard_loan_payment),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.dashboard_loan_payment_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Info Note
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = stringResource(R.string.desc_status),
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.dashboard_offline_footer),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }

    if (showAddClientDialog) {
        AddClientDialog(
            onDismiss = { showAddClientDialog = false },
            onConfirm = { name, phone, address, clientId ->
                viewModel.createClient(name, phone, address, clientId)
                showAddClientDialog = false
            },
            checkClientIdUnique = { id, callback ->
                viewModel.checkClientIdUnique(id, callback)
            }
        )
    }

    if (showDepositDialog) {
        DepositSavingsDialog(
            preselectedClient = null,
            savingsList = savingsList,
            onDismiss = { showDepositDialog = false },
            onConfirm = { savingsAccountId, amount, desc, date ->
                viewModel.depositSavings(savingsAccountId, amount, desc, date)
                showDepositDialog = false
                Toast.makeText(context, context.getString(R.string.toast_deposit_success), Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showRepayDialog) {
        RepayLoanFormDialog(
            preselectedLoan = null,
            preselectedClient = null,
            loansList = loansList,
            onDismiss = { showRepayDialog = false },
            onConfirm = { loanIdVal, principal, interest, date ->
                viewModel.repayLoan(
                    loanId = loanIdVal,
                    principalAmount = principal,
                    interestAmount = interest,
                    repaymentDate = date,
                    onSuccess = {
                        showRepayDialog = false
                        Toast.makeText(context, context.getString(R.string.toast_repayment_success), Toast.LENGTH_SHORT).show()
                    },
                    onError = { error ->
                        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepositSavingsDialog(
    preselectedClient: Client? = null,
    savingsList: List<SavingsWithClient>,
    onDismiss: () -> Unit,
    onConfirm: (savingsAccountId: Long, amount: Double, description: String, date: Long) -> Unit
) {
    var selectedClient by remember { mutableStateOf(preselectedClient) }
    var amountStr by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var errorMsgResId by remember { mutableStateOf<Int?>(null) }
    var transactionDate by remember { mutableStateOf(System.currentTimeMillis()) }
    var memberDropdownExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val calendar = Calendar.getInstance().apply { timeInMillis = transactionDate }
    val datePickerDialog = android.app.DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            calendar.set(year, month, dayOfMonth)
            transactionDate = calendar.timeInMillis
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val clients = savingsList.mapNotNull { it.client }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_deposit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Member Dropdown
                ExposedDropdownMenuBox(
                    expanded = memberDropdownExpanded && preselectedClient == null,
                    onExpandedChange = {
                        if (preselectedClient == null) memberDropdownExpanded = it
                    }
                ) {
                    OutlinedTextField(
                        value = selectedClient?.name ?: stringResource(R.string.label_select_member),
                        onValueChange = {},
                        label = { Text(stringResource(R.string.label_member)) },
                        readOnly = true,
                        trailingIcon = {
                            if (preselectedClient == null) {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = memberDropdownExpanded)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = memberDropdownExpanded && preselectedClient == null,
                        onDismissRequest = { memberDropdownExpanded = false }
                    ) {
                        clients.forEach { client ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.label_member_name_id_format, client.name, client.clientId)) },
                                onClick = {
                                    selectedClient = client
                                    memberDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Button(onClick = { datePickerDialog.show() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.client_detail_date_format, dateFormat.format(Date(transactionDate))))
                }

                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text(stringResource(R.string.dialog_amount_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.dialog_description_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMsgResId != null) {
                    Text(
                        text = stringResource(errorMsgResId!!),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val client = selectedClient
                    val amount = amountStr.toDoubleOrNull() ?: 0.0
                    val matchedSavings = savingsList.find { it.account.clientId == client?.id }

                    if (client == null) {
                        errorMsgResId = R.string.dialog_add_client_error_required
                    } else if (amount <= 0.0) {
                        errorMsgResId = R.string.dialog_error_invalid_amount
                    } else if (matchedSavings == null) {
                        errorMsgResId = R.string.dialog_error_invalid_amount
                    } else {
                        onConfirm(matchedSavings.account.id, amount, description, transactionDate)
                    }
                }
            ) {
                Text(stringResource(R.string.dialog_deposit_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepayLoanFormDialog(
    preselectedLoan: Loan? = null,
    preselectedClient: Client? = null,
    loansList: List<LoanWithClient>,
    onDismiss: () -> Unit,
    onConfirm: (loanId: Long, principal: Double, interest: Double, date: Long) -> Unit
) {
    var selectedClient by remember { mutableStateOf(preselectedClient) }
    var selectedLoanWithClient by remember { mutableStateOf(loansList.find { it.loan.id == preselectedLoan?.id }) }
    var memberDropdownExpanded by remember { mutableStateOf(false) }
    var loanDropdownExpanded by remember { mutableStateOf(false) }

    var principalStr by remember { mutableStateOf("") }
    var interestStr by remember { mutableStateOf("") }
    var errorMsgResId by remember { mutableStateOf<Int?>(null) }
    var repaymentDate by remember { mutableStateOf(System.currentTimeMillis()) }

    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    val clientsWithActiveLoans = remember(loansList) {
        loansList.filter { it.loan.status == "ACTIVE" }.mapNotNull { it.client }.distinctBy { it.id }
    }

    val activeLoansForClient = remember(selectedClient, loansList) {
        if (selectedClient == null) emptyList()
        else loansList.filter { it.loan.clientId == selectedClient?.id && it.loan.status == "ACTIVE" }
    }

    // Auto-select loan if client has only one active loan
    remember(activeLoansForClient) {
        if (preselectedLoan == null && activeLoansForClient.size == 1) {
            selectedLoanWithClient = activeLoansForClient.first()
        } else if (preselectedLoan == null && activeLoansForClient.isEmpty()) {
            selectedLoanWithClient = null
        }
    }

    val calendar = Calendar.getInstance().apply { timeInMillis = repaymentDate }
    val datePickerDialog = android.app.DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            calendar.set(year, month, dayOfMonth)
            repaymentDate = calendar.timeInMillis
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    val monthlyInterest = selectedLoanWithClient?.loan?.let {
        it.amount * (it.interestRate / 100.0) / 12.0
    } ?: 0.0

    val canSelectMember = preselectedClient == null && preselectedLoan == null
    val canSelectLoan = preselectedLoan == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_repay_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Member dropdown
                ExposedDropdownMenuBox(
                    expanded = memberDropdownExpanded && canSelectMember,
                    onExpandedChange = { if (canSelectMember) memberDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedClient?.name ?: stringResource(R.string.label_select_member),
                        onValueChange = {},
                        label = { Text(stringResource(R.string.label_member)) },
                        readOnly = true,
                        trailingIcon = {
                            if (canSelectMember) ExposedDropdownMenuDefaults.TrailingIcon(expanded = memberDropdownExpanded)
                        },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = memberDropdownExpanded && canSelectMember,
                        onDismissRequest = { memberDropdownExpanded = false }
                    ) {
                        clientsWithActiveLoans.forEach { client ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.label_member_name_id_format, client.name, client.clientId)) },
                                onClick = {
                                    selectedClient = client
                                    selectedLoanWithClient = null
                                    memberDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Loan dropdown (only shown after member selected)
                if (selectedClient != null) {
                    if (activeLoansForClient.isEmpty()) {
                        Text(
                            text = stringResource(R.string.label_no_active_loans),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        ExposedDropdownMenuBox(
                            expanded = loanDropdownExpanded && canSelectLoan,
                            onExpandedChange = { if (canSelectLoan) loanDropdownExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = selectedLoanWithClient?.let {
                                    stringResource(R.string.label_loan_with_balance_format, it.loan.amount, it.loan.outstandingBalance)
                                } ?: stringResource(R.string.label_select_active_loan),
                                onValueChange = {},
                                label = { Text(stringResource(R.string.label_active_loan)) },
                                readOnly = true,
                                trailingIcon = {
                                    if (canSelectLoan) ExposedDropdownMenuDefaults.TrailingIcon(expanded = loanDropdownExpanded)
                                },
                                modifier = Modifier.fillMaxWidth().menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = loanDropdownExpanded && canSelectLoan,
                                onDismissRequest = { loanDropdownExpanded = false }
                            ) {
                                activeLoansForClient.forEach { item ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = stringResource(R.string.label_amount_format, item.loan.amount),
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = stringResource(R.string.label_outstanding_short_format, item.loan.outstandingBalance),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = AccentRed
                                                )
                                            }
                                        },
                                        onClick = {
                                            selectedLoanWithClient = item
                                            loanDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                if (selectedLoanWithClient != null) {
                    val loan = selectedLoanWithClient!!.loan
                    Text(
                        text = stringResource(R.string.label_outstanding_balance_format, loan.outstandingBalance),
                        fontWeight = FontWeight.Bold,
                        color = AccentRed
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = { datePickerDialog.show() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.client_detail_date_format, dateFormat.format(Date(repaymentDate))))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = principalStr,
                        onValueChange = { principalStr = it },
                        label = { Text(stringResource(R.string.dialog_repay_principal_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = interestStr,
                        onValueChange = { interestStr = it },
                        label = { Text(stringResource(R.string.dialog_repay_interest_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(onClick = {
                        interestStr = String.format(Locale.US, "%.2f", monthlyInterest)
                        principalStr = "0"
                    }) {
                        Text(stringResource(R.string.dialog_repay_pay_monthly_interest))
                    }
                }

                if (errorMsgResId != null) {
                    Text(
                        text = stringResource(errorMsgResId!!),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val loan = selectedLoanWithClient?.loan
                    val principal = principalStr.toDoubleOrNull() ?: 0.0
                    val interest = interestStr.toDoubleOrNull() ?: 0.0
                    if (loan == null) {
                        errorMsgResId = R.string.dialog_add_client_error_required
                    } else if (principal <= 0.0 && interest <= 0.0) {
                        errorMsgResId = R.string.dialog_repay_error_invalid
                    } else if (principal > loan.outstandingBalance) {
                        errorMsgResId = R.string.dialog_repay_error_exceeds
                    } else {
                        onConfirm(loan.id, principal, interest, repaymentDate)
                    }
                }
            ) {
                Text(stringResource(R.string.dialog_repay_pay_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

@Composable
fun ClientPickerDialog(
    title: String = "Select Member",
    clients: List<Client>,
    onDismiss: () -> Unit,
    onSelect: (Client) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredClients = remember(clients, searchQuery) {
        if (searchQuery.isBlank()) clients
        else {
            val q = searchQuery.lowercase(Locale.getDefault())
            clients.filter { it.name.lowercase(Locale.getDefault()).contains(q) || it.clientId.toString().contains(q) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.picker_search_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredClients, key = { it.id }) { c ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(c) },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                            )
                        ) {
                            Text(
                                text = "${c.name} (ID: ${c.clientId})",
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.label_close))
            }
        }
    )
}
