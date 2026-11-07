package com.example.financeapp.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.example.financeapp.ClientDetail
import com.example.financeapp.Settings
import androidx.compose.ui.res.stringResource
import com.example.financeapp.R
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.example.financeapp.ui.main.BankViewModel
import com.example.financeapp.util.normalizePhoneNumber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientListScreen(
    onNavigate: (NavKey) -> Unit,
    onBack: () -> Unit,
    viewModel: BankViewModel,
    modifier: Modifier = Modifier
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val clients by viewModel.searchResults.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.client_list_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = stringResource(R.string.desc_back))
                    }
                },
                actions = {
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = stringResource(R.string.desc_add_client))
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.searchQuery.value = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.client_list_search_placeholder)) },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = stringResource(R.string.desc_search)) },
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (clients.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.client_list_no_clients),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(clients, key = { it.id }) { client ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigate(ClientDetail(client.id)) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = client.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row {
                                        Text(
                                            text = stringResource(R.string.client_list_phone_label),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        Text(
                                            text = client.phoneNumber,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = stringResource(R.string.client_list_client_id_label, client.clientId),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddClientDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, phone, address, clientId ->
                viewModel.createClient(name, phone, address, clientId)
                showAddDialog = false
            },
            checkClientIdUnique = { id, callback ->
                viewModel.checkClientIdUnique(id, callback)
            },
            checkPhoneNumberUnique = { phone, callback ->
                viewModel.checkPhoneNumberUnique(phone, callback)
            },
            checkClientIdNameUnique = { id, name, callback ->
                viewModel.checkClientIdNameUnique(id, name, callback)
            }
        )
    }
}

@Composable
fun AddClientDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, phone: String, address: String, clientId: Int) -> Unit,
    checkClientIdUnique: (Int, (Boolean) -> Unit) -> Unit,
    checkPhoneNumberUnique: (String, (Boolean) -> Unit) -> Unit,
    checkClientIdNameUnique: (Int, String, (Boolean) -> Unit) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var clientIdStr by remember { mutableStateOf("") }

    var errorMsgResId by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_add_client_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = clientIdStr,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() }) {
                            clientIdStr = input
                        }
                    },
                    label = { Text(stringResource(R.string.dialog_add_client_id)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.dialog_add_client_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { input ->
                        // Allow digits and '+' character
                        if (input.all { it.isDigit() || it == '+' }) {
                            phone = input
                        }
                    },
                    label = { Text(stringResource(R.string.dialog_add_client_phone)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text(stringResource(R.string.dialog_add_client_address)) },
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
                    val clientIdVal = clientIdStr.toIntOrNull()
                    val cleanPhone = normalizePhoneNumber(phone)
                    val isPhoneValid = cleanPhone.length in 7..15
                    
                    if (name.isBlank() || phone.isBlank() || address.isBlank() || clientIdStr.isBlank()) {
                        errorMsgResId = R.string.dialog_add_client_error_required
                    } else if (clientIdVal == null) {
                        errorMsgResId = R.string.dialog_add_client_error_invalid_id
                    } else if (!isPhoneValid) {
                        errorMsgResId = R.string.dialog_add_client_error_invalid_phone
                    } else {
                        checkClientIdUnique(clientIdVal) { isUnique ->
                            if (isUnique) {
                                checkPhoneNumberUnique(cleanPhone) { isPhoneUnique ->
                                    if (isPhoneUnique) {
                                        checkClientIdNameUnique(clientIdVal, name) { isNameIdUnique ->
                                            if (isNameIdUnique) {
                                                onConfirm(name, cleanPhone, address, clientIdVal)
                                            } else {
                                                errorMsgResId = R.string.dialog_add_client_error_duplicate_name_id
                                            }
                                        }
                                    } else {
                                        errorMsgResId = R.string.dialog_add_client_error_duplicate_phone
                                    }
                                }
                            } else {
                                errorMsgResId = R.string.dialog_add_client_error_duplicate_id
                            }
                        }
                    }
                }
            ) {
                Text(stringResource(R.string.dialog_add_client_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}
