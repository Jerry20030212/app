package com.ex.mountaintimer

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 單次跑山結果
 */
@Entity(
    tableName = "run_results",
    indices = [Index("routeId")]
)
data class RunResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val routeId: Long,
    val routeName: String,
    val startTimeEpoch: Long,     // 開始時間（epoch ms）
    val endTimeEpoch: Long,       // 結束時間（epoch ms）
    val totalTimeMs: Long,        // 總耗時（ms）
    val vehicleModel: String = "", // 車型
    val totalDistanceM: Double = 0.0, // 總距離 (公尺)
    val averageSpeedKmh: Double = 0.0 // 平均速度 (km/h)
)
