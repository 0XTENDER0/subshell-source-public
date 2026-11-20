package io.github.brealorg.subshell.ui.components

import io.github.brealorg.subshell.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.brealorg.subshell.media.VideoPlaybackDiagnostics
import io.github.brealorg.subshell.media.PlaybackTrackChoice
import io.github.brealorg.subshell.media.PlaybackTrackKey
import io.github.brealorg.subshell.media.PlaybackTrackOptions
import io.github.brealorg.subshell.media.PlaybackTrackSelectionMode
import io.github.brealorg.subshell.media.audioSelectionSummary
import io.github.brealorg.subshell.media.audioSummary
import io.github.brealorg.subshell.media.decoderSummary
import io.github.brealorg.subshell.media.sourceSummary
import io.github.brealorg.subshell.media.textSelectionSummary
import io.github.brealorg.subshell.media.textSummary
import io.github.brealorg.subshell.media.videoSelectionSummary
import io.github.brealorg.subshell.media.videoSummary
import io.github.brealorg.subshell.media.VIDEO_SPEED_CHOICES
import io.github.brealorg.subshell.media.videoSpeedLabel
import io.github.brealorg.subshell.ui.symbols.Symbols

internal fun playbackAudioChoiceOffered(options: PlaybackTrackOptions): Boolean = options.audio.size > 1

internal fun playbackSubtitleChoiceOffered(options: PlaybackTrackOptions): Boolean = options.text.isNotEmpty()

@Composable
internal fun PlaybackOptionsPanel(
    options: PlaybackTrackOptions,
    automaticVideoQuality: String,
    onQuality: () -> Unit,
    onAudio: () -> Unit,
    onSubtitles: () -> Unit,
    onTechnicalDetails: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    speedSummary: String? = null,
    onSpeed: () -> Unit = {},
    mediaActionRows: (@Composable () -> Unit)? = null,
    showTechnicalDetails: Boolean = true,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlaybackPanelHeader(
                title = stringResource(R.string.comp_playback_options),
                subtitle = stringResource(R.string.comp_choices_apply_to_this_video_session),
                onDismiss = onDismiss,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (mediaActionRows != null) {
                mediaActionRows()
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            PlaybackOptionNavigationRow(
                title = stringResource(R.string.comp_quality),
                summary = trackSummaryShown(options.videoSelectionSummary(automaticVideoQuality)),
                onClick = onQuality,
            )
            // VIDEO04_EMPTY_CHOICES_ARE_HIDDEN_V1: Reddit videos rarely carry a
            // second audio track or subtitles; a row with nothing to choose is noise.
            if (playbackAudioChoiceOffered(options)) {
                PlaybackOptionNavigationRow(
                    title = stringResource(R.string.comp_audio),
                    summary = trackSummaryShown(options.audioSelectionSummary()),
                    onClick = onAudio,
                )
            }
            if (playbackSubtitleChoiceOffered(options)) {
                PlaybackOptionNavigationRow(
                    title = stringResource(R.string.comp_subtitles),
                    summary = trackSummaryShown(options.textSelectionSummary()),
                    onClick = onSubtitles,
                )
            }
            if (speedSummary != null) {
                PlaybackOptionNavigationRow(
                    title = stringResource(R.string.comp_speed),
                    summary = speedSummary,
                    onClick = onSpeed,
                )
            }
            // VIDEO11_TECHNICAL_DETAILS_BEHIND_A_SETTING_V1 (owner): developer data.
            if (showTechnicalDetails) {
                PlaybackOptionNavigationRow(
                    title = stringResource(R.string.comp_technical_details),
                    summary = stringResource(R.string.comp_tracks_codecs_decoders_and_dropped_frames),
                    onClick = onTechnicalDetails,
                )
            }
        }
    }
}

@Composable
internal fun PlaybackQualityPanel(
    options: PlaybackTrackOptions,
    automaticVideoQuality: String,
    onAutomaticVideo: () -> Unit,
    onVideoTrack: (PlaybackTrackKey) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaybackSelectionPanel(
        title = stringResource(R.string.comp_quality),
        subtitle = trackSummaryShown(options.videoSelectionSummary(automaticVideoQuality)),
        onBack = onBack,
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        PlaybackOptionRow(
            title = stringResource(R.string.comp_auto),
            summary = stringResource(R.string.comp_adaptive_ceiling, automaticVideoQuality),
            selected = options.selection.videoMode == PlaybackTrackSelectionMode.AUTO,
            onClick = onAutomaticVideo,
        )
        options.video.forEach { choice ->
            PlaybackTrackChoiceRow(choice, onClick = { onVideoTrack(choice.key) })
        }
        PlaybackTrackAvailability(options.known, options.video, R.string.comp_no_video_tracks)
    }
}

