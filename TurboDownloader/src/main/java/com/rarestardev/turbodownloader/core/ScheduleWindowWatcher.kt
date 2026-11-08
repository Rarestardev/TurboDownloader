package com.rarestardev.turbodownloader.core

import com.rarestardev.turbodownloader.model.DownloadSchedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * In-process backup for the alarm window. AlarmManager remains the path that can wake a
 * stopped process; this watcher keeps start and end on time while the process is alive,
 * including when exact alarms are unavailable and the inexact alarm would be deferred.
 */
internal class ScheduleWindowWatcher(
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val readSchedule: () -> DownloadSchedule?,
    private val onStart: () -> Unit,
    private val onEnd: () -> Unit,
    private val delayFn: suspend (Long) -> Unit = { delay(it) }
) {
    private var job: Job? = null

    fun arm() {
        job?.cancel()
        val schedule = readSchedule()
        if (schedule == null) {
            job = null
            return
        }
        job = scope.launch {
            val startDelay = SchedulePlanner.inProcessDelays(schedule, clock()).untilStartMillis
            if (startDelay > 0L) delayFn(startDelay)

            val opened = readSchedule()
            if (opened != null && opened.isActive(clock())) {
                onStart()
            }

            val current = readSchedule() ?: return@launch
            val endDelay = SchedulePlanner.inProcessDelays(current, clock()).untilEndMillis
            if (endDelay > 0L) delayFn(endDelay)

            val ended = readSchedule()
            if (ended != null && ended.isExpired(clock())) {
                onEnd()
            }
        }
    }

    fun disarm() {
        job?.cancel()
        job = null
    }
}
