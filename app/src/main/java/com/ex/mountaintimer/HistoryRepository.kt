package com.ex.mountaintimer

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * 歷史紀錄 Repository
 */
class HistoryRepository(context: Context) {

    private val dao = AppDatabase.get(context).historyDao()

    // ---- 觀察所有跑山紀錄 ----
    fun observeAllRunResults(): Flow<List<RunResultEntity>> {
        return dao.observeAllRunResults()
    }

    // ---- 取得單一紀錄 ----
    suspend fun getRunResult(runId: Long): RunResultEntity? {
        return dao.getRunResult(runId)
    }

    // ---- 取得分段時間 ----
    suspend fun getSplitTimes(runId: Long): List<SplitTimeEntity> {
        return dao.getSplitTimes(runId)
    }

    // ---- 取得軌跡點 ----
    suspend fun getTrackPoints(runId: Long): List<TrackPointEntity> {
        return dao.getTrackPoints(runId)
    }

    // ---- 儲存完整跑山結果 ----
    suspend fun saveRunResult(
        routeId: Long,
        routeName: String,
        startTimeEpoch: Long,
        endTimeEpoch: Long,
        totalTimeMs: Long,
        splits: List<SplitTimeEntity>,
        trackPoints: List<TrackPointEntity>
    ): Long {
        val runId = dao.insertRunResult(
            RunResultEntity(
                routeId = routeId,
                routeName = routeName,
                startTimeEpoch = startTimeEpoch,
                endTimeEpoch = endTimeEpoch,
                totalTimeMs = totalTimeMs
            )
        )

        // 更新 splits 和 trackPoints 的 runId
        if (splits.isNotEmpty()) {
            dao.insertSplitTimes(splits.map { it.copy(runId = runId) })
        }
        if (trackPoints.isNotEmpty()) {
            dao.insertTrackPoints(trackPoints.map { it.copy(runId = runId) })
        }

        return runId
    }

    // ---- 刪除紀錄 ----
    suspend fun deleteRunResult(runId: Long) {
        dao.deleteSplitTimes(runId)
        dao.deleteTrackPoints(runId)
        dao.deleteRunResult(runId)
    }
}
