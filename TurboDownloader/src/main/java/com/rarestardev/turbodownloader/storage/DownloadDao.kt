package com.rarestardev.turbodownloader.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunk(chunk: ChunkEntity)

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getDownload(id: String): DownloadEntity?

    @Query("SELECT * FROM chunks WHERE downloadId = :id")
    suspend fun getChunks(id: String): List<ChunkEntity>

    @Query("UPDATE downloads SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("SELECT * FROM downloads")
    fun getAllDownloads(): Flow<List<DownloadEntity>>
}