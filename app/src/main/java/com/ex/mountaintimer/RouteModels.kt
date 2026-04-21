package com.ex.mountaintimer

// 最小必需資料模型：給 GateDetector / RouteRepository / MapScreen 用

data class GeoPoint(
    val lat: Double,
    val lng: Double,
    val bearing: Float = 0f,      // 行進方向（度，0=北）
    val speed: Float = 0f,        // 速度（m/s）
    val gX: Float = 0f,           // 橫向 G 力 (Lateral)
    val gY: Float = 0f            // 縱向 G 力 (Longitudinal)
)

data class Gate(
    val a: GeoPoint,
    val b: GeoPoint
)

// 給 RunEngine / 路線用
enum class CheckpointType { START, CUSTOM, FINISH }

data class Checkpoint(
    val type: CheckpointType,
    val index: Int,            // CUSTOM 用 1..10，START/FINISH 用 0
    val name: String,
    val gate: Gate
)

data class RouteDefinition(
    val id: String,
    val name: String,
    val checkpoints: List<Checkpoint>
)
