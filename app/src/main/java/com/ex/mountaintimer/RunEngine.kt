package com.ex.mountaintimer

/**
 * 一次跑山的狀態
 */
enum class RunState {
    IDLE,       // 尚未開始（等待起點）
    RUNNING,    // 計時中
    FINISHED    // 已完成
}

/**
 * 每個 checkpoint 的紀錄
 */
data class CheckpointResult(
    val checkpoint: Checkpoint,
    val timeMs: Long
)

/**
 * 跑山核心引擎
 * - 吃 GPS 點（一個一個餵）
 * - 自動判斷起點 / 分段 / 終點
 */
class RunEngine(
    private val route: RouteDefinition
) {

    var state: RunState = RunState.IDLE
        private set

    var startTimeMs: Long = 0L
        private set

    private var lastPoint: GeoPoint? = null

    private val passedCheckpoints = mutableSetOf<Checkpoint>()
    val results = mutableListOf<CheckpointResult>()

    /** 取得從起點到現在的經過時間 */
    fun getElapsedMs(nowMs: Long = System.currentTimeMillis()): Long {
        return when (state) {
            RunState.IDLE -> 0L
            RunState.RUNNING -> nowMs - startTimeMs
            RunState.FINISHED -> {
                if (results.isEmpty()) 0L
                else results.last().timeMs
            }
        }
    }

    /** 重置引擎（可再跑一次） */
    fun reset() {
        state = RunState.IDLE
        startTimeMs = 0L
        lastPoint = null
        passedCheckpoints.clear()
        results.clear()
    }

    /**
     * 每收到一個新的 GPS 點，就呼叫一次
     */
    fun onLocationUpdate(point: GeoPoint, nowMs: Long = System.currentTimeMillis()): RunEvent? {
        val prev = lastPoint
        lastPoint = point

        if (prev == null) return null

        when (state) {

            RunState.IDLE -> {
                val start = route.checkpoints.firstOrNull { it.type == CheckpointType.START }
                    ?: return null
                if (GateDetector.crossedGate(prev, point, start.gate)) {
                    state = RunState.RUNNING
                    startTimeMs = nowMs
                    passedCheckpoints.add(start)
                    return RunEvent.Started
                }
            }

            RunState.RUNNING -> {
                for (cp in route.checkpoints) {
                    if (passedCheckpoints.contains(cp)) continue

                    if (GateDetector.crossedGate(prev, point, cp.gate)) {
                        passedCheckpoints.add(cp)
                        val elapsed = nowMs - startTimeMs

                        results.add(
                            CheckpointResult(
                                checkpoint = cp,
                                timeMs = elapsed
                            )
                        )

                        if (cp.type == CheckpointType.FINISH) {
                            state = RunState.FINISHED
                            return RunEvent.Finished(results.toList())
                        }

                        return RunEvent.PassedCheckpoint(cp, elapsed)
                    }
                }
            }

            RunState.FINISHED -> {
                // 不再處理
            }
        }

        return null
    }
}

/**
 * RunEngine 對外拋出的事件
 */
sealed class RunEvent {
    data object Started : RunEvent()
    data class PassedCheckpoint(val checkpoint: Checkpoint, val timeMs: Long) : RunEvent()
    data class Finished(val results: List<CheckpointResult>) : RunEvent()
}
