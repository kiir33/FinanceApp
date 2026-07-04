package com.example.financeapp.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.financeapp.database.entities.Client
import com.example.financeapp.database.entities.Loan
import com.example.financeapp.database.entities.LoanRepayment
import com.example.financeapp.database.entities.SavingsAccount
import com.example.financeapp.database.entities.SavingsTransaction
import com.example.financeapp.database.entities.currentOutstandingBalance
import com.example.financeapp.database.entities.maturityDateMillis
import com.example.financeapp.theme.AccentGold
import com.example.financeapp.theme.AccentRed
import com.example.financeapp.ui.main.BankViewModel
import androidx.compose.ui.res.stringResource
import com.example.financeapp.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.navigation3.runtime.NavKey
import com.example.financeapp.LoanDetail
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.HorizontalDivider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientDetailScreen(
    clientId: Long,
    onNavigate: (NavKey) -> Unit,
    onBack: () -> Unit,
    viewModel: BankViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Observe state
    LaunchedEffect(clientId) {
        viewModel.selectedClientId.value = clientId
    }

    val client by viewModel.selectedClient.collectAsState()
    val savingsAccount by viewModel.selectedClientSavingsAccount.collectAsState()
    val savingsTransactions by viewModel.selectedClientSavingsTransactions.collectAsState()
    val loans by viewModel.selectedClientLoans.collectAsState()
    val savingsList by viewModel.allSavingsWithClients.collectAsState()

    var activeTab by remember { mutableStateOf(0) } // 0 = Savings, 1 = Loans

    // Dialog trigger states
    var showDepositDialog by remember { mutableStateOf(false) }
    var showWithdrawDialog by remember { mutableStateOf(false) }
    var showLoanDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(client?.name ?: stringResource(R.string.client_detail_default_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = stringResource(R.string.desc_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        if (client == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.client_detail_loading))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
            ) {
                // Client Personal Info Card
                ClientInfoCard(client = client!!)

                Spacer(modifier = Modifier.height(16.dp))

                // Tabs: Savings & Loans
                TabRow(
                    selectedTabIndex = activeTab,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        text = { Text(stringResource(R.string.client_detail_tab_savings)) }
                    )
                    Tab(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        text = { Text(stringResource(R.string.client_detail_tab_loans)) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Content panels based on tabs
                if (activeTab == 0) {
                    // Savings panel
                    SavingsPanel(
                        account = savingsAccount,
                        transactions = savingsTransactions,
                        onDepositClick = { showDepositDialog = true },
                        onWithdrawClick = { showWithdrawDialog = true }
                    )
                } else {
                    // Loans panel
                    LoansPanel(
                        loans = loans,
                        onDisburseClick = { showLoanDialog = true },
                        onLoanClick = { loan -> onNavigate(LoanDetail(loan.id)) }
                    )
                }
            }
        }
    }

    // Savings Deposit Dialog
    if (showDepositDialog && client != null) {
        DepositSavingsDialog(
            preselectedClient = client,
            savingsList = savingsList,
            onDismiss = { showDepositDialog = false },
            onConfirm = { savingsAccountId, amount, desc, date ->
                viewModel.depositSavings(savingsAccountId, amount, desc, date)
                showDepositDialog = false
                Toast.makeText(context, context.getString(R.string.toast_deposit_success), Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Savings Withdrawal Dialog
    if (showWithdrawDialog && savingsAccount != null) {
        SavingsActionDialog(
            title = stringResource(R.string.dialog_withdraw_title),
            confirmLabel = stringResource(R.string.dialog_withdraw_confirm),
            onDismiss = { showWithdrawDialog = false },
            onConfirm = { amount, desc, _ ->
                viewModel.withdrawSavings(
                    accountId = savingsAccount!!.id,
                    amount = amount,
                    description = desc,
                    onContextSuccess = {
                        showWithdrawDialog = false
                        Toast.makeText(context, context.getString(R.string.toast_withdraw_success), Toast.LENGTH_SHORT).show()
                    },
                    onContextError = { error ->
                        Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                    }
                )
            }
        )
    }

    val allClients by viewModel.allClients.collectAsState()

    // Disburse Loan Dialog
    if (showLoanDialog) {
        DisburseLoanDialog(
            borrowerId = clientId,
            clients = allClients,
            onDismiss = { showLoanDialog = false },
            onConfirm = { amount, rate, tenure, date, guarantorId ->
                viewModel.disburseLoan(
                    clientId = clientId,
                    guarantorId = guarantorId,
                    amount = amount,
                    interestRate = rate,
                    tenureMonths = tenure,
                    loanDate = date,
                    onSuccess = {
                        showLoanDialog = false
                        Toast.makeText(context, context.getString(R.string.toast_loan_success), Toast.LENGTH_SHORT).show()
                    },
                    onError = { error ->
                        Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                    }
                )
            }
        )
    }
}

// Wrapper extension to avoid capturing functions in dialog triggers directly
private fun BankViewModel.withdrawSavings(
    accountId: Long,
    amount: Double,
    description: String,
    onContextSuccess: () -> Unit,
    onContextError: (String) -> Unit
) {
    withdrawSavings(accountId, amount, description, onContextSuccess, onContextError)
}

@Composable
fun ClientInfoCard(client: Client) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.client_detail_profile_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val context = LocalContext.current
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${client.phoneNumber}"))
                        context.startActivity(intent)
                    }
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.client_detail_phone_label),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = stringResource(R.string.desc_call),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = client.phoneNumber,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(R.string.client_detail_client_id_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = client.clientId.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.client_detail_address_label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = client.address,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun SavingsPanel(
    account: SavingsAccount?,
    transactions: List<SavingsTransaction>,
    onDepositClick: () -> Unit,
    onWithdrawClick: () -> Unit
) {
    if (account == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.client_detail_init_savings))
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            // Savings Account Info & Balance Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.client_detail_account_number_format, account.accountNumber),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = String.format(Locale.getDefault(), "Rs. %,.0f", account.balance),
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = onDepositClick,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.dialog_deposit_confirm))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = onWithdrawClick,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.dialog_withdraw_confirm))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.client_detail_tx_history_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (transactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.client_detail_no_transactions), color = MaterialTheme.colorScheme.secondary)
                }
            } else {
                val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(transactions, key = { it.id }) { tx ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tx.description.ifEmpty { if (tx.amount >= 0) stringResource(R.string.client_detail_default_deposit_desc) else stringResource(R.string.client_detail_default_withdraw_desc) },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = dateFormat.format(Date(tx.transactionDate)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            Text(
                                text = String.format(Locale.getDefault(), "%sRs. %,.0f", if (tx.amount >= 0) "+" else "", tx.amount),
                                color = if (tx.amount >= 0) MaterialTheme.colorScheme.primary else AccentRed,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    }
                }
            }
        }
    }
}

