package com.ex.mountaintimer

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 路線設定步驟
 */
private enum class CreateStep {
    NAME,       // 輸入名稱
    EDIT_MAP    // 在地圖上設定 Gate
}

/**
 * 目前正在編輯的 Gate 類型
 */
private enum class EditTarget {
    START, CUSTOM, FINISH
}

/**
 * 新增路線畫面
 *
 * 流程：
 * 1. 輸入路線名稱
 * 2. 在地圖上設定起點（點兩下 A/B）、自訂點 1-10、終點
 * 3. 按「完成」儲存
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteCreateScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { RouteRepository(context) }
    val scope = rememberCoroutineScope()

    // ---- 步驟 ----
    var step by remember { mutableStateOf(CreateStep.NAME) }

    // ---- 路線名稱 ----
    var routeName by remember { mutableStateOf("") }

    // ---- Gate 編輯狀態 ----
    var editTarget by remember { mutableStateOf(EditTarget.START) }
    var customIndex by remember { mutableIntStateOf(1) }
    var editA by remember { mutableStateOf<LatLng?>(null) }
    var editB by remember { mutableStateOf<LatLng?>(null) }

    // ---- 已完成的 Gate ----
    var startGate by remember { mutableStateOf<Gate?>(null) }
    val customGates = remember { mutableStateListOf<Gate>() }
    var finishGate by remember { mutableStateOf<Gate?>(null) }

    // ---- GPS 定位（給地圖初始位置用） ----
    var hasPermission by remember { mutableStateOf(false) }
    var currentPoint by remember { mutableStateOf<GeoPoint?>(null) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(25.034, 121.564), 15f) // 預設台北
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

    // 取得目前位置（只用來設定初始鏡頭位置）
    var initialCameraSet by remember { mutableStateOf(false) }
    LaunchedEffect(hasPermission) {
        if (!hasPermission) return@LaunchedEffect
        LocationTracker.locationFlow(context).collectLatest { p ->
            currentPoint = p
            if (!initialCameraSet) {
                cameraPositionState.position =
                    CameraPosition.fromLatLngZoom(LatLng(p.lat, p.lng), 17f)
                initialCameraSet = true
            }
        }
    }

    // ---- 提示文字 ----
    val hintText = when {
        editTarget == EditTarget.START && editA == null -> "請在地圖上點擊設定 起點 Gate A（第一下）"
        editTarget == EditTarget.START && editB == null -> "請在地圖上點擊設定 起點 Gate B（第二下）"
        editTarget == EditTarget.START -> "起點 Gate 已設定 ✅，按「確認這條」"
        editTarget == EditTarget.CUSTOM && editA == null -> "請設定 自訂點$customIndex Gate A"
        editTarget == EditTarget.CUSTOM && editB == null -> "請設定 自訂點$customIndex Gate B"
        editTarget == EditTarget.CUSTOM -> "自訂點$customIndex Gate 已設定 ✅"
        editTarget == EditTarget.FINISH && editA == null -> "請設定 終點 Gate A"
        editTarget == EditTarget.FINISH && editB == null -> "請設定 終點 Gate B"
        editTarget == EditTarget.FINISH -> "終點 Gate 已設定 ✅，按「確認這條」"
        else -> ""
    }

    // ---- 儲存路線 ----
    fun saveRoute() {
        if (routeName.isBlank()) {
            Toast.makeText(context, "請輸入路線名稱", Toast.LENGTH_SHORT).show()
            return
        }
        if (startGate == null || finishGate == null) {
            Toast.makeText(context, "請至少設定起點和終點", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            repo.saveRoute(
                name = routeName.trim(),
                startGate = startGate,
                customGates = customGates.toList(),
                finishGate = finishGate
            )
            Toast.makeText(context, "✅ 路線已儲存：${routeName.trim()}", Toast.LENGTH_SHORT).show()
            onSaved()
        }
    }

    when (step) {
        // ============================================================
        // 第一步：輸入路線名稱
        // ============================================================
        CreateStep.NAME -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("新增路線") },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                            }
                        }
                    )
                }
            ) { pad ->
                Column(
                    modifier = Modifier
                        .padding(pad)
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "請輸入路線名稱",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = routeName,
                        onValueChange = { routeName = it },
                        label = { Text("路線名稱") },
                        placeholder = { Text("例：陽明山仰德大道") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            if (routeName.isBlank()) {
                                Toast.makeText(context, "請輸入名稱", Toast.LENGTH_SHORT).show()
                            } else {
                                step = CreateStep.EDIT_MAP
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = routeName.isNotBlank()
                    ) {
                        Text("下一步：設定 Gate", fontSize = 16.sp)
                    }
                }
            }
        }

        // ============================================================
        // 第二步：在地圖上設定 Gate
        // ============================================================
        CreateStep.EDIT_MAP -> {
            Box(Modifier.fillMaxSize()) {

                // ---- 地圖 ----
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    onMapClick = { latLng ->
                        // 點擊地圖設定 Gate 端點
                        if (editA == null) {
                            editA = latLng
                        } else if (editB == null) {
                            editB = latLng
                        }
                    }
                ) {
                    // 你的位置
                    currentPoint?.let { p ->
                        Marker(
                            state = MarkerState(position = LatLng(p.lat, p.lng)),
                            title = "你的位置",
                            snippet = "目前定位"
                        )
                    }

                    // 暫存 Gate（紫色）
                    val a = editA
                    val b = editB
                    if (a != null) {
                        Marker(state = MarkerState(position = a), title = "Gate A")
                    }
                    if (b != null) {
                        Marker(state = MarkerState(position = b), title = "Gate B")
                    }
                    if (a != null && b != null) {
                        Polyline(
                            points = listOf(a, b),
                            color = Color(0xFFAA00FF),
                            width = 12f
                        )
                    }

                    // ---- 已確認的 Gate ----

                    // 起點（綠）
                    startGate?.let { g ->
                        Polyline(
                            points = listOf(
                                LatLng(g.a.lat, g.a.lng),
                                LatLng(g.b.lat, g.b.lng)
                            ),
                            color = Color.Green,
                            width = 12f
                        )
                    }

                    // 自訂點（橘）
                    customGates.forEach { g ->
                        Polyline(
                            points = listOf(
                                LatLng(g.a.lat, g.a.lng),
                                LatLng(g.b.lat, g.b.lng)
                            ),
                            color = Color(0xFFFF9800),
                            width = 12f
                        )
                    }

                    // 終點（紅）
                    finishGate?.let { g ->
                        Polyline(
                            points = listOf(
                                LatLng(g.a.lat, g.a.lng),
                                LatLng(g.b.lat, g.b.lng)
                            ),
                            color = Color.Red,
                            width = 12f
                        )
                    }
                }

                // ---- 頂部提示列 ----
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .background(Color(0xCC000000))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "路線：$routeName",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = hintText,
                        color = Color(0xFFFFCC80),
                        fontSize = 14.sp
                    )

                    // 狀態摘要
                    Spacer(modifier = Modifier.height(4.dp))
                    val summary = buildString {
                        append(if (startGate != null) "✅起點" else "❌起點")
                        append("  ")
                        if (customGates.isNotEmpty()) {
                            append("✅自訂點x${customGates.size}")
                        } else {
                            append("⬜自訂點")
                        }
                        append("  ")
                        append(if (finishGate != null) "✅終點" else "❌終點")
                    }
                    Text(text = summary, color = Color(0xFFBBBBBB), fontSize = 12.sp)
                }

                // ---- 右側按鈕面板 ----
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 取消目前 Gate
                    if (editA != null || editB != null) {
                        FloatingActionButton(
                            onClick = {
                                editA = null
                                editB = null
                            },
                            containerColor = Color(0xFFFF5252),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Filled.Undo,
                                contentDescription = "取消",
                                tint = Color.White
                            )
                        }
                    }

                    // 確認這條 Gate
                    if (editA != null && editB != null) {
                        FloatingActionButton(
                            onClick = {
                                val a = editA ?: return@FloatingActionButton
                                val b = editB ?: return@FloatingActionButton
                                val gate = Gate(
                                    a = GeoPoint(a.latitude, a.longitude),
                                    b = GeoPoint(b.latitude, b.longitude)
                                )

                                when (editTarget) {
                                    EditTarget.START -> {
                                        startGate = gate
                                        editTarget = EditTarget.CUSTOM
                                        customIndex = 1
                                    }
                                    EditTarget.CUSTOM -> {
                                        if (customGates.size < 10) {
                                            customGates.add(gate)
                                            customIndex = customGates.size + 1
                                        } else {
                                            Toast.makeText(context, "最多10個自訂點", Toast.LENGTH_SHORT).show()
                                            editTarget = EditTarget.FINISH
                                        }
                                    }
                                    EditTarget.FINISH -> {
                                        finishGate = gate
                                    }
                                }
                                editA = null
                                editB = null
                            },
                            containerColor = Color(0xFF4CAF50),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "確認",
                                tint = Color.White
                            )
                        }
                    }
                }

                // ---- 底部按鈕列 ----
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color(0xCC000000))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 返回
                    OutlinedButton(
                        onClick = { step = CreateStep.NAME },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("返回", color = Color.White)
                    }

                    // 刪除上一個自訂點
                    if (editTarget == EditTarget.CUSTOM && customGates.isNotEmpty()) {
                        OutlinedButton(
                            onClick = {
                                customGates.removeAt(customGates.lastIndex)
                                customIndex = customGates.size + 1
                                Toast.makeText(context, "已刪除上一個自訂點", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("刪除上一自訂點", color = Color.White, fontSize = 12.sp)
                        }
                    }

                    // 跳到終點
                    if (editTarget == EditTarget.CUSTOM) {
                        OutlinedButton(
                            onClick = {
                                editTarget = EditTarget.FINISH
                                editA = null
                                editB = null
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("跳到終點", color = Color.White)
                        }
                    }

                    // 完成儲存
                    if (startGate != null && finishGate != null) {
                        Button(
                            onClick = { saveRoute() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("完成")
                        }
                    }
                }
            }
        }
    }
}
