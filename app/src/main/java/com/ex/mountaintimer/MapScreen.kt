package com.ex.mountaintimer

import android.Manifest
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.*

private enum class UiMode { RUN, EDIT_GATE }
private enum class EditingTarget { START, CUSTOM, FINISH }

internal fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val centis = (ms % 1000) / 10
    return "%02d:%02d.%02d".format(minutes, seconds, centis)
}

// 輔助計算距離
fun calculateDistance(p1: LatLng, p2: LatLng): Double {
    val r = 6371000.0 // 地球半徑 (公尺)
    val dLat = Math.toRadians(p2.latitude - p1.latitude)
    val dLng = Math.toRadians(p2.longitude - p1.longitude)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(p1.latitude)) * cos(Math.toRadians(p2.latitude)) * sin(dLng / 2).pow(2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}

// 輔助判斷是否穿越線段 (Gate)
fun isCrossingGate(prev: LatLng, curr: LatLng, gateA: LatLng, gateB: LatLng): Boolean {
    fun crossProduct(a: LatLng, b: LatLng, c: LatLng): Double {
        return (b.longitude - a.longitude) * (c.latitude - a.latitude) - (b.latitude - a.latitude) * (c.longitude - a.longitude)
    }
    val cp1 = crossProduct(gateA, gateB, prev)
    val cp2 = crossProduct(gateA, gateB, curr)
    val cp3 = crossProduct(prev, curr, gateA)
    val cp4 = crossProduct(prev, curr, gateB)
    return (cp1 * cp2 < 0) && (cp3 * cp4 < 0)
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

    // --- TTS 語音引擎 ---
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        val ttsEngine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.CHINESE
            }
        }
        tts = ttsEngine
        onDispose { ttsEngine.stop(); ttsEngine.shutdown() }
    }
    fun speak(text: String) { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null) }

    // --- 狀態定義 ---
    var routeName by remember { mutableStateOf("") }
    var hasPermission by remember { mutableStateOf(false) }
    var currentPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var lastPoint by remember { mutableStateOf<LatLng?>(null) }
    val uiModeState = remember { mutableStateOf(UiMode.RUN) }
    val trackPoints = remember { mutableStateListOf<LatLng>() }
    val cameraPositionState = rememberCameraPositionState()
    val mapProperties = remember(hasPermission) { MapProperties(isMyLocationEnabled = hasPermission) }

    var startGate by remember { mutableStateOf<Gate?>(null) }
    val customGates = remember { mutableStateListOf<Gate>() }
    var finishGate by remember { mutableStateOf<Gate?>(null) }
    var editingTarget by remember { mutableStateOf(EditingTarget.START) }
    var customIndex by remember { mutableStateOf(1) }

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var startedAtMs by remember { mutableLongStateOf(0L) }
    var isRunning by remember { mutableStateOf(false) }
    var lastDistanceToStart by remember { mutableDoubleStateOf(Double.MAX_VALUE) }

    // --- 權限請求 ---
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        hasPermission = result[Manifest.permission.ACCESS_FINE_LOCATION] == true || result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }
    LaunchedEffect(Unit) {
        permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    // --- 計時循環 ---
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(10) // 提高精度
        }
    }
    val elapsedMs = if (isRunning) (nowMs - startedAtMs) else 0L

    // --- 讀取路線 ---
    LaunchedEffect(selectedRouteId) {
        val r = if (selectedRouteId == null) null else repo.getRouteWithGates(selectedRouteId)
        if (r != null) {
            routeName = r.route.name
            startGate = null; customGates.clear(); finishGate = null
            r.gates.forEach { g ->
                val gate = Gate(GeoPoint(g.aLat, g.aLng), GeoPoint(g.bLat, g.bLng))
                when (g.type) {
                    "START" -> startGate = gate
                    "CUSTOM" -> customGates.add(gate)
                    "FINISH" -> finishGate = gate
                }
            }
            uiModeState.value = UiMode.RUN
            isRunning = false
            trackPoints.clear()
            lastDistanceToStart = Double.MAX_VALUE
        }
    }

    // --- 核心邏輯：自動計時與語音提醒 ---
    LaunchedEffect(hasPermission) {
        if (!hasPermission) return@LaunchedEffect
        LocationTracker.locationFlow(context).collectLatest { p ->
            val curr = LatLng(p.lat, p.lng)
            val prev = lastPoint
            currentPoint = p
            lastPoint = curr

            // 1. 初始跳轉
            if (cameraPositionState.position.target.latitude == 0.0) {
                cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(curr, 17f))
            }

            // 2. 自動跟隨鏡頭
            if (uiModeState.value == UiMode.RUN) {
                cameraPositionState.animate(CameraUpdateFactory.newLatLng(curr), 400)
            }

            // 3. 自動觸發邏輯 (僅在 RUN 模式且有路線時)
            if (uiModeState.value == UiMode.RUN && startGate != null && finishGate != null) {
                val gateStartA = LatLng(startGate!!.a.lat, startGate!!.a.lng)
                val gateStartB = LatLng(startGate!!.b.lat, startGate!!.b.lng)
                val gateFinishA = LatLng(finishGate!!.a.lat, finishGate!!.a.lng)
                val gateFinishB = LatLng(finishGate!!.b.lat, finishGate!!.b.lng)

                // A. 語音距離提醒 (離起點)
                if (!isRunning) {
                    val dist = calculateDistance(curr, gateStartA) // 簡化以 A 點為準
                    val milestones = listOf(200.0, 150.0, 100.0, 50.0)
                    for (m in milestones) {
                        if (lastDistanceToStart > m && dist <= m) {
                            speak("距離起點還有 ${m.toInt()} 公尺")
                        }
                    }
                    lastDistanceToStart = dist
                }

                // B. 穿越起點偵測
                if (!isRunning && prev != null && isCrossingGate(prev, curr, gateStartA, gateStartB)) {
                    isRunning = true
                    startedAtMs = System.currentTimeMillis()
                    trackPoints.clear()
                    trackPoints.add(curr)
                    speak("計時開始")
                }

                // C. 紀錄軌跡 (僅在計時中)
                if (isRunning) {
                    if (trackPoints.isEmpty() || trackPoints.last() != curr) {
                        trackPoints.add(curr)
                    }
                    // D. 穿越終點偵測
                    if (prev != null && isCrossingGate(prev, curr, gateFinishA, gateFinishB)) {
                        isRunning = false
                        val finalTime = formatMs(System.currentTimeMillis() - startedAtMs)
                        speak("抵達終點，成績為 $finalTime")
                        // 自動存檔
                        scope.launch {
                            repo.saveHistory(selectedRouteId ?: 0L, System.currentTimeMillis() - startedAtMs, trackPoints.map { GeoPoint(it.latitude, it.longitude) })
                        }
                    }
                }
            }
        }
    }

    // --- UI 輔助 ---
    var editA by remember { mutableStateOf<LatLng?>(null) }
    var editB by remember { mutableStateOf<LatLng?>(null) }

    Box(Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = mapProperties,
            onMapClick = { if (uiModeState.value == UiMode.EDIT_GATE) { if (editA == null) editA = it else if (editB == null) editB = it } }
        ) {
            // 繪製軌跡 (僅計時中顯示)
            if (trackPoints.size >= 2) {
                Polyline(points = trackPoints.toList(), color = Color(0xFF81D4FA), width = 25f, startCap = RoundCap(), endCap = RoundCap(), jointType = JointType.ROUND)
                Polyline(points = trackPoints.toList(), color = Color(0xFF1976D2), width = 18f, startCap = RoundCap(), endCap = RoundCap(), jointType = JointType.ROUND)
            }
            // 繪製 Gates
            startGate?.let { g -> Polyline(listOf(LatLng(g.a.lat, g.a.lng), LatLng(g.b.lat, g.b.lng)), color = Color.Green, width = 12f) }
            customGates.forEach { g -> Polyline(listOf(LatLng(g.a.lat, g.a.lng), LatLng(g.b.lat, g.b.lng)), color = Color(0xFFFF9800), width = 12f) }
            finishGate?.let { g -> Polyline(listOf(LatLng(g.a.lat, g.a.lng), LatLng(g.b.lat, g.b.lng)), color = Color.Red, width = 12f) }
            // 編輯中的 Marker
            editA?.let { Marker(MarkerState(it), title = "Gate A") }
            editB?.let { Marker(MarkerState(it), title = "Gate B") }
        }

        // --- HUD (頂部) ---
        Column(modifier = Modifier.align(Alignment.TopCenter).padding(top = 40.dp, start = 16.dp, end = 16.dp).fillMaxWidth()) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xCC121212)), shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(8.dp)) {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(routeName.ifEmpty { "尚未選擇路線" }, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(if (routeName.isEmpty()) "請點擊下方清單選擇路線" else if (!isRunning) "已就緒，通過起點將自動計時" else "正在紀錄中...", color = if (isRunning) Color(0xFF00E676) else Color(0xFFB0BEC5), fontSize = 12.sp)
                    }
                    Text(formatMs(elapsedMs), color = if (isRunning) Color(0xFF00E676) else Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                }
            }
        }

        // --- 初始提示 (居中) ---
        if (routeName.isEmpty() && uiModeState.value == UiMode.RUN) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xAA000000)), shape = RoundedCornerShape(16.dp)) {
                    Text("🏁 請選擇路線或點擊右下角新增路線", color = Color.White, modifier = Modifier.padding(24.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
                }
            }
        }

        // --- 控制列 (底部) ---
        Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp, start = 16.dp, end = 16.dp).fillMaxWidth()) {
            if (uiModeState.value == UiMode.RUN) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ControlButton(Icons.Default.List, "路線清單", onOpenRouteList)
                    ControlButton(Icons.Default.History, "歷史紀錄", onOpenHistory)
                    ControlButton(Icons.Default.Add, "新增路線", { uiModeState.value = UiMode.EDIT_GATE; editingTarget = EditingTarget.START; editA = null; editB = null })
                }
            } else {
                // 編輯模式卡片
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xEE121212)), shape = RoundedCornerShape(24.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("編輯: ${when(editingTarget){ EditingTarget.START->"起點"; EditingTarget.CUSTOM->"自訂點 $customIndex"; EditingTarget.FINISH->"終點" }}", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(modifier = Modifier.weight(1f), enabled = (editA != null && editB != null), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)), onClick = {
                                val gate = Gate(GeoPoint(editA!!.latitude, editA!!.longitude), GeoPoint(editB!!.latitude, editB!!.longitude))
                                when(editingTarget) {
                                    EditingTarget.START -> { startGate = gate; editingTarget = EditingTarget.CUSTOM }
                                    EditingTarget.CUSTOM -> { customGates.add(gate); customIndex++ }
                                    EditingTarget.FINISH -> { finishGate = gate }
                                }
                                editA = null; editB = null
                            }) { Text("完成此段") }
                            if (editingTarget == EditingTarget.CUSTOM) Button(Modifier.weight(1f), onClick = { editingTarget = EditingTarget.FINISH; editA = null; editB = null }) { Text("去畫終點") }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (startGate != null && finishGate != null) Button(Modifier.weight(1f), onClick = { 
                                scope.launch { repo.saveRoute(routeName.ifEmpty { "新路線" }, startGate!!, customGates.toList(), finishGate!!); speak("路線已儲存"); uiModeState.value = UiMode.RUN }
                            }) { Text("儲存路線") }
                            OutlinedButton(Modifier.weight(1f), onClick = { uiModeState.value = UiMode.RUN }) { Text("取消", color = Color.White) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ControlButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(Color(0xCC212121)).border(1.dp, Color(0x33FFFFFF), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White, fontSize = 10.sp)
    }
}
