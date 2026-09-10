package com.rarestardev.turbodownloader.core

import android.content.Context
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TurboDownloaderTest {

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
    }

    @Test
    fun `when enableTorrent is false, torrent engine should not be initialized`() {
        // Given
        val mockContext = mockk<Context>(relaxed = true)
        every { mockContext.applicationContext } returns mockContext

        // When
        val turboDownloader = TurboDownloader.Builder(mockContext)
            .build()
        
        // At this point torrentManager is the real one, but not initialized
        assertFalse("Torrent engine should not be initialized by default", turboDownloader.isTorrentInitialized)
        
        turboDownloader.enableTorrent = false

        // Then
        assertFalse("Torrent engine should still not be initialized", turboDownloader.isTorrentInitialized)
    }
}
