package com.ex.mountaintimer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
 * - "+" 按鈕 → 新增路線
 * - 點擊路線 → 選中回主畫面
 * - 按刪除 → 刪除路線
 */
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

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
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
                    "選擇路線",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onCreateRoute) {
                    Text("+ 新增", fontSize = 16.sp)
                }
            }
        }

        // ---- 內容 ----
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (routes.isEmpty()) {
                Spacer(modifier = Modifier.height(80.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "目前沒有已儲存的路線",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("按右上角「+ 新增」來新增路線")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onCreateRoute) {
                        Text("+ 新增路線")
                    }
                }
            } else {
                routes.forEach { item ->
                    val route = item.route
                    val customCount = item.gates.count { it.type == "CUSTOM" }

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
                                    "自訂點: $customCount 個",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            TextButton(onClick = {
                                scope.launch { repo.deleteRoute(route.id) }
                            }) {
                                Text("刪除", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}
