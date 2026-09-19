package com.rarestardev.sample

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.rarestardev.sample.ui.theme.MDMTurboTheme
import com.rarestardev.turbodownloader.core.TurboDownloader
import com.rarestardev.turbodownloader.limit.TurboLimitLevel
import com.rarestardev.turbodownloader.listener.DownloadNotificationListener
import com.rarestardev.turbodownloader.listener.NetworkConnectionListener
import com.rarestardev.turbodownloader.schedule.ScheduledDownload
import com.rarestardev.turbodownloader.state.DownloadId
import com.rarestardev.turbodownloader.state.DownloadState
import com.rarestardev.turbodownloader.state.DownloadStatus
import com.rarestardev.turbodownloader.storage.DownloadEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var downloader: TurboDownloader

    @SuppressLint("ObsoleteSdkInt")
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        downloader = TurboDownloader.Builder(this)
            .setThread(8)
            .setAutoThreading(true)
            // 🎛 The new library options (all also changeable at runtime below):
            .setTorrentEnabled(true)              // torrent ON/OFF switch
            .setSpeedLimit(TurboLimitLevel.OFF)   // three-level limit: OFF/LOW/MEDIUM/HIGH
            .setMaxConcurrentDownloads(3)         // concurrent downloads limit
            .build()

        enableEdgeToEdge()

        setContent {
            MDMTurboTheme {
                MainUiScreen()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        downloader.release()
    }

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter", "InlinedApi")
    @Composable
    private fun MainUiScreen() {
        val scope = rememberCoroutineScope()
        val observerState by downloader.downloadState().collectAsState()
        val torrentState by downloader.torrentState().collectAsState()
        val torrentTitles by downloader.torrentTitles().collectAsState()
        val allDownloads by downloader.getAllDownloads().collectAsState(emptyList())

        var hasPermission by remember { mutableStateOf(false) }
        var urlAddressState by remember { mutableStateOf("") }

        // ⚙️ Runtime settings (mirrors the Builder options)
        var torrentEnabled by remember { mutableStateOf(downloader.torrentEnabled) }
        var limitLevel by remember { mutableStateOf(downloader.speedLimit()) }
        var maxDownloads by remember { mutableIntStateOf(downloader.maxConcurrentDownloads()) }
        var scheduled by remember { mutableStateOf(listOf<ScheduledDownload>()) }

        LaunchedEffect(downloader.hasNotificationPermission()) {
            hasPermission = downloader.hasNotificationPermission()
        }

        // keep the scheduled list fresh (pure sample convenience)
        LaunchedEffect(Unit) {
            while (true) {
                scheduled = downloader.getScheduledDownloads()
                delay(1_000)
            }
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    Spacer(Modifier.height(36.dp))
                    Icon(
                        imageVector = Icons.Default.Downloading,
                        contentDescription = null,
                        modifier = Modifier.size(60.dp)
                    )
                }

                // ---------------- settings card ----------------
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .background(Color.DarkGray, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Library Settings", color = Color.White)

                        // 1) Torrent ON/OFF switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Torrent download (magnet / .torrent)",
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = torrentEnabled,
                                onCheckedChange = {
                                    torrentEnabled = it
                                    downloader.setTorrentEnabled(it) // 🔛 the on/off switch
                                }
                            )
                        }

                        // 2) Three-level speed limit
                        Text("Speed limit", color = Color.White)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TurboLimitLevel.entries.forEach { level ->
                                FilterChip(
                                    selected = limitLevel == level,
                                    onClick = {
                                        limitLevel = level
                                        downloader.setSpeedLimit(level) // 🐢 OFF/LOW/MEDIUM/HIGH
                                    },
                                    label = { Text(level.name) }
                                )
                            }
                        }

                        // 3) Concurrent downloads limit
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Max parallel downloads",
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                if (maxDownloads > 1) {
                                    maxDownloads--
                                    downloader.setMaxConcurrentDownloads(maxDownloads) // 🔢 limit
                                }
                            }) { Icon(Icons.Default.Remove, contentDescription = null, tint = Color.White) }

                            Text("$maxDownloads", color = Color.White)

                            IconButton(onClick = {
                                if (maxDownloads < 10) {
                                    maxDownloads++
                                    downloader.setMaxConcurrentDownloads(maxDownloads)
                                }
                            }) { Icon(Icons.Default.Add, contentDescription = null, tint = Color.White) }
                        }
                    }
                }

                // ---------------- url + start ----------------
                item {
                    OutlinedTextField(
                        value = urlAddressState,
                        onValueChange = { urlAddressState = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        label = { Text("Address / magnet link...") },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Default.ArrowForward,
                                contentDescription = "start download",
                                modifier = Modifier.clickable {
                                    scope.launch {
                                        if (urlAddressState.isNotEmpty()) {
                                            if (hasPermission) {
                                                val id = downloader.startDownload(urlAddressState)
                                                if (id == null) {
                                                    Toast.makeText(
                                                        this@MainActivity,
                                                        "Rejected! Torrent switch is OFF or link is invalid - check Logcat (Turbo_downloader)",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                } else {
                                                    urlAddressState = ""
                                                }
                                            } else {
                                                requestNotificationPermission()
                                            }
                                        } else {
                                            Toast.makeText(
                                                this@MainActivity,
                                                "Paste address to field...",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            )
                        }
                    )
                }

                // ---------------- schedule buttons ----------------
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        listOf("30s" to 30_000L, "1m" to 60_000L, "5m" to 300_000L)
                            .forEach { (label, delayMs) ->
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        if (urlAddressState.isNotEmpty()) {
                                            val item = downloader.scheduleDownloadAfter(
                                                urlAddressState, null, delayMs
                                            )
                                            Toast.makeText(
                                                this@MainActivity,
                                                "Scheduled (${item.id.take(8)}...) in $label",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            urlAddressState = ""
                                        } else {
                                            Toast.makeText(
                                                this@MainActivity,
                                                "Paste address first",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    label = { Text("⏰ $label") }
                                )
                            }
                    }
                }

                // ---------------- scheduled list ----------------
                if (scheduled.isNotEmpty()) {
                    item { Text("Scheduled (${scheduled.size})") }
                    items(scheduled, key = { it.id }) { item ->
                        ScheduledItem(
                            item = item,
                            onCancel = { downloader.cancelScheduled(item.id) }
                        )
                    }
                }

                // ---------------- torrent downloads ----------------
                if (torrentState.isNotEmpty()) {
                    item { Text("Torrents") }
                    items(torrentState.entries.toList(), key = { it.key.value }) { (id, state) ->
                        TorrentItem(
                            title = torrentTitles[id] ?: "Torrent ${id.value.take(8)}",
                            state = state,
                            onPause = { downloader.pause(id) },
                            onResume = { downloader.resume(id) },
                            onCancel = { scope.launch { downloader.cancel(id) } }
                        )
                    }
                }

                // ---------------- normal downloads ----------------
                item {
                    Spacer(Modifier.height(8.dp))
                    if (allDownloads.isEmpty() && torrentState.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No downloads item")
                        }
                    } else if (allDownloads.isNotEmpty()) {
                        Text("Downloads")
                    }
                }

                if (allDownloads.isNotEmpty()) {
                    items(allDownloads, key = { it.id }) { entity ->
                        val state = observerState[DownloadId(entity.id)]
                        DownloadItem(
                            entity = entity,
                            state = state,
                            onPause = { downloader.pause(DownloadId(entity.id)) },
                            onResume = { downloader.resume(DownloadId(entity.id)) },
                            onCancel = { scope.launch { downloader.cancel(DownloadId(entity.id)) } },
                            onRemove = {
                                downloader.removeDownloadFile(
                                    DownloadId(entity.id),
                                    true
                                )
                            }
                        )
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }

        notificationClickHandler()
        networkConnection()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                100
            )
        } else {
            val intent = Intent().apply {
                action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
            startActivity(intent)
        }
    }

    private fun notificationClickHandler() {
        downloader.setNotificationListener(object : DownloadNotificationListener {
            override fun onNotificationClick(downloadId: DownloadId) {
                val intent = Intent(this@MainActivity, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                }

                startActivity(intent)
            }
        })
    }

    private fun networkConnection() {
        downloader.setNetworkConnectionListener(
            object : NetworkConnectionListener {
                override fun onRetry(
                    attempt: Int,
                    maxRetries: Int,
                    delayMs: Long
                ) {
                    println("تلاش $attempt از $maxRetries – اینترنت قطع است")
                }

                override fun onInternetAvailable() {
                    println("اینترنت وصل شد، دانلود شروع می‌شود...")
                }

                override fun onInternetFailed() {
                    println("دانلود ناموفق: اتصال برقرار نشد")
                }

            }
        )
    }

    @Composable
    private fun ScheduledItem(item: ScheduledDownload, onCancel: () -> Unit) {
        val remaining = item.triggerAtMillis - System.currentTimeMillis()
        val remainingText = when {
            remaining <= 0 -> "starting..."
            remaining < 60_000 -> "${remaining / 1000}s"
            else -> "${remaining / 60_000}m"
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .background(Color(0xFF2A2A2A), RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Schedule, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.url, color = Color.White, fontSize = 12.sp, maxLines = 1)
                Text(
                    "starts in $remainingText",
                    color = Color.LightGray,
                    fontSize = 11.sp
                )
            }
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
            }
        }
    }

    @Composable
    private fun TorrentItem(
        title: String,
        state: DownloadState,
        onPause: () -> Unit,
        onResume: () -> Unit,
        onCancel: () -> Unit
    ) {
        val progress = when (state) {
            is DownloadState.Running -> state.progress.percent
            is DownloadState.Paused -> state.progress.percent
            is DownloadState.Completed -> 100
            else -> 0
        }

        val speed = when (state) {
            is DownloadState.Running -> state.progress.speedBytesPerSec.toSpeedString()
            else -> "0 KB"
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .background(Color.DarkGray, RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (state) {
                    is DownloadState.Running -> Icons.Default.Downloading
                    is DownloadState.Paused -> Icons.Default.Pause
                    is DownloadState.Completed -> Icons.Default.Check
                    is DownloadState.Failed -> Icons.Default.Error
                    else -> Icons.Default.HourglassEmpty
                },
                contentDescription = null,
                tint = Color(0xFFFF9800),
                modifier = Modifier.size(32.dp)
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text("🧲 $title", color = Color.White, fontSize = 13.sp, maxLines = 1)
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                )
                Text(
                    text = "$progress% • $speed • ${state::class.simpleName}",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
            }

            when (state) {
                is DownloadState.Running -> IconButton(onClick = onPause) {
                    Icon(Icons.Default.Pause, contentDescription = null, tint = Color.White)
                }
                is DownloadState.Paused -> IconButton(onClick = onResume) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                }
                else -> Unit
            }

            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
            }
        }
    }

    @Composable
    private fun DownloadItem(
        entity: DownloadEntity,
        state: DownloadState?,
        onRemove: () -> Unit,
        onPause: () -> Unit,
        onResume: () -> Unit,
        onCancel: () -> Unit
    ) {
        val progress = when (state) {
            is DownloadState.Running -> state.progress.percent
            is DownloadState.Paused -> state.progress.percent
            is DownloadState.Completed -> 100
            else -> 0
        }

        val downloaded = when (state) {
            is DownloadState.Running -> state.progress.downloadBytes
            else -> 0L
        }

        val speed = when (state) {
            is DownloadState.Running -> state.progress.speedBytesPerSec.toSpeedString()
            else -> "0 KB"
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .background(
                    color = Color.DarkGray,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (state) {
                    is DownloadState.Running -> Icons.Default.Downloading
                    is DownloadState.Paused -> Icons.Default.Pause
                    is DownloadState.Completed -> Icons.Default.Check
                    is DownloadState.Failed -> Icons.Default.Error
                    else -> Icons.Default.HourglassEmpty
                },
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(entity.fileName, color = Color.White)

                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                )

                Text(
                    text = "$progress% • ${formatSize(downloaded)} / ${formatSize(entity.totalBytes)} • $speed • ${entity.status.name}",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.width(12.dp))

            // Pause / Resume / Cancel
            when (entity.status) {
                DownloadStatus.RUNNING -> {
                    IconButton(onClick = onPause) {
                        Icon(Icons.Default.Pause, contentDescription = null, tint = Color.White)
                    }
                }

                DownloadStatus.PAUSED -> {
                    IconButton(onClick = onResume) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                    }
                }

                DownloadStatus.FAILED -> {
                    IconButton(onClick = onResume) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                    }
                }

                else -> {}
            }

            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
            }

            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red)
            }
        }
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = bytes.toDouble()
        var index = 0

        while (size >= 1024 && index < units.lastIndex) {
            size /= 1024
            index++
        }
        return "%.2f %s".format(size, units[index])
    }

    private fun Long.toSpeedString(): String {
        val kb = this / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1 -> "%.2f MB/s".format(mb)
            else -> "%.0f KB/s".format(kb)
        }
    }
}
