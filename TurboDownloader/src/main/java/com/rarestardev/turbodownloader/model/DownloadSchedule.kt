package com.rarestardev.turbodownloader.model

data class DownloadSchedule(
    val startAtMillis: Long,
    val endAtMillis: Long
) {
    init {
        require(startAtMillis > 0) { " startAtMillis must be greater than zero" }
        require(endAtMillis > startAtMillis) { " endAtMillis must be after startAtMillis" }
    }

    fun isBeforeStart(now: Long = System.currentTimeMillis()) = now < startAtMillis

    fun isActive(now: Long = System.currentTimeMillis()) = now in startAtMillis until endAtMillis

    fun isExpired(now: Long = System.currentTimeMillis()) = now >= endAtMillis
}