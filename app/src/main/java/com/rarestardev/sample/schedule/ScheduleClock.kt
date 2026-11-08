package com.rarestardev.sample.schedule

import com.rarestardev.turbodownloader.model.ScheduleIssue
import com.rarestardev.turbodownloader.model.validateDownloadSchedule
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object ScheduleClock {
    val fallbackZone: ZoneId = ZoneId.of("Asia/Tehran")

    fun resolveZone(candidate: ZoneId? = runCatching { ZoneId.systemDefault() }.getOrNull()): ZoneId {
        return candidate ?: fallbackZone
    }

    fun formatDateTime(millis: Long, zone: ZoneId, locale: Locale): String {
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(locale)
            .withZone(zone)
        return formatter.format(Instant.ofEpochMilli(millis))
    }

    fun localDateOf(millis: Long, zone: ZoneId): LocalDate {
        return Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    }

    fun withLocalDate(millis: Long, date: LocalDate, zone: ZoneId): Long {
        val time = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()
        return date.atTime(time).atZone(zone).toInstant().toEpochMilli()
    }

    fun withLocalTime(millis: Long, hour: Int, minute: Int, zone: ZoneId): Long {
        val date = localDateOf(millis, zone)
        return date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
    }

    /** Material3 DatePicker uses UTC midnight, not the device-zone midnight. */
    fun datePickerUtcMillis(date: LocalDate): Long {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }

    fun localDateFromDatePicker(utcMillis: Long): LocalDate {
        return Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()
    }
}

object SchedulePresets {
    fun nextMinutes(now: Long, minutes: Long): Pair<Long, Long> {
        val end = now + minutes * 60_000L
        return now to end
    }

    fun delayed(now: Long, delayMinutes: Long, durationMinutes: Long): Pair<Long, Long> {
        val start = now + delayMinutes * 60_000L
        return start to (start + durationMinutes * 60_000L)
    }

    /**
     * Next local hour window. If today's window has already ended, the next day is used.
     * A window that already started but has not ended is returned as-is.
     */
    fun localHourWindow(now: Long, zone: ZoneId, startHour: Int, endHour: Int): Pair<Long, Long> {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val todayWindow = windowOn(today, zone, startHour, endHour)
        return if (todayWindow.second > now) todayWindow else windowOn(today.plusDays(1), zone, startHour, endHour)
    }

    private fun windowOn(day: java.time.LocalDate, zone: ZoneId, startHour: Int, endHour: Int): Pair<Long, Long> {
        val start = day.atTime(startHour, 0).atZone(zone).toInstant().toEpochMilli()
        val endDay = if (endHour > startHour) day else day.plusDays(1)
        val end = endDay.atTime(endHour, 0).atZone(zone).toInstant().toEpochMilli()
        return start to end
    }
}

data class ScheduleDraft(
    val startAtMillis: Long,
    val endAtMillis: Long,
    val now: Long,
    val exactAlarmRequired: Boolean,
    val exactAlarmGranted: Boolean
) {
    val issue: ScheduleIssue? = validateDownloadSchedule(startAtMillis, endAtMillis, now)

    val canSave: Boolean = issue == null && (!exactAlarmRequired || exactAlarmGranted)
}
