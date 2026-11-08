package com.rarestardev.sample.schedule

import android.content.ActivityNotFoundException
import android.text.format.DateFormat
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rarestardev.sample.R
import com.rarestardev.turbodownloader.core.TurboDownloader
import com.rarestardev.turbodownloader.model.DownloadSchedule
import com.rarestardev.turbodownloader.model.ScheduleIssue
import com.rarestardev.turbodownloader.model.SchedulePhase
import com.rarestardev.turbodownloader.model.ScheduleSignal
import com.rarestardev.turbodownloader.model.schedulePhase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleRoute(
    downloader: TurboDownloader,
    resumeTick: Int,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var revision by remember { mutableIntStateOf(0) }
    val zone = remember { ScheduleClock.resolveZone() }
    val locale = remember { Locale.getDefault() }
    val saved = remember(resumeTick, revision) { downloader.currentSchedule() }
    val schedulingEnabled = remember(resumeTick, revision) { downloader.scheduleEnabled }
    val finishAfterQueue = remember(resumeTick, revision) { downloader.finishAfterScheduledQueue }
    val lastSignal = remember(resumeTick, revision, now) { downloader.lastScheduleSignal() }
    val access = remember(resumeTick, revision) { downloader.exactAlarmAccess() }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }

    ScheduleScreen(
        saved = saved,
        lastSignalLabel = lastSignal?.let { record ->
            val whenText = ScheduleClock.formatDateTime(record.atMillis, zone, locale)
            when (record.signal) {
                ScheduleSignal.START -> context.getString(R.string.schedule_event_start, whenText)
                ScheduleSignal.END -> context.getString(R.string.schedule_event_end, whenText)
            }
        },
        schedulingEnabled = schedulingEnabled,
        phase = schedulePhase(saved, now),
        exactAlarmRequired = access.required,
        exactAlarmGranted = access.granted,
        zoneId = zone.id,
        now = now,
        zone = zone,
        locale = locale,
        onBack = onBack,
        onSchedulingEnabled = { enabled ->
            downloader.scheduleEnabled = enabled
            revision++
        },
        finishAfterQueue = finishAfterQueue,
        onFinishAfterQueue = { enabled ->
            downloader.finishAfterScheduledQueue = enabled
            revision++
        },
        onSave = { start, end ->
            downloader.setSchedule(DownloadSchedule(start, end))
            revision++
        },
        onClear = {
            downloader.clearSchedule()
            revision++
        },
        onQueueSamples = {
            scope.launch {
                listOf(
                    "https://raw.githubusercontent.com/github/gitignore/main/Android.gitignore" to "schedule-a.txt",
                    "https://raw.githubusercontent.com/github/gitignore/main/Kotlin.gitignore" to "schedule-b.txt"
                ).forEach { (url, name) ->
                    try {
                        downloader.addToQueues(url, name)
                    } catch (error: Exception) {
                        Log.e("Turbo_downloader", "Could not queue $name", error)
                    }
                }
            }
        },
        onRequestExactAlarm = {
            val intent = downloader.exactAlarmAccess().settingsIntent ?: return@ScheduleScreen
            try {
                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                // The page already explains the missing permission.
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleScreen(
    saved: DownloadSchedule?,
    lastSignalLabel: String?,
    schedulingEnabled: Boolean,
    finishAfterQueue: Boolean,
    phase: SchedulePhase,
    exactAlarmRequired: Boolean,
    exactAlarmGranted: Boolean,
    zoneId: String,
    now: Long,
    zone: java.time.ZoneId,
    locale: Locale,
    onBack: () -> Unit,
    onSchedulingEnabled: (Boolean) -> Unit,
    onFinishAfterQueue: (Boolean) -> Unit,
    onSave: (Long, Long) -> Unit,
    onClear: () -> Unit,
    onQueueSamples: () -> Unit,
    onRequestExactAlarm: () -> Unit
) {
    val initialStart = saved?.startAtMillis ?: (now + 5 * 60_000L)
    val initialEnd = saved?.endAtMillis ?: (initialStart + 60 * 60_000L)
    var start by remember(saved) { mutableLongStateOf(initialStart) }
    var end by remember(saved) { mutableLongStateOf(initialEnd) }
    var picking by remember { mutableStateOf<PickTarget?>(null) }
    val draft = ScheduleDraft(
        startAtMillis = start,
        endAtMillis = end,
        now = now,
        exactAlarmRequired = exactAlarmRequired,
        exactAlarmGranted = exactAlarmGranted
    )
    val format: (Long) -> String = { ScheduleClock.formatDateTime(it, zone, locale) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.schedule_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        androidx.compose.material3.Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.schedule_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SchedulingSwitch(
                schedulingEnabled,
                saved != null,
                onSchedulingEnabled,
                finishAfterQueue,
                onFinishAfterQueue
            )
            PhaseCard(
                enforced = schedulingEnabled,
                phase = phase,
                saved = saved,
                format = format,
                zoneId = zoneId,
                lastSignalLabel = lastSignalLabel
            )
            ExactAlarmCard(
                required = exactAlarmRequired,
                granted = exactAlarmGranted,
                onRequest = onRequestExactAlarm
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.schedule_editor), style = MaterialTheme.typography.titleMedium)
                    TimeRow(
                        label = stringResource(R.string.schedule_start),
                        value = format(start),
                        onDate = { picking = PickTarget.StartDate },
                        onTime = { picking = PickTarget.StartTime }
                    )
                    TimeRow(
                        label = stringResource(R.string.schedule_end),
                        value = format(end),
                        onDate = { picking = PickTarget.EndDate },
                        onTime = { picking = PickTarget.EndTime }
                    )
                    if (!schedulingEnabled) {
                        Text(
                            text = stringResource(R.string.schedule_enable_required),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    draft.issue?.let { issue ->
                        Text(
                            text = stringResource(issueMessage(issue)),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            Text(stringResource(R.string.schedule_preset_title), style = MaterialTheme.typography.titleMedium)
            PresetButton(
                stringResource(R.string.schedule_preset_in_one_minute),
                SchedulePresets.delayed(now, 1, 60),
                format
            ) {
                start = it.first
                end = it.second
            }
            PresetButton(stringResource(R.string.schedule_preset_15), SchedulePresets.nextMinutes(now, 15), format) {
                start = it.first
                end = it.second
            }
            PresetButton(stringResource(R.string.schedule_preset_hour), SchedulePresets.delayed(now, 60, 60), format) {
                start = it.first
                end = it.second
            }
            PresetButton(
                stringResource(R.string.schedule_preset_night),
                SchedulePresets.localHourWindow(now, zone, 1, 6),
                format
            ) {
                start = it.first
                end = it.second
            }
            Button(
                onClick = { onSave(start, end) },
                enabled = schedulingEnabled && draft.canSave,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.schedule_save))
            }
            OutlinedButton(
                onClick = onClear,
                enabled = schedulingEnabled && saved != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.schedule_clear))
            }
            OutlinedButton(
                onClick = onQueueSamples,
                enabled = schedulingEnabled && saved != null && saved.isBeforeStart(now),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.schedule_queue_samples))
            }
            Text(stringResource(R.string.schedule_behavior_title), style = MaterialTheme.typography.titleMedium)
            listOf(
                R.string.schedule_behavior_once,
                R.string.schedule_behavior_gate,
                R.string.schedule_behavior_pause,
                R.string.schedule_behavior_expired,
                R.string.schedule_behavior_boot,
                R.string.schedule_behavior_zone
            ).forEach { id ->
                Text("• ${stringResource(id)}", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    when (picking) {
        PickTarget.StartDate -> DateDialog(start, zone, { picking = null }) { start = it }
        PickTarget.EndDate -> DateDialog(end, zone, { picking = null }) { end = it }
        PickTarget.StartTime -> TimeDialog(start, zone, { picking = null }) { hour, minute ->
            start = ScheduleClock.withLocalTime(start, hour, minute, zone)
        }
        PickTarget.EndTime -> TimeDialog(end, zone, { picking = null }) { hour, minute ->
            end = ScheduleClock.withLocalTime(end, hour, minute, zone)
        }
        null -> Unit
    }
}

@Composable
private fun SchedulingSwitch(
    enabled: Boolean,
    hasSavedWindow: Boolean,
    onChange: (Boolean) -> Unit,
    finishAfterQueue: Boolean,
    onFinishAfterQueue: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.schedule_enabled), style = MaterialTheme.typography.titleMedium)
                Switch(checked = enabled, onCheckedChange = onChange)
            }
            Text(
                stringResource(
                    if (enabled) R.string.schedule_enabled_on else R.string.schedule_enabled_off
                )
            )
            if (!enabled && hasSavedWindow) {
                Text(stringResource(R.string.schedule_kept))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.schedule_finish_queue),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = finishAfterQueue, onCheckedChange = onFinishAfterQueue)
            }
            Text(stringResource(R.string.schedule_finish_queue_detail))
        }
    }
}

