package com.jboden.costingpro.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jboden.costingpro.R
import com.jboden.costingpro.ui.common.CostingTopBar
import com.jboden.costingpro.ui.common.costingFieldColors
import com.jboden.costingpro.ui.recipes.FieldError

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE),
        viewModel::exportTo,
    )
    // Some file providers report a .zip as a generic binary file.
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
        viewModel::onImportPicked,
    )
    fun launchSafely(launch: () -> Unit) {
        try {
            launch()
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.backup_export_failed, Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(topBar = { CostingTopBar(title = stringResource(R.string.tab_settings)) }) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_vat_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    OutlinedTextField(
                        value = state.vatText,
                        onValueChange = viewModel::onVatChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.settings_vat_label)) },
                        suffix = { Text(stringResource(R.string.percent_symbol)) },
                        supportingText = {
                            Text(
                                stringResource(
                                    state.vatError?.let { it.messageRes() } ?: R.string.settings_vat_hint,
                                ),
                            )
                        },
                        isError = state.vatError != null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        colors = costingFieldColors(),
                    )
                }
            }
            BackupCard(
                busy = state.backupBusy,
                onExport = {
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
                    launchSafely { exportLauncher.launch(context.getString(R.string.backup_file_name, date)) }
                },
                onImport = {
                    launchSafely { importLauncher.launch(arrayOf(BACKUP_MIME_TYPE, "application/octet-stream")) }
                },
            )
        }
    }

    if (state.pendingImport != null) {
        AlertDialog(
            onDismissRequest = viewModel::cancelImport,
            title = { Text(stringResource(R.string.backup_import_title)) },
            text = { Text(stringResource(R.string.backup_import_message)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmImport) {
                    Text(stringResource(R.string.backup_import_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelImport) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    state.backupMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissBackupMessage,
            text = { Text(stringResource(message.textRes())) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissBackupMessage) { Text(stringResource(R.string.action_understood)) }
            },
        )
    }
}

private const val BACKUP_MIME_TYPE = "application/zip"

@Composable
private fun BackupCard(busy: Boolean, onExport: () -> Unit, onImport: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.backup_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.backup_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onExport, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.backup_export))
                }
                OutlinedButton(onClick = onImport, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.backup_import))
                }
            }
            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.backup_working), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private fun BackupMessage.textRes(): Int = when (this) {
    BackupMessage.EXPORT_DONE -> R.string.backup_export_done
    BackupMessage.EXPORT_FAILED -> R.string.backup_export_failed
    BackupMessage.IMPORT_DONE -> R.string.backup_import_done
    BackupMessage.IMPORT_INVALID -> R.string.backup_import_invalid
}

private fun FieldError.messageRes(): Int = when (this) {
    FieldError.REQUIRED -> R.string.error_required
    FieldError.VAT_RANGE -> R.string.error_vat_range
    else -> R.string.error_number
}
