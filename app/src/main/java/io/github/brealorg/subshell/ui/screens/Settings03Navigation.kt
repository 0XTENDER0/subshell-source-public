package io.github.brealorg.subshell.ui.screens

import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import io.github.brealorg.subshell.R

// SUBSHELL_SETTINGS03A_TASK_BASED_MATERIAL_SETTINGS_V1
internal enum class SettingsDestination(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int?,
    internal val legacySections: Set<String> = emptySet(),
) {
    ROOT(
        titleRes = R.string.settings_destination_settings_title,
        descriptionRes = null,
    ),
    ACCOUNTS(
        titleRes = R.string.settings_destination_accounts_title,
        descriptionRes = R.string.settings_destination_reddit_accounts_privacy_and_connection_description,
        legacySections = setOf("Accounts", "Account privacy", "Reddit connection"),
    ),
    APPEARANCE(
        titleRes = R.string.settings_destination_appearance_title,
        descriptionRes = R.string.settings_destination_theme_colours_voting_fonts_and_motion_description,
        legacySections = setOf("Appearance", "Accessibility"),
    ),
    NAVIGATION(
        titleRes = R.string.settings_destination_navigation_gestures_title,
        descriptionRes = R.string.settings_destination_controls_side_menu_navigation_bars_and_description,
        legacySections = setOf("Home & feeds", "Navigation", "Search"),
    ),
    FEEDS(
        titleRes = R.string.settings_destination_feeds_content_title,
        descriptionRes = R.string.settings_destination_start_feed_sorting_unread_state_sensitive_description,
        legacySections = setOf("Feed", "Privacy"),
    ),
    MEDIA(
        titleRes = R.string.settings_destination_media_links_title,
        descriptionRes = R.string.settings_destination_playback_quality_storage_and_captions_description,
        legacySections = setOf("Media & links"),
    ),
    COMMENTS(
        titleRes = R.string.settings_destination_comments_notifications_title,
        descriptionRes = R.string.settings_destination_comment_behaviour_actions_and_inbox_alerts_description,
        legacySections = setOf("Comments", "Notifications"),
    ),
    BACKUP(
        titleRes = R.string.settings_destination_backup_restore_title,
        descriptionRes = R.string.settings_destination_what_s_backed_up_your_backups_description,
    ),
    ADVANCED(
        titleRes = R.string.settings_destination_advanced_title,
        descriptionRes = R.string.settings_destination_developer_tools_and_diagnostics_description,
        legacySections = setOf("Diagnostics", "Developer states"),
    ),
    // ISSUE286_SETTINGS_UPDATES_V1
    UPDATES(
        titleRes = R.string.settings_destination_updates_title,
        descriptionRes = R.string.settings_destination_your_version_the_latest_one_and_description,
    ),
    ABOUT(
        titleRes = R.string.settings_destination_help_about_title,
        descriptionRes = R.string.settings_destination_version_help_and_application_information_description,
    ),
    NOTIFICATION_TYPES(
        titleRes = R.string.settings_destination_notification_types_title,
        descriptionRes = R.string.settings_destination_choose_which_reddit_activity_can_notify_description,
    ),
    // ISSUE239_WATCHED_COMMUNITIES_IN_SETTINGS_V1
    NEW_POST_NOTIFICATIONS(
        titleRes = R.string.settings_destination_new_posts_title,
        descriptionRes = R.string.settings_destination_communities_that_notify_you_about_new_description,
    ),
    CONTENT_FILTERS(
        titleRes = R.string.settings_destination_content_filters_title,
        descriptionRes = null,
    ),
    SENSITIVE_MEDIA_LAB(
        titleRes = R.string.settings_destination_sensitive_media_reddit_lab_title,
        descriptionRes = null,
    ),
    DIAGNOSTICS(
        titleRes = R.string.settings_destination_diagnostics_title,
        descriptionRes = R.string.settings_destination_local_structured_event_history_and_shareable_description,
    );

    companion object {
        fun fromName(name: String): SettingsDestination =
            entries.firstOrNull { it.name == name } ?: ROOT
    }
}

internal val settingsRootDestinations: List<SettingsDestination> = listOf(
    SettingsDestination.ACCOUNTS,
    SettingsDestination.APPEARANCE,
    SettingsDestination.NAVIGATION,
    SettingsDestination.FEEDS,
    SettingsDestination.MEDIA,
    SettingsDestination.COMMENTS,
    SettingsDestination.BACKUP,
    SettingsDestination.ADVANCED,
    SettingsDestination.UPDATES,
    SettingsDestination.ABOUT,
)

internal fun settingsSectionVisible(
    destination: SettingsDestination,
    query: String,
    searchResult: SettingsSearchResult,
    section: String,
    debug: Boolean,
): Boolean {
    if (section == "Developer states" && !debug) return false
    return when (destination) {
        SettingsDestination.ROOT ->
            query.isNotBlank() && searchResult.isSectionVisible(section)
        else ->
            section in destination.legacySections &&
                searchResult.isSectionVisible(section)
    }
}

internal fun settingsSectionTitle(
    destination: SettingsDestination,
    legacySection: String,
): String = when (destination) {
    SettingsDestination.APPEARANCE -> when (legacySection) {
        "Appearance" -> "Theme & style"
        "Accessibility" -> "Motion"
        else -> legacySection
    }
    SettingsDestination.NAVIGATION -> when (legacySection) {
        "Home & feeds" -> "Navigation bars"
        "Navigation" -> "Controls & side menu"
        else -> legacySection
    }
    SettingsDestination.FEEDS ->
        if (legacySection == "Privacy") "Hidden communities" else "Feed & content"
    SettingsDestination.ACCOUNTS ->
        if (legacySection == "Reddit connection") "Connection" else legacySection
    SettingsDestination.ADVANCED ->
        if (legacySection == "Developer states") "Developer" else legacySection
    else -> legacySection
}

internal fun settingsDestinationBackTarget(
    destination: SettingsDestination,
    returnDestination: SettingsDestination,
): SettingsDestination = when (destination) {
    SettingsDestination.ROOT -> SettingsDestination.ROOT
    SettingsDestination.NOTIFICATION_TYPES,
    SettingsDestination.NEW_POST_NOTIFICATIONS,
    SettingsDestination.CONTENT_FILTERS,
    SettingsDestination.SENSITIVE_MEDIA_LAB,
    SettingsDestination.DIAGNOSTICS,
    SettingsDestination.BACKUP,
    SettingsDestination.UPDATES,
    SettingsDestination.ABOUT -> returnDestination
    else -> SettingsDestination.ROOT
}
