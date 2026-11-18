package io.github.brealorg.subshell.ui.components

import io.github.brealorg.subshell.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.brealorg.subshell.ui.USER_TAG_LABEL_MAX_LENGTH
import io.github.brealorg.subshell.ui.USER_TAG_NOTE_MAX_LENGTH
import io.github.brealorg.subshell.ui.UserTag
import io.github.brealorg.subshell.ui.UserTagColor

// USERACTION01A1_PROFILE_BLOCK_CONFIRMATION_V1
@Composable
internal fun UserBlockConfirmationDialog(
    username: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.comp_block_u, username)) },
        text = {
            Text(
                stringResource(R.string.all2_reddit_will_add_this_account_to_your),
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(stringResource(R.string.comp_block))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.comp_cancel))
            }
        },
    )
}

// ISSUE34_USER_TAGS_V1
@Composable
internal fun UserTagEditorDialog(
    username: String,
    existing: UserTag?,
    onDismiss: () -> Unit,
    onSave: (UserTag?) -> Unit,
) {
    var label by rememberSaveable(username) { mutableStateOf(existing?.label.orEmpty()) }
    var colorName by rememberSaveable(username) {
        mutableStateOf((existing?.color ?: UserTagColor.GREY).name)
    }
    var note by rememberSaveable(username) { mutableStateOf(existing?.note.orEmpty()) }
    val color = UserTagColor.valueOf(colorName)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.comp_private_note_on_u, username)) },
        text = {
            UserTagEditorFields(
                label = label,
                onLabelChange = { label = it },
                color = color,
                onColorChange = { colorName = it.name },
                note = note,
                onNoteChange = { note = it },
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        UserTag(
                            label = label,
                            color = color,
                            note = note,
                            // TAGCOLOR01: an unknown stored colour survives
                            // unless the user picked a colour.
                            unknownColorName = existing?.unknownColorName
                                ?.takeIf { color == existing.color },
                        ),
                    )
                },
            ) {
                Text(stringResource(R.string.comp_save))
            }
        },
        dismissButton = {
            Row {
                if (existing != null) {
                    TextButton(
                        onClick = { onSave(null) },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text(stringResource(R.string.comp_remove))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.comp_cancel))
                }
            }
        },
    )
}

/** The editor's fields, stateless: tag (one line, capped), colour swatches, note (capped). */
@Composable
internal fun UserTagEditorFields(
    label: String,
    onLabelChange: (String) -> Unit,
    color: UserTagColor,
    onColorChange: (UserTagColor) -> Unit,
    note: String,
    onNoteChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.all_only_you_see_this_it_is_kept),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = label,
            onValueChange = { value ->
                onLabelChange(value.replace('\n', ' ').take(USER_TAG_LABEL_MAX_LENGTH))
            },
            label = { Text(stringResource(R.string.comp_tag)) },
            singleLine = true,
            supportingText = { Text(stringResource(R.string.comp_text_2, label.length, USER_TAG_LABEL_MAX_LENGTH)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            UserTagColor.entries.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { option ->
                        val colors = userTagChipColors(option)
                        val colorName = stringResource(userTagColorName(option))
                        val selected = option == color
                        // LOWUI7_TOUCH_TARGETS_ARE_48DP_V1 (OCB-165)
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(colors.container)
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                    shape = CircleShape,
                                )
                                .selectable(
                                    selected = selected,
                                    role = Role.RadioButton,
                                    onClick = { onColorChange(option) },
                                )
                                .semantics {
                                    contentDescription = colorName
                                },
                        )
                    }
                }
            }
        }
        OutlinedTextField(
            value = note,
            onValueChange = { value -> onNoteChange(value.take(USER_TAG_NOTE_MAX_LENGTH)) },
            label = { Text(stringResource(R.string.comp_note)) },
            minLines = 3,
            maxLines = 6,
            supportingText = { Text(stringResource(R.string.comp_text_2, note.length, USER_TAG_NOTE_MAX_LENGTH)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@androidx.annotation.StringRes
internal fun userTagColorName(color: UserTagColor): Int = when (color) {
    UserTagColor.GREY -> R.string.comp_tag_color_grey
    UserTagColor.RED -> R.string.comp_tag_color_red
    UserTagColor.ORANGE -> R.string.comp_tag_color_orange
    UserTagColor.YELLOW -> R.string.comp_tag_color_yellow
    UserTagColor.GREEN -> R.string.comp_tag_color_green
    UserTagColor.TEAL -> R.string.comp_tag_color_teal
    UserTagColor.BLUE -> R.string.comp_tag_color_blue
    UserTagColor.PURPLE -> R.string.comp_tag_color_purple
}
