package com.ex.mountaintimer

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDetailScreen(
    runId: Long,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { HistoryRepository(context) }

    var runResult by remember { mutableStateOf<RunResultEntity?>(null) }
    var splits by remember { mutableStateOf<List<SplitTimeEntity>>(emptyList()) }
    var points by remember { mutableStateOf<List<TrackPointEntity>>(emptyList()) }

    LaunchedEffect(runId) {
        runResult = repo.getRunResult(runId)
        splits = repo.getSplitTimes(runId)
        points = repo.getTrackPoints(runId)
    }

    val cameraPositionState = rememberCameraPositionState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("跑山詳情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        runResult?.let { run ->
                            exportGpx(context, run, points)
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Export GPX")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // 1. 軌跡簡圖
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
            ) {
                if (points.isNotEmpty()) {
                    val latLngs = points.map { LatLng(it.lat, it.lng) }
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        uiSettings = MapUiSettings(zoomControlsEnabled = false, scrollGesturesEnabled = false, zoomGesturesEnabled = false)
                    ) {
                        Polyline(points = latLngs, color = Color.Blue, width = 10f)
                    }
                    
                    LaunchedEffect(points) {
                        if (latLngs.isNotEmpty()) {
                            val boundsBuilder = LatLngBounds.Builder()
                            latLngs.forEach { boundsBuilder.include(it) }
                            val bounds = boundsBuilder.build()
                            cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(bounds, 50))
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("無軌跡數據")
                    }
                }
            }

            // 2. 總體數據
            runResult?.let { run ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(run.routeName, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        if (run.vehicleModel.isNotBlank()) {
                            Text("車型: ${run.vehicleModel}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            StatItem("總耗時", formatMs(run.totalTimeMs))
                            StatItem("總距離", "%.2f km".format(run.totalDistanceM / 1000.0))
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            StatItem("平均速度", "%.1f km/h".format(run.averageSpeedKmh))
                            StatItem("日期", SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(run.startTimeEpoch)))
                        }
                    }
                }
            }

            // 3. 分段數據列表
            Text(
                "分段數據",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            splits.forEach { split ->
                ListItem(
                    headlineContent = { Text(split.checkpointName, fontWeight = FontWeight.Bold) },
                    supportingContent = {
                        Text("速度: %.1f km/h | G力: %.2fG".format(split.speed * 3.6, split.gForce))
                    },
                    trailingContent = {
                        Text(formatMs(split.timeMs), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = Color.LightGray.copy(alpha = 0.5f))
            }
            
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column {
        Text(label, fontSize = 12.sp, color = Color.Gray)
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

fun exportGpx(context: Context, run: RunResultEntity, points: List<TrackPointEntity>) {
    val gpxContent = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<gpx version=\"1.1\" creator=\"MountainTimer\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        append("  <metadata>\n")
        append("    <name>${run.routeName}</name>\n")
        append("    <desc>Vehicle: ${run.vehicleModel}</desc>\n")
        append("    <time>${SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(run.startTimeEpoch))}</time>\n")
        append("  </metadata>\n")
        append("  <trk>\n")
        append("    <name>${run.routeName}</name>\n")
        append("    <trkseg>\n")
        points.forEach { p ->
            val time = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(p.timestampMs))
            append("      <trkpt lat=\"${p.lat}\" lon=\"${p.lng}\">\n")
            append("        <time>$time</time>\n")
            append("      </trkpt>\n")
        }
        append("    </trkseg>\n")
        append("  </trk>\n")
        append("</gpx>")
    }

    val fileName = "Run_${run.routeName}_${System.currentTimeMillis()}.gpx"
    
    try {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/gpx+xml")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        
        val uri: Uri? = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let {
            val outputStream: OutputStream? = resolver.openOutputStream(it)
            outputStream?.use { os ->
                os.write(gpxContent.toByteArray())
            }
            Toast.makeText(context, "GPX 已儲存至下載資料夾", Toast.LENGTH_LONG).show()
        } ?: run {
            Toast.makeText(context, "儲存失敗", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "錯誤: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
