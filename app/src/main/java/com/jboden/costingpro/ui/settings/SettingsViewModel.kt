package com.jboden.costingpro.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jboden.costingpro.data.SettingsStore
import com.jboden.costingpro.export.BackupManager
import com.jboden.costingpro.export.InvalidBackupException
import com.jboden.costingpro.ui.common.Formats
import com.jboden.costingpro.ui.recipes.FieldError
import java.io.IOException
import java.math.BigDecimal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What happened with the last export or import, to tell the user. */
enum class BackupMessage { EXPORT_DONE, EXPORT_FAILED, IMPORT_DONE, IMPORT_INVALID }

data class SettingsUiState(
    /** The VAT field as typed. */
    val vatText: String,
    /** Why the typed VAT was not saved, or null if it was. */
    val vatError: FieldError?,
    /** An export or import is running. */
    val backupBusy: Boolean = false,
    /** The backup the user picked, while the "replace everything?" dialog is open. */
    val pendingImport: Uri? = null,
    val backupMessage: BackupMessage? = null,
)

class SettingsViewModel(
    private val settings: SettingsStore,
    /** Created only when the user exports or imports. */
    createBackup: () -> BackupManager,
) : ViewModel() {

    private val backup by lazy(createBackup)

    private data class BackupState(
        val busy: Boolean = false,
        val pendingImport: Uri? = null,
        val message: BackupMessage? = null,
    )

    private val vatText = MutableStateFlow(Formats.number(settings.defaultVatPct.value))
    private val backupState = MutableStateFlow(BackupState())

    val uiState: StateFlow<SettingsUiState> = combine(vatText, backupState) { text, backup ->
        SettingsUiState(
            vatText = text,
            vatError = vatError(text),
            backupBusy = backup.busy,
            pendingImport = backup.pendingImport,
            backupMessage = backup.message,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SettingsUiState(vatText.value, vatError = null),
    )

    /** Saves the VAT as soon as what is typed is valid. Until then the stored value stays as it was. */
    fun onVatChange(value: String) {
        vatText.value = value
        if (vatError(value) == null) {
            settings.setDefaultVatPct(Formats.parseDecimal(value)!!.toDouble())
        }
    }

    private fun vatError(text: String): FieldError? {
        val value = Formats.parseDecimal(text)
        return when {
            text.isBlank() -> FieldError.REQUIRED
            value == null -> FieldError.NUMBER
            value > BigDecimal(SettingsStore.MAX_VAT_PCT) -> FieldError.VAT_RANGE
            else -> null
        }
    }

    // ---------- Backup ----------

    /** The user chose where to save the backup, or closed the picker (null). */
    fun exportTo(uri: Uri?) {
        if (uri == null) return
        runBackup {
            try {
                backup.export(uri)
                BackupMessage.EXPORT_DONE
            } catch (e: IOException) {
                BackupMessage.EXPORT_FAILED
            } catch (e: SecurityException) {
                BackupMessage.EXPORT_FAILED
            }
        }
    }

    /** The user picked a backup to import, or closed the picker (null). Asks before replacing anything. */
    fun onImportPicked(uri: Uri?) {
        if (uri != null) backupState.update { it.copy(pendingImport = uri) }
    }

    fun cancelImport() = backupState.update { it.copy(pendingImport = null) }

    fun confirmImport() {
        val uri = backupState.value.pendingImport ?: return
        backupState.update { it.copy(pendingImport = null) }
        runBackup {
            try {
                backup.import(uri)
                // The backup brings its own default VAT.
                vatText.value = Formats.number(settings.defaultVatPct.value)
                BackupMessage.IMPORT_DONE
            } catch (e: InvalidBackupException) {
                BackupMessage.IMPORT_INVALID
            }
        }
    }

    fun dismissBackupMessage() = backupState.update { it.copy(message = null) }

    private fun runBackup(work: suspend () -> BackupMessage) {
        if (backupState.value.busy) return
        backupState.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val message = work()
            backupState.update { it.copy(busy = false, message = message) }
        }
    }

    companion object {
        fun factory(settings: SettingsStore, backup: () -> BackupManager): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(settings, backup) }
        }
    }
}
