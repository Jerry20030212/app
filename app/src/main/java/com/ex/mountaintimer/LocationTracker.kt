package com.ex.mountaintimer

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.google.android.gms.location.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * GPS 定位與感測器追蹤器
 * 回傳 GeoPoint（含 bearing, speed, gX, gY）
 */
object LocationTracker {

    @SuppressLint("MissingPermission")
    fun locationFlow(context: Context): Flow<GeoPoint> = callbackFlow {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        var lastGX = 0f
        var lastGY = 0f

        // 加速度感測器監聽 (G-Force)
        val sensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    // 簡單濾波與單位轉換 (m/s^2 -> G)
                    // Android 座標: X=橫向, Y=縱向, Z=垂直
                    // 減去重力影響 (假設手機平放或固定在車架)
                    lastGX = -event.values[0] / 9.81f
                    lastGY = event.values[1] / 9.81f
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(sensorListener, accelerometer, SensorManager.SENSOR_DELAY_UI)

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 100L)
            .setMinUpdateIntervalMillis(100L)
            .setMaxUpdateDelayMillis(0L)
            .setMinUpdateDistanceMeters(0f)
            .setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
            .setWaitForAccurateLocation(true)
            .build()

        // 初始位置
        runCatching {
            client.lastLocation.await()
        }.getOrNull()?.let { loc ->
            trySend(GeoPoint(
                lat = loc.latitude,
                lng = loc.longitude,
                bearing = if (loc.hasBearing()) loc.bearing else 0f,
                speed = if (loc.hasSpeed()) loc.speed else 0f,
                gX = lastGX,
                gY = lastGY
            ))
        }

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { loc ->
                    trySend(GeoPoint(
                        lat = loc.latitude,
                        lng = loc.longitude,
                        bearing = if (loc.hasBearing()) loc.bearing else 0f,
                        speed = if (loc.hasSpeed()) loc.speed else 0f,
                        gX = lastGX,
                        gY = lastGY
                    ))
                }
            }
        }

        client.requestLocationUpdates(request, callback, context.mainLooper)

        awaitClose {
            client.removeLocationUpdates(callback)
            sensorManager.unregisterListener(sensorListener)
        }
    }
}
