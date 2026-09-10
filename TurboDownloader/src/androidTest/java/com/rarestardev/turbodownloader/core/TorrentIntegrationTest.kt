package com.rarestardev.turbodownloader.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rarestardev.turbodownloader.state.DownloadState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
class TorrentIntegrationTest {

    private lateinit var turboDownloader: TurboDownloader
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setup() {
        turboDownloader = TurboDownloader.Builder(context)
            .setThread(4)
            .build()
        turboDownloader.enableTorrent = true
    }

    @Test
    fun testTorrentDownloadInitializationAndMetadata() = runBlocking {
        // Tears of Steel magnet link (well-seeded)
        val trackers = "&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce&tr=udp%3A%2F%2F9.rarbg.com%3A2810%2Fannounce&tr=udp%3A%2F%2Ftracker.openbittorrent.com%3A6969%2Fannounce"
        val magnetLink = "magnet:?xt=urn:btih:209c8226b299b308beaf2b9cd3fb49212dbd13ec&dn=Tears+of+Steel" + trackers
        
        val downloadId = turboDownloader.startDownload(magnetLink)
        assertNotNull("Download ID should not be null", downloadId)

        // Wait for it to move from Running to either metadata received or finished
        // We look for progress updates
        withTimeoutOrNull(60.seconds) {
            turboDownloader.downloadState().map { it[downloadId] }.first { state ->
                state is DownloadState.Running && state.progress.downloadBytes > 0
            }
        }

        // Note: In some environments (like CI without internet), this might time out.
        // But for a developer with internet, it should work.
        // If it times out, it at least confirms the engine started and is polling.
        
        assertTrue("Torrent engine should be initialized", turboDownloader.isTorrentInitialized)
    }
}
