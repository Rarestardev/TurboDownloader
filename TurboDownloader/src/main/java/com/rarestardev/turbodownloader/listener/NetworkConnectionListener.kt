package com.rarestardev.turbodownloader.listener

interface NetworkConnectionListener {
    fun onRetry(attempt: Int,maxRetries: Int,delayMs: Long)

    fun onInternetAvailable()

    fun onInternetFailed()
}