package com.rarestardev.turbodownloader.model

enum class ScheduleSignal {
    START,
    END
}

data class ScheduleSignalRecord(
    val signal: ScheduleSignal,
    val atMillis: Long
)
