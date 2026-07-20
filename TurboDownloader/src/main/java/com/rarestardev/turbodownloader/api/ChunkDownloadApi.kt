package com.rarestardev.turbodownloader.api

import android.content.Context
import androidx.room.Room
import com.rarestardev.turbodownloader.core.DownloadManager
import com.rarestardev.turbodownloader.storage.DownloadDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ChunkDownloadApi(
    context: Context
) {

    private val appContext = context.applicationContext
    private val db = Room.databaseBuilder(
        appContext,
        DownloadDatabase::class.java,
        "chunk_downloads.db"
    ).build()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val manager = DownloadManager(db.dao(), scope, appContext)
}