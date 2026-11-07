package com.example.financeapp.ui.screens

import android.app.Activity
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Upload
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.navigation3.runtime.NavKey
import com.example.financeapp.R
import com.example.financeapp.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.financeapp.database.AppDatabase
import com.example.financeapp.importer.CsvImporter
import com.example.financeapp.importer.DryRunResult
import com.example.financeapp.importer.ImportResult
import com.example.financeapp.importer.ImportPreview
import com.example.financeapp.importer.ValidationError
import com.example.financeapp.ui.main.BankViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ImportStep {
    SELECT_TEMPLATE,
    SELECT_FILES,
    VALIDATION_ERRORS,
    PREVIEW,
    SUCCESS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvImportScreen(
    onNavigate: (NavKey) -> Unit,
    onBack: () -> Unit,
    viewModel: BankViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context.applicationContext) }
    val csvImporter = remember {
        CsvImporter(
            clientDao = db.clientDao(),
            savingsDao = db.savingsDao(),
            loanDao = db.loanDao(),
            userDao = db.userDao(),
            db = db
        )
    }

    var currentStep by remember { mutableStateOf(ImportStep.SELECT_TEMPLATE) }
    var selectedTemplate by remember { mutableStateOf<TemplateType?>(null) }
    var membersFileUri by remember { mutableStateOf<Uri?>(null) }
    var savingsFileUri by remember { mutableStateOf<Uri?>(null) }
    var loansFileUri by remember { mutableStateOf<Uri?>(null) }
    var dryRunResult by remember { mutableStateOf<DryRunResult?>(null) }
    var importResult by remember { mutableStateOf<ImportResult?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val runDryRun: () -> Unit = {
        if (membersFileUri == null) {
            errorMessage = context.getString(R.string.csv_import_error_members_required)
        } else {
            isLoading = true
            errorMessage = null

            coroutineScope.launch {
                val result = withContext(Dispatchers.IO) {
                    val membersStream = context.contentResolver.openInputStream(membersFileUri!!)
                    val savingsStream = savingsFileUri?.let { context.contentResolver.openInputStream(it) }
                    val loansStream = loansFileUri?.let { context.contentResolver.openInputStream(it) }
                    csvImporter.dryRun(membersStream!!, savingsStream, loansStream)
                }
                isLoading = false
                dryRunResult = result
                if (result.isValid) {
                    currentStep = ImportStep.PREVIEW
                } else {
                    currentStep = ImportStep.VALIDATION_ERRORS
                }
            }
        }
    }

    val runCommit: () -> Unit = {
        isLoading = true

        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                val membersStream = context.contentResolver.openInputStream(membersFileUri!!)
                val savingsStream = savingsFileUri?.let { context.contentResolver.openInputStream(it) }
                val loansStream = loansFileUri?.let { context.contentResolver.openInputStream(it) }
                csvImporter.commit(membersStream!!, savingsStream, loansStream)
            }
            isLoading = false
            importResult = result
            if (result.success) {
                currentStep = ImportStep.SUCCESS
            } else {
                errorMessage = result.message
            }
        }
    }

    val downloadTemplate: (Uri, TemplateType) -> Unit = { uri, template ->
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                val content = when (template) {
                    TemplateType.MEMBERS -> csvImporter.getMembersTemplate()
                    TemplateType.SAVINGS -> csvImporter.getSavingsTemplate()
                    TemplateType.LOANS -> csvImporter.getLoansTemplate()
                }
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(content.toByteArray())
                }
            }
            Toast.makeText(context, context.getString(R.string.csv_import_template_downloaded, template.displayName), Toast.LENGTH_SHORT).show()
        }
    }

    // File pickers
    val membersFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { membersFileUri = it }
    }

    val savingsFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { savingsFileUri = it }
    }

    val loansFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { loansFileUri = it }
    }

    // Template download launchers
    val membersTemplateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let { downloadTemplate(it, TemplateType.MEMBERS) }
    }

    val savingsTemplateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let { downloadTemplate(it, TemplateType.SAVINGS) }
    }

    val loansTemplateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let { downloadTemplate(it, TemplateType.LOANS) }
    }

    // Credentials sheet download
    val credentialsSheetLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let {
            importResult?.credentialsSheet?.let { csv ->
                context.contentResolver.openOutputStream(it)?.use { outputStream ->
                    outputStream.write(csv.toByteArray())
                }
                Toast.makeText(context, context.getString(R.string.csv_import_credentials_saved), Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.csv_import_title)) },
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
        modifier = modifier
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            when (currentStep) {
                ImportStep.SELECT_TEMPLATE -> TemplateSelectionScreen(
                    onTemplateSelected = { template ->
                        selectedTemplate = template
                        currentStep = ImportStep.SELECT_FILES
                    }
                )
                ImportStep.SELECT_FILES -> FileSelectionScreen(
                    selectedTemplate = selectedTemplate!!,
                    membersFileUri = membersFileUri,
                    savingsFileUri = savingsFileUri,
                    loansFileUri = loansFileUri,
                    onMembersFileClick = { membersFilePicker.launch(arrayOf("text/csv", "*/*")) },
                    onSavingsFileClick = { savingsFilePicker.launch(arrayOf("text/csv", "*/*")) },
                    onLoansFileClick = { loansFilePicker.launch(arrayOf("text/csv", "*/*")) },
                    onDownloadMembersTemplate = { membersTemplateLauncher.launch("members_import_template.csv") },
                    onDownloadSavingsTemplate = { savingsTemplateLauncher.launch("savings_import_template.csv") },
                    onDownloadLoansTemplate = { loansTemplateLauncher.launch("loans_import_template.csv") },
                    onValidateClick = { runDryRun() },
                    onBackClick = { currentStep = ImportStep.SELECT_TEMPLATE }
                )
                ImportStep.VALIDATION_ERRORS -> ValidationErrorsScreen(
                    errors = dryRunResult?.errors ?: emptyList(),
                    onBackClick = { currentStep = ImportStep.SELECT_FILES }
                )
                ImportStep.PREVIEW -> PreviewScreen(
                    preview = dryRunResult?.preview!!,
                    onBackClick = { currentStep = ImportStep.SELECT_FILES },
                    onConfirmClick = { runCommit() }
                )
                ImportStep.SUCCESS -> SuccessScreen(
                    result = importResult!!,
                    onDownloadCredentials = {
                        credentialsSheetLauncher.launch("member_credentials_summary.csv")
                    },
                    onDoneClick = {
                        currentStep = ImportStep.SELECT_TEMPLATE
                        selectedTemplate = null
                        membersFileUri = null
                        savingsFileUri = null
                        loansFileUri = null
                        dryRunResult = null
                        importResult = null
                    }
                )
            }
        }
    }
}

