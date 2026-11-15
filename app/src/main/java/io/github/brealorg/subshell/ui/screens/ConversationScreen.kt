package io.github.brealorg.subshell.ui.screens

import io.github.brealorg.subshell.R
import androidx.compose.ui.res.stringResource
import android.text.format.DateFormat
import androidx.compose.ui.layout.WindowInsetsRulers
import androidx.compose.foundation.layout.fitInside
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import io.github.brealorg.subshell.data.isValidRedditUsername
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import io.github.brealorg.subshell.data.account.MessageReplyUiState
import io.github.brealorg.subshell.data.account.RedditInboxUiState
import io.github.brealorg.subshell.data.account.MessageReplyPhase
import io.github.brealorg.subshell.data.account.DirectChatLiveUiState
import io.github.brealorg.subshell.data.account.mergeDirectChatLiveMessages
import io.github.brealorg.subshell.data.account.redditMatrixMediaIdFromMessageBody
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalContext
import io.github.brealorg.subshell.ui.components.pastedImageKeptInApp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.selection.SelectionContainer
import io.github.brealorg.subshell.model.InboxItem
import io.github.brealorg.subshell.model.MessageBubble
import io.github.brealorg.subshell.model.RemoteCommentMediaKind
import io.github.brealorg.subshell.model.projectCommentContent
import io.github.brealorg.subshell.ui.ComposerMediaAttachment
import io.github.brealorg.subshell.ui.components.CompactReplyComposer
import io.github.brealorg.subshell.ui.components.ComposerMediaReplacementDialog
import io.github.brealorg.subshell.ui.components.ErrorNotice
import io.github.brealorg.subshell.ui.components.RouteTopBar
import io.github.brealorg.subshell.ui.components.rememberRouteTopBarScrollBehavior
import io.github.brealorg.subshell.data.account.DirectChatLiveFailureKind
import io.github.brealorg.subshell.ui.UiText
import io.github.brealorg.subshell.ui.error.ErrorAction
import io.github.brealorg.subshell.ui.error.ErrorActionKind
import io.github.brealorg.subshell.ui.error.ErrorPresentation
import io.github.brealorg.subshell.ui.error.ErrorPresentationKind
import io.github.brealorg.subshell.ui.error.ReportViewportError
import io.github.brealorg.subshell.ui.error.LocalViewportErrorHostState
import io.github.brealorg.subshell.ui.error.TrayTone
import io.github.brealorg.subshell.ui.error.messageReplyErrorPresentation
import io.github.brealorg.subshell.ui.components.MarkdownText
import io.github.brealorg.subshell.ui.components.LocalUriMedia
import io.github.brealorg.subshell.ui.components.RemoteGifMedia
import io.github.brealorg.subshell.ui.components.RefreshableLazyColumn
import io.github.brealorg.subshell.ui.components.RemoteIdentityImage
import io.github.brealorg.subshell.ui.components.RemoteImage
import io.github.brealorg.subshell.ui.components.rememberComposerMediaPicker
import io.github.brealorg.subshell.ui.error.presentNotice
import io.github.brealorg.subshell.ui.symbols.Symbols
import io.github.brealorg.subshell.ui.theme.groupedRowShape
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import io.github.brealorg.subshell.data.account.DirectChatDeleteFailureKind
import io.github.brealorg.subshell.data.account.DirectChatReactionFailureKind
import io.github.brealorg.subshell.data.account.DirectChatReactionSummary
import io.github.brealorg.subshell.data.account.DirectChatReactionAsset
import io.github.brealorg.subshell.data.account.redditDirectChatReactionAsset
import io.github.brealorg.subshell.data.account.redditDirectChatReactionCatalog
import io.github.brealorg.subshell.data.account.directChatMatrixDeleteEventId
import io.github.brealorg.subshell.data.account.directChatMatrixReactionTargetEventId
import io.github.brealorg.subshell.data.account.directChatCachedReactionTargetEventId
import io.github.brealorg.subshell.data.account.directChatReactionSummaries
import io.github.brealorg.subshell.data.account.suppressRedactedDirectChatAuthoritativeMessages
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import io.github.brealorg.subshell.ui.components.InlineProgressIndicator

// SUBSHELL_INBOXM3A1_CONVERSATION_EXPRESSIVE_CHROME_PREVIEW_V1
// SUBSHELL_INBOXM3A2_UNIFIED_CONVERSATION_PRESENTATION_PREVIEW_V1
// SUBSHELL_INBOXM3A2R1_GROUPED_CONVERSATION_PRESENTATION_PREVIEW_V1
// SUBSHELL_INBOXM3A2R2_COMPACT_GROUP_PRESENTATION_PREVIEW_V1
private const val MESSAGES02B_TYPED_DETAIL_V1 = "typed-direct-chat-private-message-detail"
private const val MAX_INLINE_MESSAGE_IMAGES = 4
private const val DIRECT_CHAT_LIVE_TAIL_FOLLOW_SLOP_ITEMS = 3
// SUBSHELL_MESSAGES02E2A_R2R1_REACTION_PICKER_PRESENTATION_V1
private const val DIRECT_CHAT_REACTION_PICKER_COLUMNS = 4
internal const val DIRECT_CHAT_MESSAGE_ACTIONS_LABEL = "Message actions"

enum class ConversationDetailKind {
    DIRECT_CHAT,
    PRIVATE_MESSAGE_THREAD,
}

internal enum class ConversationReplyMediaCapability {
    TEXT_ONLY,
    IMAGE_AND_GIF,
}

internal data class OptimisticConversationMedia(
    val uri: String,
    val mimeType: String,
)

// SUBSHELL_MESSAGES02C6R5R1_CONFIRMATION_CONTINUITY_V1
internal fun optimisticConversationMedia(
    kind: ConversationDetailKind,
    state: MessageReplyUiState,
): OptimisticConversationMedia? {
    if (kind != ConversationDetailKind.DIRECT_CHAT) return null
    val phaseSupportsLocalBacking =
        state.phase == MessageReplyPhase.SENDING ||
            (
                state.sentHadMedia &&
                    state.completionRevision > 0L &&
                    (
                        state.phase == MessageReplyPhase.SUCCEEDED ||
                            state.phase == MessageReplyPhase.FAILED ||
                            state.phase == MessageReplyPhase.OUTCOME_UNKNOWN
                        )
                )
    if (!phaseSupportsLocalBacking) return null
    val uri = state.optimisticMediaUri
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: return null
    val mimeType = state.optimisticMediaMimeType
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: return null
    return OptimisticConversationMedia(uri = uri, mimeType = mimeType)
}

internal fun conversationReplyMediaCapability(
    kind: ConversationDetailKind,
): ConversationReplyMediaCapability = when (kind) {
    // SUBSHELL_MESSAGES02C5_DIRECT_CHAT_MEDIA_CAPABILITY_FAIL_CLOSED_V1
    ConversationDetailKind.DIRECT_CHAT -> ConversationReplyMediaCapability.IMAGE_AND_GIF
    ConversationDetailKind.PRIVATE_MESSAGE_THREAD ->
        ConversationReplyMediaCapability.IMAGE_AND_GIF
}

internal fun conversationReplyHasSendableContent(
    kind: ConversationDetailKind,
    text: CharSequence,
    hasMedia: Boolean,
): Boolean =
    text.isNotBlank() ||
        (
            hasMedia &&
                conversationReplyMediaCapability(kind) ==
                ConversationReplyMediaCapability.IMAGE_AND_GIF
            )

