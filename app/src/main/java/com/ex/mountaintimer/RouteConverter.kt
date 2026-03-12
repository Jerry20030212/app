package com.ex.mountaintimer

/**
 * RouteWithGates → RouteDefinition 轉換工具
 */
fun RouteWithGates.toRouteDefinition(): RouteDefinition {
    val checkpoints = gates
        .sortedWith(compareBy(
            { when (it.type) { "START" -> 0; "CUSTOM" -> 1; "FINISH" -> 2; else -> 3 } },
            { it.index }
        ))
        .map { g ->
            val type = when (g.type) {
                "START" -> CheckpointType.START
                "FINISH" -> CheckpointType.FINISH
                else -> CheckpointType.CUSTOM
            }
            val name = when (type) {
                CheckpointType.START -> "起點"
                CheckpointType.CUSTOM -> "自訂點${g.index}"
                CheckpointType.FINISH -> "終點"
            }
            Checkpoint(
                type = type,
                index = g.index,
                name = name,
                gate = Gate(
                    a = GeoPoint(g.aLat, g.aLng),
                    b = GeoPoint(g.bLat, g.bLng)
                )
            )
        }

    return RouteDefinition(
        id = route.id.toString(),
        name = route.name,
        checkpoints = checkpoints
    )
}
