package com.rarestardev.turbodownloader.core

import android.content.Context
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TurboDownloaderTest {

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
    }

    @Test
    fun `when enableTorrent is true, torrent engine should be initialized`() {
        // Given
        val mockContext = mockk<Context>(relaxed = true)
        every { mockContext.applicationContext } returns mockContext

        // When
        val turboDownloader = TurboDownloader.Builder(mockContext)
            .build()
        turboDownloader.enableTorrent = true

        // Then
        assertTrue("Torrent engine should be initialized", turboDownloader.getTorrentManager().isInitialized)
    }

    @Test
    fun `when enableTorrent is false, torrent engine should not be initialized`() {
        // Given
        val mockContext = mockk<Context>(relaxed = true)
        every { mockContext.applicationContext } returns mockContext

        // When
        val turboDownloader = TurboDownloader.Builder(mockContext)
            .build()
        turboDownloader.enableTorrent = false

        // Then
        assertFalse("Torrent engine should not be initialized", turboDownloader.getTorrentManager().isInitialized)
    }
}