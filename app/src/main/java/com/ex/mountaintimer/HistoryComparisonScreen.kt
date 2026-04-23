package com.ex.mountaintimer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryComparisonScreen(
    runIds: List<Long>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val historyRepo = remember { HistoryRepository(context) }
    
    var runsData by remember { mutableStateOf<List<Pair<RunResultEntity, List<SplitTimeEntity>>>>(emptyList()) }
    val colors = listOf(Color(0xFF2196F3), Color(0xFFFF5252), Color(0xFF4CAF50)) // Blue, Red, Green

    LaunchedEffect(runIds) {
        val data = runIds.mapNotNull { id ->
            val run = historyRepo.getRunResult(id)
            val splits = historyRepo.getSplitTimesForRun(id)
            if (run != null) run to splits else null
        }
        runsData = data
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) { Text("← 返回") }
                Spacer(modifier = Modifier.weight(1f))
                Text("數據對比", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(64.dp))
            }
        }

        if (runsData.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ---- Summary Comparison Card ----
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("總體數據對照", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))
                        
                        ComparisonRow("日期", runsData.map { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(it.first.startTimeEpoch)) }, colors)
                        Divider(Modifier.padding(vertical = 8.dp))
                        ComparisonRow("車型", runsData.map { it.first.vehicleModel.ifBlank { "-" } }, colors)
                        Divider(Modifier.padding(vertical = 8.dp))
                        ComparisonRow("總時間", runsData.map { formatMs(it.first.totalTimeMs) }, colors)
                        Divider(Modifier.padding(vertical = 8.dp))
                        ComparisonRow("平均時速", runsData.map { "%.1f km/h".format(it.first.averageSpeedKmh) }, colors)
                        Divider(Modifier.padding(vertical = 8.dp))
                        ComparisonRow("總距離", runsData.map { "%.2f km".format(it.first.totalDistanceM / 1000.0) }, colors)
                    }
                }

                // ---- Split Points Comparison ----
                Text("分段點數據對比", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                
                // Get all gate names/indices
                val maxSplits = runsData.maxOf { it.second.size }
                for (i in 0 until maxSplits) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            val gateName = runsData.firstOrNull { it.second.size > i }?.second?.get(i)?.gateName ?: "Gate ${i+1}"
                            Text(gateName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(8.dp))
                            
                            ComparisonRow("通過時間", runsData.map { 
                                if (i < it.second.size) formatMs(it.second[i].elapsedMs) else "N/A"
                            }, colors)
                            Spacer(Modifier.height(4.dp))
                            ComparisonRow("當下時速", runsData.map { 
                                if (i < it.second.size) "%.1f km/h".format(it.second[i].speedKmh) else "N/A"
                            }, colors)
                            Spacer(Modifier.height(4.dp))
                            ComparisonRow("最大G值", runsData.map { 
                                if (i < it.second.size) "%.2f G".format(it.second[i].gForce) else "N/A"
                            }, colors)
                        }
                    }
                }
                
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun ComparisonRow(label: String, values: List<String>, colors: List<Color>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            values.forEachIndexed { index, value ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors[index].copy(alpha = 0.1f))
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        value, 
                        fontSize = 13.sp, 
                        fontWeight = FontWeight.Bold,
                        color = colors[index],
                        maxLines = 1
                    )
                }
            }
        }
    }
}
