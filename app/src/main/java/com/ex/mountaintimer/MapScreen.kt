package com.ex.mountaintimer

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// ============================================================
// 時間格式化工具
// ============================================================
internal fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val centis = (ms % 1000) / 10
    return "%02d:%02d.%02d".format(minutes, seconds, centis)
}

// ============================================================
// 方向箭頭 Bitmap
// ============================================================
private fun createArrowBitmap(color: Int = 0xFF2196F3.toInt()): Bitmap {
    val size = 80
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply {
        this.color = color
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // 向上的箭頭三角形
    val path = Path().apply {
        moveTo(size / 2f, 8f)           // 頂端
        lineTo(size - 12f, size - 12f)  // 右下
        lineTo(size / 2f, size - 24f)   // 底部凹口
        lineTo(12f, size - 12f)         // 左下
        close()
    }
    canvas.drawPath(path, paint)

    // 白色邊框
    val strokePaint = Paint().apply {
        this.color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }
    canvas.drawPath(path, strokePaint)

    return bitmap
}

/**
 * 主地圖畫面
 *
 * - 顯示 GPS 位置（方向箭頭旋轉）
 * - 載入選定路線的 Gate（綠/橘/紅）
 * - 自動偵測通過 Gate → 計時
 * - 語音播報
 * - 完成後自動儲存歷史
 */
@Composable
fun MapScreen(
    selectedRouteId: Long?,
    onOpenRouteList: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { RouteRepository(context) }
    val historyRepo = remember { HistoryRepository(context) }
    val ttsManager = remember { TtsManager(context) }
    val scope = rememberCoroutineScope()

    // ---- 權限 ----
    var hasPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPermission =
            result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    // ---- GPS 位置 ----
    var currentPoint by remember { mutableStateOf<GeoPoint?>(null) }
    val cameraPositionState = rememberCameraPositionState()

    // ---- 路線資料 ----
    var selectedRoute by remember { mutableStateOf<RouteWithGates?>(null) }
    var routeDefinition by remember { mutableStateOf<RouteDefinition?>(null) }
    var routeName by remember { mutableStateOf("") }

    // ---- Gates 顯示用 ----
    var startGate by remember { mutableStateOf<Gate?>(null) }
    val customGates = remember { mutableStateListOf<Gate>() }
    var finishGate by remember { mutableStateOf<Gate?>(null) }

    // ---- RunEngine ----
    var runEngine by remember { mutableStateOf<RunEngine?>(null) }

    // ---- 計時狀態 ----
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var runState by remember { mutableStateOf(RunState.IDLE) }
    val splitTimes = remember { mutableStateListOf<CheckpointResult>() }

    // ---- 軌跡記錄 ----
    val trackPoints = remember { mutableStateListOf<LatLng>() }
    val rawTrackPoints = remember { mutableStateListOf<TrackPointEntity>() }

    // ---- 方向箭頭 Bitmap（先建 Bitmap，等 Map 初始化後再轉 Descriptor）----
    val arrowBitmap = remember { createArrowBitmap() }
    var arrowDescriptor by remember { mutableStateOf<BitmapDescriptor?>(null) }
    var mapLoaded by remember { mutableStateOf(false) }

    // ---- 顯示訊息 ----
    var statusMessage by remember { mutableStateOf("") }

    // 計時器更新（100ms）
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(100)
        }
    }

    val elapsedMs = runEngine?.getElapsedMs(nowMs) ?: 0L

    // ---- 載入選定路線 ----
    LaunchedEffect(selectedRouteId) {
        if (selectedRouteId == null) {
            selectedRoute = null
            routeDefinition = null
            routeName = ""
            startGate = null
            customGates.clear()
            finishGate = null
            runEngine = null
            runState = RunState.IDLE
            splitTimes.clear()
            trackPoints.clear()
            rawTrackPoints.clear()
            statusMessage = ""
            return@LaunchedEffect
        }

        val r = repo.getRouteWithGates(selectedRouteId) ?: return@LaunchedEffect
        selectedRoute = r
        routeName = r.route.name

        // 轉換 Gate 顯示
        startGate = null
        customGates.clear()
        finishGate = null
        r.gates.forEach { g ->
            val gate = Gate(
                a = GeoPoint(g.aLat, g.aLng),
                b = GeoPoint(g.bLat, g.bLng)
            )
            when (g.type) {
                "START" -> startGate = gate
                "CUSTOM" -> customGates.add(gate)
                "FINISH" -> finishGate = gate
            }
        }

        // 建立 RunEngine
        val rd = r.toRouteDefinition()
        routeDefinition = rd
        val engine = RunEngine(rd)
        runEngine = engine
        runState = RunState.IDLE
        splitTimes.clear()
        trackPoints.clear()
        rawTrackPoints.clear()
        statusMessage = "待機中 — 通過起點自動開始計時"

        // 啟動前景服務
        TimingService.start(context)
    }

    // ---- GPS 追蹤 + 自動偵測 ----
    LaunchedEffect(hasPermission) {
        if (!hasPermission) return@LaunchedEffect

        LocationTracker.locationFlow(context).collectLatest { p ->
            currentPoint = p
            val latLng = LatLng(p.lat, p.lng)

            // 跟隨鏡頭
            cameraPositionState.position = CameraPosition.fromLatLngZoom(latLng, 17f)

            // 餵進 RunEngine
            val engine = runEngine ?: return@collectLatest
            val event = engine.onLocationUpdate(p, System.currentTimeMillis())
            runState = engine.state

            // 記錄軌跡（只在計時中）
            if (engine.state == RunState.RUNNING) {
                trackPoints.add(latLng)
                rawTrackPoints.add(
                    TrackPointEntity(
                        runId = 0,
                        lat = p.lat,
                        lng = p.lng,
                        timestampMs = System.currentTimeMillis()
                    )
                )
            }

            // 處理事件
            when (event) {
                is RunEvent.Started -> {
                    ttsManager.announceStart()
                    statusMessage = "計時中..."
                    trackPoints.clear()
                    rawTrackPoints.clear()
                    trackPoints.add(latLng)
                    rawTrackPoints.add(
                        TrackPointEntity(
                            runId = 0,
                            lat = p.lat,
                            lng = p.lng,
                            timestampMs = System.currentTimeMillis()
                        )
                    )
                }

                is RunEvent.PassedCheckpoint -> {
                    splitTimes.add(
                        CheckpointResult(
                            checkpoint = event.checkpoint,
                            timeMs = event.timeMs
                        )
                    )
                    if (event.checkpoint.type == CheckpointType.CUSTOM) {
                        ttsManager.announceCheckpoint(event.checkpoint.index)
                    }
                    statusMessage = "${event.checkpoint.name}: ${formatMs(event.timeMs)}"
                }

                is RunEvent.Finished -> {
                    ttsManager.announceFinish()
                    val totalTime = event.results.last().timeMs
                    statusMessage = "完成！總時間: ${formatMs(totalTime)}"

                    // 儲存歷史
                    val route = selectedRoute
                    if (route != null) {
                        scope.launch {
                            val startEpoch = engine.startTimeMs
                            historyRepo.saveRunResult(
                                routeId = route.route.id,
                                routeName = route.route.name,
                                startTimeEpoch = startEpoch,
                                endTimeEpoch = startEpoch + totalTime,
                                totalTimeMs = totalTime,
                                splits = event.results.map { cr ->
                                    SplitTimeEntity(
                                        runId = 0,
                                        checkpointIndex = cr.checkpoint.index,
                                        checkpointName = cr.checkpoint.name,
                                        timeMs = cr.timeMs
                                    )
                                },
                                trackPoints = rawTrackPoints.toList()
                            )
                            Toast.makeText(context, "紀錄已儲存", Toast.LENGTH_SHORT).show()
                        }
                    }

                    // 停止前景服務
                    TimingService.stop(context)
                }

                null -> { /* 正常更新 */ }
            }
        }
    }

    // 清理 TTS
    DisposableEffect(Unit) {
        onDispose { ttsManager.shutdown() }
    }

    // ============================================================
    // UI
    // ============================================================
    Box(Modifier.fillMaxSize()) {

        // ---- 地圖 ----
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            onMapLoaded = {
                if (!mapLoaded) {
                    mapLoaded = true
                    arrowDescriptor = BitmapDescriptorFactory.fromBitmap(arrowBitmap)
                }
            }
        ) {
            // 方向箭頭（使用者位置）
            currentPoint?.let { p ->
                val desc = arrowDescriptor
                if (desc != null) {
                    Marker(
                        state = MarkerState(position = LatLng(p.lat, p.lng)),
                        icon = desc,
                        rotation = p.bearing,
                        anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                        flat = true,
                        title = "你的位置"
                    )
                } else {
                    // Map 還沒完全載入，先用預設 Marker
                    Marker(
                        state = MarkerState(position = LatLng(p.lat, p.lng)),
                        title = "你的位置"
                    )
                }
            }

            // 軌跡（藍）
            if (trackPoints.size >= 2) {
                Polyline(points = trackPoints.toList(), color = Color.Blue, width = 8f)
            }

            // 起點 Gate（綠）
            startGate?.let { g ->
                Polyline(
                    points = listOf(LatLng(g.a.lat, g.a.lng), LatLng(g.b.lat, g.b.lng)),
                    color = Color.Green,
                    width = 12f
                )
            }

            // 自訂點 Gate（橘）
            customGates.forEach { g ->
                Polyline(
                    points = listOf(LatLng(g.a.lat, g.a.lng), LatLng(g.b.lat, g.b.lng)),
                    color = Color(0xFFFF9800),
                    width = 12f
                )
            }

            // 終點 Gate（紅）
            finishGate?.let { g ->
                Polyline(
                    points = listOf(LatLng(g.a.lat, g.a.lng), LatLng(g.b.lat, g.b.lng)),
                    color = Color.Red,
                    width = 12f
                )
            }
        }

        // ---- 頂部 HUD：路線名稱 ----
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
                .background(Color(0xCC000000), RoundedCornerShape(12.dp))
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (routeName.isNotEmpty()) {
                Text(
                    text = routeName,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    text = "請先選擇路線",
                    color = Color(0xFFFFEB3B),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (statusMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = statusMessage,
                    color = Color(0xFFBBBBBB),
                    fontSize = 13.sp
                )
            }
        }

        // ---- 中央大計時器 ----
        if (runState == RunState.RUNNING || runState == RunState.FINISHED) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 100.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(16.dp))
                    .padding(horizontal = 28.dp, vertical = 16.dp)
            ) {
                Text(
                    text = formatMs(elapsedMs),
                    color = if (runState == RunState.FINISHED) Color(0xFF4CAF50) else Color.White,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }

        // ---- 分段時間面板 ----
        if (splitTimes.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Text("分段時間", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                splitTimes.forEach { cr ->
                    Text(
                        text = "${cr.checkpoint.name}: ${formatMs(cr.timeMs)}",
                        color = Color(0xFFFFCC80),
                        fontSize = 13.sp
                    )
                }
            }
        }

        // ---- 右下角按鈕 ----
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 選擇路線
            FloatingActionButton(
                onClick = onOpenRouteList,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Filled.List, contentDescription = "選擇路線")
            }

            // 歷史紀錄
            FloatingActionButton(
                onClick = onOpenHistory,
                containerColor = MaterialTheme.colorScheme.secondary
            ) {
                Icon(Icons.Filled.History, contentDescription = "歷史紀錄")
            }

            // 完成後可重置
            if (runState == RunState.FINISHED) {
                FloatingActionButton(
                    onClick = {
                        runEngine?.reset()
                        runState = RunState.IDLE
                        splitTimes.clear()
                        trackPoints.clear()
                        rawTrackPoints.clear()
                        statusMessage = "待機中 — 通過起點自動開始計時"
                        TimingService.start(context)
                    },
                    containerColor = Color(0xFF4CAF50)
                ) {
                    Text("重跑", color = Color.White, fontSize = 12.sp)
                }
            }
        }

        // ---- 沒有路線時顯示提示 ----
        if (selectedRouteId == null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xDD000000), RoundedCornerShape(16.dp))
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "請先選擇路線",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onOpenRouteList) {
                        Text("選擇路線")
                    }
                }
            }
        }
    }
}
