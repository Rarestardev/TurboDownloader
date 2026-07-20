package com.rarestardev.mdmturbo

import android.Manifest
import android.content.Context
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
import com.rarestardev.turbodownloader.api.ChunkDownloadApi
import com.rarestardev.turbodownloader.model.DownloadRequest
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
                val downloader = remember { MyRepository(this) }
                val observerState by downloader.observeState().collectAsState()
                val uri =
                    "https://cdn021.ronakfilm.com/TMaApu06/DHfCp2FI/vDZ77P9u/S01/E01/Cape.Fear.2025.S01.E01.480p.mp4"
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "MDM Turbo"
                )
                dir.mkdirs()

                var downloadId by remember { mutableStateOf(DownloadId("")) }
                var totalDownload by remember { mutableLongStateOf(-1) }
                var downloadBytes by remember { mutableLongStateOf(-1) }
                var progress by remember { mutableIntStateOf(-1) }

                observerState.forEach { (_, state) ->
                    when(state){
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
                            downloadId = downloader.startDownload(uri, dir)
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

class MyRepository(context: Context) {

    private val api = ChunkDownloadApi(context)
    private val manager = api.manager

    fun startDownload(url: String, dir: File): DownloadId {
        val request = DownloadRequest(
            uri = url,
            fileName = "file_${System.currentTimeMillis()}.bin",
            destinationDir = dir,
            chunkCount = 8
        )
        return manager.enqueue(request)
    }

    fun pause(id: DownloadId) = manager.pause(id)

    fun resume(id: DownloadId) = manager.resume(id)

    fun cancel(id: DownloadId) = manager.cancel(id)

    fun observeState() = manager.state // Flow<Map<DownloadId, DownloadState>>
}