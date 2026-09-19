package com.rarestardev.turbodownloader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConcurrentDownloadGateTest {

    @Test
    fun `grants up to max then queues`() {
        val gate = ConcurrentDownloadGate(2)
        val started = mutableListOf<String>()

        assertTrue(gate.tryAcquire("a") { started.add("a") })
        assertTrue(gate.tryAcquire("b") { started.add("b") })
        assertFalse(gate.tryAcquire("c") { started.add("c") })

        assertEquals(listOf("a", "b"), started)
        assertEquals(2, gate.activeCount())
        assertEquals(1, gate.waitingCount())
    }

    @Test
    fun `release starts the next queued download in FIFO order`() {
        val gate = ConcurrentDownloadGate(1)
        val started = mutableListOf<String>()

        assertTrue(gate.tryAcquire("a") { started.add("a") })
        assertFalse(gate.tryAcquire("b") { started.add("b") })
        assertFalse(gate.tryAcquire("c") { started.add("c") })
        assertEquals(listOf("a"), started)

        gate.release("a")
        assertEquals(listOf("a", "b"), started)
        assertEquals(1, gate.activeCount())
        assertEquals(1, gate.waitingCount())

        gate.release("b")
        assertEquals(listOf("a", "b", "c"), started)
        assertEquals(1, gate.activeCount())
        assertEquals(0, gate.waitingCount())
    }

    @Test
    fun `cancelWaiting removes a queued download without starting it`() {
        val gate = ConcurrentDownloadGate(1)
        val started = mutableListOf<String>()

        assertTrue(gate.tryAcquire("a") { started.add("a") })
        assertFalse(gate.tryAcquire("b") { started.add("b") })

        assertTrue(gate.cancelWaiting("b"))
        assertEquals(0, gate.waitingCount())

        gate.release("a")
        assertEquals(listOf("a"), started)
    }

    @Test
    fun `raising the limit starts waiting downloads immediately`() {
        val gate = ConcurrentDownloadGate(1)
        val started = mutableListOf<String>()

        assertTrue(gate.tryAcquire("a") { started.add("a") })
        assertFalse(gate.tryAcquire("b") { started.add("b") })
        assertFalse(gate.tryAcquire("c") { started.add("c") })
        assertEquals(listOf("a"), started)

        gate.setMaxConcurrent(3)

        assertEquals(listOf("a", "b", "c"), started)
        assertEquals(3, gate.activeCount())
        assertEquals(3, gate.maxConcurrent())
    }

    @Test
    fun `lowering the limit keeps current downloads running`() {
        val gate = ConcurrentDownloadGate(3)
        val started = mutableListOf<String>()
        gate.tryAcquire("a") { started.add("a") }
        gate.tryAcquire("b") { started.add("b") }

        gate.setMaxConcurrent(1)
        assertEquals(2, gate.activeCount())

        assertFalse(gate.tryAcquire("c") { started.add("c") })
        assertEquals(1, gate.waitingCount())
    }

    @Test
    fun `release of an unknown key is a safe no-op`() {
        val gate = ConcurrentDownloadGate(2)
        gate.release("ghost")
        assertEquals(0, gate.activeCount())
    }

    @Test
    fun `double release does not free extra slots`() {
        val gate = ConcurrentDownloadGate(1)
        val started = mutableListOf<String>()
        assertTrue(gate.tryAcquire("a") { started.add("a") })
        assertFalse(gate.tryAcquire("b") { started.add("b") })

        gate.release("a")
        gate.release("a") // duplicate: "a" is not held anymore

        assertEquals(listOf("a", "b"), started)
        assertEquals(1, gate.activeCount())
        assertEquals(0, gate.waitingCount())
    }

    @Test
    fun `setMaxConcurrent rejects invalid values`() {
        val gate = ConcurrentDownloadGate(1)
        try {
            gate.setMaxConcurrent(0)
            throw AssertionError("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }
}
