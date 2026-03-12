package com.ex.mountaintimer

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    // ---- RunResult ----

    @Insert
    suspend fun insertRunResult(result: RunResultEntity): Long

    @Query("SELECT * FROM run_results ORDER BY startTimeEpoch DESC")
    fun observeAllRunResults(): Flow<List<RunResultEntity>>

    @Query("SELECT * FROM run_results WHERE id = :runId LIMIT 1")
    suspend fun getRunResult(runId: Long): RunResultEntity?

    @Query("DELETE FROM run_results WHERE id = :runId")
    suspend fun deleteRunResult(runId: Long)

    // ---- SplitTime ----

    @Insert
    suspend fun insertSplitTimes(splits: List<SplitTimeEntity>)

    @Query("SELECT * FROM split_times WHERE runId = :runId ORDER BY checkpointIndex ASC")
    suspend fun getSplitTimes(runId: Long): List<SplitTimeEntity>

    @Query("DELETE FROM split_times WHERE runId = :runId")
    suspend fun deleteSplitTimes(runId: Long)

    // ---- TrackPoint ----

    @Insert
    suspend fun insertTrackPoints(points: List<TrackPointEntity>)

    @Query("SELECT * FROM track_points WHERE runId = :runId ORDER BY timestampMs ASC")
    suspend fun getTrackPoints(runId: Long): List<TrackPointEntity>

    @Query("DELETE FROM track_points WHERE runId = :runId")
    suspend fun deleteTrackPoints(runId: Long)
}
