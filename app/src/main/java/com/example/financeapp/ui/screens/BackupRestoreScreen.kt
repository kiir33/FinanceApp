package com.example.financeapp.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation3.runtime.NavKey
import com.example.financeapp.R
import com.example.financeapp.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.financeapp.backup.BackupManager
import com.example.financeapp.backup.GoogleDriveClient
import com.example.financeapp.ui.main.BackupRestoreResult
import com.example.financeapp.ui.main.BankViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    onNavigate: (NavKey) -> Unit,
    onBack: () -> Unit,
    viewModel: BankViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Tab state
    var selectedTab by remember { mutableStateOf(0) } // 0 = Local, 1 = Drive

    // Launchers for System File Picker (Local tab)
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let { viewModel.exportDatabase(it) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importDatabase(it) }
    }

    // Drive Sign-In launcher
    val driveSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                val signInResult = viewModel.handleDriveSignInResult(data)
                scope.launch {
                    when (signInResult) {
                        is GoogleDriveClient.DriveResult.Success -> {
                            snackbarHostState.showSnackbar("Signed in to Google Drive")
                            viewModel.refreshDriveBackups()
                        }
                        is GoogleDriveClient.DriveResult.Failure -> {
                            snackbarHostState.showSnackbar("Sign-in failed: ${signInResult.message}")
                        }
                    }
                }
            }
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Sign-in cancelled")
            }
        }
    }

    // Drive state
    var driveBackups by remember { mutableStateOf<List<GoogleDriveClient.DriveFile>>(emptyList()) }
    var isDriveLoading by remember { mutableStateOf(false) }
    var isDriveSignedIn by remember { mutableStateOf(viewModel.isDriveSignedIn()) }

    // Progress states
    var localBackupProgress by remember { mutableStateOf(false) }
    var localRestoreProgress by remember { mutableStateOf(false) }
    var driveBackupProgress by remember { mutableStateOf(false) }
    var driveRestoreProgress by remember { mutableStateOf(false) }
    var driveDeleteProgress by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Passphrase dialog states
    var showLocalBackupPassphrase by remember { mutableStateOf(false) }
    var showLocalRestorePassphrase by remember { mutableStateOf(false) }
    var showDriveBackupPassphrase by remember { mutableStateOf(false) }
    var showDriveRestorePassphrase by remember { mutableStateOf(false) }
    var showDriveDeleteConfirm by remember { mutableStateOf<String?>(null) }
    var showLocalRestoreConfirm by remember { mutableStateOf(false) }
    var showDriveRestoreConfirm by remember { mutableStateOf(false) }

    var passphrase by remember { mutableStateOf("") }
    var confirmPassphrase by remember { mutableStateOf("") }
    var passphraseError by remember { mutableStateOf<String?>(null) }
    var showPassphrase by remember { mutableStateOf(false) }

    // Pending operations
    var pendingLocalBackupUri by remember { mutableStateOf<Uri?>(null) }
    var pendingLocalRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var pendingDriveRestoreFileId by remember { mutableStateOf<String?>(null) }

    // Sync drive signed-in state
    LaunchedEffect(Unit) {
        isDriveSignedIn = viewModel.isDriveSignedIn()
        if (isDriveSignedIn) {
            viewModel.refreshDriveBackups()
        }
    }

    // Observe drive backups from viewModel
    LaunchedEffect(Unit) {
        viewModel.driveBackupsEvent.collect { result ->
            isDriveLoading = false
            when (result) {
                is GoogleDriveClient.DriveResult.Success -> {
                    driveBackups = result.data
                }
                is GoogleDriveClient.DriveResult.Failure -> {
                    snackbarHostState.showSnackbar("Failed to load backups: ${result.message}")
                }
            }
        }
    }

    // Handle backup/restore events
    LaunchedEffect(Unit) {
        viewModel.backupRestoreEvent.collect { result ->
            localBackupProgress = false
            localRestoreProgress = false
            driveBackupProgress = false
            driveRestoreProgress = false
            when (result) {
                BackupRestoreResult.BackupSuccess -> {
                    snackbarHostState.showSnackbar(context.getString(R.string.toast_backup_export_success))
                }
                BackupRestoreResult.RestoreSuccess -> {
                    snackbarHostState.showSnackbar(context.getString(R.string.toast_backup_restore_success))
                    (context as? Activity)?.recreate()
                }
                is BackupRestoreResult.Failure -> {
                    snackbarHostState.showSnackbar(context.getString(R.string.toast_backup_error, result.message))
                }
                BackupRestoreResult.DriveBackupSuccess -> {
                    snackbarHostState.showSnackbar("Backup uploaded to Google Drive")
                    viewModel.refreshDriveBackups()
                }
                BackupRestoreResult.DriveRestoreSuccess -> {
                    snackbarHostState.showSnackbar("Database restored from Google Drive")
                    (context as? Activity)?.recreate()
                }
                BackupRestoreResult.DriveDeleteSuccess -> {
                    snackbarHostState.showSnackbar("Backup deleted from Google Drive")
                    viewModel.refreshDriveBackups()
                }
                else -> {}
            }
        }
    }

    // Observe drive signed-in state
    LaunchedEffect(Unit) {
        viewModel.driveSignedInEvent.collect { signedIn ->
            isDriveSignedIn = signedIn
            if (signedIn) {
                viewModel.refreshDriveBackups()
            } else {
                driveBackups = emptyList()
            }
        }
    }

    // Handle drive operation progress
    LaunchedEffect(Unit) {
        viewModel.driveOperationProgress.collect { progress ->
            when (progress) {
                BackupRestoreResult.DriveUploadProgress -> driveBackupProgress = true
                BackupRestoreResult.DriveDownloadProgress -> driveRestoreProgress = true
                is BackupRestoreResult.DriveDeleteProgress -> {
                    driveDeleteProgress = driveDeleteProgress + progress.fileId
                }
                is BackupRestoreResult.DriveOperationComplete -> {
                    driveBackupProgress = false
                    driveRestoreProgress = false
                    driveDeleteProgress = driveDeleteProgress - progress.fileId
                }
                else -> {}
            }
        }
    }

    // --- Passphrase validation ---
    fun validatePassphrase(pass: String, confirm: String? = null): String? {
        if (pass.length < 8) return "Passphrase must be at least 8 characters"
        if (!pass.any { it.isLetter() }) return "Passphrase must contain at least one letter"
        if (!pass.any { it.isDigit() }) return "Passphrase must contain at least one number"
        if (confirm != null && pass != confirm) return "Passphrases do not match"
        return null
    }

    // --- Passphrase Dialog ---
    @Composable
    fun PassphraseDialog(
        title: String,
        onConfirm: (String) -> Unit,
        onDismiss: () -> Unit,
        requireConfirmation: Boolean = false,
        isLoading: Boolean = false
    ) {
        var localPassphrase by remember { mutableStateOf("") }
        var localConfirmPassphrase by remember { mutableStateOf("") }
        var localError by remember { mutableStateOf<String?>(null) }
        var localShowPassphrase by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = {
                Column(modifier = Modifier.padding(24.dp).fillMaxWidth().width(300.dp)) {
                    Text(
                        text = "Enter a strong passphrase to encrypt your backup.\nMin 8 chars, letters & numbers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    TextField(
                        value = localPassphrase,
                        onValueChange = { localPassphrase = it; localError = null },
                        label = { Text("Passphrase") },
                        placeholder = { Text("Enter passphrase") },
                        singleLine = true,
                        visualTransformation = if (localShowPassphrase) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { localShowPassphrase = !localShowPassphrase }) {
                                Icon(
                                    imageVector = if (localShowPassphrase) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (localShowPassphrase) "Hide passphrase" else "Show passphrase"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        isError = localError != null,
                        supportingText = { if (localError != null) Text(localError!!) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    if (requireConfirmation) {
                        TextField(
                            value = localConfirmPassphrase,
                            onValueChange = { localConfirmPassphrase = it },
                            label = { Text("Confirm Passphrase") },
                            placeholder = { Text("Re-enter passphrase") },
                            singleLine = true,
                            visualTransformation = if (localShowPassphrase) VisualTransformation.None else PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            isError = requireConfirmation && localPassphrase != localConfirmPassphrase && localConfirmPassphrase.isNotBlank(),
                            supportingText = {
                                if (requireConfirmation && localPassphrase != localConfirmPassphrase && localConfirmPassphrase.isNotBlank()) {
                                    Text("Passphrases do not match")
                                }
                            }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val error = validatePassphrase(localPassphrase, if (requireConfirmation) localConfirmPassphrase else null)
                        if (error != null) {
                            localError = error
                        } else {
                            onConfirm(localPassphrase)
                        }
                    },
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Confirm")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- Confirm Dialog ---
    @Composable
    fun ConfirmDialog(
        title: String,
        message: String,
        onConfirm: () -> Unit,
        onDismiss: () -> Unit,
        confirmText: String = "Confirm",
        isDestructive: Boolean = false,
        isLoading: Boolean = false
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = { Text(message, modifier = Modifier.padding(24.dp).fillMaxWidth().width(300.dp)) },
            confirmButton = {
                Button(
                    onClick = { onConfirm() },
                    colors = if (isDestructive) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors(),
                    enabled = !isLoading
                ) {
                    if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onError)
                    else Text(confirmText)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_title)) },
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.Top
        ) {
            // Tab Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Local Tab
                Text(
                    text = "Local File",
                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                    color = if (selectedTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(16.dp)
                        .fillMaxWidth()
                        .clickable { selectedTab = 0 }
                        .background(if (selectedTab == 0) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface)
                )
                // Drive Tab
                Text(
                    text = "Google Drive",
                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                    color = if (selectedTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(16.dp)
                        .fillMaxWidth()
                        .clickable { selectedTab = 1 }
                        .background(if (selectedTab == 1) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface)
                )
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant)

            // Tab Content
            when (selectedTab) {
                0 -> LocalBackupTab(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    onBackupClick = { uri ->
                        pendingLocalBackupUri = uri
                        showLocalBackupPassphrase = true
                        passphrase = ""
                        confirmPassphrase = ""
                        passphraseError = null
                    },
                    onRestoreClick = { uri ->
                        pendingLocalRestoreUri = uri
                        showLocalRestorePassphrase = true
                        passphrase = ""
                        passphraseError = null
                    },
                    isBackupLoading = localBackupProgress,
                    isRestoreLoading = localRestoreProgress
                )
                1 -> DriveBackupTab(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    isSignedIn = isDriveSignedIn,
                    accountEmail = viewModel.getDriveAccountEmail() ?: "",
                    backups = driveBackups,
                    isLoading = isDriveLoading,
                    onSignInClick = {
                        val intent = viewModel.getDriveSignInIntent()
                        driveSignInLauncher.launch(intent)
                    },
                    onSignOutClick = {
                        scope.launch { viewModel.driveSignOut() }
                    },
                    onBackupClick = {
                        showDriveBackupPassphrase = true
                        passphrase = ""
                        confirmPassphrase = ""
                        passphraseError = null
                    },
                    onRestoreClick = { fileId ->
                        pendingDriveRestoreFileId = fileId
                        showDriveRestorePassphrase = true
                        passphrase = ""
                        passphraseError = null
                    },
                    onDeleteClick = { fileId ->
                        showDriveDeleteConfirm = fileId
                    },
                    onRefreshClick = {
                        isDriveLoading = true
                        viewModel.refreshDriveBackups()
                    },
                    isBackupLoading = driveBackupProgress,
                    isRestoreLoading = { fileId -> driveRestoreProgress && pendingDriveRestoreFileId == fileId },
                    isDeleteLoading = { fileId -> fileId in driveDeleteProgress }
                )
            }

            // Dialogs
            if (showLocalBackupPassphrase) {
                PassphraseDialog(
                    title = "Backup Passphrase",
                    requireConfirmation = true,
                    onConfirm = { p ->
                        showLocalBackupPassphrase = false
                        pendingLocalBackupUri?.let { uri ->
                            localBackupProgress = true
                            scope.launch { viewModel.exportDatabaseWithPassphrase(uri, p.toCharArray()) }
                        }
                        pendingLocalBackupUri = null
                    },
                    onDismiss = {
                        showLocalBackupPassphrase = false
                        pendingLocalBackupUri = null
                    },
                    isLoading = localBackupProgress
                )
            }

            if (showLocalRestorePassphrase) {
                PassphraseDialog(
                    title = "Restore Passphrase",
                    requireConfirmation = false,
                    onConfirm = { p ->
                        showLocalRestorePassphrase = false
                        showLocalRestoreConfirm = true
                    },
                    onDismiss = {
                        showLocalRestorePassphrase = false
                        pendingLocalRestoreUri = null
                    },
                    isLoading = localRestoreProgress
                )
            }

            if (showLocalRestoreConfirm) {
                ConfirmDialog(
                    title = "Confirm Restore",
                    message = "This will overwrite ALL current data with the backup. This action cannot be undone.",
                    onConfirm = {
                        showLocalRestoreConfirm = false
                        pendingLocalRestoreUri?.let { uri ->
                            localRestoreProgress = true
                            scope.launch { viewModel.importDatabaseWithPassphrase(uri, passphrase.toCharArray()) }
                        }
                        pendingLocalRestoreUri = null
                    },
                    onDismiss = { showLocalRestoreConfirm = false },
                    confirmText = "Restore",
                    isDestructive = true,
                    isLoading = localRestoreProgress
                )
            }

            if (showDriveBackupPassphrase) {
                PassphraseDialog(
                    title = "Backup to Google Drive",
                    requireConfirmation = true,
                    onConfirm = { p ->
                        showDriveBackupPassphrase = false
                        driveBackupProgress = true
                        scope.launch { viewModel.uploadBackupToDrive(p.toCharArray()) }
                    },
                    onDismiss = { showDriveBackupPassphrase = false },
                    isLoading = driveBackupProgress
                )
            }

            if (showDriveRestorePassphrase) {
                PassphraseDialog(
                    title = "Restore from Google Drive",
                    requireConfirmation = false,
                    onConfirm = { p ->
                        showDriveRestorePassphrase = false
                        showDriveRestoreConfirm = true
                    },
                    onDismiss = {
                        showDriveRestorePassphrase = false
                        pendingDriveRestoreFileId = null
                    },
                    isLoading = driveRestoreProgress
                )
            }

            if (showDriveRestoreConfirm) {
                ConfirmDialog(
                    title = "Confirm Restore",
                    message = "This will overwrite ALL current data with the selected Google Drive backup. This action cannot be undone.",
                    onConfirm = {
                        showDriveRestoreConfirm = false
                        pendingDriveRestoreFileId?.let { fileId ->
                            driveRestoreProgress = true
                            scope.launch { viewModel.restoreFromDrive(fileId, passphrase.toCharArray()) }
                        }
                        pendingDriveRestoreFileId = null
                    },
                    onDismiss = { showDriveRestoreConfirm = false },
                    confirmText = "Restore",
                    isDestructive = true,
                    isLoading = driveRestoreProgress
                )
            }

            if (showDriveDeleteConfirm != null) {
                ConfirmDialog(
                    title = "Delete Backup",
                    message = "Permanently delete this backup from Google Drive? This cannot be undone.",
                    onConfirm = {
                        val fileId = showDriveDeleteConfirm!!
                        showDriveDeleteConfirm = null
                        scope.launch { viewModel.deleteDriveBackup(fileId) }
                    },
                    onDismiss = { showDriveDeleteConfirm = null },
                    confirmText = "Delete",
                    isDestructive = true,
                    isLoading = showDriveDeleteConfirm?.let { it in driveDeleteProgress } ?: false
                )
            }
        }
    }
}

// --- Local Backup Tab ---
@Composable
fun LocalBackupTab(
    modifier: Modifier,
    onBackupClick: (Uri) -> Unit,
    onRestoreClick: (Uri) -> Unit,
    isBackupLoading: Boolean,
    isRestoreLoading: Boolean
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Info",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Local Backup",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Save encrypted backups to your device, SD card, or USB drive using the system file picker.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Backup Section
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Create Backup",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Icon(imageVector = Icons.Default.Save, contentDescription = "Save", modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Save an encrypted .apex backup file to your device",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    val fileName = BackupManager.Companion.generateBackupFileName()
                    onBackupClick(android.net.Uri.parse("file://$fileName"))
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = !isBackupLoading
            ) {
                if (isBackupLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = "Save", modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create Backup", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Divider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(24.dp))

        // Restore Section
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Restore Backup",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Icon(imageVector = Icons.Default.Restore, contentDescription = "Restore", modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Select an .apex backup file to restore. This will overwrite all current data.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    onRestoreClick(android.net.Uri.parse("content://"))
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                enabled = !isRestoreLoading
            ) {
                if (isRestoreLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onError)
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Restore, contentDescription = "Restore", modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Restore Backup", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// --- Drive Backup Tab ---
@Composable
fun DriveBackupTab(
    modifier: Modifier,
    isSignedIn: Boolean,
    accountEmail: String,
    backups: List<GoogleDriveClient.DriveFile>,
    isLoading: Boolean,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onBackupClick: () -> Unit,
    onRestoreClick: (String) -> Unit,
    onDeleteClick: (String) -> Unit,
    onRefreshClick: () -> Unit,
    isBackupLoading: Boolean,
    isRestoreLoading: (String) -> Boolean,
    isDeleteLoading: (String) -> Boolean
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Sign-in State Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isSignedIn) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f)
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = if (isSignedIn) "Signed in to Google Drive" else "Not signed in",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isSignedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                    if (isSignedIn) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = accountEmail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Sign in to enable cloud backups to your private appDataFolder",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (isSignedIn) {
                    Button(
                        onClick = onSignOutClick,
                        modifier = Modifier.height(36.dp),
                        colors = ButtonDefaults.outlinedButtonColors()
                    ) {
                        Row {
                            Icon(imageVector = Icons.Default.Logout, contentDescription = "Sign out", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sign Out")
                        }
                    }
                } else {
                    Button(
                        onClick = onSignInClick,
                        modifier = Modifier.height(36.dp)
                    ) {
                        Row {
                            Icon(imageVector = Icons.Default.Login, contentDescription = "Sign in", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sign In")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (isSignedIn) {
            // Backup Button
            Button(
                onClick = onBackupClick,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = !isBackupLoading
            ) {
                if (isBackupLoading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Backing up...", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.CloudUpload, contentDescription = "Upload", modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Back Up Now", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Recent Backups List
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Recent Backups (max 5)",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = onRefreshClick, enabled = !isLoading) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        modifier = Modifier.size(24.dp),
                        tint = if (isLoading) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (backups.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp).padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.DriveFileMove,
                                contentDescription = "No backups",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No backups found",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap 'Back Up Now' to create your first cloud backup",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    backups.forEach { backup ->
                        DriveBackupItem(
                            backup = backup,
                            onRestoreClick = { onRestoreClick(backup.id) },
                            onDeleteClick = { onDeleteClick(backup.id) },
                            isRestoreLoading = isRestoreLoading(backup.id),
                            isDeleteLoading = isDeleteLoading(backup.id)
                        )
                    }
                }
            }
        } else {
            // Not signed in state
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp).padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = "Cloud backup",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Cloud Backup Unavailable",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Sign in with your Google account to enable encrypted backups to your private Drive appDataFolder.\n\n• Backups are encrypted before upload\n• Only this app can access them\n• Up to 5 recent backups retained",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

// --- Drive Backup List Item ---
@Composable
fun DriveBackupItem(
    backup: GoogleDriveClient.DriveFile,
    onRestoreClick: () -> Unit,
    onDeleteClick: () -> Unit,
    isRestoreLoading: Boolean,
    isDeleteLoading: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // File icon and info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(end = 16.dp)
            ) {
                Text(
                    text = backup.name,
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DriveFileMove,
                        contentDescription = "Backup file",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatBackupInfo(backup),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRestoreClick,
                    colors = ButtonDefaults.outlinedButtonColors(),
                    modifier = Modifier.height(36.dp).width(80.dp),
                    enabled = !isRestoreLoading && !isDeleteLoading
                ) {
                    if (isRestoreLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Text("Restore", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
                IconButton(
                    onClick = onDeleteClick,
                    enabled = !isDeleteLoading && !isRestoreLoading
                ) {
                    if (isDeleteLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.error)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

fun formatBackupInfo(backup: GoogleDriveClient.DriveFile): String {
    val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(backup.modifiedTime))
    val size = formatFileSize(backup.size)
    return "$date • $size"
}

fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "${bytes} B"
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        else -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}