package io.github.brealorg.subshell.ui.components

import androidx.compose.ui.res.pluralStringResource
import io.github.brealorg.subshell.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Icon
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.brealorg.subshell.data.account.RedditCommunityWikiUiFailureKind
import io.github.brealorg.subshell.data.account.RedditCommunityWikiUiState
import io.github.brealorg.subshell.model.CommunityWikiPageRef
import io.github.brealorg.subshell.ui.symbols.Symbols

// WIKI01A2_COMMUNITY_WIKI_FUNCTIONAL_PRESENTATION_V1
// WIKI01A2R1_DEDICATED_WIKI_WORKSPACE_V1
internal data class CommunityWikiPageGroups(
    val pages: List<CommunityWikiPageRef>,
    val configuration: List<CommunityWikiPageRef>,
)

internal fun communityWikiPageGroups(
    pages: List<CommunityWikiPageRef>,
): CommunityWikiPageGroups {
    val ordered = pages.sortedWith(
        compareBy<CommunityWikiPageRef>(
            { page ->
                if (page.path.equals("index", ignoreCase = true)) 0 else 1
            },
            { page -> page.path.lowercase() },
        ),
    )
    val (configuration, readable) = ordered.partition { page ->
        communityWikiConfigurationPath(page.path)
    }
    return CommunityWikiPageGroups(
        pages = readable,
        configuration = configuration,
    )
}

private fun communityWikiConfigurationPath(path: String): Boolean {
    val normalized = path.trim().lowercase()
    return normalized.startsWith("config/") ||
        normalized == "automoderator-schedule" ||
        normalized.startsWith("automoderator/") ||
        normalized.startsWith("mod/")
}

