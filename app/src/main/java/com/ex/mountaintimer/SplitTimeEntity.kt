package com.ex.mountaintimer

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 每個分段時間紀錄
 */
@Entity(
    tableName = "split_times",
    indices = [Index("runId")]
)
data class SplitTimeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val runId: Long,
    val checkpointIndex: Int,     // 對應 Checkpoint.index
    val checkpointName: String,   // 對應 Checkpoint.name
    val timeMs: Long              // 從起點開始計算的時間（ms）
)
