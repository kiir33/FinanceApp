package com.example.financeapp.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.financeapp.R
import com.example.financeapp.auth.AuthService
import com.example.financeapp.auth.PasswordHasher
import com.example.financeapp.auth.SessionManager
import com.example.financeapp.database.entities.Client
import com.example.financeapp.database.entities.User
import com.example.financeapp.importer.CsvImporter
import com.example.financeapp.ui.main.BankViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManagementScreen(
    onNavigate: (androidx.navigation3.runtime.NavKey) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: BankViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val authService = AuthService.getInstance(context.applicationContext)
    val allUsers = viewModel.allUsers.collectAsStateWithLifecycle()

    var showCreateUserDialog by remember { mutableStateOf(false) }
    var showResetPasswordDialog by remember { mutableStateOf(false) }
    var selectedUserForReset: User? by remember { mutableStateOf(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.user_management_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = stringResource(R.string.desc_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateUserDialog = true }) {
                        Icon(imageVector = Icons.Default.PersonAdd, contentDescription = stringResource(R.string.user_management_add_user))
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
            Column(modifier = Modifier.fillMaxSize()) {
                // User list
                allUsers.value?.let { users ->
                    if (users.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.user_management_no_users),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(users) { user ->
                                UserListItem(
                                    user = user,
                                    allClients = viewModel.allClients.value ?: emptyList(),
                                    onEdit = { /* Not implemented - could navigate to edit */ },
                                    onResetPassword = {
                                        selectedUserForReset = user
                                        showResetPasswordDialog = true
                                    },
                                    onToggleActive = { newActive ->
                                        toggleUserActive(user, newActive)
                                    },
                                    onUnlock = {
                                        unlockUser(user)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Create User Dialog
    if (showCreateUserDialog) {
        CreateUserDialog(
            onDismiss = { showCreateUserDialog = false },
            allClients = viewModel.allClients.value ?: emptyList(),
            onSuccess = { showCreateUserDialog = false }
        )
    }

    // Reset Password Dialog
    if (showResetPasswordDialog && selectedUserForReset != null) {
        ResetPasswordDialog(
            user = selectedUserForReset!!,
            onDismiss = { showResetPasswordDialog = false; selectedUserForReset = null },
            onSuccess = { showResetPasswordDialog = false; selectedUserForReset = null }
        )
    }
}

@Composable
private fun UserListItem(
    user: User,
    allClients: List<Client>,
    onEdit: () -> Unit,
    onResetPassword: () -> Unit,
    onToggleActive: (Boolean) -> Unit,
    onUnlock: () -> Unit
) {
    val client = user.clientId?.let { id -> allClients.find { it.id == id } }
    val isLocked = user.lockedUntil != null && user.lockedUntil!! > System.currentTimeMillis()
    val roleColor = if (user.role == SessionManager.ROLE_ADMIN) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                if (user.role == SessionManager.ROLE_ADMIN)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.secondaryContainer
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = if (user.role == SessionManager.ROLE_ADMIN)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = user.username,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.user_management_role_format, user.role),
                                style = MaterialTheme.typography.bodySmall,
                                color = roleColor,
                                fontWeight = FontWeight.Medium
                            )
                            client?.let { c ->
                                Text(
                                    text = stringResource(R.string.user_management_client_format, c.name, c.clientId),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            if (isLocked) {
                                Text(
                                    text = stringResource(R.string.user_management_locked),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            } else if (!user.isActive) {
                                Text(
                                    text = stringResource(R.string.user_management_inactive),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
                // Last login
                user.lastLoginAt?.let { lastLogin ->
                    Text(
                        text = stringResource(R.string.user_management_last_login, java.text.SimpleDateFormat("dd MMM yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(lastLogin))),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onResetPassword,
                    modifier = Modifier.weight(1f),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.user_management_reset_password))
                }
                Button(
                    onClick = { onToggleActive(!user.isActive) },
                    modifier = Modifier.weight(1f),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = if (user.isActive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Icon(
                        if (user.isActive) Icons.Default.LockOpen else Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (user.isActive) stringResource(R.string.user_management_deactivate) else stringResource(R.string.user_management_activate))
                }
                if (isLocked) {
                    Button(
                        onClick = onUnlock,
                        modifier = Modifier.weight(1f),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.user_management_unlock))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateUserDialog(
    onDismiss: () -> Unit,
    allClients: List<Client>,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val authService = AuthService.getInstance(context.applicationContext)

    var selectedClient by remember { mutableStateOf<Client?>(null) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var usernameError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmPasswordError by remember { mutableStateOf<String?>(null) }
    var clientDropdownExpanded by remember { mutableStateOf(false) }

    val client = allClients.find { it.id == selectedClient?.id }

    val validateForm = {
        var valid = true
        usernameError = if (username.isBlank()) context.getString(R.string.user_mgmt_error_username_required) else null
        if (username.isNotBlank()) {
            val existingUser = authService.userDao.getUserByUsername(username)
            if (existingUser != null) {
                usernameError = context.getString(R.string.user_mgmt_error_username_exists)
            }
        }
        passwordError = when {
            password.isBlank() -> context.getString(R.string.user_mgmt_error_password_required)
            password.length < 8 -> context.getString(R.string.user_mgmt_error_password_length)
            !password.any { it.isLetter() } -> context.getString(R.string.user_mgmt_error_password_letter)
            !password.any { it.isDigit() } -> context.getString(R.string.user_mgmt_error_password_digit)
            else -> null
        }
        confirmPasswordError = if (confirmPassword != password) context.getString(R.string.user_mgmt_error_password_mismatch) else null
        if (selectedClient == null) {
            errorMessage = context.getString(R.string.user_mgmt_error_client_required)
            valid = false
        }
        valid = usernameError == null && passwordError == null && confirmPasswordError == null && selectedClient != null
        valid
    }

    val handleSubmit = {
        if (!validateForm()) return@handleSubmit
        isLoading = true
        errorMessage = null

        val salt = PasswordHasher.generateSalt()
        val passwordHash = PasswordHasher.hashPassword(password, salt)

        val newUser = User(
            username = username,
            passwordHash = passwordHash,
            salt = salt,
            role = SessionManager.ROLE_MEMBER,
            clientId = selectedClient?.id,
            isActive = true,
            failedAttempts = 0,
            lockedUntil = null,
            mustChangePassword = true,
            createdAt = System.currentTimeMillis(),
            lastLoginAt = null
        )

        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            val userId = authService.userDao.insertUser(newUser)
            // Audit log
            authService.auditLogDao.insertAuditLog(com.example.financeapp.database.entities.AuditLog(
                userId = SessionManager.currentSession.value?.userId ?: 1,
                username = SessionManager.currentSession.value?.username ?: "admin",
                action = "USER_CREATED",
                details = "Created member login for ${selectedClient?.name} (username: $username)"
            ))
            androidx.compose.runtime.Snapshot.withMutableSnapshot {
                isLoading = false
                onSuccess()
            }
        }
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.user_management_create_user)) },
        text = {
            Column(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Client dropdown
                androidx.compose.material3.ExposedDropdownMenuBox(
                    expanded = clientDropdownExpanded,
                    onExpandedChange = { clientDropdownExpanded = it }
                ) {
                    androidx.compose.material3.OutlinedTextField(
                        value = selectedClient?.name ?: stringResource(R.string.user_mgmt_select_client),
                        onValueChange = {},
                        label = { Text(stringResource(R.string.user_mgmt_client_label)) },
                        readOnly = true,
                        trailingIcon = {
                            androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = clientDropdownExpanded)
                        },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    androidx.compose.material3.ExposedDropdownMenu(
                        expanded = clientDropdownExpanded,
                        onDismissRequest = { clientDropdownExpanded = false }
                    ) {
                        allClients.forEach { c ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(stringResource(R.string.user_mgmt_client_format, c.name, c.clientId)) },
                                onClick = {
                                    selectedClient = c
                                    // Auto-generate username from name
                                    username = CsvImporter.generateBaseUsername(c.name)
                                    clientDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Username field
                androidx.compose.material3.OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; usernameError = null },
                    label = { Text(stringResource(R.string.user_mgmt_username_label)) },
                    isError = usernameError != null,
                    supportingText = { if (usernameError != null) Text(usernameError!!, color = MaterialTheme.colorScheme.error) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Password field
                androidx.compose.material3.OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; passwordError = null },
                    label = { Text(stringResource(R.string.user_mgmt_password_label)) },
                    isError = passwordError != null,
                    supportingText = { if (passwordError != null) Text(passwordError!!, color = MaterialTheme.colorScheme.error) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    trailingIcon = {
                        androidx.compose.material3.IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (passwordVisible) stringResource(R.string.user_mgmt_hide_password) else stringResource(R.string.user_mgmt_show_password)
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )

                // Confirm Password field
                androidx.compose.material3.OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; confirmPasswordError = null },
                    label = { Text(stringResource(R.string.user_mgmt_confirm_password_label)) },
                    isError = confirmPasswordError != null,
                    supportingText = { if (confirmPasswordError != null) Text(confirmPasswordError!!, color = MaterialTheme.colorScheme.error) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    trailingIcon = {
                        androidx.compose.material3.IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                            Icon(
                                imageVector = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (confirmPasswordVisible) stringResource(R.string.user_mgmt_hide_password) else stringResource(R.string.user_mgmt_show_password)
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )

                errorMessage?.let { msg ->
                    Text(text = msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
                    Text(stringResource(R.string.user_mgmt_create_button))
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
fun ResetPasswordDialog(
    user: User,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val authService = AuthService.getInstance(context.applicationContext)

    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var newPasswordError by remember { mutableStateOf<String?>(null) }
    var confirmPasswordError by remember { mutableStateOf<String?>(null) }

    val validateForm = {
        var valid = true
        newPasswordError = when {
            newPassword.isBlank() -> context.getString(R.string.user_mgmt_error_password_required)
            newPassword.length < 8 -> context.getString(R.string.user_mgmt_error_password_length)
            !newPassword.any { it.isLetter() } -> context.getString(R.string.user_mgmt_error_password_letter)
            !newPassword.any { it.isDigit() } -> context.getString(R.string.user_mgmt_error_password_digit)
            else -> null
        }
        confirmPasswordError = if (confirmPassword != newPassword) context.getString(R.string.user_mgmt_error_password_mismatch) else null
        valid = newPasswordError == null && confirmPasswordError == null
        valid
    }

    val handleSubmit = {
        if (!validateForm()) return@handleSubmit
        isLoading = true
        errorMessage = null

        val salt = PasswordHasher.generateSalt()
        val passwordHash = PasswordHasher.hashPassword(newPassword, salt)

        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            val updatedUser = user.copy(
                passwordHash = passwordHash,
                salt = salt,
                mustChangePassword = true,
                failedAttempts = 0,
                lockedUntil = null
            )
            authService.userDao.updateUser(updatedUser)
            // Audit log
            authService.auditLogDao.insertAuditLog(com.example.financeapp.database.entities.AuditLog(
                userId = SessionManager.currentSession.value?.userId ?: 1,
                username = SessionManager.currentSession.value?.username ?: "admin",
                action = "PASSWORD_RESET_BY_ADMIN",
                details = "Admin reset password for user ${user.username} (mustChangePassword=true)"
            ))
            androidx.compose.runtime.Snapshot.withMutableSnapshot {
                isLoading = false
                onSuccess()
            }
        }
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.user_management_reset_password_title, user.username)) },
        text = {
            Column(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.user_management_reset_password_desc),
                    style = MaterialTheme.typography.bodyMedium
                )

                // New Password field
                androidx.compose.material3.OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it; newPasswordError = null },
                    label = { Text(stringResource(R.string.user_mgmt_new_password_label)) },
                    isError = newPasswordError != null,
                    supportingText = { if (newPasswordError != null) Text(newPasswordError!!, color = MaterialTheme.colorScheme.error) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    trailingIcon = {
                        androidx.compose.material3.IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (passwordVisible) stringResource(R.string.user_mgmt_hide_password) else stringResource(R.string.user_mgmt_show_password)
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )

                // Confirm New Password field
                androidx.compose.material3.OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; confirmPasswordError = null },
                    label = { Text(stringResource(R.string.user_mgmt_confirm_password_label)) },
                    isError = confirmPasswordError != null,
                    supportingText = { if (confirmPasswordError != null) Text(confirmPasswordError!!, color = MaterialTheme.colorScheme.error) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    trailingIcon = {
                        androidx.compose.material3.IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                            Icon(
                                imageVector = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (confirmPasswordVisible) stringResource(R.string.user_mgmt_hide_password) else stringResource(R.string.user_mgmt_show_password)
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )

                errorMessage?.let { msg ->
                    Text(text = msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
                    Text(stringResource(R.string.user_mgmt_reset_button))
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

private fun toggleUserActive(user: User, newActive: Boolean) {
    val context = LocalContext.current
    val authService = AuthService.getInstance(context.applicationContext)

    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
        val updatedUser = user.copy(isActive = newActive)
        authService.userDao.updateUser(updatedUser)
        // Audit log
        authService.auditLogDao.insertAuditLog(com.example.financeapp.database.entities.AuditLog(
            userId = SessionManager.currentSession.value?.userId ?: 1,
            username = SessionManager.currentSession.value?.username ?: "admin",
            action = if (newActive) "USER_ACTIVATED" else "USER_DEACTIVATED",
            details = "Admin ${if (newActive) "activated" else "deactivated"} user ${user.username}"
        ))
    }
}

private fun unlockUser(user: User) {
    val context = LocalContext.current
    val authService = AuthService.getInstance(context.applicationContext)

    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
        val updatedUser = user.copy(failedAttempts = 0, lockedUntil = null)
        authService.userDao.updateUser(updatedUser)
        // Audit log
        authService.auditLogDao.insertAuditLog(com.example.financeapp.database.entities.AuditLog(
            userId = SessionManager.currentSession.value?.userId ?: 1,
            username = SessionManager.currentSession.value?.username ?: "admin",
            action = "USER_UNLOCKED",
            details = "Admin cleared lockout for user ${user.username}"
        ))
    }
}