package com.ex.mountaintimer

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RouteDao {

    @Insert
    suspend fun insertRoute(route: RouteEntity): Long

    @Insert
    suspend fun insertGates(gates: List<GateEntity>)

    @Transaction
    @Query("SELECT * FROM routes ORDER BY id DESC")
    fun observeAllRoutesWithGates(): Flow<List<RouteWithGates>>

    @Transaction
    @Query("SELECT * FROM routes WHERE id = :routeId LIMIT 1")
    suspend fun getRouteWithGates(routeId: Long): RouteWithGates?

    @Query("DELETE FROM routes WHERE id = :routeId")
    suspend fun deleteRoute(routeId: Long)

    @Query("DELETE FROM gates WHERE routeId = :routeId")
    suspend fun deleteGatesByRoute(routeId: Long)

}
