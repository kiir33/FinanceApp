package com.example.financeapp.ui.screens

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
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Money
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.example.financeapp.R
import com.example.financeapp.Settings
import com.example.financeapp.GroupAccountView
import com.example.financeapp.ui.main.BankViewModel
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.financeapp.database.entities.GroupAccount
import com.example.financeapp.database.entities.GroupTransaction
import com.example.financeapp.database.entities.GroupTxType
import com.example.financeapp.database.entities.displayName
import kotlinx.coroutines.Dispatchers
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupAccountScreen(
    onNavigate: (androidx.navigation3.runtime.NavKey) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: BankViewModel = androidx.lifecycle.viewmodel.compose.viewModel()

    var showCashToBankDialog by remember { mutableStateOf(false) }
    var showBankToCashDialog by remember { mutableStateOf(false) }
    var showExpenseDialog by remember { mutableStateOf(false) }
    var showInterestIncomeDialog by remember { mutableStateOf(false) }
    var showEditBankInfoDialog by remember { mutableStateOf(false) }

    // Observe group account and transactions
    val groupAccount by viewModel.groupAccount.collectAsStateWithLifecycle()
    val groupTransactions by viewModel.recentGroupTransactions.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.group_account_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = stringResource(R.string.desc_back))
                    }
                },
                actions = {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showEditBankInfoDialog = true }) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = stringResource(R.string.group_account_edit_bank_info))
                        }
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
                                text = { Text(stringResource(R.string.settings_title)) },
                                onClick = {
                                    expanded = false
                                    onNavigate(Settings)
                                }
                            )
                        }
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
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                groupAccount?.let { account ->
                    // Overview Cards Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Cash in Hand Card
                        SummaryCard(
                            title = stringResource(R.string.group_account_cash_in_hand),
                            amount = account.cashBalance,
                            icon = Icons.Default.AttachMoney,
                            iconColor = MaterialTheme.colorScheme.primary,
                            isPositive = true,
                            modifier = Modifier.weight(1f)
                        )
                        // Bank Balance Card
                        SummaryCard(
                            title = stringResource(R.string.group_account_bank_balance),
                            amount = account.bankBalance,
                            icon = Icons.Default.AccountBalance,
                            iconColor = MaterialTheme.colorScheme.secondary,
                            isPositive = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Total Treasury Card
                    SummaryCard(
                        title = stringResource(R.string.group_account_total_treasury),
                        amount = account.cashBalance + account.bankBalance,
                        icon = Icons.Default.Money,
                        iconColor = MaterialTheme.colorScheme.tertiary,
                        isPositive = true,
                        isLarge = true
                    )

                    // Bank Info Card
                    BankInfoCard(
                        account = account,
                        onEditClick = { showEditBankInfoDialog = true }
                    )
                }

                // Manual Transaction Buttons
                Text(
                    text = stringResource(R.string.group_account_manual_transactions),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ActionButton(
                        title = stringResource(R.string.group_account_cash_to_bank),
                        subtitle = stringResource(R.string.group_account_cash_to_bank_desc),
                        icon = Icons.Default.ArrowDownward,
                        iconColor = MaterialTheme.colorScheme.primary,
                        onClick = { showCashToBankDialog = true },
                        modifier = Modifier.weight(1f)
                    )
                    ActionButton(
                        title = stringResource(R.string.group_account_bank_to_cash),
                        subtitle = stringResource(R.string.group_account_bank_to_cash_desc),
                        icon = Icons.Default.ArrowUpward,
                        iconColor = MaterialTheme.colorScheme.secondary,
                        onClick = { showBankToCashDialog = true },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ActionButton(
                        title = stringResource(R.string.group_account_record_expense),
                        subtitle = stringResource(R.string.group_account_record_expense_desc),
                        icon = Icons.Default.Payments,
                        iconColor = MaterialTheme.colorScheme.error,
                        onClick = { showExpenseDialog = true },
                        modifier = Modifier.weight(1f)
                    )
                    ActionButton(
                        title = stringResource(R.string.group_account_record_income),
                        subtitle = stringResource(R.string.group_account_record_income_desc),
                        icon = Icons.Default.TrendingUp,
                        iconColor = MaterialTheme.colorScheme.tertiary,
                        onClick = { showInterestIncomeDialog = true },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Ledger
                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 16.dp))

                Text(
                    text = stringResource(R.string.group_account_recent_ledger),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )

                if (groupTransactions.isEmpty()) {
                    Text(
                        text = stringResource(R.string.group_account_no_transactions),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(groupTransactions) { tx ->
                            LedgerRow(transaction = tx)
                        }
                    }
                }
            }
        }
    }

    // Cash to Bank Dialog
    if (showCashToBankDialog) {
        TransferDialog(
            title = stringResource(R.string.group_account_cash_to_bank),
            isCashToBank = true,
            groupAccount = groupAccount!!,
            onDismiss = { showCashToBankDialog = false },
            onConfirm = { amount, description ->
                viewModel.transferCashToBank(amount, description,
                    onSuccess = { showCashToBankDialog = false },
                    onError = { error -> /* TODO: show error */ }
                )
            }
        )
    }

    // Bank to Cash Dialog
    if (showBankToCashDialog) {
        TransferDialog(
            title = stringResource(R.string.group_account_bank_to_cash),
            isCashToBank = false,
            groupAccount = groupAccount!!,
            onDismiss = { showBankToCashDialog = false },
            onConfirm = { amount, description ->
                viewModel.transferBankToCash(amount, description,
                    onSuccess = { showBankToCashDialog = false },
                    onError = { error -> /* TODO: show error */ }
                )
            }
        )
    }

    // Expense Dialog
    if (showExpenseDialog) {
        ExpenseIncomeDialog(
            title = stringResource(R.string.group_account_record_expense),
            isExpense = true,
            groupAccount = groupAccount!!,
            onDismiss = { showExpenseDialog = false },
            onConfirm = { amount, description, channel ->
                viewModel.recordExpense(amount, description, channel,
                    onSuccess = { showExpenseDialog = false },
                    onError = { error -> /* TODO: show error */ }
                )
            }
        )
    }

    // Interest Income Dialog
    if (showInterestIncomeDialog) {
        ExpenseIncomeDialog(
            title = stringResource(R.string.group_account_record_income),
            isExpense = false,
            groupAccount = groupAccount!!,
            onDismiss = { showInterestIncomeDialog = false },
            onConfirm = { amount, description, channel ->
                viewModel.recordInterestIncome(amount, description, channel,
                    onSuccess = { showInterestIncomeDialog = false },
                    onError = { error -> /* TODO: show error */ }
                )
            }
        )
    }

    // Edit Bank Info Dialog
    if (showEditBankInfoDialog) {
        EditBankInfoDialog(
            account = groupAccount!!,
            onDismiss = { showEditBankInfoDialog = false },
            onSave = { bankName, bankAccountNumber ->
                viewModel.updateGroupAccountBankInfo(bankName, bankAccountNumber)
                showEditBankInfoDialog = false
            }
        )
    }
}

