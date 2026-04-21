package com.ex.mountaintimer

import android.Manifest
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
private enum class AppLanguage { EN, TW, JP }

// 多語言對照表
private object Strings {
    fun get(key: String, lang: AppLanguage): String = when (lang) {
        AppLanguage.TW -> when (key) {
            "hint_select" -> "🏁 請選擇路線或點擊右下角新增路線"
            "status_ready" -> "已就緒，通過起點將自動計時"
            "status_recording" -> "● 錄製中"
            "gate_start" -> "起點"
            "gate_finish" -> "終點"
            "gate_custom" -> "自訂點"
            "passed_custom" -> "已通過自訂點"
            "passed_finish" -> "已通過終點，計時結束"
            "timing_start" -> "計時開始"
            "dist_remind" -> "距離起點還有 %d 公尺"
            "btn_routes" -> "路線清單"
            "btn_history" -> "歷史紀錄"
            "btn_add" -> "新增路線"
            else -> key
        }
        AppLanguage.EN -> when (key) {
            "hint_select" -> "🏁 Please select a route or add a new one"
            "status_ready" -> "Ready, timing starts at the Start Gate"
            "status_recording" -> "● RECORDING"
            "gate_start" -> "Start"
            "gate_finish" -> "Finish"
            "gate_custom" -> "Custom"
            "passed_custom" -> "Passed Custom Point"
            "passed_finish" -> "Passed Finish, timing stopped"
            "timing_start" -> "Timing started"
            "dist_remind" -> "%d meters to start"
            "btn_routes" -> "Routes"
            "btn_history" -> "History"
            "btn_add" -> "Add Route"
            else -> key
        }
        AppLanguage.JP -> when (key) {
            "hint_select" -> "🏁 ルートを選択するか、新しく追加してください"
            "status_ready" -> "準備完了、スタート地点で計測開始"
            "status_recording" -> "● 記録中"
            "gate_start" -> "スタート"
            "gate_finish" -> "ゴール"
            "gate_custom" -> "カスタム"
            "passed_custom" -> "カスタム地点を通過"
            "passed_finish" -> "ゴール通過、計測終了"
            "timing_start" -> "計測開始"
            "dist_remind" -> "スタートまであと %d メートル"
            "btn_routes" -> "ルート"
            "btn_history" -> "履歴"
            "btn_add" -> "ルート追加"
            else -> key
        }
    }
}

internal fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val centis = (ms % 1000) / 10
    return "%02d:%02d.%02d".format(minutes, seconds, centis)
}

fun calculateDistance(p1: LatLng, p2: LatLng): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(p2.latitude - p1.latitude)
    val dLng = Math.toRadians(p2.longitude - p1.longitude)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(p1.latitude)) * cos(Math.toRadians(p2.latitude)) * sin(dLng / 2).pow(2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}

fun isCrossingGate(prev: LatLng, curr: LatLng, gateA: LatLng, gateB: LatLng): Boolean {
    fun crossProduct(a: LatLng, b: LatLng, c: LatLng): Double = (b.longitude - a.longitude) * (c.latitude - a.latitude) - (b.latitude - a.latitude) * (c.longitude - a.longitude)
    val cp1 = crossProduct(gateA, gateB, prev)
    val cp2 = crossProduct(gateA, gateB, curr)
    val cp3 = crossProduct(prev, curr, gateA)
    val cp4 = crossProduct(prev, curr, gateB)
    return (cp1 * cp2 < 0) && (cp3 * cp4 < 0)
}

