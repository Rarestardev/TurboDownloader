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
	 implementation("com.github.RareStarDev:TurboDownloader:v1.1.0-release")
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
- [ ] Download Scheduler
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
