package com.rarestardev.sample.schedule

import com.rarestardev.turbodownloader.model.ScheduleIssue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class ScheduleScreenLogicTest {

    private val tehran = ZoneId.of("Asia/Tehran")
    private val newYork = ZoneId.of("America/New_York")

    @Test
    fun missingZone_fallsBackToTehran_andExplicitZoneIsKept() {
        assertEquals(tehran, ScheduleClock.resolveZone(null))
        assertEquals(newYork, ScheduleClock.resolveZone(newYork))
    }

    @Test
    fun datePickerUsesUtcMidnight_soTehranEveningDoesNotShiftTheDay() {
        val local = ZonedDateTime.of(2026, 11, 8, 22, 15, 0, 0, tehran)
        val millis = local.toInstant().toEpochMilli()

        assertEquals(LocalDate.of(2026, 11, 8), ScheduleClock.localDateOf(millis, tehran))
        val utc = ScheduleClock.datePickerUtcMillis(LocalDate.of(2026, 11, 8))
        assertEquals(LocalDate.of(2026, 11, 8), ScheduleClock.localDateFromDatePicker(utc))
        assertNotEquals(LocalDate.of(2026, 11, 8).atStartOfDay(tehran).toInstant().toEpochMilli(), utc)

        val updated = ScheduleClock.withLocalTime(
            ZonedDateTime.of(2026, 11, 8, 1, 0, 0, 0, tehran).toInstant().toEpochMilli(),
            22,
            15,
            tehran
        )
        assertEquals(millis, updated)
    }

    @Test
    fun sameInstant_usesTheZonePassedIn_notAHardcodedZone() {
        val now = ZonedDateTime.of(2026, 11, 8, 7, 30, 0, 0, tehran).toInstant().toEpochMilli()
        val tehranNight = SchedulePresets.localHourWindow(now, tehran, 1, 6)
        val newYorkNight = SchedulePresets.localHourWindow(now, newYork, 1, 6)

        assertEquals(
            ZonedDateTime.of(2026, 11, 9, 1, 0, 0, 0, tehran).toInstant().toEpochMilli(),
            tehranNight.first
        )
        assertNotEquals(tehranNight, newYorkNight)
    }

    @Test
    fun nightWindow_usesTodayWhenStillOpen() {
        val during = ZonedDateTime.of(2026, 11, 8, 3, 0, 0, 0, tehran).toInstant().toEpochMilli()
        val window = SchedulePresets.localHourWindow(during, tehran, 1, 6)
        assertEquals(ZonedDateTime.of(2026, 11, 8, 1, 0, 0, 0, tehran).toInstant().toEpochMilli(), window.first)
        assertTrue(window.second > during)
    }

    @Test
    fun oneMinutePreset_isADelayedStart_notAOneMinuteDuration() {
        val now = 1_700_000_000_000L
        val window = SchedulePresets.delayed(now, delayMinutes = 1, durationMinutes = 60)
        assertEquals(now + 60_000L, window.first)
        assertEquals(window.first + 60 * 60_000L, window.second)
        assertTrue(window.second - window.first > 60_000L)
    }

    @Test
    fun draftSave_requiresFutureEnd_andExactAlarmWhenNeeded() {
        val now = 1_000_000L
        val valid = ScheduleDraft(now, now + 60_000, now, exactAlarmRequired = true, exactAlarmGranted = true)
        assertTrue(valid.canSave)

        val denied = valid.copy(exactAlarmGranted = false)
        assertFalse(denied.canSave)
        assertEquals(null, denied.issue)

        val notRequired = denied.copy(exactAlarmRequired = false)
        assertTrue(notRequired.canSave)

        val ended = ScheduleDraft(now - 120_000, now, now, false, true)
        assertEquals(ScheduleIssue.END_NOT_IN_FUTURE, ended.issue)
        assertFalse(ended.canSave)
    }

    @Test
    fun formatFollowsLocaleAndZone() {
        val millis = ZonedDateTime.of(2026, 11, 8, 22, 15, 0, 0, tehran).toInstant().toEpochMilli()
        val text = ScheduleClock.formatDateTime(millis, tehran, Locale.US)
        assertTrue(text.contains("2026"))
        assertTrue(text.contains("10:15") || text.contains("22:15"))
    }
}
