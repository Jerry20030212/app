package com.ex.mountaintimer

import android.content.Context
import kotlinx.coroutines.flow.Flow

class RouteRepository(context: Context) {

    private val dao = AppDatabase.get(context).routeDao()

    // ------------------------------------------------
    // 儲存一條路線（含起點 / 自訂點 / 終點）
    // ------------------------------------------------
    suspend fun saveRoute(
        name: String,
        startGate: Gate?,
        customGates: List<Gate>,
        finishGate: Gate?
    ): Long {
        // 1️⃣ 先存 route
        val routeId = dao.insertRoute(RouteEntity(name = name))

        // 2️⃣ 建立 gate 清單
        val gates = mutableListOf<GateEntity>()

        startGate?.let { g ->
            gates.add(g.toEntity(routeId, "START", 0))
        }

        customGates.forEachIndexed { index, g ->
            gates.add(g.toEntity(routeId, "CUSTOM", index + 1))
        }

        finishGate?.let { g ->
            gates.add(g.toEntity(routeId, "FINISH", 0))
        }

        // 3️⃣ 存所有 gate
        dao.insertGates(gates)

        return routeId
    }

    // ------------------------------------------------
    // 觀察所有路線（Flow，列表用）
    // ------------------------------------------------
    fun observeAllRoutesWithGates(): Flow<List<RouteWithGates>> {
        return dao.observeAllRoutesWithGates()
    }

    // ------------------------------------------------
    // 依 ID 取得單一路線
    // ------------------------------------------------
    suspend fun getRouteWithGates(id: Long): RouteWithGates? {
        return dao.getRouteWithGates(id)
    }

    // ------------------------------------------------
    // 刪除路線（含所屬 gate）
    // ------------------------------------------------
    suspend fun deleteRoute(routeId: Long) {
        dao.deleteGatesByRoute(routeId)
        dao.deleteRoute(routeId)
    }
}

// ------------------------------------------------
// Gate → GateEntity 轉換
// ------------------------------------------------
private fun Gate.toEntity(
    routeId: Long,
    type: String,
    index: Int
): GateEntity {
    return GateEntity(
        routeId = routeId,
        type = type,
        index = index,
        aLat = this.a.lat,
        aLng = this.a.lng,
        bLat = this.b.lat,
        bLng = this.b.lng
    )
}
