# 🚀 TurboDownloader

A fast, lightweight and powerful multi-thread download manager for Android written in Kotlin.

TurboDownloader supports resumable downloads, foreground service, download notifications, Room persistence, and an easy-to-use Builder API.

---

[![](https://jitpack.io/v/RareStarDev/TurboDownloader.svg)](https://jitpack.io/#RareStarDev/TurboDownloader)

## ✨ Features

- 🚀 Multi-thread downloading
- 🧲 **Torrent downloads with an ON/OFF switch (magnet links + .torrent files)**
- ⏰ **Scheduled downloads (start at a specific time or after a delay)**
- 🐢 **Three-level speed limit (LOW / MEDIUM / HIGH)**
- 🔢 **Concurrent download limit (max parallel downloads)**
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
	 implementation("com.github.RareStarDev:TurboDownloader:1.1.4")
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

## 🧲 Torrent Download (ON / OFF switch)

Torrent support is controlled by a simple **on/off switch**. When it is ON,
`magnet:?...` links and `.torrent` file URLs are downloaded by the built-in
torrent engine (HTTP trackers, peer-wire protocol, piece SHA-1 verification).
When it is OFF, torrent links are rejected with a clear log message.

### Turn it on in the Builder

```kotlin
val downloader = TurboDownloader.Builder(this)
    .setThread(8)
    .setTorrentEnabled(true)   // 🔛 torrent switch: ON
    .build()
```

Or toggle it at runtime:

```kotlin
downloader.setTorrentEnabled(true)  // ON
downloader.setTorrentEnabled(false) // OFF
```

### Start a torrent download (same startDownload API)

```kotlin
// magnet link
downloader.startDownload("magnet:?xt=urn:btih:<INFO_HASH>&dn=Name&tr=<tracker>")

// .torrent file URL
downloader.startDownload("https://example.com/linux.iso.torrent", fileName = "linux.iso")
```

When the switch is OFF and a torrent link is passed, `startDownload` returns
`null` and logs:

```
Turbo_downloader: Torrent download is DISABLED (torrent switch is OFF). Enable it with Builder.setTorrentEnabled(true) or setTorrentEnabled(true).
```

### Observe torrent progress

```kotlin
lifecycleScope.launch {
    downloader.torrentState().collect { torrentStates ->
        // Map<DownloadId, DownloadState> - same states as normal downloads
    }
}
```

Torrent downloads also honor pause / resume / cancel:

```kotlin
downloader.pause(id)
downloader.resume(id)
downloader.cancel(id) // deletes temp pieces
```

> **Notes**
> - The torrent engine supports single-file torrents and HTTP/HTTPS trackers
>   (magnet metadata is resolved over BEP-9 from peers). UDP trackers and DHT
>   are not supported yet.
> - A magnet link without any `tr=` (tracker) parameter cannot be resolved.
> - Completed files are saved to the public **Downloads** collection.

---

## ⏰ Scheduled Downloads

Schedule a download to start at a specific time or after a delay:

```kotlin
// start after 30 minutes
val schedule = downloader.scheduleDownloadAfter(
    url = "https://example.com/video.mp4",
    fileName = "video.mp4",
    delayMillis = 30 * 60 * 1000L
)

// or at an absolute time (epoch millis)
val schedule2 = downloader.scheduleDownload(
    url = "https://example.com/video.mp4",
    triggerAtMillis = System.currentTimeMillis() + 60 * 60 * 1000L
)
```

Manage schedules:

```kotlin
val pending: List<ScheduledDownload> = downloader.getScheduledDownloads()
downloader.cancelScheduled(schedule.id)
```

When the scheduled time arrives the download starts automatically (torrent
links are routed by the torrent switch exactly like normal downloads) and
`Turbo_downloader` logs the event.

> Schedules are kept in memory: they live as long as the app process lives
> and do not survive process death.

---

## 🐢 Speed Limit — three levels (LOW / MEDIUM / HIGH)

The speed limit has **three levels** plus OFF. The level is the strength of
the limitation, and it applies to the **total** speed of all active downloads
(HTTP + torrent) and can be changed while downloads are running:

| Level | Strength of limitation | Total speed cap |
|-------|-----------------------|-----------------|
| `OFF` | no limitation (default) | unlimited |
| `LOW` | light | 2 MB/s |
| `MEDIUM` | medium | 512 KB/s |
| `HIGH` | strong | 128 KB/s |

```kotlin
// in the Builder
val downloader = TurboDownloader.Builder(this)
    .setSpeedLimit(TurboLimitLevel.MEDIUM)
    .build()

// at runtime
downloader.setSpeedLimit(TurboLimitLevel.HIGH)   // slow down everything
downloader.setSpeedLimit(TurboLimitLevel.OFF)    // remove the limit
```

Every applied change is logged:

```
Turbo_downloader: Speed limit set to HIGH (131072 bytes/sec)
```

---

## 🔢 Concurrent Download Limit

Limit **how many downloads run at the same time**. Extra downloads are
queued automatically and start FIFO as soon as a running download completes,
fails, is cancelled, or is paused:

```kotlin
// in the Builder (default = 3)
val downloader = TurboDownloader.Builder(this)
    .setMaxConcurrentDownloads(2)
    .build()

// at runtime
downloader.setMaxConcurrentDownloads(5)
```

Inspect the current usage:

```kotlin
downloader.maxConcurrentDownloads() // the configured limit
downloader.activeDownloadCount()    // running downloads
downloader.queuedDownloadCount()    // downloads waiting for a free slot
```

The limit is shared between HTTP and torrent downloads, and every queue
decision is logged:

```
Turbo_downloader: Concurrent download limit reached (3/3 active, 1 waiting) - queued: video.mp4
```

---

## 🧪 Running the Tests

The library ships with JVM unit tests covering the speed limiter, the
concurrency gate, the scheduler, bencode decoding, torrent metadata, magnet
parsing, URL detection and the piece pool:

```bash
./gradlew :turbodownloader:testDebugUnitTest
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
| setTorrentEnabled(true/false) | 🔛 Torrent ON/OFF switch (magnet + .torrent) |
| setSpeedLimit(TurboLimitLevel) | 🐢 Speed limit: OFF / LOW / MEDIUM / HIGH |
| setMaxConcurrentDownloads(count) | 🔢 Max parallel downloads (default 3) |
| setShowFormatter(true) | Enable speed & ETA formatter |

---

## Logging

Every feature logs through the shared tag **`Turbo_downloader`** — filter
Logcat by `Turbo_downloader` to see torrent routing, metadata resolving,
tracker announces, peer counts, piece verification, schedule firing, speed
limit changes and concurrency queue decisions.

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
- [x] Download Speed Limiter
- [x] Torrent Download (ON/OFF switch)
- [x] Concurrent Download Limit
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
