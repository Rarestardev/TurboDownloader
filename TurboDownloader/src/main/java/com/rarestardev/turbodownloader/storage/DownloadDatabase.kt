package com.rarestardev.turbodownloader.storage

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [DownloadEntity::class, ChunkEntity::class], version = 3)
abstract class DownloadDatabase : RoomDatabase(){
    abstract fun dao() : DownloadDao
}