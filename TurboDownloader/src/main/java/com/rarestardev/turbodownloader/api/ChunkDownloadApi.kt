package com.rarestardev.turbodownloader.api

import android.annotation.SuppressLint
import android.content.Context
import androidx.room.Room
import com.rarestardev.turbodownloader.core.DownloadManager
import com.rarestardev.turbodownloader.service.DownloadManagerHolder
import com.rarestardev.turbodownloader.storage.DownloadDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@SuppressLint("StaticFieldLeak")
object ChunkDownloadApi {

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


            DownloadManagerHolder.manager =
                DownloadManager(
                    db.dao(),
                    scope,
                    appContext
                )
        }

        return DownloadManagerHolder.manager!!
    }
}