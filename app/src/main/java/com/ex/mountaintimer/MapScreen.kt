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
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
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

internal enum class UiMode { RUN, EDIT_GATE }
internal enum class EditingTarget { START, CUSTOM, FINISH }
internal enum class AppLanguage { EN, TW, JP }

// Multi-language strings
private object Strings {
    fun get(key: String, lang: AppLanguage): String = when (lang) {
        AppLanguage.TW -> when (key) {
            "hint_select" -> "🏁 請點擊左下角選擇路線或新增路線"
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
            "hint_select" -> "🏁 Please tap the bottom-left icon to select or add a route"
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
            "hint_select" -> "🏁 左下のアイコンをタップしてルートを選択または追加してください"
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
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val routeRepo = remember { RouteRepository(context) }
    val historyRepo = remember { HistoryRepository(context) }
    val scope = rememberCoroutineScope()

    var language by rememberSaveable { mutableStateOf(AppLanguage.TW) }
    var mapType by rememberSaveable { mutableStateOf(MapType.SATELLITE) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var showReportDialog by remember { mutableStateOf(false) }
    
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

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var startedAtMs by remember { mutableLongStateOf(0L) }
    var isRunning by remember { mutableStateOf(false) }
    var lastDistanceToStart by remember { mutableDoubleStateOf(Double.MAX_VALUE) }
    var passedCustomGateIndices by remember { mutableStateOf(setOf<Int>()) }
    val splitTimes = remember { mutableStateListOf<SplitTimeEntity>() }
    var totalDistanceM by remember { mutableDoubleStateOf(0.0) }
    var vehicleModel by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        // Android 12+ requires handling both fine and coarse location
        hasPermission = result[Manifest.permission.ACCESS_FINE_LOCATION] == true || 
                        result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }
    LaunchedEffect(Unit) { 
        permissionLauncher.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )) 
    }

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
            vehicleModel = r.route.vehicleModel
            startGate = null; customGates.clear(); finishGate = null
            r.gates.forEach { g ->
                val gate = Gate(GeoPoint(g.aLat, g.aLng), GeoPoint(g.bLat, g.bLng))
                when (g.type) {
                    "START" -> startGate = gate
                    "CUSTOM" -> customGates.add(gate)
                    "FINISH" -> finishGate = gate
                }
            }
            isRunning = false; trackPoints.clear(); lastDistanceToStart = Double.MAX_VALUE; passedCustomGateIndices = emptySet(); totalDistanceM = 0.0
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
                    trackPoints.clear(); trackPoints.add(curr); passedCustomGateIndices = emptySet(); splitTimes.clear(); totalDistanceM = 0.0
                    speak("timing_start")
                    splitTimes.add(SplitTimeEntity(runId = 0, checkpointIndex = 0, checkpointName = "START", timeMs = 0, speed = p.speed.toDouble(), gForce = sqrt(p.gX.toDouble().pow(2) + p.gY.toDouble().pow(2))))
                }

                if (isRunning) {
                    if (trackPoints.isEmpty() || trackPoints.last() != curr) {
                        if (trackPoints.isNotEmpty()) {
                            totalDistanceM += calculateDistance(trackPoints.last(), curr)
                        }
                        trackPoints.add(curr)
                    }
                    
                    customGates.forEachIndexed { index, gate ->
                        if (!passedCustomGateIndices.contains(index) && prev != null && 
                            isCrossingGate(prev, curr, LatLng(gate.a.lat, gate.a.lng), LatLng(gate.b.lat, gate.b.lng))) {
                            passedCustomGateIndices = passedCustomGateIndices + index
                            speak("passed_custom")
                            splitTimes.add(SplitTimeEntity(
                                runId = 0, 
                                checkpointIndex = index + 1, 
                                checkpointName = "CUSTOM ${index + 1}", 
                                timeMs = System.currentTimeMillis() - startedAtMs,
                                speed = p.speed.toDouble(),
                                gForce = sqrt(p.gX.toDouble().pow(2) + p.gY.toDouble().pow(2))
                            ))
                        }
                    }

                    if (prev != null && isCrossingGate(prev, curr, gFA, gFB)) {
                        val finalMs = System.currentTimeMillis() - startedAtMs
                        isRunning = false
                        speak("passed_finish")
                        splitTimes.add(SplitTimeEntity(
                            runId = 0, 
                            checkpointIndex = 99, 
                            checkpointName = "FINISH", 
                            timeMs = finalMs,
                            speed = p.speed.toDouble(),
                            gForce = sqrt(p.gX.toDouble().pow(2) + p.gY.toDouble().pow(2))
                        ))
                        scope.launch {
                            val avgSpeed = if (finalMs > 0) (totalDistanceM / (finalMs / 1000.0)) * 3.6 else 0.0
                            historyRepo.saveRunResult(
                                routeId = selectedRouteId ?: 0L,
                                routeName = routeName,
                                vehicleModel = vehicleModel,
                                startTimeEpoch = startedAtMs,
                                endTimeEpoch = System.currentTimeMillis(),
                                totalTimeMs = finalMs,
                                totalDistanceM = totalDistanceM,
                                averageSpeedKmh = avgSpeed,
                                splits = splitTimes.toList(),
                                trackPoints = trackPoints.map { TrackPointEntity(runId = 0, lat = it.latitude, lng = it.longitude, timestampMs = System.currentTimeMillis()) }
                            )
                        }
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false),
            properties = MapProperties(isMyLocationEnabled = hasPermission, mapType = mapType)
        ) {
            if (selectedRouteId != null) {
                startGate?.let { GateMarker(it, Strings.get("gate_start", language)) }
                finishGate?.let { GateMarker(it, Strings.get("gate_finish", language)) }
                customGates.forEachIndexed { i, g -> GateMarker(g, "${Strings.get("gate_custom", language)} ${i + 1}") }
                if (isRunning && trackPoints.size >= 2) {
                    Polyline(
                        points = trackPoints.toList(),
                        color = Color(0xFF4285F4),
                        width = 20f,
                        startCap = RoundCap(),
                        endCap = RoundCap(),
                        jointType = JointType.ROUND
                    )
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(Brush.verticalGradient(colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .statusBarsPadding()
                    .safeDrawingPadding(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(routeName, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        val statusText = when {
                            selectedRouteId == null -> Strings.get("hint_select", language)
                            isRunning -> Strings.get("status_recording", language)
                            else -> Strings.get("status_ready", language)
                        }
                        val statusColor by animateColorAsState(if (isRunning) Color(0xFFF44336) else Color.White, label = "statusColor")
                        Text(statusText, color = statusColor, fontSize = 16.sp)
                    }
                    SettingsMenu(
                        currentLang = language,
                        onLangChange = { language = it },
                        currentMapType = mapType,
                        onMapTypeChange = { mapType = it },
                        onReportIssue = { showReportDialog = true }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 32.dp else 12.dp)
                ) {
                    Column(modifier = Modifier.weight(if (isLandscape) 1.5f else 1.3f)) {
                        Text(
                            text = formatMs(elapsedMs),
                            color = Color.White,
                            fontSize = if (isLandscape) 40.sp else 48.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(Modifier.height(if (isLandscape) 4.dp else 12.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            IconButton(
                                onClick = onOpenRouteList,
                                modifier = Modifier
                                    .size(if (isLandscape) 48.dp else 56.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f))
                            ) {
                                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Routes", tint = Color.White)
                            }
                            Spacer(Modifier.width(16.dp))
                            IconButton(
                                onClick = onOpenHistory,
                                modifier = Modifier
                                    .size(if (isLandscape) 48.dp else 56.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f))
                            ) {
                                Icon(Icons.Default.History, contentDescription = "History", tint = Color.White)
                            }
                        }
                    }
                    
                    Row(
                        modifier = Modifier.weight(if (isLandscape) 1.5f else 0.7f),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Speedometer(speedKmh = currentPoint?.speed?.times(3.6)?.toFloat() ?: 0f)
                        if (isLandscape) Spacer(Modifier.width(24.dp)) else Spacer(Modifier.height(8.dp))
                        GForceMeter(gX = currentPoint?.gX ?: 0f, gY = currentPoint?.gY ?: 0f)
                    }
                }
            }
        }

        if (showReportDialog) {
            ReportIssueDialog(
                onDismiss = { showReportDialog = false },
                onSubmit = { issue ->
                    // Here you would typically send to Firebase
                    android.widget.Toast.makeText(context, "Issue reported: $issue", android.widget.Toast.LENGTH_LONG).show()
                    showReportDialog = false
                }
            )
        }
    }
}

@Composable
fun ReportIssueDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report Issue / Feedback") },
        text = {
            Column {
                Text("Please describe the bug or suggestion:", fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    placeholder = { Text("Type here...") }
                )
            }
        },
        confirmButton = {
            Button(onClick = { if (text.isNotBlank()) onSubmit(text) }) {
                Text("Submit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
internal fun SettingsMenu(
    currentLang: AppLanguage,
    onLangChange: (AppLanguage) -> Unit,
    currentMapType: MapType,
    onMapTypeChange: (MapType) -> Unit,
    onReportIssue: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { expanded = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color.DarkGray)
        ) {
            Text("Language", color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 12.sp)
            AppLanguage.entries.forEach { lang ->
                DropdownMenuItem(
                    text = { Text(lang.name, color = if (currentLang == lang) Color.Cyan else Color.White) },
                    onClick = { onLangChange(lang); expanded = false }
                )
            }
            Divider(color = Color.Gray)
            Text("Map Type", color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 12.sp)
            DropdownMenuItem(
                text = { Text("Normal", color = if (currentMapType == MapType.NORMAL) Color.Cyan else Color.White) },
                onClick = { onMapTypeChange(MapType.NORMAL); expanded = false }
            )
            DropdownMenuItem(
                text = { Text("Satellite", color = if (currentMapType == MapType.SATELLITE) Color.Cyan else Color.White) },
                onClick = { onMapTypeChange(MapType.SATELLITE); expanded = false }
            )
            Divider(color = Color.Gray)
            DropdownMenuItem(
                text = { Text("Report Issue", color = Color.Yellow) },
                leadingIcon = { Icon(Icons.Default.BugReport, contentDescription = null, tint = Color.Yellow) },
                onClick = { onReportIssue(); expanded = false }
            )
        }
    }
}

@Composable
fun GateMarker(gate: Gate, label: String) {
    Polyline(
        points = listOf(LatLng(gate.a.lat, gate.a.lng), LatLng(gate.b.lat, gate.b.lng)),
        color = Color.Green,
        width = 15f
    )
}

@Composable
fun Speedometer(speedKmh: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("%.0f".format(speedKmh), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        Text("km/h", color = Color.White, fontSize = 12.sp)
    }
}

@Composable
fun GForceMeter(gX: Float, gY: Float) {
    val totalG = sqrt(gX.pow(2) + gY.pow(2))
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("%.2f".format(totalG), color = Color(0xFFFFEB3B), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("G-Force", color = Color.White, fontSize = 10.sp)
    }
}
