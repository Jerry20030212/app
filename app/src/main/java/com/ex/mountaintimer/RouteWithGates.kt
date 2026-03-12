package com.ex.mountaintimer

import androidx.room.Embedded
import androidx.room.Relation

data class RouteWithGates(
    @Embedded val route: RouteEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "routeId"
    )
    val gates: List<GateEntity>
)
