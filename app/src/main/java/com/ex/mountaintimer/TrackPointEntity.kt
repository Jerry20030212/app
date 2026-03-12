package com.ex.mountaintimer

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * GPS 軌跡點（用於歷史回放）
 */
@Entity(
    tableName = "track_points",
    indices = [Index("runId")]
)
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val runId: Long,
    val lat: Double,
    val lng: Double,
    val timestampMs: Long         // epoch ms
)
