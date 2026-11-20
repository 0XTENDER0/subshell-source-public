package io.github.brealorg.subshell.ui.components

import io.github.brealorg.subshell.R
import androidx.compose.ui.res.stringResource
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.brealorg.subshell.ui.CommunityMute
import io.github.brealorg.subshell.ui.CommunityMutePreset
import io.github.brealorg.subshell.ui.symbols.Symbols
import java.util.Calendar
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import java.util.TimeZone
import android.content.Context

internal fun communityMuteStatusText(
    context: Context,
    mute: CommunityMute,
    nowEpochMillis: Long,
): String {
    val expiry = mute.expiresAtEpochMillis ?: return context.getString(R.string.comp_muted_indefinitely)
    if (expiry <= nowEpochMillis) return context.getString(R.string.comp_mute_expired)
    val formatted = DateUtils.formatDateTime(
        context,
        expiry,
        DateUtils.FORMAT_SHOW_WEEKDAY or
            DateUtils.FORMAT_SHOW_DATE or
            DateUtils.FORMAT_SHOW_TIME or
            DateUtils.FORMAT_ABBREV_MONTH,
    )
    return context.getString(R.string.comp_muted_until, formatted)
}

@androidx.compose.runtime.Composable
internal fun communityMuteRemainingText(
    mute: CommunityMute,
    nowEpochMillis: Long,
): String {
    val expiry = mute.expiresAtEpochMillis ?: return stringResource(R.string.comp_mute_remaining_1)
    val remainingMillis = (expiry - nowEpochMillis).coerceAtLeast(0L)
    val totalMinutes = ((remainingMillis + 59_999L) / 60_000L).coerceAtLeast(1L)
    val days = totalMinutes / (24L * 60L)
    val hours = (totalMinutes % (24L * 60L)) / 60L
    val minutes = totalMinutes % 60L
    return when {
        days > 0L && hours > 0L -> stringResource(R.string.comp_mute_remaining_days_hours, days, hours)
        days > 0L -> stringResource(R.string.comp_mute_remaining_days, days)
        hours > 0L && minutes > 0L -> stringResource(R.string.comp_mute_remaining_hours_minutes, hours, minutes)
        hours > 0L -> stringResource(R.string.comp_mute_remaining_hours, hours)
        else -> stringResource(R.string.comp_mute_remaining_minutes, minutes)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityMuteSheet(
    communityLabel: String,
    nowEpochMillis: Long,
    onDismiss: () -> Unit,
    onMute: (Long?) -> Unit,
    currentMute: CommunityMute? = null,
    onUnmute: (() -> Unit)? = null,
    // LOWUI8_MUTE_EXPIRY_COUNTS_FROM_THE_TAP_V1 (OCB-112/129): nowEpochMillis
    // is read when the caller composed the sheet, which may have sat open for
    // a while; a chosen duration counts from the tap.
    clock: () -> Long = System::currentTimeMillis,
) {
    val context = LocalContext.current
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    // M3225_MUTE_UNTIL_USES_M3_PICKERS_V1 (#225, components-04, owner): Until…
    // replaces the sheet with the M3 date and time pickers (app theme, dynamic
    // colour, pure black), not Android's View pickers.
    var untilPickerFromMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    untilPickerFromMillis?.let { from ->
        CommunityMuteUntilPicker(
            nowEpochMillis = from,
            clock = clock,
            onSelected = { until ->
                onMute(until)
                onDismiss()
            },
            onDismiss = onDismiss,
        )
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = if (currentMute == null) {
                    stringResource(R.string.all_mute, communityLabel)
                } else {
                    stringResource(R.string.all_mute_settings)
                },
                style = MaterialTheme.typography.headlineSmall,
            )

            if (currentMute != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Symbols.VolumeOff,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = communityLabel,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = communityMuteStatusText(
                                    context = context,
                                    mute = currentMute,
                                    nowEpochMillis = nowEpochMillis,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            Text(
                text = stringResource(R.string.comp_hide_posts_from_home_popular_and_all),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp, bottom = 10.dp),
            )

            if (currentMute != null) {
                Text(
                    text = stringResource(R.string.comp_change_duration),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            CommunityMutePreset.entries
                .filterNot { preset -> preset == CommunityMutePreset.INDEFINITE }
                .forEach { preset ->
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        content = { Text(stringResource(preset.labelRes)) },
                        supportingContent = {
                            preset.unmuteAfterRes?.let { Text(stringResource(it)) }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onMute(preset.expiryFrom(clock()))
                                onDismiss()
                            },
                    )
                }

            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                content = { Text(stringResource(R.string.comp_until)) },
                supportingContent = { Text(stringResource(R.string.comp_choose_an_exact_date_and_time)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { untilPickerFromMillis = clock() },
            )

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                content = { Text(stringResource(CommunityMutePreset.INDEFINITE.labelRes)) },
                supportingContent = { Text(stringResource(R.string.comp_keep_muted_until_you_unmute_it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onMute(null)
                        onDismiss()
                    },
            )

            if (currentMute != null && onUnmute != null) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = {
                        Icon(
                            imageVector = Symbols.VolumeUp,
                            contentDescription = null,
                        )
                    },
                    content = { Text(stringResource(R.string.comp_unmute_community)) },
                    supportingContent = {
                        Text(stringResource(R.string.comp_show_posts_from_in_mixed_feeds_again, communityLabel))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onUnmute()
                            onDismiss()
                        },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommunityMuteUntilPicker(
    nowEpochMillis: Long,
    clock: () -> Long,
    onSelected: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    // The M3 date picker speaks in UTC midnights; the chosen day is kept as one.
    var chosenDayUtc by rememberSaveable { mutableStateOf<Long?>(null) }
    val day = chosenDayUtc
    if (day == null) {
        val todayUtc = localDayAsUtcMidnight(nowEpochMillis)
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = todayUtc + DAY_MILLIS,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= todayUtc
            },
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    enabled = dateState.selectedDateMillis != null,
                    onClick = { chosenDayUtc = dateState.selectedDateMillis },
                ) {
                    Text(stringResource(R.string.comp_next))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.comp_cancel)) }
            },
        ) {
            DatePicker(state = dateState)
        }
        return
    }
    val utcDay = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = day }
    val year = utcDay.get(Calendar.YEAR)
    val month = utcDay.get(Calendar.MONTH)
    val dayOfMonth = utcDay.get(Calendar.DAY_OF_MONTH)
    // LOWUX2_MUTE_UNTIL_TIME_STARTS_IN_THE_FUTURE_V1 (OCB-130): today starts an
    // hour ahead, and a past time keeps the time picker open with the reason
    // instead of ending the flow.
    val start = remember(day) {
        communityMuteUntilTimeStart(
            nowEpochMillis = clock(),
            year = year,
            month = month,
            dayOfMonth = dayOfMonth,
        )
    }
    val timeState = rememberTimePickerState(
        initialHour = start.get(Calendar.HOUR_OF_DAY),
        initialMinute = start.get(Calendar.MINUTE),
        is24Hour = DateFormat.is24HourFormat(context),
    )
    var refusedPastTime by remember { mutableStateOf(false) }
    LaunchedEffect(timeState.hour, timeState.minute) { refusedPastTime = false }
    TimePickerDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.comp_mute_until)) },
        confirmButton = {
            TextButton(
                onClick = {
                    val selected = Calendar.getInstance().apply {
                        set(year, month, dayOfMonth, timeState.hour, timeState.minute, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    if (selected > clock()) onSelected(selected) else refusedPastTime = true
                },
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = { chosenDayUtc = null }) { Text(stringResource(R.string.comp_back)) }
        },
    ) {
        TimePicker(state = timeState)
        if (refusedPastTime) {
            Text(
                stringResource(R.string.all_choose_a_time_in_the_future),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

private const val DAY_MILLIS = 24L * 60L * 60L * 1_000L

/** The local calendar day of [epochMillis] as the UTC midnight the M3 date picker uses. */
internal fun localDayAsUtcMidnight(epochMillis: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = epochMillis }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

/** Where the Until… time picker starts: an hour from now on today, else this time of day. */
internal fun communityMuteUntilTimeStart(nowEpochMillis: Long, year: Int, month: Int, dayOfMonth: Int): Calendar {
    val now = Calendar.getInstance().apply { timeInMillis = nowEpochMillis }
    val today = now.get(Calendar.YEAR) == year &&
        now.get(Calendar.MONTH) == month &&
        now.get(Calendar.DAY_OF_MONTH) == dayOfMonth
    return Calendar.getInstance().apply {
        timeInMillis = if (today) nowEpochMillis + 60L * 60L * 1_000L else nowEpochMillis
    }
}