@Composable
private fun PhaseCard(
    enforced: Boolean,
    phase: SchedulePhase,
    saved: DownloadSchedule?,
    format: (Long) -> String,
    zoneId: String,
    lastSignalLabel: String?
) {
    val container = when {
        !enforced -> MaterialTheme.colorScheme.surfaceVariant
        phase == SchedulePhase.ACTIVE -> MaterialTheme.colorScheme.primaryContainer
        phase == SchedulePhase.WAITING -> MaterialTheme.colorScheme.tertiaryContainer
        phase == SchedulePhase.EXPIRED -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val label = if (!enforced) {
        R.string.schedule_enabled_off
    } else {
        when (phase) {
            SchedulePhase.NONE -> R.string.schedule_phase_none
            SchedulePhase.WAITING -> R.string.schedule_phase_waiting
            SchedulePhase.ACTIVE -> R.string.schedule_phase_active
            SchedulePhase.EXPIRED -> R.string.schedule_phase_expired
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Icon(Icons.Filled.Alarm, contentDescription = null)
                Text(stringResource(label), style = MaterialTheme.typography.titleMedium)
            }
            if (saved != null) {
                Text(stringResource(R.string.schedule_saved_start, format(saved.startAtMillis)))
                Text(stringResource(R.string.schedule_saved_end, format(saved.endAtMillis)))
            }
            Text(stringResource(R.string.schedule_zone, zoneId))
            if (lastSignalLabel != null) {
                Text(lastSignalLabel, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ExactAlarmCard(required: Boolean, granted: Boolean, onRequest: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.schedule_exact_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(
                    when {
                        !required -> R.string.schedule_exact_not_required
                        granted -> R.string.schedule_exact_granted
                        else -> R.string.schedule_exact_needed
                    }
                )
            )
            if (required && !granted) {
                Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.schedule_exact_grant))
                }
            }
        }
    }
}

