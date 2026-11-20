package io.github.brealorg.subshell.ui.components

import io.github.brealorg.subshell.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.Modifier
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.brealorg.subshell.data.account.RedditModeratorActionRequest
import io.github.brealorg.subshell.data.account.RedditModeratorActionUiState
import io.github.brealorg.subshell.data.account.RedditModeratorQueueItem
import io.github.brealorg.subshell.data.account.RedditModeratorQueueKind
import io.github.brealorg.subshell.data.account.canConfirmModeratorSpam
import io.github.brealorg.subshell.data.account.moderatorSpamRequestOrNull
import io.github.brealorg.subshell.ui.symbols.Symbols

// MODERATION02A1_SHARED_EXPLICIT_SPAM_CONFIRMATION_V1
// Used by both the Inbox aggregate queue and the community Mod tools queue.
/**
 * The spam question, for the queue's Spam button and (ISSUE295_SPAM_ASKS_FROM_THE_SHEET_TOO_V1)
 * the Moderate sheet's Spam row: spam removes the item and trains Reddit's filter, so it is
 * never sent on one tap.
 */
@Composable
internal fun ModeratorSpamConfirmationDialog(
    kind: RedditModeratorQueueKind,
    confirmation: RedditModeratorActionRequest,
    summary: String?,
    canConfirm: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (kind) {
                    RedditModeratorQueueKind.POST -> stringResource(R.string.all_mark_this_post_as_spam)
                    RedditModeratorQueueKind.COMMENT -> stringResource(R.string.all_mark_this_comment_as_spam)
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "r/${confirmation.communityName} · ${confirmation.thingFullname}",
                    style = MaterialTheme.typography.labelLarge,
                )
                summary?.takeIf(String::isNotBlank)?.let { text ->
                    Text(
                        text,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    stringResource(R.string.all2_this_removes_the_item_and_marks_it),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = canConfirm) {
                Text(stringResource(R.string.comp_mark_as_spam))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.comp_cancel))
            }
        },
    )
}
