package com.ex.mountaintimer

import android.Manifest
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
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

    var routeName by remember { mutableStateOf("測試路線") }

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

    var hasInitializedCamera by remember { mutableStateOf(false) }

    LaunchedEffect(currentPoint, hasPermission) {
        if (hasPermission && currentPoint != null && !hasInitializedCamera) {
            val latLng = LatLng(currentPoint!!.lat, currentPoint!!.lng)
            cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(latLng, 17f))
            hasInitializedCamera = true
        }
    }

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

    var editA by remember { mutableStateOf<LatLng?>(null) }
    var editB by remember { mutableStateOf<LatLng?>(null) }
    var startGate by remember { mutableStateOf<Gate?>(null) }
    val customGates = remember { mutableStateListOf<Gate>() }
    var finishGate by remember { mutableStateOf<Gate?>(null) }
    var editingTarget by remember { mutableStateOf(EditingTarget.START) }
    var customIndex by remember { mutableStateOf(1) }

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

    var selectedRoute by remember { mutableStateOf<RouteWithGates?>(null) }

    LaunchedEffect(selectedRouteId) {
        selectedRoute = if (selectedRouteId == null) null else repo.getRouteWithGates(selectedRouteId)
    }

    LaunchedEffect(selectedRoute) {
        val r = selectedRoute ?: return@LaunchedEffect
        routeName = r.route.name
        startGate = null
        customGates.clear()
        finishGate = null
        r.gates.forEach { g ->
            val gate = Gate(GeoPoint(g.aLat, g.aLng), GeoPoint(g.bLat, g.bLng))
            when (g.type) {
                "START" -> startGate = gate
                "CUSTOM" -> customGates.add(gate)
                "FINISH" -> finishGate = gate
            }
        }
        uiModeState.value = UiMode.RUN
        editA = null
        editB = null
        editingTarget = EditingTarget.START
        customIndex = customGates.size + 1
        isRunning = false
    }

    fun saveCurrentRoute() {
        val s = startGate
        val f = finishGate
        if (s == null || f == null) {
            Toast.makeText(context, "❌ 請先設定起點與終點再儲存", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            repo.saveRoute(routeName, s, customGates.toList(), f)
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
                editA == null -> "模式：編輯 $targetName｜請點 Gate A"
                editB == null -> "模式：編輯 $targetName｜請點 Gate B"
                else -> "模式：編輯 $targetName｜已完成 ✅"
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
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
            currentPoint?.let { p ->
                Marker(state = MarkerState(position = LatLng(p.lat, p.lng)), title = "你的位置")
            }

            if (uiModeState.value == UiMode.RUN && trackPoints.size >= 2) {
                Polyline(points = trackPoints, color = Color(0xFF81D4FA), width = 25f, startCap = RoundCap(), endCap = RoundCap(), jointType = JointType.ROUND)
                Polyline(points = trackPoints, color = Color(0xFF1976D2), width = 18f, startCap = RoundCap(), endCap = RoundCap(), jointType = JointType.ROUND)
            }

            val a = editA
            val b = editB
            if (a != null) Marker(state = MarkerState(position = a), title = "Gate A")
            if (b != null) Marker(state = MarkerState(position = b), title = "Gate B")
            if (a != null && b != null) {
                Polyline(points = listOf(a, b), color = Color(0xFFAA00FF), width = 12f)
            }

            startGate?.let { g ->
                Polyline(points = listOf(LatLng(g.a.lat, g.a.lng), LatLng(g.b.lat, g.b.lng)), color = Color.Green, width = 12f)
            }
            customGates.forEach { g ->
                Polyline(points = listOf(LatLng(g.a.lat, g.a.lng), LatLng(g.b.lat, g.b.lng)), color = Color(0xFFFF9800), width = 12f)
            }
            finishGate?.let { g ->
                Polyline(points = listOf(LatLng(g.a.lat, g.a.lng), LatLng(g.b.lat, g.b.lng)), color = Color.Red, width = 12f)
            }
        }

        // HUD (頂部)
        Box(modifier = Modifier.align(Alignment.TopCenter).padding(top = 40.dp, start = 16.dp, end = 16.dp).fillMaxWidth()) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xCC121212)), shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(defaultElevation = 8.dp), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(text = routeName.ifEmpty { "未選擇路線" }, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(text = hintText, color = Color(0xFFB0BEC5), fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = formatMs(elapsedMs), color = if (isRunning) Color(0xFF00E676) else Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        if (isRunning) {
                            Text(text = "RECORDING", color = Color(0xFFFF5252), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 控制列 (底部)
        Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp, start = 16.dp, end = 16.dp).fillMaxWidth()) {
            when (uiModeState.value) {
                UiMode.RUN -> {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        ControlButton(icon = Icons.Default.List, label = "路線", onClick = onOpenRouteList)
                        Box(modifier = Modifier.size(84.dp).shadow(12.dp, CircleShape).clip(CircleShape).background(Brush.linearGradient(if (isRunning) listOf(Color(0xFFFF5252), Color(0xFFD32F2F)) else listOf(Color(0xFF2196F3), Color(0xFF1976D2)))).clickable {
                            if (isRunning) isRunning = false else { isRunning = true; startedAtMs = nowMs }
                        }, contentAlignment = Alignment.Center) {
                            Icon(imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                        }
                        ControlButton(icon = Icons.Default.History, label = "歷史", onClick = onOpenHistory)
                        ControlButton(icon = Icons.Default.Add, label = "編輯", onClick = { uiModeState.value = UiMode.EDIT_GATE; editingTarget = EditingTarget.START; customIndex = 1; editA = null; editB = null; isRunning = false })
                    }
                }
                UiMode.EDIT_GATE -> {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xCC121212)), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("編輯模式: ${when(editingTarget){ EditingTarget.START -> "起點"; EditingTarget.CUSTOM -> "自訂點 $customIndex"; EditingTarget.FINISH -> "終點" }}", color = Color.White, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(modifier = Modifier.weight(1f), enabled = (editA != null && editB != null), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)), onClick = {
                                    val a = editA ?: return@Button
                                    val b = editB ?: return@Button
                                    val gate = Gate(GeoPoint(a.latitude, a.longitude), GeoPoint(b.latitude, b.longitude))
                                    when (editingTarget) {
                                        EditingTarget.START -> { startGate = gate; editingTarget = EditingTarget.CUSTOM }
                                        EditingTarget.CUSTOM -> { customGates.add(gate); customIndex += 1 }
                                        EditingTarget.FINISH -> { finishGate = gate }
                                    }
                                    editA = null; editB = null
                                }) { Text("完成此段") }
                                if (editingTarget == EditingTarget.CUSTOM) {
                                    Button(modifier = Modifier.weight(1f), onClick = { editingTarget = EditingTarget.FINISH; editA = null; editB = null }) { Text("去畫終點") }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (finishGate != null && startGate != null) {
                                    Button(modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)), onClick = { saveCurrentRoute() }) { Text("儲存路線") }
                                }
                                OutlinedButton(modifier = Modifier.weight(1f), onClick = { uiModeState.value = UiMode.RUN; editA = null; editB = null }) { Text("返回", color = Color.White) }
                            }
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
        Box(modifier = Modifier.size(52.dp).clip(CircleShape).background(Color(0xAA212121)).border(1.dp, Color(0x33FFFFFF), CircleShape), contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = null, tint = Color.White)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}