@Composable
fun MapScreen(selectedRouteId: Long?, onOpenRouteList: () -> Unit, onOpenHistory: () -> Unit) {
    val context = LocalContext.current
    val routeRepo = remember { RouteRepository(context) }
    val historyRepo = remember { HistoryRepository(context) }
    val scope = rememberCoroutineScope()

    var language by remember { mutableStateOf(AppLanguage.TW) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    
    DisposableEffect(language) {
        val ttsEngine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = when(language) {
                    AppLanguage.TW -> Locale.TRADITIONAL_CHINESE
                    AppLanguage.EN -> Locale.ENGLISH
                    AppLanguage.JP -> Locale.JAPANESE
                }
            }
        }
        tts = ttsEngine
        onDispose { ttsEngine.stop(); ttsEngine.shutdown() }
    }

    fun speak(key: String, arg: Int? = null) {
        val text = if (arg != null) Strings.get(key, language).format(arg) else Strings.get(key, language)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    var routeName by remember { mutableStateOf("") }
    var hasPermission by remember { mutableStateOf(false) }
    var currentPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var lastPoint by remember { mutableStateOf<LatLng?>(null) }
    val uiModeState = remember { mutableStateOf(UiMode.RUN) }
    val trackPoints = remember { mutableStateListOf<LatLng>() }
    val cameraPositionState = rememberCameraPositionState()
    
    var startGate by remember { mutableStateOf<Gate?>(null) }
    val customGates = remember { mutableStateListOf<Gate>() }
    var finishGate by remember { mutableStateOf<Gate?>(null) }
    var editingTarget by remember { mutableStateOf(EditingTarget.START) }
    var customIndex by remember { mutableStateOf(1) }

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var startedAtMs by remember { mutableLongStateOf(0L) }
    var isRunning by remember { mutableStateOf(false) }
    var lastDistanceToStart by remember { mutableDoubleStateOf(Double.MAX_VALUE) }
    val passedCustomGateIndices = remember { mutableStateSetOf<Int>() }
    val splitTimes = remember { mutableStateListOf<SplitTimeEntity>() }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        hasPermission = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
    }
    LaunchedEffect(Unit) { permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)) }

    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(10)
        }
    }
    val elapsedMs = if (isRunning) (nowMs - startedAtMs) else 0L

    LaunchedEffect(selectedRouteId) {
        val r = if (selectedRouteId == null) null else routeRepo.getRouteWithGates(selectedRouteId)
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
            isRunning = false; trackPoints.clear(); lastDistanceToStart = Double.MAX_VALUE; passedCustomGateIndices.clear()
        }
    }

    LaunchedEffect(hasPermission) {
        if (!hasPermission) return@LaunchedEffect
        LocationTracker.locationFlow(context).collectLatest { p ->
            val curr = LatLng(p.lat, p.lng)
            val prev = lastPoint
            currentPoint = p
            lastPoint = curr

            if (cameraPositionState.position.target.latitude == 0.0) {
                cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(curr, 17f))
            }
            if (uiModeState.value == UiMode.RUN) {
                cameraPositionState.animate(CameraUpdateFactory.newLatLng(curr), 300)
            }

            if (uiModeState.value == UiMode.RUN && startGate != null && finishGate != null) {
                val gSA = LatLng(startGate!!.a.lat, startGate!!.a.lng)
                val gSB = LatLng(startGate!!.b.lat, startGate!!.b.lng)
                val gFA = LatLng(finishGate!!.a.lat, finishGate!!.a.lng)
                val gFB = LatLng(finishGate!!.b.lat, finishGate!!.b.lng)

                if (!isRunning) {
                    val dist = calculateDistance(curr, gSA)
                    val milestones = listOf(200.0, 150.0, 100.0, 50.0)
                    for (m in milestones) {
                        if (lastDistanceToStart > m && dist <= m) speak("dist_remind", m.toInt())
                    }
                    lastDistanceToStart = dist
                }

                if (!isRunning && prev != null && isCrossingGate(prev, curr, gSA, gSB)) {
                    isRunning = true; startedAtMs = System.currentTimeMillis()
                    trackPoints.clear(); trackPoints.add(curr); passedCustomGateIndices.clear(); splitTimes.clear()
                    speak("timing_start")
                    // 記錄起點數據
                    splitTimes.add(SplitTimeEntity(runId = 0, checkpointIndex = 0, checkpointName = "START", timeMs = 0, speed = p.speed, gForce = sqrt(p.gX.pow(2) + p.gY.pow(2))))
                }

                if (isRunning) {
                    if (trackPoints.isEmpty() || trackPoints.last() != curr) trackPoints.add(curr)
                    
                    // 自訂點偵測
                    customGates.forEachIndexed { index, gate ->
                        if (!passedCustomGateIndices.contains(index) && prev != null && 
                            isCrossingGate(prev, curr, LatLng(gate.a.lat, gate.a.lng), LatLng(gate.b.lat, gate.b.lng))) {
                            passedCustomGateIndices.add(index)
                            speak("passed_custom")
                            splitTimes.add(SplitTimeEntity(
                                runId = 0, 
                                checkpointIndex = index + 1, 
                                checkpointName = "CUSTOM ${index + 1}", 
                                timeMs = System.currentTimeMillis() - startedAtMs,
                                speed = p.speed,
                                gForce = sqrt(p.gX.pow(2) + p.gY.pow(2))
                            ))
                        }
                    }

                    if (prev != null && isCrossingGate(prev, curr, gFA, gFB)) {
                        isRunning = false
                        val finalMs = System.currentTimeMillis() - startedAtMs
                        speak("passed_finish")
                        splitTimes.add(SplitTimeEntity(
                            runId = 0, 
                            checkpointIndex = 99, 
                            checkpointName = "FINISH", 
                            timeMs = finalMs,
                            speed = p.speed,
                            gForce = sqrt(p.gX.pow(2) + p.gY.pow(2))
                        ))
                        scope.launch {
                            historyRepo.saveRunResult(
                                routeId = selectedRouteId ?: 0L,
                                routeName = routeName,
                                startTimeEpoch = startedAtMs,
                                endTimeEpoch = System.currentTimeMillis(),
                                totalTimeMs = finalMs,
                                splits = splitTimes.toList(),
                                trackPoints = trackPoints.map { TrackPointEntity(runId = 0, lat = it.latitude, lng = it.longitude, timestampMs = 0) }
                            )
                        }
                    }
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = hasPermission, mapType = MapType.TERRAIN),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false)
        ) {
            if (trackPoints.size >= 2) {
                Polyline(points = trackPoints.toList(), color = Color(0xFF81D4FA), width = 25f, startCap = RoundCap(), endCap = RoundCap(), jointType = JointType.ROUND)
                Polyline(points = trackPoints.toList(), color = Color(0xFF1976D2), width = 18f, startCap = RoundCap(), endCap = RoundCap(), jointType = JointType.ROUND)
            }
            startGate?.let { g -> Polyline(listOf(LatLng(g.a.lat, g.a.lng), LatLng(g.b.lat, g.b.lng)), color = Color.Green, width = 12f) }
            customGates.forEach { g -> Polyline(listOf(LatLng(g.a.lat, g.a.lng), LatLng(g.b.lat, g.b.lng)), color = Color.Yellow, width = 10f) }
            finishGate?.let { g -> Polyline(listOf(LatLng(g.a.lat, g.a.lng), LatLng(g.b.lat, g.b.lng)), color = Color.Red, width = 12f) }
        }

        // --- 頂部 HUD ---
        Column(Modifier.align(Alignment.TopCenter).padding(top = 48.dp, start = 16.dp, end = 16.dp).fillMaxWidth()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xCC121212)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(routeName.ifEmpty { Strings.get("gate_start", language) }, color = Color.White, fontSize = 14.sp)
                        Text(formatMs(elapsedMs), color = if(isRunning) Color(0xFF00E676) else Color.White, fontSize = 36.sp, fontWeight = FontWeight.Black)
                        if (isRunning) Text(Strings.get("status_recording", language), color = Color.Red, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        else Text(Strings.get("status_ready", language), color = Color.Gray, fontSize = 10.sp)
                    }
                    
                    // 時速表
                    Column(horizontalAlignment = Alignment.End) {
                        val speedKmh = ((currentPoint?.speed ?: 0f) * 3.6f).toInt()
                        Text("$speedKmh", color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Bold)
                        Text("km/h", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            // G-Force & Language Row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                GForceMeter(currentPoint?.gX ?: 0f, currentPoint?.gY ?: 0f)
                
                // 語言切換按鈕
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xCC121212)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.clickable { 
                        language = when(language) {
                            AppLanguage.TW -> AppLanguage.EN
                            AppLanguage.EN -> AppLanguage.JP
                            AppLanguage.JP -> AppLanguage.TW
                        }
                    }
                ) {
                    Text(language.name, color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontWeight = FontWeight.Bold)
                }
            }
        }

        if (selectedRouteId == null && uiModeState.value == UiMode.RUN) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xAA000000)), shape = RoundedCornerShape(16.dp)) {
                    Text(Strings.get("hint_select", language), color = Color.White, modifier = Modifier.padding(24.dp), textAlign = TextAlign.Center)
                }
            }
        }

        // --- 底部控制列 ---
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp, start = 16.dp, end = 16.dp).fillMaxWidth()) {
            if (uiModeState.value == UiMode.RUN) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ControlButton(Icons.Default.List, Strings.get("btn_routes", language), onOpenRouteList)
                    ControlButton(Icons.Default.History, Strings.get("btn_history", language), onOpenHistory)
                    ControlButton(Icons.Default.Add, Strings.get("btn_add", language)) { 
                        uiModeState.value = UiMode.EDIT_GATE; editingTarget = EditingTarget.START
                    }
                }
            } else {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xEE121212)), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("編輯: ${when(editingTarget){ EditingTarget.START->Strings.get("gate_start", language); EditingTarget.CUSTOM->"${Strings.get("gate_custom", language)} $customIndex"; EditingTarget.FINISH->Strings.get("gate_finish", language) }}", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("請在地圖上點擊兩點來畫出通過線", color = Color.Gray, fontSize = 12.sp)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(modifier = Modifier.weight(1f), onClick = { uiModeState.value = UiMode.RUN }) { Text("取消") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GForceMeter(gX: Float, gY: Float) {
    Box(Modifier.size(80.dp).background(Color(0xCC121212), CircleShape).border(1.dp, Color(0x33FFFFFF), CircleShape), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(60.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            drawCircle(Color.Gray, radius = size.width / 2, style = Stroke(1f))
            drawCircle(Color.Gray, radius = size.width / 4, style = Stroke(1f))
            drawLine(Color.Gray, Offset(0f, center.y), Offset(size.width, center.y), 1f)
            drawLine(Color.Gray, Offset(center.x, 0f), Offset(center.x, size.height), 1f)
            
            val posX = (center.x + (gX * (size.width / 2))).coerceIn(0f, size.width)
            val posY = (center.y - (gY * (size.height / 2))).coerceIn(0f, size.height)
            drawCircle(Color(0xFF00E676), radius = 6f, center = Offset(posX, posY))
        }
        Text("G", color = Color(0x66FFFFFF), fontSize = 10.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp))
    }
}

@Composable
fun ControlButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(Color(0xCC212121)).border(1.dp, Color(0x33FFFFFF), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White, fontSize = 10.sp)
    }
}
