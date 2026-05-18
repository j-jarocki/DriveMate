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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
                        if (loc.speed > DriveSession.maxSpeed) DriveSession.maxSpeed = loc.speed
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
        val total = prefs.getFloat("total_km", 0f) + dist
        prefs.edit().putFloat("total_km", total).apply()
    }

    private fun saveFinalStats() {
        val prefs = getSharedPreferences("app_stats", Context.MODE_PRIVATE)
        val currentPenalties = prefs.getInt("total_penalties", 0) + penalties
        val totalKm = prefs.getFloat("total_km", 0f)
        val rank = if (totalKm >= 5.0f) {
            val score = currentPenalties / totalKm
            when {
                score < 0.2 -> "Mistrz Kierownicy"
                score < 0.5 -> "Dobry Kierowca"
                else -> "Początkujący"
            }
        } else "Brak (przejedź 5km)"
        prefs.edit().putInt("total_penalties", currentPenalties).putString("rank", rank).apply()
    }

    override fun onSensorChanged(e: SensorEvent?) {
        if (Math.abs(e?.values?.get(1) ?: 0f) > 5f) penalties++
    }
    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    override fun onBind(i: Intent?): IBinder? = null
}