package com.rarestardev.turbodownloader.core

import android.content.Intent

data class ExactAlarmAccess(
    val required: Boolean,
    val granted: Boolean,
    val settingsIntent: Intent?
)