internal fun LazyListScope.communityWikiItems(
    state: RedditCommunityWikiUiState,
    matches: Boolean,
    selectedPagePath: String?,
    showConfigurationPages: Boolean,
    onSelectedPagePathChange: (String?) -> Unit,
    onShowConfigurationPagesChange: (Boolean) -> Unit,
    onAuthorize: () -> Unit,
    onRetryIndex: () -> Unit,
    onRetryPage: (String) -> Unit,
) {
    if (!matches || state.loading) {
        item("wiki-loading") {
            CommunityWikiLoading(
                label = if (selectedPagePath == null) {
                    stringResource(R.string.all_loading_wiki)
                } else {
                    stringResource(R.string.all_loading_wiki_page)
                },
            )
        }
        return
    }

    if (state.authorizationRequired) {
        item("wiki-authorization") {
            CommunityWikiAuthorization(
                authorizing = state.authorizing,
                onAuthorize = onAuthorize,
            )
        }
        return
    }

    val failure = state.failureKind
    if (failure != null) {
        item("wiki-failure-${failure.name}") {
            CommunityWikiFailure(
                kind = failure,
                httpStatusCode = state.failureHttpStatusCode,
                details = state.failureDetails,
                retryable = state.failureRetryable,
                onRetry = if (state.failureRetryable) {
                    {
                        if (selectedPagePath == null) {
                            onRetryIndex()
                        } else {
                            onRetryPage(selectedPagePath)
                        }
                    }
                } else {
                    null
                },
            )
        }
        return
    }

    if (selectedPagePath != null) {
        val page = state.page
            ?.takeIf { candidate -> candidate.path == selectedPagePath }

        if (page == null) {
            item("wiki-page-awaiting") {
                CommunityWikiLoading(stringResource(R.string.all_loading_wiki_page))
            }
            return
        }

        item("wiki-page-title-${page.path}") {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = page.path,
                    style = MaterialTheme.typography.titleLarge,
                )
                page.revisionAuthor?.let { author ->
                    Text(
                        text = stringResource(R.string.comp_last_revised_by_u, author),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                page.revisionReason?.let { reason ->
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item("wiki-page-body-${page.path}") {
            if (page.markdown.isBlank()) {
                CommunityWikiNotice(
                    title = stringResource(R.string.comp_empty_wiki_page),
                    message = stringResource(R.string.all_reddit_returned_this_wiki_page_without_markdown),
                )
            } else {
                MarkdownText(
                    text = page.markdown,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        return
    }

    val index = state.index
    if (index == null) {
        item("wiki-index-awaiting") {
            CommunityWikiLoading(stringResource(R.string.all_loading_wiki))
        }
        return
    }

    if (index.pages.isEmpty()) {
        item("wiki-index-empty") {
            CommunityWikiNotice(
                title = stringResource(R.string.comp_no_wiki_pages),
                message = stringResource(R.string.all_reddit_did_not_return_any_wiki_pages),
            )
        }
        return
    }

    val groups = communityWikiPageGroups(index.pages)

    item("wiki-index-heading") {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.comp_community_pages),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.comp_choose_a_page_to_read_it_in),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (groups.pages.isEmpty()) {
        item("wiki-readable-pages-empty") {
            CommunityWikiNotice(
                title = stringResource(R.string.comp_no_general_wiki_pages),
                message =
                    stringResource(R.string.all_this_wiki_only_returned_configuration_or_moderation),
            )
        }
    } else {
        items(
            items = groups.pages,
            key = { page -> "wiki-page-${page.path.lowercase()}" },
        ) { page ->
            CommunityWikiPageRow(
                page = page,
                onClick = { onSelectedPagePathChange(page.path) },
            )
        }
    }

    if (groups.configuration.isNotEmpty()) {
        item("wiki-configuration-toggle") {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                content = {
                    Text(
                        text = stringResource(R.string.comp_configuration_pages),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                supportingContent = {
                    Text(
                        text = pluralStringResource(
                            R.plurals.wiki_advanced_pages,
                            groups.configuration.size,
                            groups.configuration.size,
                        ),
                    )
                },
                trailingContent = {
                    Text(
                        text = if (showConfigurationPages) stringResource(R.string.all_hide) else stringResource(R.string.all_show),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onShowConfigurationPagesChange(!showConfigurationPages)
                    },
            )
            HorizontalDivider()
        }

        if (showConfigurationPages) {
            items(
                items = groups.configuration,
                key = { page ->
                    "wiki-configuration-${page.path.lowercase()}"
                },
            ) { page ->
                CommunityWikiPageRow(
                    page = page,
                    onClick = { onSelectedPagePathChange(page.path) },
                )
            }
        }
    }
}

@Composable
private fun CommunityWikiPageRow(
    page: CommunityWikiPageRef,
    onClick: () -> Unit,
) {
    // WIKI01A2R2_EXPRESSIVE_PAGE_ROW_V1
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        ListItem(
            content = {
                Text(
                    text = page.path,
                    maxLines = 2,
                    fontWeight =
                        if (page.path.equals("index", ignoreCase = true)) {
                            FontWeight.SemiBold
                        } else {
                            FontWeight.Normal
                        },
                )
            },
            leadingContent = {
                Icon(
                    imageVector = Symbols.Article,
                    contentDescription = null,
                )
            },
            trailingContent = {
                Icon(
                    imageVector = Symbols.ChevronRight,
                    contentDescription = null, // the row reads the page name (ISSUE219)
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        )
    }
}

@Composable
private fun CommunityWikiAuthorization(
    authorizing: Boolean,
    onAuthorize: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.comp_wiki_access),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text =
                    "Reddit requires separate permission to read community Wiki pages. This permission is read-only.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (authorizing) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InlineProgressIndicator()
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.comp_waiting_for_reddit))
                }
            } else {
                Button(onClick = onAuthorize) {
                    Text(stringResource(R.string.comp_enable_wiki_access))
                }
            }
        }
    }
}

@Composable
private fun CommunityWikiLoading(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InlineProgressIndicator()
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CommunityWikiFailure(
    kind: RedditCommunityWikiUiFailureKind,
    httpStatusCode: Int?,
    // BLOCKPAGE02_SMALLER_READ_SURFACES_NAME_THE_BLOCK_PAGE_V1
    details: io.github.brealorg.subshell.data.error.FailureDetails?,
    retryable: Boolean,
    onRetry: (() -> Unit)?,
) {
    val title: String
    val message: String
    when (kind) {
        RedditCommunityWikiUiFailureKind.UNAVAILABLE -> {
            title = stringResource(R.string.comp_wiki_unavailable)
            message =
                stringResource(R.string.all2_this_community_wiki_is_unavailable_through_reddit)
        }
        RedditCommunityWikiUiFailureKind.CLIENT_CONFIGURATION -> {
            title = stringResource(R.string.comp_wiki_unavailable)
            message = stringResource(R.string.all_wiki_access_is_not_configured_for_this)
        }
        RedditCommunityWikiUiFailureKind.SESSION -> {
            title = stringResource(R.string.comp_wiki_access_needs_attention)
            message = stringResource(R.string.all_the_reddit_session_could_not_be_used)
        }
        RedditCommunityWikiUiFailureKind.HTTP -> {
            title = stringResource(R.string.comp_couldn_t_load_wiki)
            message = io.github.brealorg.subshell.ui.error.redditWikiHttpFailureMessage(httpStatusCode, details).asString()
        }
        RedditCommunityWikiUiFailureKind.TRANSPORT -> {
            title = stringResource(R.string.comp_couldn_t_reach_reddit)
            message = "subshell couldn't reach Reddit while loading this Wiki."
        }
        RedditCommunityWikiUiFailureKind.PROTOCOL -> {
            title = stringResource(R.string.comp_couldn_t_read_wiki)
            message =
                stringResource(R.string.all_reddit_returned_a_wiki_response_that_subshell)
        }
    }

    CommunityWikiNotice(
        title = title,
        message = message,
        action = if (retryable && onRetry != null) {
            {
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.comp_retry))
                }
            }
        } else {
            null
        },
    )
}

@Composable
private fun CommunityWikiNotice(
    title: String,
    message: String,
    action: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            action?.invoke()
        }
    }
}
