package io.github.brealorg.subshell.ui.components

import androidx.compose.ui.res.pluralStringResource
import io.github.brealorg.subshell.ui.UiText
import io.github.brealorg.subshell.R
import androidx.compose.ui.res.stringResource
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.brealorg.subshell.model.Post
import io.github.brealorg.subshell.model.PostKind
import io.github.brealorg.subshell.model.UrlCleaner
import io.github.brealorg.subshell.model.markdownLinkTargetsForRenderedText
import io.github.brealorg.subshell.model.redditReadableText
import io.github.brealorg.subshell.ui.error.LocalViewportErrorHostState
import io.github.brealorg.subshell.ui.error.ViewportErrorHostState
import io.github.brealorg.subshell.ui.noticeCopied
import io.github.brealorg.subshell.ui.noticeFailure
import io.github.brealorg.subshell.ui.symbols.Symbols

/**
 * ISSUE300_ONE_COPY_ENTRY_OPENS_A_SHEET_V1 (#300, owner 2026-11-13: one "Copy…" entry, not three
 * menu entries; the post menu was shortened in ISSUE295): a post and a comment offer the same
 * sheet: Copy text, Select text, Copy link to the post/comment, and every web link in the text as
 * a link card that opens the link menu (LINKMENU01).
 */
internal val COPY_MENU_ENTRY_LABEL: Int = R.string.comp_copy_menu

/** What the copy sheet offers for one post or comment. */
internal data class CopySheetContent(
    /** A comment's sheet (else a post's); each text is its own sentence (TRANSLATE14). */
    val isComment: Boolean,
    /** A title shown above the text in Select text (a post's title), or null. */
    val title: String?,
    /** The Markdown source of the text, as stored. */
    val body: String,
    /** The canonical Reddit link of the post or comment. */
    val redditLink: String,
    /** Web links in the item, as written (originals; the link menu cleans them). */
    val links: List<String>,
) {
    /** The text Copy text puts on the clipboard: title and body, images as their address. */
    val copyText: String
        get() = listOfNotNull(
            title?.trim()?.takeIf(String::isNotEmpty),
            body.redditReadableText().trim().takeIf(String::isNotEmpty),
        ).joinToString("\n\n")
}

internal fun postCopySheetContent(post: Post, redditLink: String): CopySheetContent =
    CopySheetContent(
        isComment = false,
        title = post.title,
        body = post.body,
        redditLink = redditLink,
        links = (
            listOfNotNull(post.externalUrl?.trim()?.takeIf { post.kind == PostKind.LINK }) +
                webLinksInMarkdown(post.body)
            ).filter(::isWebLink).distinct(),
    )

internal fun commentCopySheetContent(body: String, redditLink: String): CopySheetContent =
    CopySheetContent(
        isComment = true,
        title = null,
        body = body,
        redditLink = redditLink,
        links = webLinksInMarkdown(body),
    )

/**
 * Every web link the rendered [source] shows, in reading order, once each, as written. Found
 * the same way MarkdownText finds them (per block, labelled links and bare addresses); code
 * blocks, Reddit mentions and image placeholders are not links here.
 */
internal fun webLinksInMarkdown(source: String): List<String> =
    parseMarkdownBlocks(source)
        .mapNotNull { block ->
            when (block) {
                is MarkdownBlock.Paragraph -> block.text
                is MarkdownBlock.Heading -> block.text
                is MarkdownBlock.Quote -> block.text
                is MarkdownBlock.Spoiler -> block.text
                is MarkdownBlock.Code, MarkdownBlock.HorizontalRule -> null
            }
        }
        .flatMap { text ->
            val rendered = markdownStyledAnnotatedString(
                source = text,
                linkColor = Color.Unspecified,
                codeBackground = Color.Unspecified,
                codeFontFamily = FontFamily.Monospace,
            )
            markdownLinkTargetsForRenderedText(
                source = text,
                renderedText = rendered.text,
                renderedMarkdownLinks = rendered.renderedMarkdownLinks(),
            ).sortedBy { link -> link.start }.map { link -> link.url.trim() }
        }
        .filter(::isWebLink)
        .distinct()

