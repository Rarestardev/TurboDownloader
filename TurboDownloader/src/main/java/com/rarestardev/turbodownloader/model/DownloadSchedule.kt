package com.rarestardev.turbodownloader.model

data class DownloadSchedule(
    val startAtMillis: Long,
    val endAtMillis: Long
) {
    init {
        require(startAtMillis > 0) { "startAtMillis must be greater than zero" }
        require(endAtMillis > startAtMillis) { "endAtMillis must be after startAtMillis" }
    }

    fun isBeforeStart(now: Long = System.currentTimeMillis()) = now < startAtMillis

    fun isActive(now: Long = System.currentTimeMillis()) = now in startAtMillis until endAtMillis

    fun isExpired(now: Long = System.currentTimeMillis()) = now >= endAtMillis
}

enum class ScheduleIssue {
    START_NOT_POSITIVE,
    END_NOT_AFTER_START,
    END_NOT_IN_FUTURE
}

enum class SchedulePhase {
    NONE,
    WAITING,
    ACTIVE,
    EXPIRED
}

/**
 * Structural and timing rules shared by the scheduler and the scheduling screen.
 * A one-time window may already have started, but its end must still be ahead of [now].
 */
fun validateDownloadSchedule(
    startAtMillis: Long,
    endAtMillis: Long,
    now: Long
): ScheduleIssue? {
    if (startAtMillis <= 0L) return ScheduleIssue.START_NOT_POSITIVE
    if (endAtMillis <= startAtMillis) return ScheduleIssue.END_NOT_AFTER_START
    if (endAtMillis <= now) return ScheduleIssue.END_NOT_IN_FUTURE
    return null
}

fun schedulePhase(schedule: DownloadSchedule?, now: Long): SchedulePhase {
    return when {
        schedule == null -> SchedulePhase.NONE
        schedule.isExpired(now) -> SchedulePhase.EXPIRED
        schedule.isBeforeStart(now) -> SchedulePhase.WAITING
        else -> SchedulePhase.ACTIVE
    }
}