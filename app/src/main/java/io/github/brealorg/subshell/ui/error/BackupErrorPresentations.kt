package io.github.brealorg.subshell.ui.error

import io.github.brealorg.subshell.ui.UiText
import io.github.brealorg.subshell.R
import io.github.brealorg.subshell.backup.BackupFailureOperation
import io.github.brealorg.subshell.data.error.FailureDetails
import io.github.brealorg.subshell.data.error.LocalFailureKind
import io.github.brealorg.subshell.data.error.toSafeTechnicalDetail

internal fun backupErrorPresentation(
    operation: BackupFailureOperation,
    details: FailureDetails,
): ErrorPresentation {
    val title = when (operation) {
        BackupFailureOperation.CONNECT_FOLDER -> UiText.Resource(R.string.error_backup_couldn_t_connect_backup_folder)
        BackupFailureOperation.SYNC -> UiText.Resource(R.string.error_backup_couldn_t_sync_backup)
        BackupFailureOperation.IMPORT -> UiText.Resource(R.string.error_backup_couldn_t_import_backup)
        BackupFailureOperation.EXPORT -> UiText.Resource(R.string.error_backup_couldn_t_export_backup)
        BackupFailureOperation.SAVE -> UiText.Resource(R.string.error_backup_couldn_t_save_backup)
        BackupFailureOperation.RESTORE -> UiText.Resource(R.string.error_backup_couldn_t_restore_settings)
        BackupFailureOperation.RENAME -> UiText.Resource(R.string.error_backup_couldn_t_rename_saved_setup)
        BackupFailureOperation.DELETE -> UiText.Resource(R.string.error_backup_couldn_t_delete_saved_setup)
    }
    val message = when (details.localKind) {
        LocalFailureKind.PERMISSION -> UiText.Resource(R.string.error_backup_android_didn_t_grant_subshell_access_to)
        LocalFailureKind.VALIDATION -> UiText.Resource(R.string.error_backup_the_selected_backup_data_isn_t_valid)
        LocalFailureKind.IO -> UiText.Resource(R.string.error_backup_subshell_couldn_t_read_or_write_the_backup)
        LocalFailureKind.STORAGE -> UiText.Resource(R.string.error_backup_the_local_backup_store_couldn_t_complete)
        LocalFailureKind.SECURITY -> UiText.Resource(R.string.error_backup_the_backup_operation_was_blocked_by_a)
        LocalFailureKind.CONFIGURATION -> UiText.Resource(R.string.error_backup_the_backup_configuration_isn_t_valid)
        LocalFailureKind.DECODING -> UiText.Resource(R.string.error_backup_the_backup_data_couldn_t_be_decoded)
        LocalFailureKind.UNKNOWN,
        null,
        -> UiText.Resource(R.string.error_backup_the_backup_operation_failed_before_it_could)
    }
    return ErrorPresentation(
        kind = ErrorPresentationKind.DEGRADED,
        title = title,
        message = message,
        technicalDetail = details.toSafeTechnicalDetail(),
    )
}