private fun isWebLink(value: String): Boolean =
    value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CopySheet(
    content: CopySheetContent,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val notices = LocalViewportErrorHostState.current
    val cleanLinks = LocalCleanLinks.current
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    var selecting by remember(content) { mutableStateOf(false) }
    var linkAction by remember(content) { mutableStateOf<LinkActionTarget?>(null) }
    linkAction?.let { target ->
        LinkActionsSheet(
            target = target,
            onDismiss = {
                linkAction = null
                onDismiss()
            },
        )
        return
    }
    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
    ) {
        if (selecting) {
            SelectTextContent(content)
            return@ModalBottomSheet
        }
        CopyListItem(
            label = stringResource(R.string.comp_copy_text),
            icon = { Icon(Symbols.ContentCopy, contentDescription = null) },
            enabled = content.copyText.isNotEmpty(),
            onClick = {
                onDismiss()
                copyPlainText(
                    context,
                    context.getString(if (content.isComment) R.string.comp_clip_comment_text else R.string.comp_clip_post_text),
                    content.copyText,
                    UiText.Resource(R.string.nt_copied_what_text),
                    notices,
                )
            },
        )
        CopyListItem(
            label = stringResource(R.string.comp_select_text),
            supporting = stringResource(if (content.isComment) R.string.comp_copy_part_comment else R.string.comp_copy_part_post),
            icon = { Icon(Symbols.Edit, contentDescription = null) },
            enabled = content.copyText.isNotEmpty(),
            onClick = { selecting = true },
        )
        CopyListItem(
            label = stringResource(if (content.isComment) R.string.comp_copy_link_to_comment else R.string.comp_copy_link_to_post),
            icon = { Icon(Symbols.Link, contentDescription = null) },
            onClick = {
                onDismiss()
                copyPlainText(
                    context,
                    context.getString(if (content.isComment) R.string.comp_clip_comment_link else R.string.comp_clip_post_link),
                    content.redditLink,
                    UiText.Resource(R.string.nt_copied_what_link),
                    notices,
                )
            },
        )
        if (content.links.isNotEmpty()) {
            Text(
                text = pluralStringResource(
                    if (content.isComment) R.plurals.comp_links_in_comment else R.plurals.comp_links_in_post,
                    content.links.size,
                ),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp, bottom = 4.dp)
                    .semantics { heading() },
            )
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 16.dp),
            ) {
                content.links.forEach { original ->
                    val target = linkActionTarget(original, cleanLinks) ?: return@forEach
                    LinkCardSurface(
                        domain = linkDomain(target.link),
                        address = UrlCleaner.cardLabel(target.link),
                        iconDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clickable(onClickLabel = stringResource(R.string.comp_link_actions)) { linkAction = target },
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectTextContent(content: CopySheetContent) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.comp_select_text),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 4.dp).semantics { heading() },
        )
        Text(
            text = stringResource(R.string.comp_long_press_to_select_then_copy),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        SelectionContainer {
            Column {
                content.title?.takeIf(String::isNotBlank)?.let { title ->
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                val body = content.body.redditReadableText()
                if (body.isNotBlank()) MarkdownText(text = body)
            }
        }
    }
}

@Composable
private fun CopyListItem(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    supporting: String? = null,
    enabled: Boolean = true,
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        content = { Text(label) },
        supportingContent = supporting?.let { text -> { Text(text) } },
        leadingContent = icon,
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    )
}

internal fun copyPlainText(
    context: Context,
    clipLabel: String,
    text: String,
    noticeWhat: UiText,
    notices: ViewportErrorHostState?,
) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    if (clipboard == null) {
        notices.noticeFailure(UiText.Resource(R.string.nt_couldn_t_copy), UiText.Resource(R.string.nt_the_clipboard_is_unavailable_2))
        return
    }
    clipboard.setPrimaryClip(ClipData.newPlainText(clipLabel, text))
    notices.noticeCopied(noticeWhat)
}
