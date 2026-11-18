package io.github.brealorg.subshell.ui.app

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import io.github.brealorg.subshell.R
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.brealorg.subshell.backup.SettingsBackupRepository
import io.github.brealorg.subshell.ui.InteractionSide
import io.github.brealorg.subshell.ui.PostLayout
import io.github.brealorg.subshell.ui.RedditLinkHandlingState
import io.github.brealorg.subshell.ui.RedditLinkHandlingStatus
import io.github.brealorg.subshell.ui.SubshellState
import io.github.brealorg.subshell.ui.ThemeChoice
import io.github.brealorg.subshell.ui.VideoAutoplay
import io.github.brealorg.subshell.ui.components.SegmentedChoiceRow
import io.github.brealorg.subshell.ui.components.SubshellWordmark
import io.github.brealorg.subshell.ui.symbols.Symbols
import io.github.brealorg.subshell.ui.theme.AppTextScale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ISSUE271_ONE_SETUP_FLOW_AFTER_SIGN_IN_V1 (owner 2026-10-28): after the first
 * sign-in, one full-screen flow asks for the choices that matter — hand,
 * look, content and video, Reddit links, backups — instead of the link
 * screen, the side dialog and the backup dialog interrupting one after
 * another. DEFAULTS102_NO_NOTIFICATION_PAGE_V1 (owner 2026-11-11): there is
 * no notification page; inbox notifications are on by default and Android's
 * own question comes once when the flow ends (InboxNotificationPermissionEffect). Every page can be skipped and
 * says where to change it later. It runs while the interaction side has never
 * been chosen (a fresh install); Skip or Done confirms the side, so it never
 * returns and existing installs never see it.
 */
internal enum class FirstRunSetupPage { HAND, LOOK, CONTENT, LINKS, BACKUPS, DONE }

/** The link page only where Android lets subshell open Reddit links and it does not yet. */
internal fun firstRunSetupPages(linkHandlingState: RedditLinkHandlingState): List<FirstRunSetupPage> =
    FirstRunSetupPage.entries.filter { page ->
        page != FirstRunSetupPage.LINKS ||
            (linkHandlingState.supported && linkHandlingState.status != RedditLinkHandlingStatus.ENABLED)
    }

@Composable
internal fun FirstRunSetupScreen(
    state: SubshellState,
    backupRepository: SettingsBackupRepository,
    linkHandlingState: RedditLinkHandlingState,
    onOpenRedditLinkSettings: () -> Unit,
    onDismissRedditLinkPrompt: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pages = firstRunSetupPages(linkHandlingState)
    var pageName by rememberSaveable { mutableStateOf(FirstRunSetupPage.HAND.name) }
    val page = FirstRunSetupPage.valueOf(pageName)
    val index = pages.indexOf(page).coerceAtLeast(0)
    val steps = pages.size - 1

    fun goTo(target: FirstRunSetupPage) {
        pageName = target.name
    }
    fun next() {
        pages.getOrNull(index + 1)?.let(::goTo)
    }
    fun back() {
        pages.getOrNull(index - 1)?.let(::goTo)
    }
    // Done or Skip: the side is the one shown (Right unless picked), and the
    // link and backup questions are answered, so none of them comes back.
    fun finish() {
        onDismissRedditLinkPrompt()
        backupRepository.dismissFolderOnboarding()
        state.confirmInteractionSide()
    }

    BackHandler(enabled = index > 0) { back() }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(start = 24.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SubshellWordmark(Modifier.weight(1f))
                if (page != FirstRunSetupPage.DONE) {
                    TextButton(onClick = ::finish) { Text(stringResource(R.string.setup_skip)) }
                }
            }
            if (page != FirstRunSetupPage.DONE) {
                SetupProgress(step = index, steps = steps)
            }
            // Each page starts at its top; a page taller than the screen says so.
            val scroll = rememberSaveable(page, saver = ScrollState.Saver) { ScrollState(0) }
            val scrollScope = rememberCoroutineScope()
            Box(Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (page) {
                    FirstRunSetupPage.HAND -> HandPage(state)
                    FirstRunSetupPage.LOOK -> LookPage(state)
                    FirstRunSetupPage.CONTENT -> ContentPage(state)
                    FirstRunSetupPage.LINKS -> LinksPage(
                        linkHandlingState = linkHandlingState,
                        onOpenSettings = onOpenRedditLinkSettings,
                        onDone = ::next,
                    )
                    FirstRunSetupPage.BACKUPS -> BackupsPage(backupRepository)
                    FirstRunSetupPage.DONE -> DonePage(state, linkHandlingState, backupRepository)
                }
            }
            // Only when a real part of the page is hidden, not a few pixels of padding.
            val hiddenBelow = with(LocalDensity.current) { (scroll.maxValue - scroll.value).toDp() }
            if (hiddenBelow > 48.dp) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, MaterialTheme.colorScheme.surface),
                            ),
                        )
                        .padding(top = 24.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    TextButton(onClick = { scrollScope.launch { scroll.animateScrollTo(scroll.maxValue) } }) {
                        Icon(Symbols.ExpandMore, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.setup_more_below))
                    }
                }
            }
            }
            if (page != FirstRunSetupPage.DONE) {
                Text(
                    stringResource(R.string.all_you_can_change_this_later_in_settings),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (index > 0 && page != FirstRunSetupPage.DONE) {
                    TextButton(onClick = ::back) { Text(stringResource(R.string.setup_back)) }
                }
                Spacer(Modifier.weight(1f))
                when (page) {
                    // These pages move on with their own answer.
                    FirstRunSetupPage.LINKS -> Unit
                    FirstRunSetupPage.DONE -> Button(onClick = ::finish) { Text(stringResource(R.string.setup_start)) }
                    else -> Button(onClick = ::next) { Text(stringResource(R.string.setup_next)) }
                }
            }
        }
    }
}

