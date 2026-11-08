package com.rarestardev.turbodownloader.core

import com.rarestardev.turbodownloader.state.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ConcurrentTransferGateTest {

    @Test
    fun defaultCap_admitsOneFile() {
        val gate = ConcurrentTransferGate()
        assertEquals(1, gate.cap)
        assertTrue(gate.requestSlot("a"))
        assertFalse(gate.requestSlot("b"))
        assertEquals(listOf("a"), gate.activeIds())
        assertEquals(listOf("b"), gate.heldIds())
    }

    @Test
    fun bounds_accept1And5_andRejectOutside() {
        assertEquals(1, ConcurrentTransferGate(1).cap)
        assertEquals(5, ConcurrentTransferGate(5).cap)
        assertThrows(IllegalArgumentException::class.java) { ConcurrentTransferGate(0) }
        assertThrows(IllegalArgumentException::class.java) { ConcurrentTransferGate(6) }
        val gate = ConcurrentTransferGate()
        assertThrows(IllegalArgumentException::class.java) { gate.setCap(0) }
        assertThrows(IllegalArgumentException::class.java) { gate.setCap(6) }
        assertEquals(1, gate.cap)
    }

    @Test
    fun liveCap_1to3to1to5_keepsEarliestAndRestoresLater() {
        val gate = ConcurrentTransferGate(1)
        assertTrue(gate.requestSlot("a"))
        assertFalse(gate.requestSlot("b"))
        assertFalse(gate.requestSlot("c"))

        val toThree = gate.setCap(3)
        assertEquals(emptyList<String>(), toThree.stop)
        assertEquals(listOf("b", "c"), toThree.start)
        assertEquals(listOf("a", "b", "c"), gate.activeIds())

        val toOne = gate.setCap(1)
        assertEquals(listOf("b", "c"), toOne.stop)
        assertEquals(listOf("a"), gate.activeIds())

        assertEquals(emptyList<String>(), gate.onConcurrencyStopped("b"))
        assertEquals(emptyList<String>(), gate.onConcurrencyStopped("c"))
        assertEquals(listOf("a"), gate.activeIds())
        assertEquals(listOf("b", "c"), gate.heldIds())

        val toFive = gate.setCap(5)
        assertEquals(listOf("b", "c"), toFive.start)
        assertEquals(listOf("a", "b", "c"), gate.activeIds())
        assertTrue(gate.heldIds().isEmpty())
    }

    @Test
    fun completion_fillsTheNextHeldSlotWithoutDuplicating() {
        val gate = ConcurrentTransferGate(1)
        gate.requestSlot("a")
        gate.requestSlot("b")
        val started = gate.onSlotReleased("a")
        assertEquals(listOf("b"), started)
        assertEquals(listOf("b"), gate.activeIds())
        assertEquals(emptyList<String>(), gate.onSlotReleased("b"))
    }

    @Test
    fun manualRelease_doesNotReturnThePausedFile() {
        val gate = ConcurrentTransferGate(1)
        gate.requestSlot("a")
        gate.requestSlot("b")
        val started = gate.onSlotReleased("a")
        assertEquals(listOf("b"), started)
        assertFalse(gate.heldIds().contains("a"))
    }

    @Test
    fun queueOnlyId_isNotAdmittedWhenCapGrows() {
        val gate = ConcurrentTransferGate(1)
        gate.requestSlot("a")
        val effect = gate.setCap(3)
        assertTrue(effect.start.isEmpty())
        assertEquals(listOf("a"), gate.activeIds())
    }

    @Test
    fun decrease_doesNotStartReplacementsUntilSurplusStops() {
        val gate = ConcurrentTransferGate(1)
        gate.requestSlot("a")
        gate.requestSlot("b")
        gate.setCap(2)
        val down = gate.setCap(1)
        assertEquals(listOf("b"), down.stop)
        assertTrue(down.start.isEmpty())
        assertEquals(2, gate.activeIds().size + down.stop.size)
        assertEquals(emptyList<String>(), gate.onConcurrencyStopped("b"))
        assertEquals(listOf("a"), gate.activeIds())
    }

    @Test
    fun scheduledFinish_waitsWhileConcurrencyHeld() {
        assertFalse(
            scheduledQueueRunFinished(
                finishEnabled = true,
                cohortIds = setOf("a", "b"),
                statusById = mapOf(
                    "a" to DownloadStatus.COMPLETED,
                    "b" to DownloadStatus.PAUSED
                ),
                runningIds = emptySet()
            )
        )
    }
}
