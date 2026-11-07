package com.example.financeapp.ui.screens

import android.app.Application
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.financeapp.R
import com.example.financeapp.auth.AuthResult
import com.example.financeapp.auth.AuthService
import com.example.financeapp.auth.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminSetupScreen(
    onSetupComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val authService = remember { AuthService.getInstance(context.applicationContext as Application) }

    var username by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var usernameError by remember { mutableStateOf<String?>(null) }
    var fullNameError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmPasswordError by remember { mutableStateOf<String?>(null) }

    val validateForm = {
        var valid = true
        usernameError = if (username.isBlank()) context.getString(R.string.admin_setup_error_username_required) else null
        fullNameError = if (fullName.isBlank()) context.getString(R.string.admin_setup_error_name_required) else null
        phoneError = if (phoneNumber.isBlank()) context.getString(R.string.admin_setup_error_phone_required) else null
        if (phoneNumber.isNotBlank()) {
            val cleanPhone = phoneNumber.replace(Regex("[^0-9]"), "")
            if (cleanPhone.length !in 7..15) {
                phoneError = context.getString(R.string.admin_setup_error_invalid_phone)
            }
        }
        passwordError = when {
            password.isBlank() -> context.getString(R.string.admin_setup_error_password_required)
            password.length < 8 -> context.getString(R.string.admin_setup_error_password_length)
            !password.any { it.isLetter() } -> context.getString(R.string.admin_setup_error_password_letter)
            !password.any { it.isDigit() } -> context.getString(R.string.admin_setup_error_password_digit)
            else -> null
        }
        confirmPasswordError = if (confirmPassword != password) context.getString(R.string.admin_setup_error_password_mismatch) else null
        valid = usernameError == null && fullNameError == null && phoneError == null &&
                passwordError == null && confirmPasswordError == null
        valid
    }

    val handleSubmit: () -> Unit = {
        if (validateForm()) {
            isLoading = true
            errorMessage = null

            coroutineScope.launch {
                val result = withContext(Dispatchers.IO) {
                    authService.createInitialAdmin(
                        username = username,
                        password = password,
                        fullName = fullName,
                        phoneNumber = phoneNumber
                    )
                }
                isLoading = false
                when (result) {
                    is AuthResult.AdminCreated -> {
                        SessionManager.startSession(
                            com.example.financeapp.auth.UserSession(
                                userId = result.user.id,
                                username = result.user.username,
                                role = result.user.role,
                                clientId = result.user.clientId
                            )
                        )
                        onSetupComplete()
                    }
                    is AuthResult.AdminCreationFailure.ValidationError -> {
                        errorMessage = result.message
                    }
                    is AuthResult.AdminCreationFailure.UserAlreadyExists -> {
                        errorMessage = context.getString(R.string.admin_setup_error_user_exists)
                    }
                    else -> {
                        errorMessage = context.getString(R.string.admin_setup_error_unknown)
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.admin_setup_title)) },
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
                .padding(24.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .width(400.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Title
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.admin_setup_title),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.admin_setup_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Error message
                        errorMessage?.let { msg ->
                            Text(
                                text = msg,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Username field
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it; usernameError = null },
                            label = { Text(stringResource(R.string.admin_setup_username_label)) },
                            placeholder = { Text(stringResource(R.string.admin_setup_username_hint)) },
                            isError = usernameError != null,
                            supportingText = { if (usernameError != null) Text(usernameError!!, color = MaterialTheme.colorScheme.error) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                        )

                        // Full Name field
                        OutlinedTextField(
                            value = fullName,
                            onValueChange = { fullName = it; fullNameError = null },
                            label = { Text(stringResource(R.string.admin_setup_name_label)) },
                            placeholder = { Text(stringResource(R.string.admin_setup_name_hint)) },
                            isError = fullNameError != null,
                            supportingText = { if (fullNameError != null) Text(fullNameError!!, color = MaterialTheme.colorScheme.error) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Phone Number field
                        OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = { phoneNumber = it; phoneError = null },
                            label = { Text(stringResource(R.string.admin_setup_phone_label)) },
                            placeholder = { Text(stringResource(R.string.admin_setup_phone_hint)) },
                            isError = phoneError != null,
                            supportingText = { if (phoneError != null) Text(phoneError!!, color = MaterialTheme.colorScheme.error) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                        )

                        // Password field
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it; passwordError = null },
                            label = { Text(stringResource(R.string.admin_setup_password_label)) },
                            placeholder = { Text(stringResource(R.string.admin_setup_password_hint)) },
                            isError = passwordError != null,
                            supportingText = { if (passwordError != null) Text(passwordError!!, color = MaterialTheme.colorScheme.error) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (passwordVisible) VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (passwordVisible) stringResource(R.string.admin_setup_hide_password) else stringResource(R.string.admin_setup_show_password)
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                        )

                        // Confirm Password field
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it; confirmPasswordError = null },
                            label = { Text(stringResource(R.string.admin_setup_confirm_password_label)) },
                            placeholder = { Text(stringResource(R.string.admin_setup_confirm_password_hint)) },
                            isError = confirmPasswordError != null,
                            supportingText = { if (confirmPasswordError != null) Text(confirmPasswordError!!, color = MaterialTheme.colorScheme.error) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                    Icon(
                                        imageVector = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (confirmPasswordVisible) stringResource(R.string.admin_setup_hide_password) else stringResource(R.string.admin_setup_show_password)
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                        )

                        // Submit button
                        Button(
                            onClick = handleSubmit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            enabled = !isLoading,
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            if (isLoading) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.admin_setup_create_button),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PasswordVisibilityIcon(
    visible: Boolean,
    onClick: () -> Unit,
    contentDescription: String
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = if (visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}