@Composable
private fun TimeRow(label: String, value: String, onDate: () -> Unit, onTime: () -> Unit) {
    Text(label, style = MaterialTheme.typography.labelLarge)
    Text(value)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onDate, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.schedule_pick_date))
        }
        OutlinedButton(onClick = onTime, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.schedule_pick_time))
        }
    }
}

@Composable
private fun PresetButton(
    title: String,
    window: Pair<Long, Long>,
    format: (Long) -> String,
    onApply: (Pair<Long, Long>) -> Unit
) {
    OutlinedButton(onClick = { onApply(window) }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Text(title)
            Text(
                stringResource(R.string.schedule_preset_range, format(window.first), format(window.second)),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(
    current: Long,
    zone: java.time.ZoneId,
    onDismiss: () -> Unit,
    onDate: (Long) -> Unit
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = ScheduleClock.datePickerUtcMillis(ScheduleClock.localDateOf(current, zone))
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { utc ->
                    onDate(ScheduleClock.withLocalDate(current, ScheduleClock.localDateFromDatePicker(utc), zone))
                }
                onDismiss()
            }) { Text(stringResource(R.string.schedule_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.schedule_dismiss)) }
        }
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(
    current: Long,
    zone: java.time.ZoneId,
    onDismiss: () -> Unit,
    onTime: (Int, Int) -> Unit
) {
    val context = LocalContext.current
    val local = Instant.ofEpochMilli(current).atZone(zone)
    val state = rememberTimePickerState(
        initialHour = local.hour,
        initialMinute = local.minute,
        is24Hour = DateFormat.is24HourFormat(context)
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onTime(state.hour, state.minute)
                onDismiss()
            }) { Text(stringResource(R.string.schedule_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.schedule_dismiss)) }
        },
        text = { TimePicker(state = state) }
    )
}

private fun issueMessage(issue: ScheduleIssue): Int {
    return when (issue) {
        ScheduleIssue.START_NOT_POSITIVE -> R.string.schedule_issue_start
        ScheduleIssue.END_NOT_AFTER_START -> R.string.schedule_issue_order
        ScheduleIssue.END_NOT_IN_FUTURE -> R.string.schedule_issue_future
    }
}

private enum class PickTarget { StartDate, EndDate, StartTime, EndTime }
