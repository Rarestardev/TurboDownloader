package com.rarestardev.turbodownloader.api

import android.annotation.SuppressLint
import android.content.Context
import androidx.room.Room
import com.rarestardev.turbodownloader.core.ConcurrentDownloadGate
import com.rarestardev.turbodownloader.core.DownloadManager
import com.rarestardev.turbodownloader.limit.SpeedLimiter
import com.rarestardev.turbodownloader.listener.DownloadNotificationListener
import com.rarestardev.turbodownloader.service.DownloadManagerHolder
import com.rarestardev.turbodownloader.storage.DownloadDatabase
import com.rarestardev.turbodownloader.torrent.TorrentDownloadManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@SuppressLint("StaticFieldLeak")
object ChunkDownloadApi {

    private var listener: DownloadNotificationListener? = null
    private var limiter: SpeedLimiter? = null
    private var gate: ConcurrentDownloadGate? = null
    private var torrentManager: TorrentDownloadManager? = null
    private var torrentScope: CoroutineScope? = null

    @Synchronized
    fun get(
        context: Context
    ): DownloadManager {

        if (DownloadManagerHolder.manager == null) {

            val appContext =
                context.applicationContext

            val db =
                Room.databaseBuilder(
                    appContext,
                    DownloadDatabase::class.java,
                    "chunk_downloads.db"
                ).build()

            val scope =
                CoroutineScope(
                    Dispatchers.IO +
                            SupervisorJob()
                )

            limiter = SpeedLimiter()
            gate = ConcurrentDownloadGate()

            DownloadManagerHolder.manager =
                DownloadManager(
                    db.dao(),
                    scope,
                    appContext,
                    requireNotNull(gate),
                    requireNotNull(limiter)
                )
        }

        return DownloadManagerHolder.manager!!
    }

    /** Speed limiter shared by the HTTP engine and the torrent engine. */
    fun speedLimiter(): SpeedLimiter =
        requireNotNull(limiter) { "ChunkDownloadApi.get(context) must be called first" }

    /** Concurrency gate shared by the HTTP engine and the torrent engine. */
    fun downloadGate(): ConcurrentDownloadGate =
        requireNotNull(gate) { "ChunkDownloadApi.get(context) must be called first" }

    /** Singleton torrent engine (magnet links + .torrent files). */
    @Synchronized
    fun getTorrentManager(
        context: Context
    ): TorrentDownloadManager {
        get(context) // make sure the core (gate/limiter) is initialized

        if (torrentManager == null) {
            torrentScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            torrentManager = TorrentDownloadManager(
                context.applicationContext,
                requireNotNull(torrentScope),
                speedLimiter(),
                downloadGate()
            )
        }
        return torrentManager!!
    }

    fun setNotificationListener(
        listener: DownloadNotificationListener
    ) {
        this.listener = listener
    }


    fun getNotificationListener():
            DownloadNotificationListener? {

        return listener
    }
}