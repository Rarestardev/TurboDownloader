package com.rarestardev.turbodownloader.schedule

import kotlinx.coroutines.test.advanceTimeBy

import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadSchedulerTest {

    @Test
    fun `fires a scheduled download when its time arrives`() = runTest {
        var now = 0L
        val fired = mutableListOf<String>()
        val scheduler = DownloadScheduler(backgroundScope, now = { now }) { fired.add(it.url) }

        scheduler.scheduleAfter("https://example.com/a.zip", fileName = "a.zip", delayMillis = 1_000)

        advanceTimeBy(999)
        runCurrent()
        assertEquals(0, fired.size)
        assertEquals(1, scheduler.pendingCount())

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf("https://example.com/a.zip"), fired)
        assertEquals(0, scheduler.pendingCount())
    }

    @Test
    fun `fires schedules in time order`() = runTest {
        var now = 0L
        val fired = mutableListOf<String>()
        val scheduler = DownloadScheduler(backgroundScope, now = { now }) { fired.add(it.url) }

        val later = scheduler.scheduleAfter("later", null, 2_000)
        val sooner = scheduler.scheduleAfter("sooner", null, 500)
        assertEquals(sooner.triggerAtMillis + 1_500, later.triggerAtMillis)

        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(listOf("sooner", "later"), fired)
    }

    @Test
    fun `cancelling a schedule prevents it from firing`() = runTest {
        var now = 0L
        val fired = mutableListOf<String>()
        val scheduler = DownloadScheduler(backgroundScope, now = { now }) { fired.add(it.url) }

        val item = scheduler.scheduleAfter("cancelled", null, 1_000)
        assertTrue(scheduler.cancel(item.id))

        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(0, fired.size)
        assertFalse(scheduler.cancel(item.id)) // second cancel: already removed
    }

    @Test
    fun `scheduled list is ordered by trigger time`() = runTest {
        var now = 100L
        val scheduler = DownloadScheduler(backgroundScope, now = { now }) { }
        scheduler.scheduleAfter("b", null, 2_000)
        scheduler.scheduleAfter("a", null, 1_000)

        val pending = scheduler.scheduled()
        assertEquals(listOf("a", "b"), pending.map { it.url })
        assertEquals(1_100L, pending.first().triggerAtMillis)
    }

    @Test
    fun `schedule with past trigger time fires immediately`() = runTest {
        var now = 5_000L
        val fired = mutableListOf<String>()
        val scheduler = DownloadScheduler(backgroundScope, now = { now }) { fired.add(it.url) }

        scheduler.schedule("past", null, triggerAtMillis = 1_000)
        runCurrent()

        assertEquals(listOf("past"), fired)
    }
}
