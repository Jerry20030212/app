package com.ex.mountaintimer

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "gates",
    indices = [Index("routeId")]
)
data class GateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val routeId: Long,

    // "START" / "CUSTOM" / "FINISH"
    val type: String,

    // CUSTOM: 1..10，START/FINISH: 0
    val index: Int,

    val aLat: Double,
    val aLng: Double,
    val bLat: Double,
    val bLng: Double
)