@Composable
private fun SetupProgress(step: Int, steps: Int) {
    val stepDescription = stringResource(R.string.setup_step, step + 1, steps)
    Row(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .semantics { contentDescription = stepDescription },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(steps) { dot ->
            Box(
                Modifier
                    .size(width = if (dot == step) 20.dp else 8.dp, height = 8.dp)
                    .background(
                        if (dot == step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(4.dp),
                    ),
            )
        }
    }
}

@Composable
private fun PageTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineMedium)
}

@Composable
private fun PageText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun HandPage(state: SubshellState) {
    PageTitle(stringResource(R.string.all_which_hand_do_you_use))
    PageText(stringResource(R.string.set_vote_and_reply_buttons_the_side_menu))
    Row(
        modifier = Modifier.fillMaxWidth().selectableGroup().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        listOf(InteractionSide.LEFT to stringResource(R.string.all_left_hand), InteractionSide.RIGHT to stringResource(R.string.all_right_hand)).forEach { (side, label) ->
            val selected = state.interactionSide == side
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .selectable(selected = selected, role = Role.RadioButton) { state.previewInteractionSide(side) },
                shape = MaterialTheme.shapes.large,
                color = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                border = BorderStroke(
                    2.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    HandPreview(side)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selected, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
    Text(
        stringResource(R.string.all_the_orange_edge_is_where_the_side),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A small phone: a post card with its buttons, the menu edge and the Create button on [side]. */
@Composable
private fun HandPreview(side: InteractionSide) {
    val colors = MaterialTheme.colorScheme
    val toSide = if (side == InteractionSide.LEFT) Alignment.Start else Alignment.End
    val edge = if (side == InteractionSide.LEFT) Alignment.CenterStart else Alignment.CenterEnd
    val fab = if (side == InteractionSide.LEFT) Alignment.BottomStart else Alignment.BottomEnd
    Box(
        Modifier
            .size(width = 124.dp, height = 220.dp)
            .background(colors.surface, RoundedCornerShape(18.dp))
            .padding(2.dp),
    ) {
        Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth().height(6.dp).background(colors.outlineVariant, RoundedCornerShape(3.dp)))
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceContainerHigh, RoundedCornerShape(10.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = toSide,
            ) {
                Box(Modifier.fillMaxWidth(0.7f).height(6.dp).background(colors.outlineVariant, RoundedCornerShape(3.dp)))
                Box(Modifier.fillMaxWidth().height(44.dp).background(colors.secondaryContainer, RoundedCornerShape(6.dp)))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(3) { Box(Modifier.size(14.dp).background(colors.primary, RoundedCornerShape(7.dp))) }
                }
            }
        }
        Box(
            Modifier
                .align(edge)
                .size(width = 4.dp, height = 64.dp)
                .background(Color(0xFFFF8A3D), RoundedCornerShape(2.dp)),
        )
        Box(
            Modifier
                .align(fab)
                .padding(10.dp)
                .size(30.dp)
                .background(colors.primaryContainer, RoundedCornerShape(10.dp)),
        )
    }
}

@Composable
private fun LookPage(state: SubshellState) {
    PageTitle(stringResource(R.string.setup_look_title))
    // How posts look with the layout and theme chosen below.
    PostLayoutPreview(state.postLayout)
    val themeLabels = ThemeChoice.entries.associateWith { stringResource(it.shortLabelRes) }
    SegmentedChoiceRow(
        options = ThemeChoice.entries,
        selected = state.themeChoice,
        label = { choice -> themeLabels.getValue(choice) },
        onSelect = state::updateThemeChoice,
        showSelectedIcon = false,
    )
    val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    if (dynamicSupported) {
        SetupSwitchRow(
            title = stringResource(R.string.set_dynamic_colour),
            description = stringResource(R.string.set_wallpaper_colours_instead_of_subshell_orange),
            checked = state.dynamicColor,
            onCheckedChange = state::updateDynamicColor,
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PostLayout.entries.forEach { layout ->
            val selected = state.postLayout == layout
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .selectable(selected = selected, role = Role.RadioButton) { state.updatePostLayout(layout) },
                shape = MaterialTheme.shapes.medium,
                color = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                border = BorderStroke(
                    2.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(postLayoutIcon(layout), contentDescription = null)
                    Text(
                        stringResource(layout.labelRes),
                        // bodySmall, not labelMedium: the label styles use the code font.
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
    Text(stringResource(R.string.set_text_size), style = MaterialTheme.typography.titleSmall)
    SegmentedChoiceRow(
        options = AppTextScale.entries,
        selected = state.appTextScale,
        label = { it.label },
        onSelect = state::updateAppTextScale,
        showSelectedIcon = false,
    )
}

private val previewPosts = listOf(
    Triple("r/battlestations", R.string.app_finally_happy_with_my_desk, R.drawable.media_linux_desk),
    Triple("r/homelab", R.string.app_the_rack_is_done_for_now, R.drawable.media_homelab),
    Triple("r/androiddev", R.string.app_where_the_apps_get_made, R.drawable.media_android_desk),
)

/**
 * Posts as the feed shows them in [layout] (Large: one big card with its
 * picture and votes; Compact: smaller cards with a shorter picture; List: the
 * title with a thumbnail; Grid: square tiles), in the theme's colours, at one
 * fixed height so switching layouts does not move the page.
 */
@Composable
private fun PostLayoutPreview(layout: PostLayout) {
    val previewDescription = stringResource(R.string.setup_preview_of_the_post_layout, stringResource(layout.labelRes))
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(236.dp)
            .semantics { contentDescription = previewDescription },
        shape = MaterialTheme.shapes.large,
        color = colors.background,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (layout) {
                PostLayout.LARGE -> previewPosts.first().let { (community, title, image) ->
                    PreviewCard(community, stringResource(title), image, mediaHeight = 124, stats = true)
                }
                PostLayout.COMPACT -> previewPosts.take(2).forEach { (community, title, image) ->
                    PreviewCard(community, stringResource(title), image, mediaHeight = 38, stats = false)
                }
                PostLayout.LIST -> previewPosts.forEach { (community, title, image) ->
                    PreviewListRow(community, stringResource(title), image)
                }
                PostLayout.GRID -> repeat(2) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(3) { column ->
                            PreviewImage(
                                previewPosts[(row + column) % previewPosts.size].third,
                                Modifier.weight(1f).height(103.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewImage(image: Int, modifier: Modifier) {
    Image(
        painter = painterResource(image),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(RoundedCornerShape(8.dp)),
    )
}

@Composable
private fun PreviewCard(community: String, title: String, image: Int, mediaHeight: Int, stats: Boolean) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainerLow, RoundedCornerShape(12.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(community, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1)
        Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        PreviewImage(image, Modifier.fillMaxWidth().height(mediaHeight.dp))
        if (stats) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Symbols.ThumbUp, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.onSurfaceVariant)
                Text(stringResource(R.string.set_1_2k), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                Icon(Symbols.ThumbDown, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Icon(Symbols.Comment, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.onSurfaceVariant)
                Text("84", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PreviewListRow(community: String, title: String, image: Int) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainerLow, RoundedCornerShape(10.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            Text(community, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        PreviewImage(image, Modifier.size(48.dp))
    }
}

private fun postLayoutIcon(layout: PostLayout): ImageVector = when (layout) {
    PostLayout.LARGE -> Symbols.ViewAgenda
    PostLayout.COMPACT -> Symbols.ViewDay
    PostLayout.LIST -> Symbols.ViewList
    PostLayout.GRID -> Symbols.GridView
}

@Composable
private fun SetupSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    ListItem(
        content = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ),
    )
}

@Composable
private fun ContentPage(state: SubshellState) {
    PageTitle(stringResource(R.string.all_what_do_you_want_to_see))
    SectionLabel("NSFW")
    SetupSwitchRow(
        title = stringResource(R.string.set_show_nsfw_posts),
        description = stringResource(R.string.set_posts_reddit_marks_nsfw_appear_in_feeds),
        checked = state.showNsfwPosts,
        onCheckedChange = state::updateShowNsfwPosts,
    )
    SetupSwitchRow(
        title = stringResource(R.string.set_show_nsfw_media_without_a_tap),
        description = if (state.showNsfwPosts) {
            stringResource(R.string.app_nsfw_images_gifs_and_videos_show)
        } else {
            stringResource(R.string.all_turn_on_nsfw_posts_first)
        },
        checked = state.showNsfwPosts && state.showNsfwMedia,
        onCheckedChange = state::updateShowNsfwMedia,
        enabled = state.showNsfwPosts,
    )
    SectionLabel(stringResource(R.string.all_video))
    Text(stringResource(R.string.set_play_videos_and_gifs_by_themselves), style = MaterialTheme.typography.bodyLarge)
    SegmentedChoiceRow(
        options = VideoAutoplay.entries,
        selected = state.videoAutoplay,
        label = { stringResource(it.labelRes) },
        onSelect = state::updateVideoAutoplay,
        showSelectedIcon = false,
    )
    SetupSwitchRow(
        title = stringResource(R.string.set_start_videos_muted),
        description = stringResource(R.string.set_tap_the_sound_button_to_hear_them),
        checked = state.startVideosMuted,
        onCheckedChange = state::updateStartVideosMuted,
    )
}

@Composable
private fun LinksPage(
    linkHandlingState: RedditLinkHandlingState,
    onOpenSettings: () -> Unit,
    onDone: () -> Unit,
) {
    var opened by rememberSaveable { mutableStateOf(false) }
    PageTitle(stringResource(R.string.all_open_reddit_links_here))
    PageText(
        stringResource(R.string.app_links_to_reddit_com_in_other),
    )
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LinkStep(1, stringResource(R.string.all_tap_set_up_android_s_page_for))
            LinkStep(2, stringResource(R.string.all_turn_on_open_supported_links_and_tick))
            LinkStep(3, stringResource(R.string.all_come_back_subshell_checks_what_you_turned))
        }
    }
    val status = when (linkHandlingState.status) {
        RedditLinkHandlingStatus.ENABLED -> stringResource(R.string.all_reddit_links_open_in_subshell)
        RedditLinkHandlingStatus.PARTIAL -> stringResource(R.string.all_some_reddit_addresses_open_in_subshell)
        else -> null
    }
    if (opened && status != null) {
        Text(status, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
    }
    Text(
        stringResource(R.string.all_if_another_reddit_app_already_opens_these),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
        if (opened) {
            Button(onClick = onDone) { Text(stringResource(R.string.set_next)) }
        } else {
            TextButton(onClick = onDone) { Text(stringResource(R.string.set_not_now)) }
            Button(
                onClick = {
                    opened = true
                    onOpenSettings()
                },
            ) { Text(stringResource(R.string.set_set_up)) }
        }
    }
}

@Composable
private fun LinkStep(number: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(28.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                number.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun BackupsPage(repository: SettingsBackupRepository) {
    val scope = rememberCoroutineScope()
    var folder by remember(repository) { mutableStateOf(repository.status().folderLocation) }
    var connected by remember(repository) { mutableStateOf(repository.folderConnected()) }
    var failed by remember { mutableStateOf(false) }
    var found by remember { mutableIntStateOf(0) }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { repository.connectFolder(uri) } }
                    .onSuccess { connection ->
                        failed = !connection.isProtected
                        connected = connection.isProtected
                        found = connection.importedSnapshots
                        folder = repository.status().folderLocation
                    }
                    .onFailure { error ->
                        if (error is CancellationException) throw error
                        failed = true
                    }
            }
        }
    }
    PageTitle(stringResource(R.string.all_keep_your_setup_safe))
    PageText(stringResource(R.string.set_your_settings_are_backed_up_on_this))
    // One row: what a folder is for, or where the copies go.
    ListItem(
        content = { Text(stringResource(R.string.set_backup_folder)) },
        supportingContent = {
            Text(
                when {
                    connected && found > 0 -> pluralStringResource(
                        R.plurals.set_copied_to_folder_found,
                        found,
                        folder ?: stringResource(R.string.set_your_folder),
                        found,
                    )
                    connected -> stringResource(R.string.set_copied_to_folder, folder ?: stringResource(R.string.set_your_folder))
                    failed -> stringResource(R.string.all_couldn_t_use_that_folder_try_another)
                    else -> stringResource(R.string.all_copies_that_survive_removing_subshell)
                },
                color = if (failed && !connected) MaterialTheme.colorScheme.error else Color.Unspecified,
            )
        },
        leadingContent = { Icon(Symbols.Folder, contentDescription = null) },
        trailingContent = {
            if (!connected) {
                FilledTonalButton(onClick = { folderLauncher.launch(null) }) { Text(stringResource(R.string.set_choose)) }
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.clip(MaterialTheme.shapes.large),
    )
    SectionLabel(stringResource(R.string.scr_privacy_sensitive_backup_data))
    PrivacyBackupSwitch(stringResource(R.string.all_hidden_communities), repository::hiddenCommunitiesBackupEnabled, repository::setHiddenCommunitiesBackupEnabled)
    PrivacyBackupSwitch(stringResource(R.string.all_muted_communities), repository::communityMutesBackupEnabled, repository::setCommunityMutesBackupEnabled)
    PrivacyBackupSwitch(stringResource(R.string.all_post_drafts), repository::postComposerDraftsBackupEnabled, repository::setPostComposerDraftsBackupEnabled)
    PrivacyBackupSwitch(
        stringResource(R.string.all_saved_moderator_responses),
        repository::moderatorSavedResponsesBackupEnabled,
        repository::setModeratorSavedResponsesBackupEnabled,
    )
    PrivacyBackupSwitch(stringResource(R.string.all_private_notes_tags), repository::userTagsBackupEnabled, repository::setUserTagsBackupEnabled)
}

@Composable
private fun PrivacyBackupSwitch(title: String, read: () -> Boolean, write: (Boolean) -> Unit) {
    var checked by remember { mutableStateOf(read()) }
    ListItem(
        content = { Text(title) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.toggleable(value = checked, role = Role.Switch) { enabled ->
            checked = enabled
            write(enabled)
        },
    )
}

@Composable
private fun DonePage(
    state: SubshellState,
    linkHandlingState: RedditLinkHandlingState,
    repository: SettingsBackupRepository,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(Symbols.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
        Text(stringResource(R.string.set_you_re_all_set), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(
            stringResource(R.string.all_everything_here_lives_in_settings_if_you),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
    val theme = stringResource(state.themeChoice.shortLabelRes)
    val rows = buildList {
        add(stringResource(R.string.app_summary_hand) to stringResource(if (state.interactionSide == InteractionSide.LEFT) R.string.interaction_side_left else R.string.interaction_side_right))
        add(stringResource(R.string.all_theme_posts_text) to "$theme · ${stringResource(state.postLayout.labelRes)} · ${state.appTextScale.label}")
        add(stringResource(R.string.app_nsfw_posts) to stringResource(if (state.showNsfwPosts) R.string.app_summary_on else R.string.app_summary_off))
        add(stringResource(R.string.all_videos) to if (state.startVideosMuted) stringResource(R.string.app_summary_videos_muted, stringResource(state.videoAutoplay.labelRes)) else stringResource(state.videoAutoplay.labelRes))
        add(stringResource(R.string.all_notifications) to stringResource(if (state.inboxNotificationsEnabled) R.string.app_summary_on else R.string.app_summary_off))
        if (linkHandlingState.supported) {
            add(stringResource(R.string.app_reddit_links) to if (linkHandlingState.status == RedditLinkHandlingStatus.ENABLED) stringResource(R.string.app_open_here) else stringResource(R.string.app_not_set_up))
        }
        add(stringResource(R.string.all_backup_folder) to if (repository.folderConnected()) stringResource(R.string.all_chosen) else stringResource(R.string.all_not_chosen))
    }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            rows.forEach { (key, value) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Text(key, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(value, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