// SUBSHELL_MESSAGES02D2A_INBOUND_TYPING_UI_V1
internal fun directChatPeerTypingLabel(
    kind: ConversationDetailKind,
    participantUsername: String?,
    liveState: DirectChatLiveUiState,
): String? {
    if (kind != ConversationDetailKind.DIRECT_CHAT || !liveState.peerTyping) return null
    val participant = participantUsername?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val username = if (participant.startsWith("u/", ignoreCase = true)) {
        participant.drop(2)
    } else {
        participant
    }.takeIf(String::isNotBlank) ?: return null
    return "u/$username is typing…"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    item: InboxItem,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    writeEnabled: Boolean = true,
    isRefreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    participantAvatarUri: String? = null,
    ownAvatarUri: String? = null,
    ownUsername: String? = null,
    accountStableId: String? = null,
    replyText: String = "",
    onReplyTextChange: (String) -> Unit = {},
    replyMedia: ComposerMediaAttachment? = null,
    onReplyMediaChange: (ComposerMediaAttachment?) -> Unit = {},
    replyParentFullname: String? = null,
    messageReplyState: MessageReplyUiState = MessageReplyUiState(),
    directChatLiveState: DirectChatLiveUiState = DirectChatLiveUiState(),
    directChatParticipantUsername: String? = null,
    onStartDirectChatLive: (String, String, String) -> Unit = { _, _, _ -> },
    onStopDirectChatLive: (String, String) -> Unit = { _, _ -> },
    // SUBSHELL_MESSAGES02E2A_DIRECT_CHAT_REACTION_WRITE_V1
    onReactDirectChatMessage: (String, String) -> Unit = { _, _ -> },
    onResetDirectChatReactionFailure: () -> Unit = {},
    onDeleteDirectChatMessage: (String) -> Unit = {},
    onResetDirectChatDeleteFailure: () -> Unit = {},
    onSendPrivateMessageReply: (String, ComposerMediaAttachment?) -> Unit = { _, _ -> },
    onReconnectInbox: () -> Unit = {},
    // ISSUE303B_AN_EXPIRED_WEB_SIGN_IN_OFFERS_SIGN_IN_AGAIN_V1: reconnects the
    // account in the app's login window, which renews Reddit's web sign-in
    // (the inbox authorization only refreshes when the OAuth grant is valid).
    onSignInAgain: () -> Unit = {},
    onAuthorizeMediaReply: () -> Unit = {},
    interactionActionsAtStart: Boolean = false,
    onResetMessageReplyState: () -> Unit = {},
    onRefreshInboxAfterSuccessfulDirectChatExit: () -> Unit = {},
    // CONVPROFILE01_TAP_PARTICIPANT_OPENS_PROFILE_V1
    onOpenUser: ((String) -> Unit)? = null,
) {
    val authoritativeMessages = item.conversation.ifEmpty {
        listOf(
            MessageBubble(
                id = item.id,
                sender = item.sender,
                age = item.age,
                body = item.preview.ifBlank { item.title },
            ),
        )
    }
    val detailKind = conversationDetailKind(item)
    val scopedReplyState = accountStableId
        ?.takeIf(String::isNotBlank)
        ?.takeIf { account -> messageReplyState.matches(account, item.id) }
        ?.let { messageReplyState }
        ?: MessageReplyUiState()
    val scopedDirectChatLiveState = if (
        detailKind == ConversationDetailKind.DIRECT_CHAT &&
        !accountStableId.isNullOrBlank() &&
        !directChatParticipantUsername.isNullOrBlank() &&
        directChatLiveState.matches(
            accountStableId = accountStableId,
            inboxItemId = item.id,
            participantUsername = directChatParticipantUsername,
        )
    ) {
        directChatLiveState
    } else {
        DirectChatLiveUiState()
    }
    val peerTypingLabel = directChatPeerTypingLabel(
        kind = detailKind,
        participantUsername = directChatParticipantUsername,
        liveState = scopedDirectChatLiveState,
    )
    var directChatDeleteCandidateEventId by rememberSaveable(item.id) {
        mutableStateOf<String?>(null)
    }
    var directChatReactionCandidateEventId by rememberSaveable(item.id) {
        mutableStateOf<String?>(null)
    }
    // SUBSHELL_MESSAGES02D3B_DELETE_OWN_DIRECT_CHAT_MESSAGE_V1
    val displayAuthoritativeMessages = if (detailKind == ConversationDetailKind.DIRECT_CHAT) {
        suppressRedactedDirectChatAuthoritativeMessages(
            authoritative = authoritativeMessages,
            live = scopedDirectChatLiveState.messages,
            redactedEventIds = scopedDirectChatLiveState.redactedMessageIds,
        )
    } else {
        authoritativeMessages
    }
    val liveMessages = scopedDirectChatLiveState.messages
        .filterNot { message -> message.id in scopedDirectChatLiveState.redactedMessageIds }
        .filterNot { message ->
            shouldSuppressDirectChatLiveMessageForLocalMedia(
                message = message,
                state = scopedReplyState,
            )
        }
    val messages = if (detailKind == ConversationDetailKind.DIRECT_CHAT) {
        mergeDirectChatLiveMessages(
            authoritative = displayAuthoritativeMessages,
            live = liveMessages,
        )
    } else {
        authoritativeMessages
    }
    // SUBSHELL_MESSAGES02C7_FOREGROUND_MATRIX_DIRECT_CHAT_LIVE_READ_V1
    // Only Reddit's normal inbox projection clears the C6R5R1 local media backing. A live
    // Matrix m.image is server-authentic but intentionally suppressed while that local URI is
    // carrying the visual continuity across upload confirmation.
    var outgoingIdsBeforeSend by remember(accountStableId, item.id) { mutableStateOf<Set<String>?>(null) }
    val confirmedReplyVisible = messageReplyAppearsInConversation(
        messages = authoritativeMessages,
        state = scopedReplyState,
        outgoingIdsBeforeSend = outgoingIdsBeforeSend,
    )
    val liveTextReplyVisible = !scopedReplyState.sentHadMedia &&
        messageReplyAppearsInConversation(
            messages = liveMessages,
            state = scopedReplyState,
            outgoingIdsBeforeSend = outgoingIdsBeforeSend,
        )
    val localTextEcho = scopedReplyState.sentText
        ?.takeIf(String::isNotBlank)
        ?.takeIf {
            scopedReplyState.phase == MessageReplyPhase.SUCCEEDED &&
                !scopedReplyState.sentHadMedia &&
                !confirmedReplyVisible &&
                !liveTextReplyVisible
        }
        ?.let { sentText ->
            MessageBubble(
                id = "messages02c-local-${scopedReplyState.completionRevision}",
                sender = "you",
                age = "now",
                body = sentText,
                outgoing = true,
                createdEpochSeconds = scopedReplyState.completedAtEpochSeconds,
            )
        }
    val optimisticMedia = optimisticConversationMedia(
        kind = detailKind,
        state = scopedReplyState,
    ).takeUnless { confirmedReplyVisible }
    val optimisticMediaEcho = optimisticMedia?.let { media ->
        MessageBubble(
            id = "messages02c-optimistic-${media.uri.hashCode()}",
            sender = "you",
            age = "now",
            body = "",
            outgoing = true,
        )
    }
    val localMatrixMediaEcho = scopedReplyState.sentMediaUri
        ?.takeIf(String::isNotBlank)
        ?.takeIf {
            scopedReplyState.phase == MessageReplyPhase.SUCCEEDED &&
                scopedReplyState.sentHadMedia &&
                !confirmedReplyVisible
        }
        ?.let { mediaUri ->
            MessageBubble(
                id = scopedReplyState.sentMatrixEventId
                    ?.takeIf(String::isNotBlank)
                    ?: "messages02c-matrix-${scopedReplyState.completionRevision}",
                sender = "you",
                age = "now",
                body = "sent an image\n$mediaUri",
                outgoing = true,
                createdEpochSeconds = scopedReplyState.completedAtEpochSeconds,
            )
        }
    val localEcho = optimisticMediaEcho ?: localMatrixMediaEcho ?: localTextEcho
    val displayMessages = if (localEcho != null) {
        messages + localEcho
    } else {
        messages
    }
    val messageGroups = conversationMessageGroups(displayMessages)
    val listState = rememberLazyListState()
    var initialLatestScrollComplete by remember(item.id) { mutableStateOf(false) }
    var successfulDirectChatReplyObserved by remember(item.id) { mutableStateOf(false) }
    var directChatLiveActivityObserved by remember(item.id) { mutableStateOf(false) }
    var followDirectChatLiveTail by remember(item.id) { mutableStateOf(true) }
    val latestScopedReplyState by rememberUpdatedState(scopedReplyState)
    val latestSuccessfulDirectChatReplyObserved by
        rememberUpdatedState(successfulDirectChatReplyObserved)
    val latestDirectChatLiveActivityObserved by
        rememberUpdatedState(directChatLiveActivityObserved)
    val latestRefreshInboxAfterSuccessfulDirectChatExit by
        rememberUpdatedState(onRefreshInboxAfterSuccessfulDirectChatExit)
    val latestStartDirectChatLive by rememberUpdatedState(onStartDirectChatLive)
    val latestStopDirectChatLive by rememberUpdatedState(onStopDirectChatLive)
    // LOWUI5_CONVERSATION_IME_READ_OUTSIDE_COMPOSITION_V1 (OCB-194): the IME
    // inset is read in the effect below, not during composition, so its
    // animation does not recompose the whole conversation frame by frame.
    val imeInsets = WindowInsets.ime
    val density = LocalDensity.current
    val latestListIndex = latestConversationListIndex(
        messageCount = messageGroups.size,
        hasLocalEcho = false,
    )
    val hostView = LocalView.current
    val hostLifecycle = remember(hostView) {
        hostView.findViewTreeLifecycleOwner()?.lifecycle
    }

    DisposableEffect(
        detailKind,
        accountStableId,
        item.id,
        directChatParticipantUsername,
        hostLifecycle,
    ) {
        val account = accountStableId?.trim().orEmpty()
        val participant = directChatParticipantUsername?.trim().orEmpty()
        if (
            detailKind != ConversationDetailKind.DIRECT_CHAT ||
            account.isEmpty() ||
            participant.isEmpty() ||
            hostLifecycle == null
        ) {
            onDispose { }
        } else {
            fun startLive() {
                latestStartDirectChatLive(account, item.id, participant)
            }
            fun stopLive() {
                latestStopDirectChatLive(account, item.id)
            }
            if (hostLifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                startLive()
            }
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> startLive()
                    Lifecycle.Event.ON_STOP -> stopLive()
                    else -> Unit
                }
            }
            hostLifecycle.addObserver(observer)
            onDispose {
                hostLifecycle.removeObserver(observer)
                stopLive()
            }
        }
    }

    LaunchedEffect(scopedDirectChatLiveState.contentRevision) {
        if (
            scopedDirectChatLiveState.contentRevision > 0L &&
            scopedDirectChatLiveState.messages.isNotEmpty()
        ) {
            directChatLiveActivityObserved = true
            if (followDirectChatLiveTail) {
                latestListIndex?.let { targetIndex ->
                    scrollConversationTailIntoView(
                        listState = listState,
                        targetIndex = targetIndex,
                    )
                }
            }
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val totalCount = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index
            totalCount == 0 ||
                lastVisible == null ||
                lastVisible >= (totalCount - DIRECT_CHAT_LIVE_TAIL_FOLLOW_SLOP_ITEMS)
        }
            .distinctUntilChanged()
            .collectLatest { atTail -> followDirectChatLiveTail = atTail }
    }

    LaunchedEffect(
        liveTextReplyVisible,
        scopedReplyState.phase,
        scopedReplyState.completionRevision,
    ) {
        if (
            liveTextReplyVisible &&
            scopedReplyState.phase == MessageReplyPhase.SUCCEEDED &&
            !scopedReplyState.sentHadMedia
        ) {
            onResetMessageReplyState()
        }
    }

    // ISSUE99_UNKNOWN_REPLY_READ_BACK_V1: a reply whose answer was lost counts
    // as sent once it shows up in the thread (own message, same text, around
    // the send time): the text is cleared and the warning goes, as on success.
    // Until then Send stays blocked and nothing is re-sent.
    LaunchedEffect(
        scopedReplyState.phase,
        scopedReplyState.completionRevision,
        confirmedReplyVisible,
        liveTextReplyVisible,
    ) {
        if (
            scopedReplyState.phase == MessageReplyPhase.OUTCOME_UNKNOWN &&
            !scopedReplyState.sentHadMedia &&
            (confirmedReplyVisible || liveTextReplyVisible)
        ) {
            val sent = scopedReplyState.sentDraftText ?: scopedReplyState.sentText
            if (sent != null && replyText.trim() == sent.trim()) onReplyTextChange("")
            onResetMessageReplyState()
        } else if (
            // ISSUE303_AN_UNKNOWN_IMAGE_SEND_KEEPS_ITS_MEDIA_ID_V1: an image whose
            // send answer was unclear counts as sent once Reddit lists an image
            // with its media id; the text in the composer was never sent and stays.
            scopedReplyState.phase == MessageReplyPhase.OUTCOME_UNKNOWN &&
            scopedReplyState.sentHadMedia &&
            scopedReplyState.sentMatrixEventId.isNullOrBlank() &&
            !scopedReplyState.sentMatrixMediaId.isNullOrBlank() &&
            confirmedReplyVisible
        ) {
            onResetMessageReplyState()
        }
    }

    LaunchedEffect(item.id, messages.size) {
        if (!initialLatestScrollComplete && messages.isNotEmpty()) {
            // LOWUI8_CONVERSATION_OPENS_AT_THE_NEWEST_MESSAGE_V1 (OCB-195): the
            // list holds message groups, and a group taller than the screen
            // showed its top; land the newest message like every other tail.
            latestListIndex?.let { targetIndex ->
                scrollConversationTailIntoView(listState = listState, targetIndex = targetIndex)
            }
            initialLatestScrollComplete = true
        }
    }

    LaunchedEffect(
        scopedReplyState.phase,
        scopedReplyState.completionRevision,
        scopedReplyState.sentText,
        scopedReplyState.sentHadMedia,
        replyText,
    ) {
        val submittedText = scopedReplyState.sentDraftText ?: scopedReplyState.sentText
        if (
            scopedReplyState.phase == MessageReplyPhase.SUCCEEDED &&
            submittedText != null &&
            replyText.trim() == submittedText.trim()
        ) {
            onReplyTextChange("")
        }
    }

    LaunchedEffect(
        detailKind,
        scopedReplyState.phase,
        scopedReplyState.completionRevision,
    ) {
        if (
            detailKind == ConversationDetailKind.DIRECT_CHAT &&
            scopedReplyState.completionRevision > 0L &&
            (
                scopedReplyState.phase == MessageReplyPhase.SUCCEEDED ||
                    scopedReplyState.sentHadMedia
                )
        ) {
            successfulDirectChatReplyObserved = true
        }
    }

    LaunchedEffect(
        scopedReplyState.phase,
        scopedReplyState.completionRevision,
        scopedReplyState.sentHadMedia,
    ) {
        if (
            scopedReplyState.sentHadMedia &&
            scopedReplyState.completionRevision > 0L &&
            scopedReplyState.phase != MessageReplyPhase.SENDING
        ) {
            // The Matrix writer now supplies a server-confirmed stable mxc/download echo. Keep
            // refreshing the Reddit inbox projection so the temporary echo is replaced by the
            // normal cross-client thread item as soon as Reddit's legacy projection catches up.
            onRefresh?.invoke()
        }
    }

    DisposableEffect(item.id, accountStableId, detailKind) {
        onDispose {
            if (
                shouldRefreshInboxAfterDirectChatExit(
                    kind = detailKind,
                    successfulReplyObserved = latestSuccessfulDirectChatReplyObserved,
                    liveActivityObserved = latestDirectChatLiveActivityObserved,
                    state = latestScopedReplyState,
                )
            ) {
                latestRefreshInboxAfterSuccessfulDirectChatExit()
            }
        }
    }

    LaunchedEffect(
        confirmedReplyVisible,
        scopedReplyState.phase,
        scopedReplyState.completionRevision,
        messages.lastOrNull()?.id,
    ) {
        if (
            confirmedReplyVisible &&
            scopedReplyState.phase == MessageReplyPhase.SUCCEEDED
        ) {
            // SUBSHELL_MESSAGES02C6R4_DIRECT_CHAT_TAIL_FOLLOW_V1
            // Reconciliation replaces the server-confirmed Matrix echo with a different
            // authoritative Reddit/Matrix projection key. Keep the just-sent tail visible
            // before clearing reply state instead of leaving the replacement below viewport.
            latestConversationListIndex(
                messageCount = conversationMessageGroups(messages).size,
                hasLocalEcho = false,
            )?.let { targetIndex ->
                scrollConversationTailIntoView(
                    listState = listState,
                    targetIndex = targetIndex,
                )
            }
            onResetMessageReplyState()
        }
    }

    LaunchedEffect(localEcho?.id) {
        if (localEcho != null) {
            latestListIndex?.let { targetIndex ->
                scrollConversationTailIntoView(
                    listState = listState,
                    targetIndex = targetIndex,
                )
            }
        }
    }

    // LOWUX3_KEYBOARD_FOLLOWS_THE_TAIL_ONLY_WHILE_OPENING_V1 (OCB-196): keyed on
    // the tail index, every new message restarted this and pulled a reader who
    // had scrolled up back to the bottom while the keyboard was open; the tail
    // is followed only while the keyboard grows.
    val latestTailIndex by rememberUpdatedState(latestListIndex)
    LaunchedEffect(item.id) {
        var previousImeBottomPx = 0
        snapshotFlow { imeInsets.getBottom(density) }.collect { imeBottomPx ->
            val opening = imeBottomPx > previousImeBottomPx
            previousImeBottomPx = imeBottomPx
            val tail = latestTailIndex
            if (opening && tail != null) {
                scrollConversationTailIntoView(
                    listState = listState,
                    targetIndex = tail,
                )
            }
        }
    }

    LaunchedEffect(
        scopedDirectChatLiveState.redactedMessageIds,
        scopedDirectChatLiveState.deletingEventId,
        directChatDeleteCandidateEventId,
    ) {
        val candidate = directChatDeleteCandidateEventId
        if (
            candidate != null &&
            candidate in scopedDirectChatLiveState.redactedMessageIds &&
            scopedDirectChatLiveState.deletingEventId != candidate
        ) {
            directChatDeleteCandidateEventId = null
        }
    }

    directChatReactionCandidateEventId?.let { eventId ->
        DirectChatReactionPickerSheet(
            reacting = scopedDirectChatLiveState.reactingEventId == eventId,
            onDismiss = { directChatReactionCandidateEventId = null },
            onSelect = { key ->
                directChatReactionCandidateEventId = null
                onReactDirectChatMessage(eventId, key)
            },
        )
    }

    // ISSUE222_REACTION_FAILURE_IN_THE_TRAY_V1: an error is never a dialog
    // (LOWUI4_NO_DIALOG_ERROR_KIND_V1); the failure goes to the tray and the
    // state is reset at once, so the same failure can show again later.
    val reactionFailureKind = scopedDirectChatLiveState.reactionFailureKind
    val reactionNoticeHost = LocalViewportErrorHostState.current
    LaunchedEffect(reactionFailureKind) {
        val failureKind = reactionFailureKind ?: return@LaunchedEffect
        reactionNoticeHost?.presentNotice(
            scopeKey = DIRECT_CHAT_REACTION_NOTICE_SCOPE,
            title = UiText.Resource(R.string.ep_reaction_not_confirmed),
            message = UiText.Plain(directChatReactionFailureMessage(failureKind)),
            tone = TrayTone.ERROR,
        )
        onResetDirectChatReactionFailure()
    }

    // ISSUE303B_AN_EXPIRED_WEB_SIGN_IN_OFFERS_SIGN_IN_AGAIN_V1: when opening the
    // chat finds Reddit's web sign-in expired (a 401 on a just-minted token),
    // the tray says so once, with Sign in again; it goes when the chat leaves
    // the screen or live chat starts again.
    ReportViewportError(
        scopeKey = DIRECT_CHAT_WEB_SIGN_IN_SCOPE,
        presentation = directChatWebSignInExpiredPresentation(
            directChat = detailKind == ConversationDetailKind.DIRECT_CHAT,
            failureKind = scopedDirectChatLiveState.failureKind,
        ),
        failureDetails = null,
        onRetry = onSignInAgain,
    )

    directChatDeleteCandidateEventId?.let { eventId ->
        DirectChatDeleteDialog(
            deleting = scopedDirectChatLiveState.deletingEventId == eventId,
            failureKind = scopedDirectChatLiveState.deleteFailureKind,
            onDismiss = {
                if (scopedDirectChatLiveState.deletingEventId == null) {
                    directChatDeleteCandidateEventId = null
                    onResetDirectChatDeleteFailure()
                }
            },
            onConfirm = { onDeleteDirectChatMessage(eventId) },
        )
    }

    val routeTopBarScroll = rememberRouteTopBarScrollBehavior()
    Column(
        modifier
            .fillMaxSize()
            .navigationBarsPadding()
            // COMPOSER232_COMPOSER_SITS_ON_THE_KEYBOARD_V1 (#232): the thread and
            // its composer fit above the keyboard by the IME ruler.
            .fitInside(WindowInsetsRulers.Ime.current),
    ) {
        RouteTopBar(
            scrollBehavior = routeTopBarScroll,
            title = {
                CompositionLocalProvider(LocalConversationOpenUser provides onOpenUser) {
                    ConversationHeaderIdentity(
                        item = item,
                        kind = detailKind,
                        avatarUri = participantAvatarUri,
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Symbols.ArrowBack,
                        contentDescription = stringResource(R.string.scr_back),
                    )
                }
            },
        )

        RefreshableLazyColumn(
            // ISSUE273_ONLY_THE_THREAD_MOVES_THE_TOP_BAR_V1: the reply field
            // below the thread does not move the bar.
            modifier = Modifier
                .weight(1f)
                .nestedScroll(routeTopBarScroll.nestedScrollConnection),
            state = listState,
            // SUBSHELL_INBOXM3A2_COMPOSER_SEAM_REMOVAL_V1
            contentPadding = PaddingValues(
                start = 14.dp,
                top = 8.dp,
                end = 14.dp,
                bottom = 0.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            enabled = onRefresh != null,
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
        ) {
            itemsIndexed(messageGroups, key = { _, group -> group.key }) { groupIndex, group ->
                val previousGroup = messageGroups.getOrNull(groupIndex - 1)
                val firstMessage = group.messages.first()
                val startsNewDay = previousGroup == null ||
                    conversationDayKey(previousGroup.messages.last()) !=
                    conversationDayKey(firstMessage)

                if (startsNewDay) {
                    ConversationDayDivider(
                        label = conversationDayLabel(firstMessage),
                    )
                }

                CompositionLocalProvider(LocalConversationOpenUser provides onOpenUser) {
                    when (detailKind) {
                        ConversationDetailKind.DIRECT_CHAT -> {
                            DirectChatMessageGroup(
                                group = group,
                                participantAvatarUri = participantAvatarUri,
                                ownAvatarUri = ownAvatarUri,
                                ownUsername = ownUsername,
                                writeEnabled = writeEnabled,
                                liveState = scopedDirectChatLiveState,
                                optimisticMediaEchoId = optimisticMediaEcho?.id,
                                optimisticMedia = optimisticMedia,
                                localEchoId = localEcho?.id,
                                localEchoSending =
                                    scopedReplyState.phase == MessageReplyPhase.SENDING,
                                onReactCandidate = { eventId ->
                                    directChatReactionCandidateEventId = eventId
                                },
                                onDeleteCandidate = { eventId ->
                                    directChatDeleteCandidateEventId = eventId
                                },
                            )
                        }

                        ConversationDetailKind.PRIVATE_MESSAGE_THREAD -> {
                            PrivateMessageThreadGroup(
                                group = group,
                                participantAvatarUri = participantAvatarUri,
                                ownAvatarUri = ownAvatarUri,
                                ownUsername = ownUsername,
                            )
                        }
                    }
                }
            }
        }

        if (writeEnabled) {
            peerTypingLabel?.let { label ->
                Text(
                    text = label,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ISSUE55_COMPOSER_KEYED_ON_CONVERSATION_V1: the two-pane inbox swaps
            // the conversation in place; the reply field (attachment included)
            // belongs to one account and conversation, so an image attached in
            // one chat never follows to the next.
            key(accountStableId, item.id) {
                MessageConversationComposer(
                    kind = detailKind,
                    text = replyText,
                    replyTargetAvailable = !replyParentFullname.isNullOrBlank(),
                    state = scopedReplyState,
                    onTextChange = onReplyTextChange,
                    initialMedia = replyMedia,
                    onMediaChange = onReplyMediaChange,
                    onSend = { text, media ->
                        // OCB-199: the outgoing messages already here when the
                        // reply is sent; any other one is new.
                        outgoingIdsBeforeSend = (authoritativeMessages + liveMessages)
                            .filter { message -> message.outgoing }
                            .map { message -> message.id }
                            .toSet()
                        onSendPrivateMessageReply(text, media)
                    },
                    onReconnectInbox = onReconnectInbox,
                    onSignInAgain = onSignInAgain,
                    onAuthorizeMediaReply = onAuthorizeMediaReply,
                    onRefresh = onRefresh,
                    onResetState = onResetMessageReplyState,
                    interactionActionsAtStart = interactionActionsAtStart,
                    longReplyTitle = "Reply to ${conversationParticipantLabel(item.sender)}",
                    quote = (authoritativeMessages + liveMessages)
                        .lastOrNull { message -> !message.outgoing }
                        ?.body,
                    blockedElsewhereMessage = messageReplyBlockedElsewhereMessage(
                        global = messageReplyState,
                        scoped = scopedReplyState,
                    ),
                )
            }
        }
    }
}

internal fun latestConversationListIndex(
    messageCount: Int,
    hasLocalEcho: Boolean,
): Int? {
    require(messageCount >= 0) { "Message count cannot be negative" }
    if (messageCount == 0 && !hasLocalEcho) return null
    return if (hasLocalEcho) messageCount else messageCount - 1
}

private suspend fun scrollConversationTailIntoView(
    listState: LazyListState,
    targetIndex: Int,
) {
    listState.scrollToItem(targetIndex)

    // The local/authoritative GIF item can change measured height after its remote
    // aspect ratio resolves. Re-check the actual measured tail for two frames and
    // consume only the overflow below the viewport.
    repeat(2) {
        withFrameNanos { }
        val layoutInfo = listState.layoutInfo
        val target = layoutInfo.visibleItemsInfo
            .firstOrNull { item -> item.index == targetIndex }
            ?: return@repeat
        val overflowPx =
            target.offset + target.size - layoutInfo.viewportEndOffset
        if (overflowPx > 0) {
            listState.scrollBy(overflowPx.toFloat())
        }
    }
}

internal fun shouldRefreshInboxAfterDirectChatExit(
    kind: ConversationDetailKind,
    successfulReplyObserved: Boolean,
    state: MessageReplyUiState,
    liveActivityObserved: Boolean = false,
): Boolean =
    kind == ConversationDetailKind.DIRECT_CHAT &&
        (
            successfulReplyObserved ||
                liveActivityObserved ||
                (
                    state.phase == MessageReplyPhase.SUCCEEDED &&
                        state.completionRevision > 0L
                    )
            )

@Composable
private fun MessageConversationComposer(
    kind: ConversationDetailKind,
    text: String,
    replyTargetAvailable: Boolean,
    state: MessageReplyUiState,
    onTextChange: (String) -> Unit,
    initialMedia: ComposerMediaAttachment?,
    onMediaChange: (ComposerMediaAttachment?) -> Unit,
    onSend: (String, ComposerMediaAttachment?) -> Unit,
    onReconnectInbox: () -> Unit,
    onSignInAgain: () -> Unit,
    onAuthorizeMediaReply: () -> Unit,
    onRefresh: (() -> Unit)?,
    onResetState: () -> Unit,
    interactionActionsAtStart: Boolean,
    longReplyTitle: String,
    quote: String?,
    blockedElsewhereMessage: String? = null,
) {
    val directChat = kind == ConversationDetailKind.DIRECT_CHAT
    val mediaAllowed = conversationReplyMediaCapability(kind) ==
        ConversationReplyMediaCapability.IMAGE_AND_GIF
    val editor = rememberTextFieldState(initialText = text)
    // RECREATE02_MESSAGE_ATTACHMENT_SURVIVES_RECREATION_V1 (#241): the field
    // starts from the draft's attachment and reports every change back.
    var media by remember(kind) { mutableStateOf(initialMedia) }
    val latestOnMediaChange by rememberUpdatedState(onMediaChange)
    LaunchedEffect(media) { latestOnMediaChange(media) }
    var pendingMediaReplacement by remember(kind) {
        mutableStateOf<ComposerMediaAttachment?>(null)
    }
    // ISSUE303_CHECKED_GIVES_AN_UNCLEAR_IMAGE_BACK_V1 (Pixel 11, 2026-11-14): an
    // image whose own send answer was unclear left the field, and "Checked"
    // (the reader looked and it is not there) gave nothing back. It is held
    // while the answer is unclear and comes back on Checked; when Reddit lists
    // it instead, it is dropped.
    var mediaHeldWhileUnclear by remember(kind) {
        mutableStateOf<ComposerMediaAttachment?>(null)
    }
    val latestOnTextChange by rememberUpdatedState(onTextChange)

    fun updateMedia(next: ComposerMediaAttachment?) {
        media = next
    }

    fun requestMedia(next: ComposerMediaAttachment) {
        if (!mediaAllowed) return
        if (media == null) {
            updateMedia(next)
        } else {
            pendingMediaReplacement = next
        }
    }

    val mediaPicker = rememberComposerMediaPicker(::requestMedia)
    val composerVisibleMedia = media.takeUnless {
        directChat &&
            state.phase == MessageReplyPhase.SENDING &&
            state.optimisticMediaUri != null
    }
    val sendEnabled = replyTargetAvailable &&
        conversationReplyHasSendableContent(
            kind = kind,
            text = editor.text,
            hasMedia = media != null,
        ) &&
        !state.blocksSend &&
        blockedElsewhereMessage == null
    val status = when {
        !replyTargetAvailable ->
            if (directChat) {
                stringResource(R.string.all_this_chat_does_not_expose_a_reply)
            } else {
                stringResource(R.string.all_this_message_thread_does_not_expose_a)
            }
        blockedElsewhereMessage != null -> blockedElsewhereMessage
        state.phase == MessageReplyPhase.SENDING ->
            when {
                directChat && media != null -> null
                media != null -> "Uploading and sending…"
                else -> stringResource(R.string.all_sending)
            }
        else -> null
    }
    val errorPresentation = if (
        state.phase == MessageReplyPhase.FAILED ||
        state.phase == MessageReplyPhase.OUTCOME_UNKNOWN
    ) {
        messageReplyErrorPresentation(
            state = state,
            directChat = directChat,
            canRefresh = onRefresh != null,
        )
    } else {
        null
    }

    LaunchedEffect(mediaAllowed) {
        if (!mediaAllowed) {
            media = null
            pendingMediaReplacement = null
        }
    }

    // LOWUI8_REPLY_FIELD_OWNS_ITS_KEYSTROKES_V1 (OCB-309): the field reports
    // each change up; the parent's text coming back a keystroke late wrote an
    // older value over newer typing. A value the field reported is an echo
    // (it and the values before it are dropped from the queue); only a value
    // the field never reported (a clear after sending, a restored draft) is
    // pushed down.
    val reportedTexts = remember { ArrayDeque<String>() }
    var pushedText by remember { mutableStateOf<String?>(null) }
    val latestText by rememberUpdatedState(text)
    LaunchedEffect(text) {
        val echo = reportedTexts.indexOf(text)
        if (echo >= 0) {
            repeat(echo + 1) { reportedTexts.removeFirst() }
        } else if (editor.text.toString() != text) {
            reportedTexts.clear()
            pushedText = text
            editor.edit {
                replace(0, length, text)
                selection = TextRange(text.length)
            }
        }
    }
    LaunchedEffect(editor) {
        snapshotFlow { editor.text.toString() }
            .collectLatest { value ->
                // The field's own report of a value pushed down is not typing.
                if (value == pushedText) {
                    pushedText = null
                    latestOnTextChange(value)
                    return@collectLatest
                }
                pushedText = null
                // Already the parent's text (the first value): no echo follows.
                if (value != latestText) reportedTexts.addLast(value)
                while (reportedTexts.size > 64) reportedTexts.removeFirst()
                latestOnTextChange(value)
            }
    }
    // ISSUE306_AN_OLD_SEND_LEAVES_A_NEW_DRAFT_ALONE_V1 (#306): a send that ended
    // before this composer appeared (the reply state still SUCCEEDED from it)
    // cleared the attachment of the draft the reader came back to.
    val completionSeenAtStart = remember(kind) { state.completionRevision }
    val pasteContext = LocalContext.current
    LaunchedEffect(state.phase, state.completionRevision, state.sentHadMedia) {
        val endedHere = state.completionRevision > completionSeenAtStart
        val mediaCommitted = endedHere && (
            state.phase == MessageReplyPhase.SUCCEEDED ||
                (
                    state.sentHadMedia &&
                        state.completionRevision > 0L &&
                        (
                            state.phase == MessageReplyPhase.FAILED ||
                                state.phase == MessageReplyPhase.OUTCOME_UNKNOWN
                            )
                    )
            )
        val imageUnclear = state.phase == MessageReplyPhase.OUTCOME_UNKNOWN &&
            state.sentHadMedia &&
            state.sentMatrixEventId.isNullOrBlank()
        if (!imageUnclear) mediaHeldWhileUnclear = null
        if (mediaCommitted) {
            if (imageUnclear && media != null) mediaHeldWhileUnclear = media
            // SUBSHELL_MESSAGES02E2A2_REDDIT_PARITY_DUAL_WRITE_V1
            // On partial success only the text stays in the composer; the media is already remote.
            media = null
            pendingMediaReplacement = null
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (errorPresentation != null) {
                // SUBSHELL_MESSAGES02C4R3R1_MESSAGE_REPLY_ERROR_PRESENTATION_PARITY_V1
                ErrorNotice(
                    presentation = errorPresentation,
                    diagnosticScopeKey = "messages_reply",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    onAction = { action ->
                        when (action) {
                            ErrorActionKind.RETRY -> onRefresh?.invoke()
                            ErrorActionKind.SIGN_IN -> {
                                if (state.failureKind == io.github.brealorg.subshell.data.account.MessageReplyFailureKind.MEDIA_AUTHORIZATION_REQUIRED) {
                                    onAuthorizeMediaReply()
                                } else if (state.failureKind == io.github.brealorg.subshell.data.account.MessageReplyFailureKind.CHAT_WEB_SIGN_IN_EXPIRED) {
                                    onSignInAgain()
                                } else {
                                    onReconnectInbox()
                                }
                                onResetState()
                            }
                            ErrorActionKind.DISMISS -> {
                                mediaHeldWhileUnclear?.let { held -> media = held }
                                mediaHeldWhileUnclear = null
                                onResetState()
                            }
                            else -> Unit
                        }
                    },
                )
            }

            CompactReplyComposer(
                state = editor,
                media = composerVisibleMedia,
                placeholder = if (directChat) stringResource(R.string.all_message) else stringResource(R.string.all_reply),
                sendEnabled = sendEnabled,
                onSend = { onSend(editor.text.toString(), media) },
                // ISSUE306_A_PASTED_IMAGE_IS_KEPT_IN_THE_APP_V1 (#306): as in the comment
                // and post composers (COMPOSER16), a pasted image is copied in at once:
                // the paste's read grant ends, and HEIC/AVIF are made JPEG.
                onMediaReceived = { pasted -> requestMedia(pastedImageKeptInApp(pasteContext, pasted)) },
                onMediaRemoved = { updateMedia(null) },
                onPickImage = mediaPicker.pickImage,
                onPickGif = mediaPicker.pickGif,
                mediaEnabled = mediaAllowed && replyTargetAvailable && !state.inProgress,
                showMediaActions = mediaAllowed,
                interactionActionsAtStart = interactionActionsAtStart,
                maxCharacters = io.github.brealorg.subshell.data.account.MESSAGE_REPLY_TEXT_MAX_LENGTH,
                sendContentDescription = when {
                    state.inProgress && directChat -> stringResource(R.string.all_sending_message)
                    state.inProgress -> stringResource(R.string.all_sending_reply_2)
                    directChat -> stringResource(R.string.all_send_message)
                    else -> stringResource(R.string.all_send_reply)
                },
                longReplyTitle = longReplyTitle,
                quote = quote,
            )

            if (status != null) {
                Text(
                    text = status,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    val replacement = pendingMediaReplacement
    if (replacement != null && media != null) {
        ComposerMediaReplacementDialog(
            currentMedia = requireNotNull(media),
            replacementMedia = replacement,
            onReplace = {
                updateMedia(replacement)
                pendingMediaReplacement = null
            },
            onKeepExisting = { pendingMediaReplacement = null },
        )
    }
}

@Composable
private fun ConversationHeaderIdentity(
    item: InboxItem,
    kind: ConversationDetailKind,
    avatarUri: String?,
) {
    val participant = conversationParticipantLabel(item.sender)
    val profileUsername = conversationProfileUsername(item.sender)
    val openUser = LocalConversationOpenUser.current
    Row(
        modifier = if (profileUsername != null && openUser != null) {
            Modifier.clickable(
                onClickLabel = stringResource(R.string.scr_open_u_profile, profileUsername),
                role = Role.Button,
            ) { openUser(profileUsername) }
        } else {
            Modifier
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ConversationParticipantAvatar(
            sender = item.sender,
            avatarUri = avatarUri,
            size = 36,
        )
        Column {
            Text(
                text = when (kind) {
                    ConversationDetailKind.DIRECT_CHAT ->
                        conversationParticipantDisplayName(item.sender)
                    ConversationDetailKind.PRIVATE_MESSAGE_THREAD ->
                        item.title.ifBlank { stringResource(R.string.all_message) }
                },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when (kind) {
                    ConversationDetailKind.DIRECT_CHAT -> stringResource(R.string.all_direct_chat)
                    ConversationDetailKind.PRIVATE_MESSAGE_THREAD -> participant
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DirectChatDeleteDialog(
    deleting: Boolean,
    failureKind: DirectChatDeleteFailureKind?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!deleting) onDismiss() },
        title = { Text(stringResource(R.string.scr_delete_message)) },
        text = {
            Text(
                when (failureKind) {
                    DirectChatDeleteFailureKind.SESSION ->
                        stringResource(R.string.all_your_reddit_chat_session_needs_attention_reopen)
                    DirectChatDeleteFailureKind.ROOM ->
                        "subshell no longer has a verified room for this chat. Reopen the chat and try again."
                    DirectChatDeleteFailureKind.HTTP ->
                        stringResource(R.string.all_reddit_rejected_the_delete_request)
                    DirectChatDeleteFailureKind.OUTCOME_UNKNOWN ->
                        stringResource(R.string.all_deletion_wasn_t_confirmed_check_the_chat)
                    DirectChatDeleteFailureKind.PROTOCOL ->
                        stringResource(R.string.all_reddit_returned_a_response_subshell_couldn_t)
                    DirectChatDeleteFailureKind.NOT_OWNER ->
                        stringResource(R.string.all_only_your_own_live_direct_chat_messages)
                    DirectChatDeleteFailureKind.UNAVAILABLE ->
                        stringResource(R.string.all_direct_chat_deletion_isn_t_available_for)
                    null ->
                        stringResource(R.string.all_this_will_delete_the_message_for_everyone)
                },
            )
        },
        confirmButton = {
            if (deleting) {
                TextButton(onClick = {}, enabled = false) {
                    InlineProgressIndicator()
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.scr_deleting))
                }
            } else if (failureKind == null) {
                TextButton(onClick = onConfirm) {
                    Text(stringResource(R.string.scr_delete_2), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = {
            if (!deleting) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.scr_cancel)) }
            }
        },
    )
}

// SUBSHELL_INBOXM3A2R1_GROUPED_CHAT_GRAMMAR_V1
// SUBSHELL_INBOXM3A2R2_GROUP_LAYOUT_ONLY_V1
@Composable
private fun DirectChatMessageGroup(
    group: ConversationMessageGroup,
    participantAvatarUri: String?,
    ownAvatarUri: String?,
    ownUsername: String?,
    writeEnabled: Boolean,
    liveState: DirectChatLiveUiState,
    optimisticMediaEchoId: String?,
    optimisticMedia: OptimisticConversationMedia?,
    localEchoId: String?,
    localEchoSending: Boolean,
    onReactCandidate: (String) -> Unit,
    onDeleteCandidate: (String) -> Unit,
) {
    val outgoing = group.outgoing
    val identitySender = if (outgoing) {
        ownUsername?.takeIf(String::isNotBlank) ?: "you"
    } else {
        group.sender
    }
    val identityAvatarUri = if (outgoing) ownAvatarUri else participantAvatarUri

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 5.dp, bottom = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (!outgoing) {
            ConversationParticipantAvatar(
                sender = identitySender,
                avatarUri = identityAvatarUri,
                size = 36,
            )
            Spacer(Modifier.width(8.dp))
        }

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = if (outgoing) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(0.86f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (outgoing) {
                        Arrangement.End
                    } else {
                        Arrangement.Start
                    },
                ) {
                    if (outgoing) {
                        Text(
                            text = conversationTimeLabel(group.messages.last()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = stringResource(R.string.scr_you),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Text(
                            text = conversationParticipantLabel(group.sender),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = conversationTimeLabel(group.messages.last()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                group.messages.forEachIndexed { messageIndex, message ->
                    val liveReactionEventId = if (writeEnabled) {
                        directChatMatrixReactionTargetEventId(
                            displayed = message,
                            live = liveState.messages,
                            redactedEventIds = liveState.redactedMessageIds,
                        )
                    } else {
                        null
                    }
                    val reactionPresentationEventId =
                        liveReactionEventId
                            ?: directChatCachedReactionTargetEventId(
                                displayed = message,
                                cachedTargets = liveState.cachedReactionTargets,
                            )
                    val reactionSummaries = reactionPresentationEventId
                        ?.let { eventId ->
                            directChatReactionSummaries(
                                targetEventId = eventId,
                                reactions = liveState.reactions,
                                redactedEventIds = liveState.redactedMessageIds,
                            )
                        }
                        .orEmpty()
                    val deleteEventId = if (writeEnabled) {
                        directChatMatrixDeleteEventId(
                            displayed = message,
                            live = liveState.messages,
                            redactedEventIds = liveState.redactedMessageIds,
                        )
                    } else {
                        null
                    }

                    // SUBSHELL_INBOXM3A2R2D1_NULLABLE_ACTION_PROGRESS_GUARD_V1
                    DirectChatMessage(
                        message = message,
                        outgoing = outgoing,
                        messageIndex = messageIndex,
                        messageCount = group.messages.size,
                        localMedia = optimisticMedia
                            ?.takeIf {
                                optimisticMediaEchoId == message.id &&
                                    localEchoId == message.id
                            },
                        deliveryLabel = if (
                            localEchoSending && localEchoId == message.id
                        ) {
                            stringResource(R.string.all_sending)
                        } else {
                            null
                        },
                        reactionSummaries = reactionSummaries,
                        reactInProgress =
                            liveReactionEventId != null &&
                                liveState.reactingEventId == liveReactionEventId,
                        onReact = liveReactionEventId?.let { eventId ->
                            { onReactCandidate(eventId) }
                        },
                        deleteInProgress =
                            deleteEventId != null &&
                                liveState.deletingEventId == deleteEventId,
                        onDelete = deleteEventId?.let { eventId ->
                            { onDeleteCandidate(eventId) }
                        },
                    )
                }
            }
        }

        if (outgoing) {
            Spacer(Modifier.width(8.dp))
            ConversationParticipantAvatar(
                sender = identitySender,
                avatarUri = identityAvatarUri,
                size = 36,
            )
        }
    }
}

// SUBSHELL_INBOXM3A2R2_COMPACT_MESSAGE_UNIT_V1
@Composable
private fun DirectChatMessage(
    message: MessageBubble,
    outgoing: Boolean,
    messageIndex: Int,
    messageCount: Int,
    localMedia: OptimisticConversationMedia? = null,
    deliveryLabel: String? = null,
    reactionSummaries: List<DirectChatReactionSummary> = emptyList(),
    reactInProgress: Boolean = false,
    onReact: (() -> Unit)? = null,
    deleteInProgress: Boolean = false,
    onDelete: (() -> Unit)? = null,
) {
    var actionMenuExpanded by remember(message.id) { mutableStateOf(false) }
    val unitShape = groupedRowShape(
        index = messageIndex,
        count = messageCount,
        outer = MaterialTheme.shapes.large,
        inner = MaterialTheme.shapes.extraSmall,
    )
    val hasActions = onReact != null || onDelete != null
    val actionsAvailable = hasActions && !deleteInProgress && !reactInProgress

    // ISSUE235_LONG_PRESS_SELECTS_TEXT_V1 (owner: one behaviour in the whole
    // app): a long press on a message selects its text, as on a comment; the
    // menu is the ⋮ beside it (as on a comment). Replaces LOWUI7 (OCB-197),
    // where a long press opened the menu. TalkBack keeps the named action.
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (actionsAvailable) {
                    Modifier.semantics {
                        onLongClick(label = DIRECT_CHAT_MESSAGE_ACTIONS_LABEL) {
                            actionMenuExpanded = true
                            true
                        }
                    }
                } else {
                    Modifier
                },
            ),
        shape = unitShape,
        color = if (outgoing) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            deliveryLabel?.let { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                ConversationMessageBody(
                    body = message.body,
                    renderMarkdown = true,
                    localMedia = localMedia,
                    selectable = true, // ISSUE235_LONG_PRESS_SELECTS_TEXT_V1
                    // ISSUE306_THE_MESSAGE_MENU_IS_A_FULL_TOUCH_TARGET_V1 (#306): the ⋮ was a
                    // 24 dp target beside selectable text, so a tap just off it started a
                    // selection. It is a 40 dp button in the icon's old 24 dp place and the
                    // text keeps clear of it.
                    modifier = if (hasActions) {
                        Modifier.padding(end = 32.dp)
                    } else {
                        Modifier
                    },
                )

                if (hasActions) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            // Takes the 24 dp the icon always took (a one-line
                            // message keeps its height); the 40 dp button around
                            // it reaches 8 dp past every side.
                            .layout { measurable, _ ->
                                val target = 40.dp.roundToPx()
                                val slot = 24.dp.roundToPx()
                                val button = measurable.measure(Constraints.fixed(target, target))
                                layout(slot, slot) {
                                    button.place((slot - target) / 2, (slot - target) / 2)
                                }
                            },
                    ) {
                        IconButton(
                            modifier = Modifier.size(40.dp),
                            enabled = !deleteInProgress && !reactInProgress,
                            onClick = { actionMenuExpanded = true },
                        ) {
                            if (deleteInProgress || reactInProgress) {
                                InlineProgressIndicator()
                            } else {
                                Icon(
                                    imageVector = Symbols.MoreVert,
                                    contentDescription = stringResource(R.string.scr_message_actions),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = actionMenuExpanded,
                            onDismissRequest = { actionMenuExpanded = false },
                        ) {
                            if (onReact != null) {
                                DropdownMenuItem(
                                    text = {
                                        Text(if (reactInProgress) stringResource(R.string.all_reacting) else stringResource(R.string.all_react))
                                    },
                                    enabled = !reactInProgress,
                                    onClick = {
                                        actionMenuExpanded = false
                                        onReact()
                                    },
                                )
                            }
                            if (onDelete != null) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(R.string.scr_delete_message_2),
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Symbols.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    onClick = {
                                        actionMenuExpanded = false
                                        onDelete()
                                    },
                                )
                            }
                        }
                    }
                }
            }

            if (reactionSummaries.isNotEmpty()) {
                DirectChatReactionStrip(
                    reactions = reactionSummaries,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun DirectChatReactionStrip(
    reactions: List<DirectChatReactionSummary>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        reactions.forEach { reaction ->
            // LOWUI7_REACTIONS_SAY_WHICH_AND_HOW_MANY_V1 (OCB-198): the chip is
            // read as one item, named as in the picker, with its count.
            val chipLabel = directChatReactionChipLabel(reaction).asString()
            Surface(
                modifier = Modifier.clearAndSetSemantics { contentDescription = chipLabel },
                shape = MaterialTheme.shapes.extraLarge,
                color = if (reaction.self) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val asset = redditDirectChatReactionAsset(reaction.key)
                    if (asset != null) {
                        DirectChatReactionAssetMedia(
                            asset = asset,
                            label = stringResource(R.string.scr_reddit_reaction),
                            playing = true,
                            modifier = Modifier.size(24.dp),
                        )
                    } else {
                        Text(
                            text = reaction.key,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    if (reaction.count > 1) {
                        Text(
                            text = reaction.count.toString(),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DirectChatReactionPickerSheet(
    reacting: Boolean,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    // M3225_REACTION_PICKER_IS_A_SHEET_V1 (#225, modinbox-15): a grid of
    // reactions is a modal bottom sheet, not a dialog; it stays while a
    // reaction is being sent.
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        confirmValueChange = { value -> !reacting || value != SheetValue.Hidden },
    )
    ModalBottomSheet(
        onDismissRequest = { if (!reacting) onDismiss() },
        sheetState = sheetState,
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            Text(
                stringResource(R.string.all_react_to_message),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            LazyColumn(
                modifier = Modifier.heightIn(max = 430.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(
                    items = redditDirectChatReactionCatalog.chunked(DIRECT_CHAT_REACTION_PICKER_COLUMNS),
                    key = { row -> row.first().key },
                ) { rowAssets ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        rowAssets.forEachIndexed { columnIndex, asset ->
                            val catalogIndex =
                                redditDirectChatReactionCatalog.indexOf(asset) + 1
                            TextButton(
                                enabled = !reacting,
                                onClick = { onSelect(asset.key) },
                                modifier = Modifier.size(68.dp),
                                contentPadding = PaddingValues(7.dp),
                            ) {
                                DirectChatReactionAssetMedia(
                                    asset = asset,
                                    label = stringResource(R.string.scr_reddit_reaction_2, catalogIndex),
                                    playing = true,
                                    modifier = Modifier.size(54.dp),
                                )
                            }
                        }
                        repeat(DIRECT_CHAT_REACTION_PICKER_COLUMNS - rowAssets.size) {
                            Spacer(Modifier.size(62.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectChatReactionAssetMedia(
    asset: DirectChatReactionAsset,
    label: String,
    playing: Boolean,
    modifier: Modifier = Modifier,
) {
    if (asset.animated) {
        RemoteGifMedia(
            uri = asset.uri,
            label = label,
            playing = playing,
            modifier = modifier,
            contentScale = ContentScale.Fit,
            aspectRatio = null,
        )
    } else {
        RemoteImage(
            uri = asset.uri,
            label = label,
            modifier = modifier,
            contentScale = ContentScale.Fit,
            aspectRatio = null,
        )
    }
}

internal const val DIRECT_CHAT_REACTION_NOTICE_SCOPE = "direct-chat-reaction"
internal const val DIRECT_CHAT_WEB_SIGN_IN_SCOPE = "direct-chat-web-sign-in"

/** ISSUE303B_AN_EXPIRED_WEB_SIGN_IN_OFFERS_SIGN_IN_AGAIN_V1 */
internal fun directChatWebSignInExpiredPresentation(
    directChat: Boolean,
    failureKind: DirectChatLiveFailureKind?,
): ErrorPresentation? =
    if (directChat && failureKind == DirectChatLiveFailureKind.WEB_SIGN_IN_EXPIRED) {
        ErrorPresentation(
            kind = ErrorPresentationKind.DEGRADED,
            title = UiText.Resource(R.string.chat_web_sign_in_expired_title),
            message = UiText.Resource(R.string.chat_web_sign_in_expired_live),
            primaryAction = ErrorAction(
                ErrorActionKind.SIGN_IN,
                UiText.Resource(R.string.chat_web_sign_in_again),
            ),
        )
    } else {
        null
    }

internal fun directChatReactionFailureMessage(failureKind: DirectChatReactionFailureKind): String =
    when (failureKind) {
        DirectChatReactionFailureKind.SESSION ->
            "Your Reddit chat session needs attention. Reopen the chat and try again."
        DirectChatReactionFailureKind.ROOM ->
            "subshell no longer has a verified room for this chat. Reopen the chat and try again."
        DirectChatReactionFailureKind.HTTP ->
            "Reddit rejected the reaction request."
        DirectChatReactionFailureKind.OUTCOME_UNKNOWN ->
            "The reaction wasn't confirmed. Check the chat in another Reddit client before trying again."
        DirectChatReactionFailureKind.PROTOCOL ->
            "Reddit returned a response subshell couldn't verify."
        DirectChatReactionFailureKind.TARGET ->
            "This message is not backed by one unambiguous live Direct Chat event."
        DirectChatReactionFailureKind.UNAVAILABLE ->
            "Direct Chat reactions aren't available for this session."
    }

@Composable
private fun PrivateMessageThreadGroup(
    group: ConversationMessageGroup,
    participantAvatarUri: String?,
    ownAvatarUri: String?,
    ownUsername: String?,
) {
    val outgoing = group.outgoing
    val identitySender = if (outgoing) {
        ownUsername?.takeIf(String::isNotBlank) ?: "you"
    } else {
        group.sender
    }
    val identityAvatarUri = if (outgoing) ownAvatarUri else participantAvatarUri

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 5.dp, bottom = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (!outgoing) {
            ConversationParticipantAvatar(
                sender = identitySender,
                avatarUri = identityAvatarUri,
                size = 36,
            )
            Spacer(Modifier.width(8.dp))
        }

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = if (outgoing) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(0.86f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (outgoing) {
                        Arrangement.End
                    } else {
                        Arrangement.Start
                    },
                ) {
                    if (outgoing) {
                        Text(
                            text = conversationTimeLabel(group.messages.last()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = stringResource(R.string.scr_you),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Text(
                            text = conversationParticipantLabel(group.sender),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = conversationTimeLabel(group.messages.last()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                group.messages.forEachIndexed { messageIndex, message ->
                    PrivateMessageThreadMessage(
                        message = message,
                        outgoing = outgoing,
                        messageIndex = messageIndex,
                        messageCount = group.messages.size,
                    )
                }
            }
        }

        if (outgoing) {
            Spacer(Modifier.width(8.dp))
            ConversationParticipantAvatar(
                sender = identitySender,
                avatarUri = identityAvatarUri,
                size = 36,
            )
        }
    }
}

@Composable
private fun PrivateMessageThreadMessage(
    message: MessageBubble,
    outgoing: Boolean,
    messageIndex: Int,
    messageCount: Int,
) {
    val unitShape = groupedRowShape(
        index = messageIndex,
        count = messageCount,
        outer = MaterialTheme.shapes.large,
        inner = MaterialTheme.shapes.extraSmall,
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = unitShape,
        color = if (outgoing) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
    ) {
        Box(modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)) {
            ConversationMessageBody(body = message.body, selectable = true) // ISSUE235_LONG_PRESS_SELECTS_TEXT_V1
        }
    }
}

@Composable
private fun ConversationMessageBody(
    body: String,
    modifier: Modifier = Modifier,
    renderMarkdown: Boolean = true,
    localMedia: OptimisticConversationMedia? = null,
    selectable: Boolean = false,
) {
    val projection = remember(body) { projectCommentContent(body) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (localMedia != null) {
            // SUBSHELL_MESSAGES02C6R5_OPTIMISTIC_DIRECT_CHAT_MEDIA_ECHO_V1
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                LocalUriMedia(
                    uri = localMedia.uri,
                    mimeType = localMedia.mimeType,
                    contentDescription = if (
                        localMedia.mimeType.equals("image/gif", ignoreCase = true)
                    ) {
                        stringResource(R.string.all_sending_gif)
                    } else {
                        stringResource(R.string.all_sending_image)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(MaterialTheme.shapes.medium),
                )
            }
        }
        if (projection.displayText.isNotBlank()) {
            ConversationBodyText(
                text = projection.displayText,
                renderMarkdown = renderMarkdown,
                selectable = selectable,
            )
        }
        projection.media.take(MAX_INLINE_MESSAGE_IMAGES).forEachIndexed { index, media ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                when (media.kind) {
                    RemoteCommentMediaKind.GIF -> RemoteGifMedia(
                        uri = media.uri,
                        label = stringResource(R.string.scr_message_gif, index + 1),
                        playing = true,
                        contentScale = ContentScale.Fit,
                        adaptToSourceAspectRatio = true,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium),
                    )
                    RemoteCommentMediaKind.IMAGE -> RemoteImage(
                        uri = media.uri,
                        label = stringResource(R.string.scr_message_image, index + 1),
                        contentScale = ContentScale.Fit,
                        aspectRatio = null,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        showFailureDetails = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(MaterialTheme.shapes.medium),
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationBodyText(
    text: String,
    renderMarkdown: Boolean,
    selectable: Boolean,
) {
    if (renderMarkdown) {
        MarkdownText(text = text, selectable = selectable)
    } else if (selectable) {
        SelectionContainer { ConversationPlainText(text) }
    } else {
        ConversationPlainText(text)
    }
}

@Composable
private fun ConversationPlainText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun ConversationDayDivider(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun ConversationParticipantAvatar(
    sender: String,
    avatarUri: String?,
    size: Int,
) {
    // CONVPROFILE01_TAP_PARTICIPANT_OPENS_PROFILE_V1: a participant's avatar
    // opens their profile (the header row handles its own avatar).
    val profileUsername = conversationProfileUsername(sender)
    val openUser = LocalConversationOpenUser.current
    val fallback = conversationParticipantDisplayName(sender)
        .firstOrNull()
        ?.uppercase()
        ?: "?"
    val label = stringResource(R.string.scr_avatar_for, conversationParticipantLabel(sender))
    val tappable = profileUsername != null && openUser != null
    // ISSUE219_PARTICIPANT_AVATAR_HAS_A_48_DP_TARGET_V1: the avatar keeps its
    // size in the layout; the tap target over it is 48 dp (centred overflow),
    // and that target carries the avatar's label.
    Box(modifier = Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        RemoteIdentityImage(
            uri = avatarUri,
            contentDescription = if (tappable) null else label,
            modifier = Modifier
                .size(size.dp)
                .clip(CircleShape),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        fallback,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        if (profileUsername != null && openUser != null) {
            Box(
                modifier = Modifier
                    .requiredSize(maxOf(size, 48).dp)
                    .clip(CircleShape)
                    .clickable(
                        onClickLabel = stringResource(R.string.scr_open_u_profile, profileUsername),
                        role = Role.Button,
                    ) { openUser(profileUsername) }
                    .semantics { contentDescription = label },
            )
        }
    }
}

internal fun conversationDetailKind(item: InboxItem): ConversationDetailKind =
    if (item.title.trim().equals("[direct chat room]", ignoreCase = true)) {
        ConversationDetailKind.DIRECT_CHAT
    } else {
        ConversationDetailKind.PRIVATE_MESSAGE_THREAD
    }

internal fun resolveConversationReplyParentFullname(item: InboxItem): String? =
    item.conversation
        .asReversed()
        .asSequence()
        .map { message -> message.id.trim().lowercase() }
        .firstOrNull(::isCanonicalPrivateMessageFullname)
        ?: item.id
            .trim()
            .lowercase()
            .takeIf(::isCanonicalPrivateMessageFullname)

internal fun messageReplyAppearsInConversation(
    messages: List<MessageBubble>,
    state: MessageReplyUiState,
    outgoingIdsBeforeSend: Set<String>? = null,
): Boolean {
    val completedAt = state.completedAtEpochSeconds ?: return false
    val sentText = state.sentText?.trim()?.takeIf(String::isNotEmpty)
    val sentDraftText = state.sentDraftText?.trim()?.takeIf(String::isNotEmpty)
    // LOWUI8_REPLY_CONFIRMATION_TOLERATES_A_WRONG_CLOCK_V1 (OCB-199): the
    // window compares Reddit's time with the device clock, so a clock more
    // than 3 minutes fast never confirmed the reply (local echo and server
    // copy both shown). An outgoing message that was not in the conversation
    // when the reply was sent is new whatever the clocks say; an older one
    // with the same text is never taken for the reply.
    return messages.asReversed().any { message ->
        val recentEnough = (outgoingIdsBeforeSend != null && message.id !in outgoingIdsBeforeSend) ||
            message.createdEpochSeconds
                ?.let { created -> created >= completedAt - 180L }
                ?: false
        message.outgoing &&
            recentEnough &&
            if (state.sentHadMedia) {
                val matrixMediaId = state.sentMatrixMediaId
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                if (matrixMediaId != null) {
                    message.body.contains(matrixMediaId)
                } else {
                    // Preserve the legacy private-message media confirmation fallback. Direct Chat
                    // Matrix media always carries its unique media ID and never matches a previous GIF.
                    sentDraftText == null || message.body.contains(sentDraftText)
                }
            } else {
                sentText != null && message.body.trim() == sentText
            }
    }
}

internal fun shouldSuppressDirectChatLiveMessageForLocalMedia(
    message: MessageBubble,
    state: MessageReplyUiState,
): Boolean {
    if (!message.outgoing || state.optimisticMediaUri.isNullOrBlank()) return false
    val phaseSupportsLocalBacking =
        state.phase == MessageReplyPhase.SENDING ||
            (
                state.sentHadMedia &&
                    state.completionRevision > 0L &&
                    (
                        state.phase == MessageReplyPhase.SUCCEEDED ||
                            state.phase == MessageReplyPhase.FAILED ||
                            state.phase == MessageReplyPhase.OUTCOME_UNKNOWN
                        )
                )
    if (!phaseSupportsLocalBacking) return false
    val eventId = state.sentMatrixEventId?.trim()?.takeIf(String::isNotEmpty)
    if (eventId != null && message.id == eventId) return true
    val mediaId = state.sentMatrixMediaId?.trim()?.takeIf(String::isNotEmpty)
    if (
        mediaId != null &&
        redditMatrixMediaIdFromMessageBody(message.body) == mediaId
    ) {
        return true
    }
    // A Matrix echo can beat the writer result by a few milliseconds. While a media-only send is
    // still in SENDING, prefer the already-rendered local URI over any new outgoing live m.image.
    return state.phase == MessageReplyPhase.SENDING
}

private fun isCanonicalPrivateMessageFullname(value: String): Boolean =
    value.startsWith("t4_") &&
        value.length in 4..35 &&
        value.substring(3).all(Char::isLetterOrDigit)

internal fun conversationParticipantUsername(sender: String): String? {
    val normalized = sender.trim()
    if (normalized.isBlank()) return null
    if (normalized.startsWith("r/", ignoreCase = true)) return null
    if (normalized.startsWith("#")) return null
    if (normalized.equals("reddit", ignoreCase = true)) return null
    return normalized.removePrefix("u/").takeIf(String::isNotBlank)
}

internal fun conversationParticipantLabel(sender: String): String {
    val normalized = sender.trim()
    return when {
        normalized.startsWith("u/", ignoreCase = true) -> normalized
        normalized.startsWith("r/", ignoreCase = true) -> normalized
        normalized.startsWith("#") -> "r/${normalized.removePrefix("#")}"
        normalized.equals("reddit", ignoreCase = true) -> "reddit"
        normalized.isBlank() -> "reddit"
        else -> "u/$normalized"
    }
}

private fun conversationParticipantDisplayName(sender: String): String {
    val normalized = conversationParticipantLabel(sender)
    return when {
        normalized.startsWith("u/", ignoreCase = true) -> normalized.substring(2)
        else -> normalized
    }
}

internal data class ConversationMessageGroup(
    val messages: List<MessageBubble>,
) {
    init {
        require(messages.isNotEmpty()) { "Conversation message group cannot be empty" }
    }

    val key: String = messages.first().id
    val outgoing: Boolean = messages.first().outgoing
    val sender: String = messages.first().sender
}

internal fun conversationMessageGroups(
    messages: List<MessageBubble>,
): List<ConversationMessageGroup> {
    if (messages.isEmpty()) return emptyList()
    val groups = mutableListOf<MutableList<MessageBubble>>()

    messages.forEach { message ->
        val current = groups.lastOrNull()
        val previous = current?.lastOrNull()
        if (
            current != null &&
            previous != null &&
            sameConversationMessageGroup(previous, message) &&
            conversationMessagesWithinGroupingWindow(previous, message)
        ) {
            current += message
        } else {
            groups += mutableListOf(message)
        }
    }

    return groups.map { group -> ConversationMessageGroup(group.toList()) }
}

private fun conversationMessagesWithinGroupingWindow(
    first: MessageBubble,
    second: MessageBubble,
): Boolean {
    val firstEpoch = first.createdEpochSeconds
    val secondEpoch = second.createdEpochSeconds
    if (firstEpoch == null || secondEpoch == null || firstEpoch <= 0L || secondEpoch <= 0L) {
        return true
    }
    val delta = secondEpoch - firstEpoch
    return delta in 0L..CONVERSATION_GROUP_WINDOW_SECONDS
}

private const val CONVERSATION_GROUP_WINDOW_SECONDS = 5L * 60L

private fun sameConversationMessageGroup(
    first: MessageBubble,
    second: MessageBubble,
): Boolean = first.outgoing == second.outgoing &&
    first.sender.equals(second.sender, ignoreCase = true) &&
    conversationDayKey(first) == conversationDayKey(second)

private fun conversationDayKey(message: MessageBubble): String {
    val epoch = message.createdEpochSeconds
    return if (epoch != null && epoch > 0L) {
        Instant.ofEpochSecond(epoch)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .toString()
    } else {
        "legacy:${message.age}"
    }
}

internal fun conversationDayLabel(
    message: MessageBubble,
    nowEpochSeconds: Long = Instant.now().epochSecond,
): String {
    val epoch = message.createdEpochSeconds ?: return message.age
    if (epoch <= 0L) return message.age
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochSecond(epoch).atZone(zone).toLocalDate()
    val today = Instant.ofEpochSecond(nowEpochSeconds).atZone(zone).toLocalDate()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        // LOWUX3_CONVERSATION_DATES_AND_TIMES_READ_RIGHT_V1 (OCB-200): a date
        // from another year carries the year, and times follow the phone's
        // 12/24-hour setting.
        else -> date.format(
            DateTimeFormatter.ofPattern(
                if (date.year == today.year) "d MMM" else "d MMM yyyy",
                Locale.getDefault(),
            ),
        )
    }
}

@Composable
private fun conversationTimeLabel(message: MessageBubble): String {
    val use24Hour = DateFormat.is24HourFormat(LocalContext.current)
    return conversationTimeLabel(message, use24Hour)
}

internal fun conversationTimeLabel(message: MessageBubble, use24Hour: Boolean): String {
    val epoch = message.createdEpochSeconds ?: return message.age
    if (epoch <= 0L) return message.age
    return Instant.ofEpochSecond(epoch)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm a", Locale.getDefault()))
}

internal fun resolveConversationRefreshItem(
    selected: InboxItem,
    inboxItemId: String,
    liveItems: List<InboxItem>,
): InboxItem = liveItems.firstOrNull { item -> item.id == inboxItemId } ?: selected

// ISSUE50_CONVERSATION_BACK_WITHOUT_SELECTION_V1: with a selection, exactly
// as before (the live item rebinds it); without one (after Back from a link
// opened in a live conversation) the route's item from the live list, or
// null = nothing to show (the host leaves the route).
internal fun resolveConversationRouteItem(
    selected: InboxItem?,
    inboxItemId: String,
    liveItems: List<InboxItem>,
): InboxItem? = if (selected != null) {
    resolveConversationRefreshItem(selected, inboxItemId, liveItems)
} else {
    liveItems.firstOrNull { item -> item.id == inboxItemId }
}

// ISSUE48_CONVERSATION_WAITS_FOR_THE_LIVE_INBOX_V1: a restored conversation
// route (process death, activity recreation) has no selection, and live
// Reddit conversations come only from the live inbox, which loads after
// start. The route waits for that inbox, pages it a few times when the item
// is older than the first page, and is left only when the inbox has loaded
// (or failed) without it. Signed out there is nothing to wait for.
internal enum class ConversationRouteStep { SHOW, WAIT, LOAD_MORE, LEAVE }

internal const val CONVERSATION_RESTORE_MAX_EXTRA_PAGES = 5

internal fun conversationRouteStep(
    itemFound: Boolean,
    authenticated: Boolean,
    accountStableId: String,
    inbox: RedditInboxUiState,
    extraPagesLoaded: Int,
): ConversationRouteStep = when {
    itemFound -> ConversationRouteStep.SHOW
    !authenticated -> ConversationRouteStep.LEAVE
    !inbox.matches(accountStableId) -> ConversationRouteStep.WAIT
    inbox.loading || inbox.appending -> ConversationRouteStep.WAIT
    inbox.failureKind != null || inbox.authorizationRequired || inbox.authorizing ->
        ConversationRouteStep.LEAVE
    !inbox.loaded -> ConversationRouteStep.WAIT
    inbox.nextCursor != null && extraPagesLoaded < CONVERSATION_RESTORE_MAX_EXTRA_PAGES ->
        ConversationRouteStep.LOAD_MORE
    else -> ConversationRouteStep.LEAVE
}

// ISSUE98_REPLY_BLOCKED_ELSEWHERE_V1: the reply writer keeps one state for
// every conversation and refuses a send while a reply is being sent or its
// outcome is unknown. Send in another conversation used to look enabled and
// do nothing; it is now off and says why.
internal fun messageReplyBlockedElsewhereMessage(
    global: MessageReplyUiState,
    scoped: MessageReplyUiState,
): String? = when {
    !global.blocksSend || scoped.blocksSend -> null
    global.phase == MessageReplyPhase.SENDING -> "Another message is still being sent."
    else -> "A message in another conversation may have been sent. Open it and confirm before sending here."
}

// CONVPROFILE01_TAP_PARTICIPANT_OPENS_PROFILE_V1: the participant a tap may
// open: a real Reddit user (not Reddit itself, a community or a group chat).
internal fun conversationProfileUsername(sender: String): String? =
    conversationParticipantUsername(sender)?.takeIf(::isValidRedditUsername)

internal val LocalConversationOpenUser = staticCompositionLocalOf<((String) -> Unit)?> { null }

internal fun directChatReactionChipLabel(reaction: DirectChatReactionSummary): UiText {
    val catalogIndex = redditDirectChatReactionCatalog.indexOfFirst { it.key == reaction.key }
    val name: UiText = if (catalogIndex >= 0) {
        UiText.Resource(R.string.reaction_described, catalogIndex + 1)
    } else {
        UiText.Plain(reaction.key)
    }
    val count = UiText.Plural(R.plurals.reaction_count, reaction.count, reaction.count)
    return UiText.Resource(if (reaction.self) R.string.reaction_with_count_self else R.string.reaction_with_count, name, count)
}
