package com.example.drivemate

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.*

object DriveSession {
    var isDriving = false
    var totalDistance = 0.0
    var selectedCarId: Int = -1
    var maxSpeed = 0.0f
}

class DriveService : Service(), SensorEventListener {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var lastLocation: Location? = null
    private lateinit var sensorManager: SensorManager
    private var accelSensor: Sensor? = null

    private var penalties = 0
    private var lastPenaltyTime = 0L

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        sensorManager.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_NORMAL)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("drive_channel", "Drive", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_DRIVE") {
            saveFinalStats()
            fusedLocationClient.removeLocationUpdates(locationCallback)
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }
        val pIntent = PendingIntent.getActivity(this, 0, Intent(this, DriveActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val note = NotificationCompat.Builder(this, "drive_channel")
            .setContentTitle("DriveMate").setContentText("Jazda w toku").setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pIntent).build()
        startForeground(3, note)
        startLocationUpdates()
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000).build()
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(res: LocationResult) {
                for (loc in res.locations) {
                    if (lastLocation != null) {
                        val dist = lastLocation!!.distanceTo(loc) / 1000.0
                        DriveSession.totalDistance += dist
                        if (loc.speed > DriveSession.maxSpeed) {
                            DriveSession.maxSpeed = loc.speed
                        }
                        updateGlobalStats(dist.toFloat())
                        LocalBroadcastManager.getInstance(this@DriveService).sendBroadcast(Intent("UPDATE_DISTANCE"))
                    }
                    lastLocation = loc
                }
            }
        }
        fusedLocationClient.requestLocationUpdates(req, locationCallback, Looper.getMainLooper())
    }

    private fun updateGlobalStats(dist: Float) {
        val prefs = getSharedPreferences("app_stats", Context.MODE_PRIVATE)
        val currentTripKm = prefs.getFloat("current_trip_km", 0f) + dist
        prefs.edit().putFloat("current_trip_km", currentTripKm).apply()
    }

    private fun saveFinalStats() {
        val prefs = getSharedPreferences("app_stats", Context.MODE_PRIVATE)
        val tripKm = prefs.getFloat("current_trip_km", 0f)
        val oldTotalKm = prefs.getFloat("total_km", 0f)
        val totalKm = oldTotalKm + tripKm

        val distRankKey = when {
            totalKm >= 501.0f -> "rank_kubica"
            totalKm >= 101.0f -> "rank_baby_driver"
            totalKm >= 51.0f -> "rank_niedzielny"
            totalKm >= 1.0f -> "rank_poczatkujacy"
            else -> "stats_rank_none"
        }

        var styleRankKey = prefs.getString("style_rank_key", "style_rank_none") ?: "style_rank_none"
        if (totalKm >= 5.0f && tripKm > 0.1f) {
            val incidentsPerKm = penalties.toFloat() / tripKm
            styleRankKey = when {
                incidentsPerKm < 0.2f -> "style_rank_a"
                incidentsPerKm < 0.5f -> "style_rank_b"
                incidentsPerKm < 1.0f -> "style_rank_c"
                incidentsPerKm < 2.0f -> "style_rank_d"
                else -> "style_rank_f"
            }
        }

        val topSpeed = prefs.getFloat("top_speed", 0f)
        val sessionMaxSpeed = DriveSession.maxSpeed * 3.6f
        if (sessionMaxSpeed > topSpeed) {
            prefs.edit().putFloat("top_speed", sessionMaxSpeed).apply()
        }

        val carPrefs = getSharedPreferences("car_stats", Context.MODE_PRIVATE)
        if (DriveSession.selectedCarId != -1) {
            val currentCarKm = carPrefs.getFloat("car_${DriveSession.selectedCarId}", 0f) + tripKm
            carPrefs.edit().putFloat("car_${DriveSession.selectedCarId}", currentCarKm).apply()
        }

        prefs.edit()
            .putFloat("total_km", totalKm)
            .putFloat("current_trip_km", 0f)
            .putString("rank_key", distRankKey)
            .putString("style_rank_key", styleRankKey)
            .apply()

        penalties = 0
    }

    override fun onSensorChanged(e: SensorEvent?) {
        if (e == null) return

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastPenaltyTime > 2500) {
            val x = Math.abs(e.values[0])
            val z = Math.abs(e.values[2])

            if (x > 4.8f || z > 4.8f) {
                penalties++
                lastPenaltyTime = currentTime
            }
        }
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    override fun onBind(i: Intent?): IBinder? = null
}