@Composable
internal fun PlaybackAudioPanel(
    options: PlaybackTrackOptions,
    onAutomaticAudio: () -> Unit,
    onAudioTrack: (PlaybackTrackKey) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaybackSelectionPanel(
        title = stringResource(R.string.comp_audio),
        subtitle = trackSummaryShown(options.audioSelectionSummary()),
        onBack = onBack,
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        PlaybackOptionRow(
            title = stringResource(R.string.comp_auto),
            summary = stringResource(R.string.comp_use_the_source_default),
            selected = options.selection.audioMode == PlaybackTrackSelectionMode.AUTO,
            onClick = onAutomaticAudio,
        )
        options.audio.forEach { choice ->
            PlaybackTrackChoiceRow(choice, onClick = { onAudioTrack(choice.key) })
        }
        PlaybackTrackAvailability(options.known, options.audio, R.string.comp_no_audio_tracks)
    }
}

@Composable
internal fun PlaybackSubtitlesPanel(
    options: PlaybackTrackOptions,
    onAutomaticText: () -> Unit,
    onTextOff: () -> Unit,
    onTextTrack: (PlaybackTrackKey) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaybackSelectionPanel(
        title = stringResource(R.string.comp_subtitles),
        subtitle = trackSummaryShown(options.textSelectionSummary()),
        onBack = onBack,
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        PlaybackOptionRow(
            title = stringResource(R.string.comp_off),
            summary = stringResource(R.string.comp_disable_text_tracks),
            selected = options.selection.textMode == PlaybackTrackSelectionMode.OFF,
            onClick = onTextOff,
        )
        PlaybackOptionRow(
            title = stringResource(R.string.comp_auto),
            summary = stringResource(R.string.comp_use_forced_or_source_default_subtitles),
            selected = options.selection.textMode == PlaybackTrackSelectionMode.AUTO,
            onClick = onAutomaticText,
        )
        options.text.forEach { choice ->
            PlaybackTrackChoiceRow(choice, onClick = { onTextTrack(choice.key) })
        }
        PlaybackTrackAvailability(options.known, options.text, R.string.comp_no_subtitle_tracks)
    }
}

/** A one-tap action row (Share, Download) with progress while it runs. */
@Composable
internal fun PlaybackActionRow(
    title: String,
    summary: String,
    busy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = enabled, onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = summary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            if (busy) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.5.dp,
                )
            }
        }
    }
}

@Composable
internal fun PlaybackSpeedPanel(
    speed: Float,
    onSpeed: (Float) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaybackSelectionPanel(
        title = stringResource(R.string.comp_speed),
        subtitle = stringResource(R.string.comp_for_this_video),
        onBack = onBack,
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        VIDEO_SPEED_CHOICES.forEach { choice ->
            PlaybackOptionRow(
                title = videoSpeedLabel(choice),
                summary = "",
                selected = choice == speed,
                onClick = { onSpeed(choice) },
            )
        }
    }
}

