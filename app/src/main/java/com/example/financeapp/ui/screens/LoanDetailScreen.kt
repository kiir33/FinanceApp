package com.example.financeapp.ui.screens

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.financeapp.R
import com.example.financeapp.database.entities.Loan
import com.example.financeapp.database.entities.LoanRepayment
import com.example.financeapp.database.entities.currentOutstandingBalance
import com.example.financeapp.database.entities.maturityDateMillis
import com.example.financeapp.ui.main.BankViewModel
import com.example.financeapp.theme.AccentRed
import com.example.financeapp.theme.AccentGreen
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanDetailScreen(
    loanId: Long,
    onBack: () -> Unit,
    viewModel: BankViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val loansList by viewModel.allLoansWithClients.collectAsState()
    val loanWithClient = remember(loansList, loanId) { loansList.find { it.loan.id == loanId } }
    val repayments by viewModel.getLoanRepayments(loanId).collectAsState(initial = emptyList())
    var showRepayDialog by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }
    val shortDateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val now = System.currentTimeMillis()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_loan_details)) },
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
        if (loanWithClient == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.client_detail_loading), color = MaterialTheme.colorScheme.secondary)
            }
        } else {
            val loan = loanWithClient.loan
            val client = loanWithClient.client
            val guarantor = loanWithClient.guarantor
            val totalInterestPaid = repayments.sumOf { it.interestPaid }
            val currentOutstanding = loan.currentOutstandingBalance(now, totalInterestPaid)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Part 1: Borrower & Guarantor Information
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Loan Associations",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = stringResource(R.string.label_borrower),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        Text(
                                            text = client?.name ?: stringResource(R.string.label_unknown_member),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = stringResource(R.string.label_member_id_format, client?.clientId?.toString() ?: "N/A"),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = stringResource(R.string.label_guarantor),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        Text(
                                            text = guarantor?.name ?: stringResource(R.string.label_no_guarantor),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (guarantor != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.secondary
                                        )
                                        if (guarantor != null) {
                                            Text(
                                                text = stringResource(R.string.label_member_id_format, guarantor.clientId.toString()),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Part 2: Financial Details Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.label_repayment_summary),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (loan.status == "ACTIVE") AccentRed.copy(alpha = 0.1f)
                                                else AccentGreen.copy(alpha = 0.1f)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = loan.status,
                                            color = if (loan.status == "ACTIVE") AccentRed else AccentGreen,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = stringResource(R.string.label_principal_disbursed),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        Text(
                                            text = String.format(Locale.getDefault(), "Rs. %,.0f", loan.amount),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = stringResource(R.string.loan_details_outstanding_as_of_today),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        Text(
                                            text = String.format(Locale.getDefault(), "Rs. %,.0f", currentOutstanding),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (loan.status == "ACTIVE") AccentRed else MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                Spacer(modifier = Modifier.height(12.dp))

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = stringResource(R.string.loan_details_rate_format, loan.interestRate),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Text(
                                        text = stringResource(R.string.loan_details_tenure_format, loan.tenureMonths),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Text(
                                        text = stringResource(R.string.loan_details_disbursed_on, shortDateFormat.format(Date(loan.loanDate))),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Text(
                                        text = stringResource(R.string.loan_details_maturity_date, shortDateFormat.format(Date(loan.maturityDateMillis()))),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (now > loan.maturityDateMillis() && loan.status == "ACTIVE") AccentRed else MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                    }

                    // Part 3: Repayment Logs
                    item {
                        Text(
                            text = stringResource(R.string.label_repayment_history),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    if (repayments.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(stringResource(R.string.label_no_repayments), color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    } else {
                        items(repayments, key = { it.id }) { r ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stringResource(R.string.label_repayment_amount),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        Text(
                                            text = String.format(Locale.getDefault(), "Rs. %,.0f", r.amount),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = stringResource(
                                                R.string.label_repayment_log_format,
                                                String.format(Locale.getDefault(), "Rs. %,.0f", r.principalPaid),
                                                String.format(Locale.getDefault(), "Rs. %,.0f", r.interestPaid)
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.label_repayment_date_format, dateFormat.format(Date(r.timestamp))),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                    }
                }

                // Part 4: Bottom Repay Button
                if (loan.status == "ACTIVE") {
                    Button(
                        onClick = { showRepayDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.button_register_repayment), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showRepayDialog && loanWithClient != null) {
        RepayLoanFormDialog(
            preselectedLoan = loanWithClient.loan,
            preselectedClient = loanWithClient.client,
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
