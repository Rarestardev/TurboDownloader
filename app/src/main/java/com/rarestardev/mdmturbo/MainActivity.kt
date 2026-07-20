package com.rarestardev.mdmturbo

import android.Manifest
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.rarestardev.mdmturbo.ui.theme.MDMTurboTheme
import com.rarestardev.turbodownloader.core.TurboDownloader
import com.rarestardev.turbodownloader.state.DownloadId
import com.rarestardev.turbodownloader.state.DownloadState
import java.io.File

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            100
        )

        enableEdgeToEdge()

        setContent {
            MDMTurboTheme {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "MDM Turbo"
                )
                dir.mkdirs()

                val downloader = TurboDownloader.Builder(this)
                    .setThread(4)
                    .setDir(dir)
                    .setPermissionChecked(true)
                    .build()

                val observerState by downloader.observeState().collectAsState()
                val uri =
                    "https://cdn021.ronakfilm.com/TMaApu06/DHfCp2FI/vDZ77P9u/S01/E01/Cape.Fear.2025.S01.E01.480p.mp4"

                var downloadId by remember { mutableStateOf(DownloadId("")) }
                var totalDownload by remember { mutableLongStateOf(-1) }
                var downloadBytes by remember { mutableLongStateOf(-1) }
                var progress by remember { mutableIntStateOf(-1) }

                observerState.forEach { (_, state) ->
                    when (state) {
                        is DownloadState.Cancelled -> println(state.id.value)
                        is DownloadState.Completed -> println(state.id.value)
                        is DownloadState.Failed -> println(state.id.value)
                        is DownloadState.Paused -> println(state.id.value)
                        is DownloadState.Queued -> {
                            println(state.id.value)
                        }

                        is DownloadState.Running -> {
                            totalDownload = state.progress.totalBytes
                            progress = state.progress.percent
                            downloadBytes = state.progress.downloadBytes
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .background(Color.DarkGray),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = {
                            downloadId = downloader.startDownload(uri, "")
                        }
                    ) {
                        Text(text = "download")
                    }

                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = {
                            downloader.pause(downloadId)
                        }
                    ) {
                        Text(text = "pause")
                    }

                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = {
                            downloader.resume(downloadId)
                        }
                    ) {
                        Text(text = "resume")
                    }

                    Spacer(Modifier.height(12.dp))

                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                    )

                    Text(
                        text = "${formatSize(downloadBytes)} / ${formatSize(totalDownload)}"
                    )
                }
            }
        }
    }
}

fun formatSize(bytes: Long): String {
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