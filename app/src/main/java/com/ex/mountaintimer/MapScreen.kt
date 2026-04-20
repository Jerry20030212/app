package com.ex.mountaintimer

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.Cap
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private enum class UiMode { RUN, EDIT_GATE }
private enum class EditingTarget { START, CUSTOM, FINISH }

internal fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val centis = (ms % 1000) / 10
    return "%02d:%02d.%02d".format(minutes, seconds, centis)
}

@Composable
fun MapScreen(
    selectedRouteId: Long?,
    onOpenRouteList: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { RouteRepository(context) }
    val scope = rememberCoroutineScope()

    // 路線名稱（先用固定字串，你之後再改成輸入）
    var routeName by remember { mutableStateOf("測試路線") }

    // -----------------------------
    // 權限 / 定位 / 軌跡
    // -----------------------------
    var hasPermission by remember { mutableStateOf(false) }
    var currentPoint by remember { mutableStateOf<GeoPoint?>(null) }

    val uiModeState = remember { mutableStateOf(UiMode.RUN) }
    val trackPoints = remember { mutableStateListOf<LatLng>() }
    val cameraPositionState = rememberCameraPositionState()
    val mapProperties = remember(hasPermission) {
        MapProperties(isMyLocationEnabled = hasPermission)
    }

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

    // ✅ RUN 模式時鏡頭跟著目前位置，編輯模式時保留手動移圖空間
    LaunchedEffect(hasPermission) {
        if (!hasPermission) return@LaunchedEffect

        LocationTracker.locationFlow(context).collectLatest { p ->
            currentPoint = p
            val latLng = LatLng(p.lat, p.lng)

            if (uiModeState.value == UiMode.RUN) {
                val lastPoint = trackPoints.lastOrNull()
                if (lastPoint == null || lastPoint != latLng) {
                    trackPoints.add(latLng)
                }

                val nextZoom = cameraPositionState.position.zoom.takeIf { it > 0f } ?: 17f
                cameraPositionState.animate(
                    update = CameraUpdateFactory.newLatLngZoom(latLng, nextZoom),
                    durationMs = 600
                )
            }
        }
    }

    // -----------------------------
    // Gate 編輯狀態（點兩下：A → B）
    // -----------------------------
    var editA by remember { mutableStateOf<LatLng?>(null) }
    var editB by remember { mutableStateOf<LatLng?>(null) }

    var startGate by remember { mutableStateOf<Gate?>(null) }
    val customGates = remember { mutableStateListOf<Gate>() }
    var finishGate by remember { mutableStateOf<Gate?>(null) }

    var editingTarget by remember { mutableStateOf(EditingTarget.START) }
    var customIndex by remember { mutableStateOf(1) }

    // -----------------------------
    // 跑山計時（簡化版）
    // -----------------------------
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var startedAtMs by remember { mutableLongStateOf(0L) }
    var isRunning by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(100)
        }
    }
    val elapsedMs = if (isRunning) (nowMs - startedAtMs) else 0L

    // -----------------------------
    // ✅ 讀取選到的路線（從 RouteList 回來後）
    // -----------------------------
    var selectedRoute by remember { mutableStateOf<RouteWithGates?>(null) }

    LaunchedEffect(selectedRouteId) {
        selectedRoute = if (selectedRouteId == null) null else repo.getRouteWithGates(selectedRouteId)
    }

    // 把 DB 的 gate 套回畫面（綠/橘/紅）
    LaunchedEffect(selectedRoute) {
        val r = selectedRoute ?: return@LaunchedEffect

        // 路線名稱也更新
        routeName = r.route.name

        // 清掉舊的
        startGate = null
        customGates.clear()
        finishGate = null

        // 套新資料
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

        // 回 RUN 模式
        uiModeState.value = UiMode.RUN
        editA = null
        editB = null
        editingTarget = EditingTarget.START
        customIndex = customGates.size + 1
        isRunning = false
    }

    // -----------------------------
    // ✅ 存路線（你要的 B：把畫好的 gate 存起來）
    // -----------------------------
    fun saveCurrentRoute() {
        val s = startGate
        val f = finishGate
        if (s == null || f == null) {
            Toast.makeText(context, "❌ 請先設定起點與終點再儲存", Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch {
            repo.saveRoute(
                name = routeName,
                startGate = s,
                customGates = customGates.toList(),
                finishGate = f
            )
            Toast.makeText(context, "✅ 已儲存路線：$routeName", Toast.LENGTH_SHORT).show()
        }
    }

    val hintText = when (uiModeState.value) {
        UiMode.RUN -> "模式：跑山（自動計時）"
        UiMode.EDIT_GATE -> {
            val targetName = when (editingTarget) {
                EditingTarget.START -> "起點"
                EditingTarget.CUSTOM -> "自訂點$customIndex"
                EditingTarget.FINISH -> "終點"
            }
            when {
                editA == null -> "模式：編輯 $targetName｜請點 Gate A（第一下）"
                editB == null -> "模式：編輯 $targetName｜請點 Gate B（第二下）"
                else -> "模式：編輯 $targetName｜已完成 ✅（按「完成這條」）"
            }
        }
    }

    // -----------------------------
    // UI
    // -----------------------------
    Box(Modifier.fillMaxSize()) {

        // 地圖（只畫 marker / polyline）
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = mapProperties,
            onMapClick = { latLng ->
                if (uiModeState.value == UiMode.EDIT_GATE) {
                    if (editA == null) editA = latLng
                    else if (editB == null) editB = latLng
                }
            }
        ) {
            // 你的位置 marker
            currentPoint?.let { p ->
                Marker(
                    state = MarkerState(position = LatLng(p.lat, p.lng)),
                    title = "你的位置"
                )
            }

            // 軌跡（Google Maps 導航風格）— 只在 RUN
            if (uiModeState.value == UiMode.RUN && trackPoints.size >= 2) {
                // 底色邊框（淺藍色，較寬）
                Polyline(
                    points = trackPoints,
                    color = Color(0xFF81D4FA),
                    width = 25f,
                    startCap = RoundCap(),
                    endCap = RoundCap(),
                    jointType = JointType.ROUND
                )
                // 主色線條（深藍色，填滿中央）
                Polyline(
                    points = trackPoints,
                    color = Color(0xFF1976D2),
                    width = 18f,
                    startCap = RoundCap(),
                    endCap = RoundCap(),
                    jointType = JointType.ROUND
                )
            }

            // 暫存 Gate（紫）
            val a = editA
            val b = editB
            if (a != null) Marker(state = MarkerState(position = a), title = "Gate A")
            if (b != null) Marker(state = MarkerState(position = b), title = "Gate B")
            if (a != null && b != null) {
                Polyline(points = listOf(a, b), color = Color(0xFFAA00FF), width = 12f)
            }

            // ✅ 已存起點 Gate（綠）— 永遠顯示
            startGate?.let { g ->
                Polyline(
                    points = listOf(LatLng(g.a.lat, g.a.lng), LatLng(g.b.lat, g.b.lng)),
                    color = Color.Green,
                    width = 12f
                )
            }

            // ✅ 已存自訂點 Gate（橘）— 永遠顯示
            customGates.forEach { g ->
                Polyline(
                    points = listOf(LatLng(g.a.lat, g.a.lng), LatLng(g.b.lat, g.b.lng)),
                    color = Color(0xFFFF9800),
                    width = 12f
                )
            }

            // ✅ 已存終點 Gate（紅）— 永遠顯示
            finishGate?.let { g ->
                Polyline(
                    points = listOf(LatLng(g.a.lat, g.a.lng), LatLng(g.b.lat, g.b.lng)),
                    color = Color.Red,
                    width = 12f
                )
            }
        }

        // HUD（左上）
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .background(Color(0xAA000000))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("目前路線：$routeName", color = Color.White)
            Text(hintText, color = Color.White)
            Text("時間：${formatMs(elapsedMs)}", color = Color.White)
        }

        // 右上角按鈕群
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (uiModeState.value) {
                UiMode.RUN -> {
                    Button(onClick = onOpenRouteList) { Text("選擇路線") }
                    Button(onClick = onOpenHistory) { Text("歷史紀錄") }

                    Button(onClick = {
                        uiModeState.value = UiMode.EDIT_GATE
                        editingTarget = EditingTarget.START
                        customIndex = 1
                        editA = null
                        editB = null
                        isRunning = false
                    }) {
                        Text("+ 編輯路線")
                    }

                    Button(onClick = {
                        if (!isRunning) {
                            isRunning = true
                            startedAtMs = nowMs
                        }
                    }) { Text("開始計時") }

                    OutlinedButton(onClick = { isRunning = false }) { Text("停止") }
                }

                UiMode.EDIT_GATE -> {
                    // ✅ 取消目前 Gate：只清暫存，不動已畫好的線
                    OutlinedButton(onClick = {
                        editA = null
                        editB = null
                    }) { Text("取消目前Gate") }

                    // ✅ (可選) 刪除上一個自訂點：只刪橘線最後一條，不動起點/終點
                    if (editingTarget == EditingTarget.CUSTOM && customGates.isNotEmpty()) {
                        OutlinedButton(onClick = {
                            customGates.removeAt(customGates.lastIndex)
                            customIndex = (customIndex - 1).coerceAtLeast(1)
                            Toast.makeText(context, "已刪除上一個自訂點", Toast.LENGTH_SHORT).show()
                        }) { Text("刪除上一個自訂點") }
                    }

                    // ✅ 完成這條（把紫線存成綠/橘/紅其中之一）
                    Button(
                        enabled = (editA != null && editB != null),
                        onClick = {
                            val a = editA ?: return@Button
                            val b = editB ?: return@Button

                            val gate = Gate(
                                a = GeoPoint(a.latitude, a.longitude),
                                b = GeoPoint(b.latitude, b.longitude)
                            )

                            when (editingTarget) {
                                EditingTarget.START -> {
                                    startGate = gate
                                    editingTarget = EditingTarget.CUSTOM
                                    customIndex = 1
                                }

                                EditingTarget.CUSTOM -> {
                                    customGates.add(gate)
                                    customIndex += 1
                                }

                                EditingTarget.FINISH -> {
                                    finishGate = gate
                                }
                            }

                            editA = null
                            editB = null
                        }
                    ) { Text("完成這條") }

                    // ✅ 你說你畫完自訂點後「看不到終點」：這顆就是答案
                    if (editingTarget == EditingTarget.CUSTOM) {
                        OutlinedButton(onClick = {
                            editingTarget = EditingTarget.FINISH
                            editA = null
                            editB = null
                        }) { Text("下一步：畫終點") }
                    }

                    // ✅ 如果終點已畫好：可以存
                    if (finishGate != null && startGate != null) {
                        Button(onClick = { saveCurrentRoute() }) { Text("儲存路線") }
                    }

                    OutlinedButton(onClick = {
                        uiModeState.value = UiMode.RUN
                        editA = null
                        editB = null
                    }) { Text("回跑山") }
                }
            }
        }
    }
}
