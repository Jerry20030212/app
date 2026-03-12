package com.ex.mountaintimer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
@OptIn(ExperimentalMaterial3Api::class)
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

        // 載入路線的 Gate（用來在地圖上標示）
        val result = runResult
        if (result != null) {
            val routeWithGates = routeRepo.getRouteWithGates(result.routeId)
            routeGates = routeWithGates?.gates ?: emptyList()
        }
    }

    val run = runResult

    // 計算軌跡的中心點和 Polyline
    val trackLatLngs = trackPoints.map { LatLng(it.lat, it.lng) }
    val centerLatLng = if (trackLatLngs.isNotEmpty()) {
        val avgLat = trackLatLngs.map { it.latitude }.average()
        val avgLng = trackLatLngs.map { it.longitude }.average()
        LatLng(avgLat, avgLng)
    } else {
        LatLng(25.034, 121.564) // 預設台北
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(centerLatLng, 15f)
    }

    // 當軌跡載入後更新鏡頭
    LaunchedEffect(trackLatLngs) {
        if (trackLatLngs.isNotEmpty()) {
            val avgLat = trackLatLngs.map { it.latitude }.average()
            val avgLng = trackLatLngs.map { it.longitude }.average()
            cameraPositionState.position =
                CameraPosition.fromLatLngZoom(LatLng(avgLat, avgLng), 16f)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(run?.routeName ?: "紀錄詳情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { pad ->
        Box(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
        ) {
            if (run == null) {
                // 載入中
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                // ---- 地圖 ----
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
                            title = "起點",
                            snippet = "開始位置"
                        )
                    }

                    // 終點標記
                    if (trackLatLngs.size >= 2) {
                        Marker(
                            state = MarkerState(position = trackLatLngs.last()),
                            title = "終點",
                            snippet = "結束位置"
                        )
                    }

                    // 路線 Gate 標示
                    routeGates.forEach { g ->
                        val gateColor = when (g.type) {
                            "START" -> Color.Green
                            "FINISH" -> Color.Red
                            else -> Color(0xFFFF9800) // 橘
                        }
                        Polyline(
                            points = listOf(
                                LatLng(g.aLat, g.aLng),
                                LatLng(g.bLat, g.bLng)
                            ),
                            color = gateColor,
                            width = 10f
                        )

                        // 自訂點標記（顯示在 Gate 中點）
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
                        .background(Color(0xEE000000), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        .padding(16.dp)
                ) {
                    // 路線名 + 日期
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

                    // 總時間
                    Text(
                        text = "總時間: ${formatMs(run.totalTimeMs)}",
                        color = Color(0xFF4CAF50),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // 分段時間
                    if (splitTimes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = Color(0xFF555555))
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
