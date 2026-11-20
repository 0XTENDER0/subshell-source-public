package io.github.brealorg.subshell.ui.components

import io.github.brealorg.subshell.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.AppBarMenuState
import androidx.compose.material3.AppBarRow
import androidx.compose.material3.AppBarRowScope
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.brealorg.subshell.ui.MarkdownAction
import io.github.brealorg.subshell.ui.ReadingMaxWidth
import io.github.brealorg.subshell.ui.symbols.Symbols

private data class ComposerFormatAction(
    val action: MarkdownAction,
    @androidx.annotation.StringRes val labelRes: Int,
    val icon: ImageVector,
)

private val composerFormatActions = listOf(
    ComposerFormatAction(MarkdownAction.BOLD, R.string.comp_format_bold, Symbols.FormatBold),
    ComposerFormatAction(MarkdownAction.ITALIC, R.string.comp_format_italic, Symbols.FormatItalic),
    ComposerFormatAction(MarkdownAction.LINK, R.string.comp_format_link, Symbols.Link),
    ComposerFormatAction(MarkdownAction.CODE_BLOCK, R.string.comp_format_code_block, Symbols.Code),
    ComposerFormatAction(MarkdownAction.QUOTE, R.string.comp_format_quote, Symbols.FormatQuote),
    ComposerFormatAction(
        MarkdownAction.BULLET_LIST,
        R.string.comp_format_bulleted_list,
        Symbols.FormatListBulleted,
    ),
    ComposerFormatAction(
        MarkdownAction.NUMBERED_LIST,
        R.string.comp_format_numbered_list,
        Symbols.FormatListNumbered,
    ),
    ComposerFormatAction(
        MarkdownAction.STRIKETHROUGH,
        R.string.comp_format_strikethrough,
        Symbols.StrikethroughS,
    ),
    ComposerFormatAction(MarkdownAction.INLINE_CODE, R.string.comp_format_inline_code, Symbols.Code),
    ComposerFormatAction(MarkdownAction.SPOILER, R.string.comp_format_spoiler, Symbols.VisibilityOff),
)

/** Undo and redo, then the first four formats; the rest go to "More formatting". */
internal const val COMPOSER_FORMATTING_VISIBLE_ITEMS = 6

/**
 * COMPOSER232_FORMATTING_IS_AN_APP_BAR_ROW_V1 (#232): the formatting actions are
 * one M3 AppBarRow item set, shared by the quick reply's Aa row, the long reply
 * and the docked bar of the edit screens. AppBarRow moves what does not fit
 * into the overflow menu by itself; each icon has its tooltip.
 */
@OptIn(ExperimentalFoundationApi::class)
internal fun AppBarRowScope.composerFormattingItems(
    // TRANSLATE14: the row's labels are read here (this scope is not composable).
    resources: android.content.res.Resources,
    state: TextFieldState,
    enabled: Boolean,
    onFormat: (MarkdownAction) -> Unit,
    onReturnToEditor: () -> Unit,
) {
    clickableItem(
        onClick = {
            onReturnToEditor()
            state.undoState.undo()
        },
        icon = { Icon(Symbols.Undo, contentDescription = stringResource(R.string.comp_undo)) },
        label = resources.getString(R.string.comp_undo),
        enabled = enabled && state.undoState.canUndo,
    )
    clickableItem(
        onClick = {
            onReturnToEditor()
            state.undoState.redo()
        },
        icon = { Icon(Symbols.Redo, contentDescription = stringResource(R.string.comp_redo)) },
        label = resources.getString(R.string.comp_redo),
        enabled = enabled && state.undoState.canRedo,
    )
    composerFormatActions.forEach { item ->
        clickableItem(
            onClick = {
                onReturnToEditor()
                onFormat(item.action)
            },
            icon = { Icon(item.icon, contentDescription = resources.getString(item.labelRes)) },
            label = resources.getString(item.labelRes),
            enabled = enabled,
        )
    }
}

@Composable
internal fun ComposerFormattingOverflow(menuState: AppBarMenuState, enabled: Boolean = true) {
    IconButton(enabled = enabled, onClick = { menuState.show() }) {
        Icon(Symbols.MoreHoriz, contentDescription = stringResource(R.string.comp_more_formatting))
    }
}

/**
 * Canonical formatting dock shared by Create Post, the edit screens and the
 * moderator removal message. The quick reply shows the same items behind Aa.
 */
@Composable
fun ComposerFormattingDock(
    state: TextFieldState,
    onFormat: (MarkdownAction) -> Unit,
    onReturnToEditor: () -> Unit,
    modifier: Modifier = Modifier,
    // POSTEDIT02_EDITOR_LOCKED_WHILE_IN_FLIGHT_V1: off while the text belongs
    // to a request in flight (undo/redo edit the state directly).
    enabled: Boolean = true,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        // M3225_DOCKED_BARS_ARE_FLAT_SURFACE_CONTAINERS_V1 (#225, components-13,
        // settings-15): a docked bar is surfaceContainer and flat, as M3's
        // bottom app bar, not the legacy background role with a 10 dp shadow.
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        val formattingResources = androidx.compose.ui.platform.LocalResources.current
        AppBarRow(
            modifier = Modifier
                .fillMaxWidth()
                // Clear of the gesture/navigation bar while the keyboard is hidden.
                .navigationBarsPadding()
                // M3224_READING_WIDTH_V1: the actions stay together on a wide
                // window instead of spreading edge to edge.
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = ReadingMaxWidth)
                .padding(horizontal = 4.dp, vertical = 3.dp),
            overflowIndicator = { menuState -> ComposerFormattingOverflow(menuState, enabled) },
            maxItemCount = COMPOSER_FORMATTING_VISIBLE_ITEMS + 1,
        ) {
            composerFormattingItems(
                resources = formattingResources,
                state = state,
                enabled = enabled,
                onFormat = onFormat,
                onReturnToEditor = onReturnToEditor,
            )
        }
    }
}
