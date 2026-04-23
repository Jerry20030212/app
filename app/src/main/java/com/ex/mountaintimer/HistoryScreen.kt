package com.ex.mountaintimer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpenDetail: (runId: Long) -> Unit,
    onOpenComparison: (runIds: List<Long>) -> Unit
) {
    val context = LocalContext.current
    val historyRepo = remember { HistoryRepository(context) }
    val scope = rememberCoroutineScope()

    val runs by historyRepo.observeAllRunResults().collectAsState(initial = emptyList())
    val groupedRuns = remember(runs) { runs.groupBy { it.routeName } }
    
    var expandedRoutes by remember { mutableStateOf(setOf<String>()) }
    var selectedRunIds by remember { mutableStateOf(setOf<Long>()) }
    var isCompareMode by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize()) {
        // ---- Top Bar ----
        Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) { Text("← 返回") }
                Spacer(modifier = Modifier.weight(1f))
                Text("歷史紀錄", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                
                if (runs.isNotEmpty()) {
                    IconButton(onClick = { 
                        isCompareMode = !isCompareMode 
                        if (!isCompareMode) selectedRunIds = emptySet()
                    }) {
                        Icon(
                            Icons.Default.CompareArrows, 
                            contentDescription = "Compare",
                            tint = if (isCompareMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // ---- Comparison Floating Bar ----
        AnimatedVisibility(visible = isCompareMode && selectedRunIds.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "已選擇 ${selectedRunIds.size} / 3 筆紀錄",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { onOpenComparison(selectedRunIds.toList()) },
                        enabled = selectedRunIds.size >= 2
                    ) {
                        Text("開始對比")
                    }
                }
            }
        }

        // ---- Content ----
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (runs.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("目前沒有歷史紀錄", fontSize = 18.sp, color = Color.Gray)
                }
            } else {
                groupedRuns.forEach { (routeName, routeRuns) ->
                    RouteGroupCard(
                        routeName = routeName,
                        runs = routeRuns,
                        isExpanded = expandedRoutes.contains(routeName),
                        onToggleExpand = {
                            expandedRoutes = if (expandedRoutes.contains(routeName)) {
                                expandedRoutes - routeName
                            } else {
                                expandedRoutes + routeName
                            }
                        },
                        isCompareMode = isCompareMode,
                        selectedRunIds = selectedRunIds,
                        onRunSelected = { id ->
                            selectedRunIds = if (selectedRunIds.contains(id)) {
                                selectedRunIds - id
                            } else if (selectedRunIds.size < 3) {
                                selectedRunIds + id
                            } else {
                                selectedRunIds // Max 3
                            }
                        },
                        onOpenDetail = onOpenDetail,
                        onDeleteRun = { id -> scope.launch { historyRepo.deleteRunResult(id) } },
                        dateFormat = dateFormat
                    )
                }
            }
        }
    }
}

@Composable
fun RouteGroupCard(
    routeName: String,
    runs: List<RunResultEntity>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    isCompareMode: Boolean,
    selectedRunIds: Set<Long>,
    onRunSelected: (Long) -> Unit,
    onOpenDetail: (Long) -> Unit,
    onDeleteRun: (Long) -> Unit,
    dateFormat: SimpleDateFormat
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(routeName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${runs.size} 項紀錄", style = MaterialTheme.typography.bodySmall)
                }
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(bottom = 8.dp)
                ) {
                    runs.sortedByDescending { it.startTimeEpoch }.forEach { run ->
                        Divider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = Color.LightGray)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    if (isCompareMode) onRunSelected(run.id) else onOpenDetail(run.id) 
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isCompareMode) {
                                Checkbox(
                                    checked = selectedRunIds.contains(run.id),
                                    onCheckedChange = { onRunSelected(run.id) }
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            
                            Column(Modifier.weight(1f)) {
                                Text(
                                    dateFormat.format(Date(run.startTimeEpoch)),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                if (run.vehicleModel.isNotBlank()) {
                                    Text(run.vehicleModel, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                            }
                            
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    formatMs(run.totalTimeMs),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "%.1f km | %.1f km/h".format(run.totalDistanceM / 1000.0, run.averageSpeedKmh),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                            }

                            if (!isCompareMode) {
                                IconButton(onClick = { onDeleteRun(run.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
