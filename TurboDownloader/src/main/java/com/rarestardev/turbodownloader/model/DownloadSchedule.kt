package com.rarestardev.turbodownloader.model

import java.time.LocalTime

data class DownloadSchedule(
    val startTime: LocalTime,
    val endTime: LocalTime
)