@Composable
private fun SummaryCard(
    title: String,
    amount: Double,
    icon: ImageVector,
    iconColor: androidx.compose.ui.graphics.Color,
    isPositive: Boolean,
    isLarge: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(if (isLarge) 120.dp else 100.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isLarge) 20.dp else 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isLarge) 48.dp else 40.dp)
                        .clip(RoundedCornerShape(if (isLarge) 24.dp else 20.dp))
                        .background(iconColor.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(if (isLarge) 24.dp else 20.dp)
                    )
                }
                Spacer(modifier = Modifier.height(if (isLarge) 12.dp else 8.dp))
                Text(
                    text = title,
                    style = if (isLarge) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.group_account_amount_format, String.format(Locale.getDefault(), "%,.2f", amount)),
                    style = if (isLarge) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun BankInfoCard(account: GroupAccount, onEditClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.group_account_bank_info),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                androidx.compose.material3.TextButton(onClick = onEditClick) {
                    Text(stringResource(R.string.group_account_edit))
                }
            }
            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.group_account_bank_name),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = account.bankName.ifBlank { stringResource(R.string.group_account_not_set) },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.group_account_bank_account_number),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = account.bankAccountNumber.ifBlank { stringResource(R.string.group_account_not_set) },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(iconColor.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LedgerRow(transaction: GroupTransaction) {
    val type = GroupTxType.valueOf(transaction.type)
    val (icon, color, isInflow) = when (type) {
        GroupTxType.SAVINGS_DEPOSIT, GroupTxType.LOAN_REPAYMENT, GroupTxType.INTEREST_INCOME, GroupTxType.BANK_WITHDRAWAL -> {
            Triple(Icons.Default.ArrowDownward, MaterialTheme.colorScheme.primary, true)
        }
        GroupTxType.SAVINGS_WITHDRAWAL, GroupTxType.LOAN_DISBURSEMENT, GroupTxType.EXPENSE, GroupTxType.BANK_DEPOSIT -> {
            Triple(Icons.Default.ArrowUpward, MaterialTheme.colorScheme.error, false)
        }
    }

    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()) }
    val channelIcon = if (transaction.channel == "CASH") Icons.Default.AttachMoney else Icons.Default.AccountBalance

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(color.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = type.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(channelIcon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(14.dp))
                            Text(
                                text = transaction.channel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            if (transaction.description.isNotBlank()) {
                                Text(
                                    text = "• ${transaction.description}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.group_account_amount_format, (if (isInflow) "+" else "-") + String.format(Locale.getDefault(), "%,.2f", transaction.amount)),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                    Text(
                        text = dateFormat.format(Date(transaction.transactionDate)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            // Running balances
            if (transaction.relatedClientId != null) {
                Text(
                    text = stringResource(R.string.group_account_related_member, transaction.relatedClientId!!),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferDialog(
    title: String,
    isCashToBank: Boolean,
    groupAccount: GroupAccount,
    onDismiss: () -> Unit,
    onConfirm: (Double, String) -> Unit
) {
    val context = LocalContext.current
    var amountStr by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val maxAmount = if (isCashToBank) groupAccount.cashBalance else groupAccount.bankBalance

    val handleSubmit: () -> Unit = {
        val amount = amountStr.toDoubleOrNull() ?: 0.0
        if (amount <= 0) {
            errorMessage = context.getString(R.string.group_account_error_invalid_amount)
        } else if (amount > maxAmount) {
            errorMessage = if (isCashToBank)
                context.getString(R.string.group_account_error_insufficient_cash)
            else
                context.getString(R.string.group_account_error_insufficient_bank)
        } else {
            isLoading = true
            errorMessage = null
            onConfirm(amount, description.ifBlank { if (isCashToBank) context.getString(R.string.group_account_cash_to_bank) else context.getString(R.string.group_account_bank_to_cash) })
        }
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (isCashToBank)
                        context.getString(R.string.group_account_transfer_from_cash, String.format(Locale.getDefault(), "%,.2f", groupAccount.cashBalance))
                    else
                        context.getString(R.string.group_account_transfer_from_bank, String.format(Locale.getDefault(), "%,.2f", groupAccount.bankBalance)),
                    style = MaterialTheme.typography.bodyMedium
                )
                androidx.compose.material3.OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it; errorMessage = null },
                    label = { Text(stringResource(R.string.group_account_amount_label)) },
                    isError = errorMessage != null,
                    supportingText = { if (errorMessage != null) Text(errorMessage!!, color = MaterialTheme.colorScheme.error) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                androidx.compose.material3.OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.group_account_description_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = handleSubmit,
                enabled = !isLoading
            ) {
                if (isLoading) {
                    androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.primary)
                } else {
                    Text(stringResource(R.string.group_account_transfer_button))
                }
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseIncomeDialog(
    title: String,
    isExpense: Boolean,
    groupAccount: GroupAccount,
    onDismiss: () -> Unit,
    onConfirm: (Double, String, String) -> Unit
) {
    val context = LocalContext.current
    var amountStr by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var channel by remember { mutableStateOf("CASH") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val handleSubmit: () -> Unit = {
        val amount = amountStr.toDoubleOrNull() ?: 0.0
        val balance = if (channel == "CASH") groupAccount.cashBalance else groupAccount.bankBalance
        if (amount <= 0) {
            errorMessage = context.getString(R.string.group_account_error_invalid_amount)
        } else if (isExpense && amount > balance) {
            errorMessage = if (channel == "CASH")
                context.getString(R.string.group_account_error_insufficient_cash)
            else
                context.getString(R.string.group_account_error_insufficient_bank)
        } else {
            isLoading = true
            errorMessage = null
            onConfirm(amount, description.ifBlank { title }, channel)
        }
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                androidx.compose.material3.OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it; errorMessage = null },
                    label = { Text(stringResource(R.string.group_account_amount_label)) },
                    isError = errorMessage != null,
                    supportingText = { if (errorMessage != null) Text(errorMessage!!, color = MaterialTheme.colorScheme.error) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                androidx.compose.material3.OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.group_account_description_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                // Channel selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    androidx.compose.material3.RadioButton(
                        selected = channel == "CASH",
                        onClick = { channel = "CASH" },
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text(text = stringResource(R.string.group_account_channel_cash, String.format(Locale.getDefault(), "%,.2f", groupAccount.cashBalance)))
                    Spacer(modifier = Modifier.weight(1f))
                    androidx.compose.material3.RadioButton(
                        selected = channel == "BANK",
                        onClick = { channel = "BANK" },
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text(text = stringResource(R.string.group_account_channel_bank, String.format(Locale.getDefault(), "%,.2f", groupAccount.bankBalance)))
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = handleSubmit,
                enabled = !isLoading
            ) {
                if (isLoading) {
                    androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.primary)
                } else {
                    Text(if (isExpense) stringResource(R.string.group_account_record_button) else stringResource(R.string.group_account_record_button))
                }
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBankInfoDialog(
    account: GroupAccount,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    val context = LocalContext.current
    var bankName by remember { mutableStateOf(account.bankName) }
    var bankAccountNumber by remember { mutableStateOf(account.bankAccountNumber) }
    var isLoading by remember { mutableStateOf(false) }

    val handleSave: () -> Unit = {
        isLoading = true
        onSave(bankName, bankAccountNumber)
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.group_account_edit_bank_info)) },
        text = {
            Column(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                androidx.compose.material3.OutlinedTextField(
                    value = bankName,
                    onValueChange = { bankName = it },
                    label = { Text(stringResource(R.string.group_account_bank_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                androidx.compose.material3.OutlinedTextField(
                    value = bankAccountNumber,
                    onValueChange = { bankAccountNumber = it },
                    label = { Text(stringResource(R.string.group_account_bank_account_number)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = handleSave,
                enabled = !isLoading
            ) {
                if (isLoading) {
                    androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.primary)
                } else {
                    Text(stringResource(R.string.group_account_save_button))
                }
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}