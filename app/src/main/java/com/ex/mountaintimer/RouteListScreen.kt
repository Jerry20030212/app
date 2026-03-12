package com.ex.mountaintimer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * 路線列表畫面
 *
 * - 顯示已儲存的路線
 * - 左上 "+" 按鈕 → 新增路線
 * - 點擊路線 → 選中回主畫面
 * - 右滑或按刪除 → 刪除路線
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteListScreen(
    onBack: () -> Unit,
    onPickRoute: (routeId: Long) -> Unit,
    onCreateRoute: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { RouteRepository(context) }
    val scope = rememberCoroutineScope()

    val routes by repo.observeAllRoutesWithGates().collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("選擇路線") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // ✅ "+" 按鈕新增路線
                    IconButton(onClick = onCreateRoute) {
                        Icon(Icons.Filled.Add, contentDescription = "新增路線")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (routes.isEmpty()) {
                // 空列表提示
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "目前沒有已儲存的路線",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("按右上角 + 來新增路線")
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onCreateRoute) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("新增路線")
                        }
                    }
                }
                return@Column
            }

            routes.forEach { item ->
                val route = item.route
                val gateCount = item.gates.size
                val startCount = item.gates.count { it.type == "START" }
                val customCount = item.gates.count { it.type == "CUSTOM" }
                val finishCount = item.gates.count { it.type == "FINISH" }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPickRoute(route.id) },
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                route.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "起點: $startCount | 自訂點: $customCount | 終點: $finishCount",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(onClick = {
                            scope.launch { repo.deleteRoute(route.id) }
                        }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "刪除",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}
