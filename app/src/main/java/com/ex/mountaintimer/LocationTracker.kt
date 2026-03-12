package com.ex.mountaintimer

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

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
            1000L // 1秒
        )
            .setMinUpdateIntervalMillis(500L)
            .setWaitForAccurateLocation(false)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
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

        client.requestLocationUpdates(request, callback, context.mainLooper)

        awaitClose { client.removeLocationUpdates(callback) }
    }
}
