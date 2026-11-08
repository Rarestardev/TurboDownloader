# 🚀 TurboDownloader

A fast, lightweight and powerful multi-thread download manager for Android written in Kotlin.

TurboDownloader supports resumable downloads, foreground service, download notifications, Room persistence, and an easy-to-use Builder API.

---

[![](https://jitpack.io/v/RareStarDev/TurboDownloader.svg)](https://jitpack.io/#RareStarDev/TurboDownloader)

## ✨ Features

- 🚀 Multi-thread downloading
- ⏸ Pause & Resume downloads
- ❌ Cancel downloads
- 📂 Automatic file merging
- 🔔 Foreground Service notifications
- 📊 Real-time progress updates
- 💾 Download persistence using Room
- 📱 Android 7.0+ (API 24)
- ⚡ Kotlin First
- 🧩 Easy Builder API
- 🕒 One-time download windows, turned on or off at runtime
- 🎯 Lightweight & easy to integrate

---

## Installation

### Step 1

Add JitPack repository:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### Step 2

Add dependency:

```kotlin
dependencies {
	 implementation("com.github.RareStarDev:TurboDownloader:1.4.0")
}
```

---

## Quick Start

```kotlin
val downloader = TurboDownloader.Builder(
    activity = this,
    context = this
)
    .setThread(8)
    .setDir(getExternalFilesDir(null)!!)
    .setShowFormatter(true)
    .setPermissionChecked(true)
    .build()
```

Start download:

```kotlin
val id = downloader.startDownload(
    url = "https://example.com/video.mp4",
    fileName = "video.mp4"
)
```

Pause:

```kotlin
downloader.pause(id)
```

Resume:

```kotlin
downloader.resume(id)
```

Cancel:

```kotlin
downloader.cancel(id)
```

Release completed downloads:

```kotlin
downloader.release()
```

---

## Observe Download State

```kotlin
lifecycleScope.launch {
    downloader.downloadState().collect { states ->
        // Observe download states
    }
}
```

---

## Builder Options

| Method | Description |
|---------|-------------|
| setThread(count) | Number of download threads |
| setDir(file) | Download destination |
| setPermissionChecked(true) | Automatically request notification permission |
| setShowFormatter(true) | Enable speed & ETA formatter |
| enableSchedule(true) | Optional. Sets the initial value of `scheduleEnabled` only. Omit it to keep the last saved choice and change scheduling later on the instance. |

---

## Download schedule

Scheduling stays off until you turn it on. Do that on the live instance, not only in the builder:

```kotlin
val downloader = TurboDownloader.Builder(context)
    .setThread(8)
    .build()

if (downloader.scheduleEnabled) {
    downloader.reconcileSchedule()
}

downloader.scheduleEnabled = true
```

`scheduleEnabled` is saved. The next time the process starts, read it and call `reconcileSchedule()` when it is already true so the saved window is armed again after a restart or force-stop.

Turning scheduling on applies the saved window:

- downloads run only between the saved start and end
- the window does not repeat
- outside the window, an immediate download waits; a queued item stays queued
- when the start time is reached, queued items start; a download you paused yourself stays paused
- after the end, they stay paused until `clearSchedule()`
- a download you paused yourself stays paused

Turning scheduling off keeps the saved window but stops enforcing it. Downloads that the window itself had paused may continue. Items added only to the queue stay held, and a download you paused yourself stays paused. Set `scheduleEnabled = true` again to arm the same window. Opening the app or calling `reconcileSchedule()` does not start those held items.

`startDownload` is the immediate download. It starts at once when scheduling is off or the window is already open, and waits only when a saved window is still closed.

`addToQueues` never starts a download by itself. Not when scheduling is off, not when the window is already open, and not when a second item is added. Call `resume(id)` to start that held item explicitly. An enabled schedule also starts held items when its start time arrives.

Change the window only while scheduling is on:

```kotlin
downloader.scheduleEnabled = true
downloader.setSchedule(
    DownloadSchedule(
        startAtMillis = start,
        endAtMillis = end
    )
)
downloader.clearSchedule()
val saved = downloader.currentSchedule()
```

`setSchedule`, `clearSchedule`, and `reconcileSchedule` throw if scheduling is off. `currentSchedule()` still returns a window that was kept while scheduling is off. The end must be after the start and in the future. A window may already have started.

Exact alarms:

- Android 7 through 11 fire the window at the chosen time without an extra permission.
- On Android 12 and later, the window opens on time while the app is in the background only if exact alarms are allowed. `exactAlarmAccess()` reports whether that permission is required, whether it is granted, and the settings screen that requests it.
- While this process is alive, the window still opens and closes on time if exact alarms are unavailable. A stopped process needs the exact alarm to wake it.

The library manifest already declares `SCHEDULE_EXACT_ALARM` and `RECEIVE_BOOT_COMPLETED`.

---

## Download States

- Queued
- Running
- Paused
- Completed
- Failed
- Cancelled

---

## Requirements

- Android API 24+
- Kotlin
- Jetpack

---

## Roadmap

- [x] Download Queue
- [ ] Download Priority
- [ ] Download Groups
- [x] Retry Failed Downloads
- [x] Download Scheduler
- [ ] Wi-Fi Only Downloads
- [ ] Download Speed Limiter
- [x] Download Listener
- [ ] Custom Notifications
- [ ] Compose Components

---

## License

MIT License

---

## Author

Developed by **Soheyl Darzi**

GitHub:
https://github.com/RareStarDev

---

## Contributing

Contributions, issues and feature requests are welcome!

If you like this project, please ⭐ the repository.
