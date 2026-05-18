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
import com.example.drivemate.NotificationHelper.createNotificationChannel
import com.example.drivemate.data.CarDatabase
import com.google.android.gms.location.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.max

object DriveSession {
    var isDriving = false
    var totalDistance = 0.0
    var selectedCarId: Int = -1
}

class DriveService : Service(), SensorEventListener {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var lastLocation: Location? = null

    // Czujniki i zmienne systemu oceniania
    private lateinit var sensorManager: SensorManager
    private var linearAccelerometer: Sensor? = null
    private var rankDistance = 0.0f
    private var penalties = 0

    // Czas blokady (debouncing), by nie naliczać kar co milisekundę
    private var lastBrakingTime = 0L
    private var lastAccelTime = 0L
    private var lastTurnTime = 0L

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        linearAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

        val prefs = getSharedPreferences("driving_telematics", Context.MODE_PRIVATE)
        rankDistance = prefs.getFloat("rank_distance", 0.0f)
        penalties = prefs.getInt("rank_penalties", 0)

        linearAccelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        createNotificationChannel(this)
        createServiceNotificationChannel()
    }

    private fun createServiceNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "drive_channel",
                "Drive Tracking",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_DRIVE") {
            stopLocationUpdates()
            sensorManager.unregisterListener(this)

            saveDistanceToDatabase()

            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val notificationIntent = Intent(this, DriveActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, "drive_channel")
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.driving_in_progress))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(3, notification)
        startLocationUpdates()

        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateIntervalMillis(2000)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (location in result.locations) {
                    if (lastLocation != null) {
                        val distance = lastLocation!!.distanceTo(location) / 1000.0
                        DriveSession.totalDistance += distance
                        rankDistance += distance.toFloat()

                        checkAndUpdateRank()
                        saveTelematicsState()

                        val broadcastIntent = Intent("UPDATE_DISTANCE")
                        LocalBroadcastManager.getInstance(this@DriveService).sendBroadcast(broadcastIntent)
                    }
                    lastLocation = location
                }
            }
        }
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    private fun checkAndUpdateRank() {
        if (rankDistance >= 10.0f) {
            val score = max(0, 100 - penalties)
            val finalGrade = when {
                score >= 95 -> "A+"
                score >= 90 -> "A"
                score >= 85 -> "B+"
                score >= 80 -> "B"
                score >= 70 -> "C"
                score >= 60 -> "D"
                else -> "F"
            }
            getSharedPreferences("driving_telematics", Context.MODE_PRIVATE).edit()
                .putString("driver_rank", finalGrade)
                .apply()

            rankDistance = 0.0f
            penalties = 0
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LINEAR_ACCELERATION) {
            val x = event.values[0]
            val y = event.values[1]
            val currentTime = System.currentTimeMillis()

            if (y < -3.8f && currentTime - lastBrakingTime > 2500) {
                penalties += 5
                lastBrakingTime = currentTime
                saveTelematicsState()
            }
            else if (y > 3.2f && currentTime - lastAccelTime > 2500) {
                penalties += 3
                lastAccelTime = currentTime
                saveTelematicsState()
            }
            if (Math.abs(x) > 4.2f && currentTime - lastTurnTime > 2500) {
                penalties += 4
                lastTurnTime = currentTime
                saveTelematicsState()
            }
        }
    }

    private fun saveTelematicsState() {
        getSharedPreferences("driving_telematics", Context.MODE_PRIVATE).edit().apply {
            putFloat("rank_distance", rankDistance)
            putInt("rank_penalties", penalties)
            apply()
        }
    }

    private fun saveDistanceToDatabase() {
        val carId = DriveSession.selectedCarId
        val drivenDistance = DriveSession.totalDistance

        if (carId != -1 && drivenDistance > 0.0) {
            CoroutineScope(Dispatchers.IO).launch {
                val database = CarDatabase.getDatabase(applicationContext)
                val car = database.carDao().getCarById(carId)
                if (car != null) {
                    car.mileage = car.mileage + drivenDistance
                    database.carDao().update(car)
                }
            }
        }
    }

    private fun stopLocationUpdates() {
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onBind(intent: Intent?): IBinder? = null
}