@Composable
private fun PlaybackSelectionPanel(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .heightIn(max = 680.dp)
            .fillMaxWidth(),
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            PlaybackPanelHeader(
                title = title,
                subtitle = subtitle,
                onBack = onBack,
                onDismiss = onDismiss,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 8.dp)
                    // ISSUE219_PLAYBACK_CHOICES_ARE_ONE_RADIO_GROUP_V1
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun PlaybackOptionNavigationRow(
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = summary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Icon(
                Symbols.ChevronRight,
                contentDescription = null, // the row reads its title (ISSUE219)
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlaybackPanelHeader(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    Symbols.ArrowBack,
                    contentDescription = stringResource(R.string.comp_back_to_playback_options),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(
                Symbols.Close,
                contentDescription = stringResource(R.string.comp_close_playback_options),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun PlaybackTrackChoiceRow(
    choice: PlaybackTrackChoice,
    onClick: () -> Unit,
) {
    PlaybackOptionRow(
        title = trackSummaryShown(choice.title),
        summary = trackSummaryShown(choice.summary),
        selected = choice.selected,
        onClick = onClick,
    )
}

@Composable
private fun PlaybackOptionRow(
    title: String,
    summary: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (summary.isNotBlank()) {
                    Text(
                        text = summary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            if (selected) {
                Icon(
                    Symbols.Check,
                    // The row already says "selected" (ISSUE219).
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun PlaybackTrackAvailability(
    known: Boolean,
    choices: List<PlaybackTrackChoice>,
    @androidx.annotation.StringRes text: Int,
) {
    if (!known || choices.isNotEmpty()) return
    Text(
        text = stringResource(text),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelMedium,
    )
}

@Composable
internal fun PlaybackDiagnosticsPanel(
    diagnostics: VideoPlaybackDiagnostics,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .heightIn(max = 680.dp)
            .fillMaxWidth(),
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            PlaybackPanelHeader(
                title = stringResource(R.string.comp_technical_details),
                subtitle = stringResource(R.string.comp_read_only_runtime_diagnostics),
                onBack = onBack,
                onDismiss = onDismiss,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PlaybackDiagnosticRow(stringResource(R.string.all_source), diagnostics.sourceSummary())
                PlaybackDiagnosticRow(stringResource(R.string.all_video_tracks), diagnostics.tracks.videoSummary())
                PlaybackDiagnosticRow(stringResource(R.string.all_audio_tracks), diagnostics.tracks.audioSummary())
                PlaybackDiagnosticRow(stringResource(R.string.all_text_tracks), diagnostics.tracks.textSummary())
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PlaybackDiagnosticRow(stringResource(R.string.all_active_video), diagnostics.activeVideoFormat.videoSummary())
                PlaybackDiagnosticRow(stringResource(R.string.all_video_decoder), decoderSummary(
                    diagnostics.videoDecoderName,
                    diagnostics.videoDecoderInitializationMs,
                ))
                PlaybackDiagnosticRow(stringResource(R.string.all_active_audio), diagnostics.activeAudioFormat.audioSummary())
                PlaybackDiagnosticRow(stringResource(R.string.all_audio_decoder), decoderSummary(
                    diagnostics.audioDecoderName,
                    diagnostics.audioDecoderInitializationMs,
                ))
                PlaybackDiagnosticRow(stringResource(R.string.all_dropped_frames), diagnostics.droppedVideoFrames.toString())
                diagnostics.lastCodecEvent?.let { PlaybackDiagnosticRow(stringResource(R.string.all_codec_event), it) }
            }
        }
    }
}

@Composable
private fun PlaybackDiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(112.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

// TRANSLATE14_TRACK_SUMMARIES_ARE_SHOWN_TRANSLATED_V1: media/VideoTrackSelection
// builds English summaries (tests read them); the panel shows them translated.
// Track names that come from the stream (a language, a label) are shown as they are.
private val TRACK_AUTO_CEILING = Regex("""^Auto · (.+) ceiling$""")
private val TRACK_VIDEO_NUMBER = Regex("""^Video (\d+)$""")
private val TRACK_PIXEL_VIDEO = Regex("""^(\d+)px video$""")

@Composable
internal fun trackSummaryShown(summary: String): String {
    TRACK_AUTO_CEILING.matchEntire(summary)?.let { return stringResource(R.string.comp_track_auto_ceiling, it.groupValues[1]) }
    TRACK_VIDEO_NUMBER.matchEntire(summary)?.let { return stringResource(R.string.comp_track_video_number, it.groupValues[1].toInt()) }
    TRACK_PIXEL_VIDEO.matchEntire(summary)?.let { return stringResource(R.string.comp_track_pixel_video, it.groupValues[1].toInt()) }
    return when (summary) {
        "Off" -> stringResource(R.string.comp_track_off)
        "Auto · source default" -> stringResource(R.string.comp_track_auto_source_default)
        "Auto · forced or source default" -> stringResource(R.string.comp_track_auto_forced_or_source_default)
        "Loading tracks…" -> stringResource(R.string.comp_track_loading)
        "Selected track unavailable" -> stringResource(R.string.comp_track_selected_unavailable)
        "Text track" -> stringResource(R.string.comp_track_text)
        else -> summary
    }
}
