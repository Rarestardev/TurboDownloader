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

        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "MDM Turbo"
        )
        dir.mkdirs()

        val downloader = TurboDownloader.Builder(this,this)
            .setThread(8)
            .setDir(dir)
            .setPermissionChecked(true)
            .build()

        enableEdgeToEdge()

        setContent {
            MDMTurboTheme {
                val observerState by downloader.downloadState().collectAsState()
//                val allDownloads by downloader.getAllDownloads().collectAsState(emptyList())

                val uri =
                    "https://cdn021.ronakfilm.com/TMaApu06/DHfCp2FI/vDZ77P9u/S01/E01/Cape.Fear.2025.S01.E01.480p.mp4"

                var downloadId by remember { mutableStateOf(DownloadId("")) }
                var totalDownload by remember { mutableLongStateOf(-1) }
                var downloadBytes by remember { mutableLongStateOf(-1) }
                var progress by remember { mutableIntStateOf(-1) }

                observerState.forEach { (_, state) ->
                    when (val result = state) {
                        is DownloadState.Cancelled -> println("Download cancelled")
                        is DownloadState.Completed -> println("Download completed")
                        is DownloadState.Failed -> println("Download failed")
                        is DownloadState.Paused -> println("Download paused")
                        is DownloadState.Queued -> {
                            println("Download Queued")
                        }

                        is DownloadState.Running -> {
                            totalDownload = result.progress.totalBytes
                            progress = result.progress.percent
                            downloadBytes = result.progress.downloadBytes

                            println("Progress : $progress \n , TotalDownload : $totalDownload \n , DownloadBytes : $downloadBytes")
                        }

                        DownloadState.Idle -> { println("idle")}
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
                            downloader.startDownload(uri)
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

                    /*LazyColumn(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(allDownloads){ entity  ->
                            val state = observerState[DownloadId(entity.id)]
                            DownloadItem(
                                downloader = downloader,
                                entity = entity,
                                state = state,
                                onPause = { downloader.pause(DownloadId(entity.id)) },
                                onResume = { downloader.resume(DownloadId(entity.id)) },
                                onCancel = { downloader.cancel(DownloadId(entity.id)) }
                            )
                        }
                    }*/
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


/*@Composable
fun DownloadItem(
    entity: DownloadEntity,
    state: DownloadState?,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    downloader: TurboDownloader
) {
    val progress = when(state) {
        is DownloadState.Running -> state.progress.percent
        is DownloadState.Paused -> state.progress.percent
        is DownloadState.Completed -> 100
        else -> 0
    }

    val speed = when(state) {
        is DownloadState.Running -> downloader.formatSpeed(state.speedBytesPerSec)
        else -> ""
    }

    val eta = when(state) {
        is DownloadState.Running -> downloader.formatEta(state.etaSeconds)
        else -> ""
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        // Status Icon
        Icon(
            imageVector = when(state) {
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
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            )

            Text(
                text = "${formatSize(entity.totalBytes)} • $speed • $eta",
                color = Color.LightGray,
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.width(12.dp))

        // Pause / Resume / Cancel
        when(state) {
            is DownloadState.Running -> {
                IconButton(onClick = onPause) {
                    Icon(Icons.Default.Pause, contentDescription = null, tint = Color.White)
                }
            }
            is DownloadState.Paused -> {
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
}*/