@Composable
fun LoansPanel(
    loans: List<Loan>,
    onDisburseClick: () -> Unit,
    onLoanClick: (Loan) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.client_detail_loans_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            IconButton(onClick = onDisburseClick) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.desc_new_loan),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (loans.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.client_detail_no_loans), color = MaterialTheme.colorScheme.secondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(loans, key = { it.id }) { loan ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLoanClick(loan) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (loan.status == "ACTIVE") MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.05f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = stringResource(R.string.client_detail_disbursed_format, loan.amount),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = stringResource(R.string.client_detail_loan_details_format, loan.interestRate, loan.tenureMonths),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
                                    val now = System.currentTimeMillis()
                                    Text(
                                        text = stringResource(R.string.client_detail_loan_date_format, dateFormat.format(Date(loan.loanDate))),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Text(
                                        text = stringResource(R.string.loan_details_maturity_date, dateFormat.format(Date(loan.maturityDateMillis()))),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (now > loan.maturityDateMillis() && loan.status == "ACTIVE") AccentRed else MaterialTheme.colorScheme.secondary
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (loan.status == "ACTIVE") AccentGold.copy(alpha = 0.15f)
                                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = loan.status,
                                        color = if (loan.status == "ACTIVE") AccentGold else MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = stringResource(R.string.label_balance_as_of_today),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    val currentOutstanding = loan.currentOutstandingBalance()
                                    Text(
                                        text = String.format(Locale.getDefault(), "Rs. %,.0f", currentOutstanding),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Black,
                                        color = if (currentOutstanding > 0) AccentRed else MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SavingsActionDialog(
    title: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, description: String, transactionDate: Long) -> Unit
) {
    var amountStr by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var errorMsgResId by remember { mutableStateOf<Int?>(null) }
    var transactionDate by remember { mutableStateOf(System.currentTimeMillis()) }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    val amount = amountStr.toDoubleOrNull()
                    if (amount == null || amount <= 0.0) {
                        errorMsgResId = R.string.dialog_error_invalid_amount
                    } else {
                        onConfirm(amount, description, transactionDate)
                    }
                }
            ) {
                Text(confirmLabel)
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
fun DisburseLoanDialog(
    borrowerId: Long,
    clients: List<Client>,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, rate: Double, tenure: Int, loanDate: Long, guarantorId: Long?) -> Unit
) {
    var amountStr by remember { mutableStateOf("") }
    var rateStr by remember { mutableStateOf("") }
    var tenureStr by remember { mutableStateOf("") }
    var errorMsgResId by remember { mutableStateOf<Int?>(null) }
    var loanDate by remember { mutableStateOf(System.currentTimeMillis()) }
    var selectedGuarantor by remember { mutableStateOf<Client?>(null) }
    var showGuarantorSelector by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val calendar = Calendar.getInstance().apply { timeInMillis = loanDate }
    val datePickerDialog = android.app.DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            calendar.set(year, month, dayOfMonth)
            loanDate = calendar.timeInMillis
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_disburse_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { datePickerDialog.show() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.client_detail_date_format, dateFormat.format(Date(loanDate))))
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showGuarantorSelector = true }
                ) {
                    OutlinedTextField(
                        value = selectedGuarantor?.name ?: stringResource(R.string.label_no_guarantor),
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text(stringResource(R.string.label_guarantor_optional)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text(stringResource(R.string.dialog_disburse_principal)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = rateStr,
                    onValueChange = { rateStr = it },
                    label = { Text(stringResource(R.string.dialog_disburse_rate)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tenureStr,
                    onValueChange = { tenureStr = it },
                    label = { Text(stringResource(R.string.dialog_disburse_tenure)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                    val amount = amountStr.toDoubleOrNull()
                    val rate = rateStr.toDoubleOrNull()
                    val tenure = tenureStr.toIntOrNull()

                    if (amount == null || amount <= 0.0) {
                        errorMsgResId = R.string.dialog_disburse_error_principal
                    } else if (rate == null || rate < 0.0) {
                        errorMsgResId = R.string.dialog_disburse_error_rate
                    } else if (tenure == null || tenure <= 0) {
                        errorMsgResId = R.string.dialog_disburse_error_tenure
                    } else {
                        onConfirm(amount, rate, tenure, loanDate, selectedGuarantor?.id)
                    }
                }
            ) {
                Text(stringResource(R.string.dialog_disburse_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )

    if (showGuarantorSelector) {
        AlertDialog(
            onDismissRequest = { showGuarantorSelector = false },
            title = { Text(stringResource(R.string.label_select_guarantor)) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    item {
                        Text(
                            text = stringResource(R.string.label_no_guarantor),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedGuarantor = null
                                    showGuarantorSelector = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        HorizontalDivider()
                    }
                    items(clients.filter { it.id != borrowerId }) { c ->
                        Text(
                            text = stringResource(R.string.label_member_name_id_format, c.name, c.clientId),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedGuarantor = c
                                    showGuarantorSelector = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGuarantorSelector = false }) {
                    Text(stringResource(R.string.label_close))
                }
            }
        )
    }
}
