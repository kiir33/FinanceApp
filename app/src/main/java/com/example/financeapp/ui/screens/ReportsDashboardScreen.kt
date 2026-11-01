package com.example.financeapp.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.unit.sp
import com.example.financeapp.R
import com.example.financeapp.reports.CsvExporter
import com.example.financeapp.reports.ReportGenerator
import com.example.financeapp.ui.main.BankViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsDashboardScreen(
    onBack: () -> Unit,
    viewModel: BankViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showDateRangeDialog by remember { mutableStateOf(false) }
    var selectedReport: ReportType? by remember { mutableStateOf(null) }
    var fromDate by remember { mutableStateOf(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000) }
    var toDate by remember { mutableStateOf(System.currentTimeMillis()) }

    // Export launcher
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        val currentReport = selectedReport
        if (uri != null && currentReport != null) {
            exportReport(context, coroutineScope, uri, currentReport, fromDate, toDate)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reports_dashboard_title)) },
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
                // Header
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.reports_dashboard_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.reports_dashboard_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                // Report cards
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(ReportType.values()) { report ->
                        ReportCard(
                            report = report,
                            onClick = {
                                if (report.needsDateRange) {
                                    selectedReport = report
                                    showDateRangeDialog = true
                                } else {
                                    selectedReport = report
                                    fromDate = 0 // Will be ignored
                                    toDate = System.currentTimeMillis()
                                    // Use a default filename
                                    val fileName = "${report.fileName}_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())}.csv"
                                    exportLauncher.launch(fileName)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Date Range Dialog
    if (showDateRangeDialog && selectedReport != null) {
        DateRangeDialog(
            report = selectedReport!!,
            fromDate = fromDate,
            toDate = toDate,
            onDismiss = { showDateRangeDialog = false; selectedReport = null },
            onConfirm = { from, to ->
                fromDate = from
                toDate = to
                val fileName = "${selectedReport!!.fileName}_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())}.csv"
                exportLauncher.launch(fileName)
                showDateRangeDialog = false
                selectedReport = null
            }
        )
    }
}

private fun exportReport(
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    uri: android.net.Uri,
    report: ReportType,
    fromDate: Long,
    toDate: Long
) {
    scope.launch(Dispatchers.IO) {
        val csvContent = when (report) {
            ReportType.MEMBER_DIRECTORY -> ReportGenerator.generateMemberDirectory(context)
            ReportType.ALL_MEMBERS_SAVINGS_SUMMARY -> ReportGenerator.generateAllMembersSavingsSummary(context)
            ReportType.SAVINGS_PASSBOOK_TRANSACTIONS -> ReportGenerator.generateSavingsPassbookTransactions(context, fromDate, toDate)
            ReportType.ALL_LOANS_MASTER_LEDGER -> ReportGenerator.generateAllLoansMasterLedger(context)
            ReportType.ACTIVE_LOANS_AGING_REPORT -> ReportGenerator.generateActiveLoansAgingReport(context)
            ReportType.LOAN_REPAYMENTS_HISTORY -> ReportGenerator.generateLoanRepaymentsHistory(context)
            ReportType.GROUP_BALANCE_SHEET -> ReportGenerator.generateGroupBalanceSheet(context)
            ReportType.INCOME_STATEMENT_PROFIT_LOSS -> ReportGenerator.generateIncomeStatementProfitLoss(context)
            ReportType.TREASURY_CASH_FLOW -> ReportGenerator.generateTreasuryCashFlow(context)
            ReportType.SYSTEM_AUDIT_TRAIL -> ReportGenerator.generateSystemAuditTrail(context)
        }

        val rows = csvContent.split("\r\n").map { it.split(",").toList() }
        val result = CsvExporter.writeToUri(context, uri, rows)
        kotlinx.coroutines.withContext(Dispatchers.Main) {
            result
                .onSuccess {
                    Toast.makeText(context, context.getString(R.string.reports_export_success, report.displayName), Toast.LENGTH_LONG).show()
                }
                .onFailure { e ->
                    Toast.makeText(context, context.getString(R.string.reports_export_error, e.message), Toast.LENGTH_LONG).show()
                }
        }
    }
}

@Composable
private fun ReportCard(
    report: ReportType,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(80.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = report.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = report.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = report.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = stringResource(R.string.reports_download),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeDialog(
    report: ReportType,
    fromDate: Long,
    toDate: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long, Long) -> Unit
) {
    val context = LocalContext.current
    var from by remember { mutableStateOf(fromDate) }
    var to by remember { mutableStateOf(toDate) }
    var fromStr by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(fromDate))) }
    var toStr by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(toDate))) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    val handleConfirm: () -> Unit = {
        try {
            val fromMillis = dateFormat.parse(fromStr)?.time ?: fromDate
            val toMillis = dateFormat.parse(toStr)?.time ?: toDate
            if (fromMillis > toMillis) {
                errorMessage = context.getString(R.string.reports_error_date_range)
            } else {
                onConfirm(fromMillis, toMillis)
            }
        } catch (e: Exception) {
            errorMessage = context.getString(R.string.reports_error_invalid_date)
        }
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reports_date_range_title, report.displayName)) },
        text = {
            Column(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.reports_date_range_desc),
                    style = MaterialTheme.typography.bodyMedium
                )
                androidx.compose.material3.OutlinedTextField(
                    value = fromStr,
                    onValueChange = { fromStr = it; errorMessage = null },
                    label = { Text(stringResource(R.string.reports_from_date_label)) },
                    isError = errorMessage != null,
                    supportingText = { if (errorMessage != null) Text(errorMessage!!, color = MaterialTheme.colorScheme.error) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    trailingIcon = {
                        IconButton(onClick = { /* TODO: show date picker */ }) {
                            Icon(Icons.Default.TableChart, contentDescription = "Pick date")
                        }
                    }
                )
                androidx.compose.material3.OutlinedTextField(
                    value = toStr,
                    onValueChange = { toStr = it; errorMessage = null },
                    label = { Text(stringResource(R.string.reports_to_date_label)) },
                    isError = errorMessage != null,
                    supportingText = { if (errorMessage != null) Text(errorMessage!!, color = MaterialTheme.colorScheme.error) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    trailingIcon = {
                        IconButton(onClick = { /* TODO: show date picker */ }) {
                            Icon(Icons.Default.TableChart, contentDescription = "Pick date")
                        }
                    }
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = handleConfirm) {
                Text(stringResource(R.string.dialog_confirm))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

enum class ReportType(
    val displayName: String,
    val fileName: String,
    val description: String,
    val icon: ImageVector,
    val needsDateRange: Boolean = false
) {
    MEMBER_DIRECTORY(
        displayName = "Member Directory",
        fileName = "member_directory",
        description = "Annual member registry with loan counts",
        icon = Icons.Default.PictureAsPdf,
        needsDateRange = false
    ),
    ALL_MEMBERS_SAVINGS_SUMMARY(
        displayName = "Savings Summary",
        fileName = "all_members_savings_summary",
        description = "AGM savings sheet: deposited, withdrawn, balance",
        icon = Icons.Default.Assessment,
        needsDateRange = false
    ),
    SAVINGS_PASSBOOK_TRANSACTIONS(
        displayName = "Savings Passbook",
        fileName = "savings_passbook_transactions",
        description = "Detailed savings ledger with date range filter",
        icon = Icons.Default.Description,
        needsDateRange = true
    ),
    ALL_LOANS_MASTER_LEDGER(
        displayName = "Loan Master Ledger",
        fileName = "all_loans_master_ledger",
        description = "Complete loan book with guarantor and repayment splits",
        icon = Icons.Default.TableChart,
        needsDateRange = false
    ),
    ACTIVE_LOANS_AGING_REPORT(
        displayName = "Active Loans Aging",
        fileName = "active_loans_aging_report",
        description = "Delinquency monitor with payoff amounts",
        icon = Icons.Default.Assessment,
        needsDateRange = false
    ),
    LOAN_REPAYMENTS_HISTORY(
        displayName = "Repayment History",
        fileName = "loan_repayments_history",
        description = "Audit of loan collections with principal/interest split",
        icon = Icons.Default.Description,
        needsDateRange = false
    ),
    GROUP_BALANCE_SHEET(
        displayName = "Balance Sheet",
        fileName = "group_balance_sheet",
        description = "Treasury & financial position (A=L+E)",
        icon = Icons.Default.Assessment,
        needsDateRange = false
    ),
    INCOME_STATEMENT_PROFIT_LOSS(
        displayName = "Profit & Loss",
        fileName = "income_statement_profit_loss",
        description = "P&L statement with revenue and expenses",
        icon = Icons.Default.Assessment,
        needsDateRange = false
    ),
    TREASURY_CASH_FLOW(
        displayName = "Cash Flow",
        fileName = "treasury_cash_flow",
        description = "Bank & cash movement with running balances",
        icon = Icons.Default.TableChart,
        needsDateRange = false
    ),
    SYSTEM_AUDIT_TRAIL(
        displayName = "Audit Trail",
        fileName = "system_audit_trail",
        description = "Security & operations log with ISO timestamps",
        icon = Icons.Default.Description,
        needsDateRange = false
    )
}