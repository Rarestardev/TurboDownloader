package com.rarestardev.turbodownloader.storage.converter

import androidx.room.TypeConverter
import com.rarestardev.turbodownloader.state.DownloadStatus
import com.rarestardev.turbodownloader.state.PauseReason

class DownloadConverters {

    @TypeConverter
    fun fromDownloadStatus(value: DownloadStatus?): String? =
        value?.name

    @TypeConverter
    fun toDownloadStatus(value: String?): DownloadStatus? =
        value?.let { DownloadStatus.valueOf(it) }

    @TypeConverter
    fun fromPauseReason(value: PauseReason?): String? =
        value?.name

    @TypeConverter
    fun toPauseReason(value: String?): PauseReason? =
        value?.let { PauseReason.valueOf(it) }
}