@Composable
private fun TemplateSelectionScreen(onTemplateSelected: (TemplateType) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.csv_import_select_template),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )
        Text(
            text = stringResource(R.string.csv_import_select_template_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)
        )

        TemplateCard(
            template = TemplateType.MEMBERS,
            onClick = { onTemplateSelected(TemplateType.MEMBERS) }
        )
        TemplateCard(
            template = TemplateType.SAVINGS,
            onClick = { onTemplateSelected(TemplateType.SAVINGS) }
        )
        TemplateCard(
            template = TemplateType.LOANS,
            onClick = { onTemplateSelected(TemplateType.LOANS) }
        )
    }
}

@Composable
private fun TemplateCard(template: TemplateType, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .width(400.dp)
            .padding(vertical = 8.dp)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
                    imageVector = template.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column {
                Text(
                    text = template.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = template.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun FileSelectionScreen(
    selectedTemplate: TemplateType,
    membersFileUri: Uri?,
    savingsFileUri: Uri?,
    loansFileUri: Uri?,
    onMembersFileClick: () -> Unit,
    onSavingsFileClick: () -> Unit,
    onLoansFileClick: () -> Unit,
    onDownloadMembersTemplate: () -> Unit,
    onDownloadSavingsTemplate: () -> Unit,
    onDownloadLoansTemplate: () -> Unit,
    onValidateClick: () -> Unit,
    onBackClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.csv_import_step_files),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // File pickers
        FilePickerRow(
            title = stringResource(R.string.csv_import_members_file),
            subtitle = stringResource(R.string.csv_import_members_file_desc),
            fileUri = membersFileUri,
            isRequired = true,
            onPickClick = onMembersFileClick,
            onDownloadTemplate = onDownloadMembersTemplate
        )

        if (selectedTemplate != TemplateType.MEMBERS || true) { // Always show for now
            FilePickerRow(
                title = stringResource(R.string.csv_import_savings_file),
                subtitle = stringResource(R.string.csv_import_savings_file_desc),
                fileUri = savingsFileUri,
                isRequired = false,
                onPickClick = onSavingsFileClick,
                onDownloadTemplate = onDownloadSavingsTemplate
            )

            FilePickerRow(
                title = stringResource(R.string.csv_import_loans_file),
                subtitle = stringResource(R.string.csv_import_loans_file_desc),
                fileUri = loansFileUri,
                isRequired = false,
                onPickClick = onLoansFileClick,
                onDownloadTemplate = onDownloadLoansTemplate
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Validate button
        Button(
            onClick = onValidateClick,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = stringResource(R.string.csv_import_validate_button),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
private fun FilePickerRow(
    title: String,
    subtitle: String,
    fileUri: Uri?,
    isRequired: Boolean,
    onPickClick: () -> Unit,
    onDownloadTemplate: () -> Unit
) {
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
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (isRequired) {
                            Text(
                                text = "*",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = onDownloadTemplate) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Download", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.csv_import_download_template))
                    }
                    Button(onClick = onPickClick) {
                        Icon(Icons.Default.Upload, contentDescription = "Pick", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.csv_import_pick_file))
                    }
                }
            }
            fileUri?.let { uri ->
                Text(
                    text = uri.lastPathSegment ?: uri.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ValidationErrorsScreen(
    errors: List<ValidationError>,
    onBackClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.csv_import_validation_errors),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = stringResource(R.string.csv_import_error_count, errors.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(errors) { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.csv_import_error_row, error.row),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = error.column,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Text(
                            text = error.reason,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Button(
            onClick = onBackClick,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Text(stringResource(R.string.csv_import_back_to_files))
        }
    }
}

@Composable
private fun PreviewScreen(
    preview: ImportPreview,
    onBackClick: () -> Unit,
    onConfirmClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.csv_import_preview_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = stringResource(R.string.csv_import_preview_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
        )

        // Preview summary cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PreviewStatCard(
                title = stringResource(R.string.csv_import_members_count),
                value = preview.memberCount.toString(),
                icon = Icons.Default.Description,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            PreviewStatCard(
                title = stringResource(R.string.csv_import_total_savings),
                value = String.format(Locale.getDefault(), "%,.2f", preview.totalSavingsBalance),
                icon = Icons.Default.Assessment,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f)
            )
            PreviewStatCard(
                title = stringResource(R.string.csv_import_total_loans),
                value = String.format(Locale.getDefault(), "%,.2f", preview.totalLoanAmount),
                icon = Icons.Default.AttachMoney,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )
        }

        // Username preview
        if (preview.memberUsernames.isNotEmpty()) {
            Text(
                text = stringResource(R.string.csv_import_generated_usernames),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    preview.memberUsernames.forEach { (memberId, username) ->
                        Text(
                            text = stringResource(R.string.csv_import_username_format, memberId, username),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onBackClick,
                modifier = Modifier.weight(1f).height(50.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(stringResource(R.string.csv_import_back))
            }
            Button(
                onClick = onConfirmClick,
                modifier = Modifier.weight(1f).height(50.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = stringResource(R.string.csv_import_confirm_button),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
private fun PreviewStatCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp),
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
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(color.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun SuccessScreen(
    result: ImportResult,
    onDownloadCredentials: () -> Unit,
    onDoneClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(40.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.csv_import_success_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = result.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .width(400.dp)
                .padding(horizontal = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.csv_import_credentials_sheet),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.csv_import_credentials_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                Button(
                    onClick = onDownloadCredentials,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.csv_import_download_credentials),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onDoneClick,
            modifier = Modifier.width(200.dp).height(50.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = stringResource(R.string.csv_import_done),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

enum class TemplateType(
    val displayName: String,
    val fileName: String,
    val description: String,
    val icon: ImageVector
) {
    MEMBERS(
        displayName = "Members",
        fileName = "members_import_template.csv",
        description = "Member_ID,Full_Name,Phone_Number,Address",
        icon = Icons.Default.Description
    ),
    SAVINGS(
        displayName = "Savings",
        fileName = "savings_import_template.csv",
        description = "Member_ID,Account_Number,Opening_Balance",
        icon = Icons.Default.Assessment
    ),
    LOANS(
        displayName = "Loans",
        fileName = "loans_import_template.csv",
        description = "Member_ID,Guarantor_Member_ID,Disbursed_Amount,Interest_Rate,Tenure_Months,Disbursement_Date,Current_Outstanding",
        icon = Icons.Default.AttachMoney
    )
}