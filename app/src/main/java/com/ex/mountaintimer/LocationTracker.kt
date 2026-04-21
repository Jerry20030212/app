package com.ex.mountaintimer

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * GPS 定位追蹤器
 * 回傳 GeoPoint（含 bearing 和 speed）
 */
object LocationTracker {

    @SuppressLint("MissingPermission")
    fun locationFlow(context: Context): Flow<GeoPoint> = callbackFlow {
        val client = LocationServices.getFusedLocationProviderClient(context)

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            100L
        )
            .setMinUpdateIntervalMillis(100L)
            .setMaxUpdateDelayMillis(0L)
            .setMinUpdateDistanceMeters(0f)
            .setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
            .setWaitForAccurateLocation(true)
            .build()

        // 嘗試獲取最後已知位置作為初始值
        runCatching {
            client.lastLocation.await()
        }.getOrNull()?.let { loc ->
            trySend(
                GeoPoint(
                    lat = loc.latitude,
                    lng = loc.longitude,
                    bearing = if (loc.hasBearing()) loc.bearing else 0f,
                    speed = if (loc.hasSpeed()) loc.speed else 0f
                )
            )
        }

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { loc ->
                    trySend(
                        GeoPoint(
                            lat = loc.latitude,
                            lng = loc.longitude,
                            bearing = if (loc.hasBearing()) loc.bearing else 0f,
                            speed = if (loc.hasSpeed()) loc.speed else 0f
                        )
                    )
                }
            }
        }

        client.requestLocationUpdates(request, callback, context.mainLooper)

        awaitClose { client.removeLocationUpdates(callback) }
    }
}
