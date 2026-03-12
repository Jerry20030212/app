package com.ex.mountaintimer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 歷史紀錄詳情畫面
 *
 * - 地圖上顯示跑山軌跡（藍色 Polyline）
 * - 標示所有自訂點的位置
 * - 顯示分段時間
 */
@Composable
fun HistoryDetailScreen(
    runId: Long,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val historyRepo = remember { HistoryRepository(context) }
    val routeRepo = remember { RouteRepository(context) }

    var runResult by remember { mutableStateOf<RunResultEntity?>(null) }
    var splitTimes by remember { mutableStateOf<List<SplitTimeEntity>>(emptyList()) }
    var trackPoints by remember { mutableStateOf<List<TrackPointEntity>>(emptyList()) }
    var routeGates by remember { mutableStateOf<List<GateEntity>>(emptyList()) }

    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()) }

    // 載入資料
    LaunchedEffect(runId) {
        runResult = historyRepo.getRunResult(runId)
        splitTimes = historyRepo.getSplitTimes(runId)
        trackPoints = historyRepo.getTrackPoints(runId)

        val result = runResult
        if (result != null) {
            val routeWithGates = routeRepo.getRouteWithGates(result.routeId)
            routeGates = routeWithGates?.gates ?: emptyList()
        }
    }

    val run = runResult

    // 計算軌跡的中心點
    val trackLatLngs = trackPoints.map { LatLng(it.lat, it.lng) }
    val centerLatLng = if (trackLatLngs.isNotEmpty()) {
        val avgLat = trackLatLngs.map { it.latitude }.average()
        val avgLng = trackLatLngs.map { it.longitude }.average()
        LatLng(avgLat, avgLng)
    } else {
        LatLng(25.034, 121.564)
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(centerLatLng, 15f)
    }

    LaunchedEffect(trackLatLngs) {
        if (trackLatLngs.isNotEmpty()) {
            val avgLat = trackLatLngs.map { it.latitude }.average()
            val avgLng = trackLatLngs.map { it.longitude }.average()
            cameraPositionState.position =
                CameraPosition.fromLatLngZoom(LatLng(avgLat, avgLng), 16f)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ---- 頂部列 ----
        Surface(
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) {
                    Text("← 返回")
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    run?.routeName ?: "紀錄詳情",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(64.dp))
            }
        }

        // ---- 地圖 + 資訊 ----
        if (run == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                // 地圖
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState
                ) {
                    // 軌跡（藍色 Polyline）
                    if (trackLatLngs.size >= 2) {
                        Polyline(
                            points = trackLatLngs,
                            color = Color.Blue,
                            width = 8f
                        )
                    }

                    // 起點標記
                    if (trackLatLngs.isNotEmpty()) {
                        Marker(
                            state = MarkerState(position = trackLatLngs.first()),
                            title = "起點"
                        )
                    }

                    // 終點標記
                    if (trackLatLngs.size >= 2) {
                        Marker(
                            state = MarkerState(position = trackLatLngs.last()),
                            title = "終點"
                        )
                    }

                    // 路線 Gate 標示
                    routeGates.forEach { g ->
                        val gateColor = when (g.type) {
                            "START" -> Color.Green
                            "FINISH" -> Color.Red
                            else -> Color(0xFFFF9800)
                        }
                        Polyline(
                            points = listOf(
                                LatLng(g.aLat, g.aLng),
                                LatLng(g.bLat, g.bLng)
                            ),
                            color = gateColor,
                            width = 10f
                        )

                        // 自訂點標記
                        if (g.type == "CUSTOM") {
                            val midLat = (g.aLat + g.bLat) / 2
                            val midLng = (g.aLng + g.bLng) / 2
                            Marker(
                                state = MarkerState(position = LatLng(midLat, midLng)),
                                title = "自訂點${g.index}",
                                snippet = splitTimes
                                    .find { it.checkpointIndex == g.index }
                                    ?.let { "時間: ${formatMs(it.timeMs)}" }
                                    ?: ""
                            )
                        }
                    }
                }

                // ---- 底部資訊面板 ----
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Color(0xEE000000),
                            RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                        )
                        .padding(16.dp)
                ) {
                    Text(
                        text = run.routeName,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = dateFormat.format(Date(run.startTimeEpoch)),
                        color = Color(0xFFBBBBBB),
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "總時間: ${formatMs(run.totalTimeMs)}",
                        color = Color(0xFF4CAF50),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (splitTimes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Divider(color = Color(0xFF555555))
                        Spacer(modifier = Modifier.height(8.dp))

                        splitTimes.forEach { split ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = split.checkpointName,
                                    color = Color(0xFFFFCC80),
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = formatMs(split.timeMs),
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
