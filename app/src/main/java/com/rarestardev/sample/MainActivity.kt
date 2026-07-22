package com.rarestardev.sample

import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rarestardev.sample.ui.theme.MDMTurboTheme
import com.rarestardev.turbodownloader.core.TurboDownloader
import com.rarestardev.turbodownloader.listener.DownloadNotificationListener
import com.rarestardev.turbodownloader.state.DownloadId
import com.rarestardev.turbodownloader.state.DownloadState
import com.rarestardev.turbodownloader.state.DownloadStatus
import com.rarestardev.turbodownloader.storage.DownloadEntity
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var downloader: TurboDownloader

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "MDM Turbo"
        )
        dir.mkdirs()

        downloader = TurboDownloader.Builder(this, this)
            .setThread(8)
            .setDir(dir)
            .setPermissionChecked(true)
            .setNotificationListener(object : DownloadNotificationListener {
                override fun onNotificationClick(downloadId: DownloadId) {
                    println("click")
                }

                override fun onPauseClick(downloadId: DownloadId) {
                    println("pause click")
                }

                override fun onResumeClick(downloadId: DownloadId) {
                    println("resume click")
                }

                override fun onCancelClick(downloadId: DownloadId) {
                    println("cancel click")
                }

            })
            .build()

        enableEdgeToEdge()

        setContent {
            MDMTurboTheme {
                val scope = rememberCoroutineScope()
                val observerState by downloader.downloadState().collectAsState()
                val allDownloads by downloader.getAllDownloads().collectAsState(emptyList())

//                val uri = "https://cdn021.ronakfilm.com/TMaApu06/DHfCp2FI/vDZ77P9u/S01/E01/Cape.Fear.2025.S01.E01.480p.mp4"
                val uri = "https://cdn01.ronakfilm.com/vC9_--j9/vHheMtmx/vDZ77P9u/Trailer.dub.mp4"

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.DarkGray),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(36.dp))

                    Button(
                        onClick = {
                            downloader.startDownload(uri)
                        },
                        modifier = Modifier.statusBarsPadding()
                    ) {
                        Text(text = "download")
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(allDownloads) { entity ->
                            val state = observerState[DownloadId(entity.id)]
                            DownloadItem(
                                entity = entity,
                                state = state,
                                onPause = { downloader.pause(DownloadId(entity.id)) },
                                onResume = { downloader.resume(DownloadId(entity.id)) },
                                onCancel = { scope.launch { downloader.cancel(DownloadId(entity.id)) } }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        downloader.release()
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


@Composable
fun DownloadItem(
    entity: DownloadEntity,
    state: DownloadState?,
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
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        // Status Icon
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

            else -> {}
        }

        IconButton(onClick = onCancel) {
            Icon(Icons.Default.Close, contentDescription = null, tint = Color.Red)
        }
    }
}

fun Long.toSpeedString(): String {

    val kb = this / 1024.0

    val mb = kb / 1024.0

    return when {
        mb >= 1 -> "%.2f MB/s".format(mb)
        else -> "%.0f KB/s".format(kb)
    }
}