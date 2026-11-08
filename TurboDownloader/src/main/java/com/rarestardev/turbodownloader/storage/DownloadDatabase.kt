package com.rarestardev.turbodownloader.storage

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.rarestardev.turbodownloader.storage.converter.DownloadConverters

@Database(
    entities = [
        DownloadEntity::class,
        ChunkEntity::class
    ],
    version = 3
)
@TypeConverters(DownloadConverters::class)
abstract class DownloadDatabase : RoomDatabase() {

    abstract fun dao(): DownloadDao

    companion object {

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    ALTER TABLE downloads
                    ADD COLUMN pauseReason TEXT NOT NULL DEFAULT 'NONE'
                    """.trimIndent()
                )
            }
        }
    }
}