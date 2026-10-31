package com.example.financeapp.reports

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExporter {

    const val UTF8_BOM = "\uFEFF"
    const val CSV_MIME_TYPE = "text/csv"
    const val DATE_PATTERN = "yyyy-MM-dd"
    const val DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss"
    const val AMOUNT_PATTERN = "%.2f"

    fun escape(value: String?): String {
        val text = value ?: return ""
        val needsQuoting = text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuoting) return text
        return "\"" + text.replace("\"", "\"\"") + "\""
    }

    fun row(vararg values: String?): String =
        values.joinToString(",") { escape(it) }

    fun formatAmount(amount: Double?): String =
        amount?.let { String.format(Locale.US, AMOUNT_PATTERN, it) } ?: ""

    fun formatDate(epochMillis: Long?): String =
        formatDate(epochMillis, DATE_PATTERN)

    fun formatDateTime(epochMillis: Long?): String =
        formatDate(epochMillis, DATE_TIME_PATTERN)

    fun formatDate(epochMillis: Long?, pattern: String): String {
        if (epochMillis == null) return ""
        return SimpleDateFormat(pattern, Locale.US).format(Date(epochMillis))
    }

    fun buildCsv(rows: List<List<String?>>, includeBom: Boolean = true): String = buildString {
        if (includeBom) append(UTF8_BOM)
        rows.forEach { fields ->
            append(fields.joinToString(",") { escape(it) })
            append("\r\n")
        }
    }

    fun buildCsv(headers: List<String?>, rows: List<List<String?>>): String =
        buildCsv(listOf(headers) + rows)

    fun buildTemplate(template: CsvTemplate): String =
        buildCsv(template.rows)

    suspend fun writeToUri(
        context: Context,
        uri: Uri,
        rows: List<List<String?>>
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val outputStream = context.contentResolver.openOutputStream(uri, "wt")
                ?: return@withContext Result.failure(IOException("Unable to open destination file."))
            writeRows(outputStream, rows)
            Result.success(uri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun writeTemplateToUri(
        context: Context,
        uri: Uri,
        template: CsvTemplate
    ): Result<Uri> = writeToUri(context, uri, template.rows)

    fun writeToFile(file: File, rows: List<List<String?>>): Result<File> {
        return try {
            file.outputStream().use { writeRows(it, rows) }
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun writeRows(outputStream: OutputStream, rows: List<List<String?>>) {
        OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
            writer.write(UTF8_BOM)
            rows.forEach { fields ->
                writer.write(fields.joinToString(",") { escape(it) })
                writer.write("\r\n")
            }
        }
    }
}

enum class CsvTemplate(
    val fileName: String,
    val rows: List<List<String?>>
) {
    MEMBERS(
        fileName = "members_import_template.csv",
        rows = listOf(
            listOf("Member_ID", "Full_Name", "Phone_Number", "Address"),
            listOf("101", "John Doe", "9800000001", "Kathmandu"),
            listOf("102", "Jane Smith", "9800000002", "Pokhara")
        )
    ),
    SAVINGS(
        fileName = "savings_import_template.csv",
        rows = listOf(
            listOf("Member_ID", "Account_Number", "Opening_Balance"),
            listOf("101", "SAV-100101", "15000.00"),
            listOf("102", "SAV-100102", "25000.00")
        )
    ),
    LOANS(
        fileName = "loans_import_template.csv",
        rows = listOf(
            listOf(
                "Member_ID",
                "Guarantor_Member_ID",
                "Disbursed_Amount",
                "Interest_Rate",
                "Tenure_Months",
                "Disbursement_Date_YYYY_MM_DD",
                "Current_Principal_Outstanding"
            ),
            listOf("101", "102", "50000", "12.0", "12", "2026-01-15", "40000.00")
        )
    )
}

class CsvFileSaver internal constructor(
    private val launch: (fileName: String, rows: List<List<String?>>) -> Unit
) {
    fun save(fileName: String, rows: List<List<String?>>) {
        launch(fileName, rows)
    }

    fun save(template: CsvTemplate) {
        launch(template.fileName, template.rows)
    }
}

@Composable
fun rememberCsvFileSaver(
    onResult: (Result<Uri>) -> Unit
): CsvFileSaver {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pendingRows = remember { mutableStateOf<List<List<String?>>?>(null) }
    val currentOnResult by rememberUpdatedState(onResult)

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(CsvExporter.CSV_MIME_TYPE)
    ) { uri ->
        val rows = pendingRows.value
        pendingRows.value = null
        if (uri != null && rows != null) {
            scope.launch {
                currentOnResult(CsvExporter.writeToUri(context, uri, rows))
            }
        }
    }

    return remember(launcher) {
        CsvFileSaver { fileName, rows ->
            pendingRows.value = rows
            launcher.launch(fileName)
        }
    }
}
