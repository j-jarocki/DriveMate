package com.example.drivemate

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.drivemate.data.CarDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class DriveActivity : AppCompatActivity() {

    private lateinit var db: CarDatabase
    private lateinit var carSpinner: Spinner
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var distanceText: TextView
    private lateinit var rankText: TextView
    private lateinit var kmLeftText: TextView
    private var carsList: List<Car> = emptyList()

    private val distanceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateDistanceText()
            updateTelematicsUI()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drive)

        db = CarDatabase.getDatabase(this)
        carSpinner = findViewById(R.id.carSpinner)
        startButton = findViewById(R.id.startDriveButton)
        stopButton = findViewById(R.id.stopDriveButton)
        distanceText = findViewById(R.id.distanceTextView)
        rankText = findViewById(R.id.rankTextView)
        kmLeftText = findViewById(R.id.kmLeftTextView)

        loadCars()

        if (DriveSession.isDriving) {
            startButton.isEnabled = false
            stopButton.isEnabled = true
            carSpinner.isEnabled = false
        } else {
            stopButton.isEnabled = false
        }

        updateDistanceText()
        updateTelematicsUI()

        startButton.setOnClickListener {
            if (checkPermissions()) {
                val selectedCar = carsList.getOrNull(carSpinner.selectedItemPosition)
                if (selectedCar != null) {
                    startDriving(selectedCar)
                } else {
                    Toast.makeText(this, getString(R.string.choose_car), Toast.LENGTH_SHORT).show()
                }
            } else {
                requestPermissions()
            }
        }

        stopButton.setOnClickListener { stopDriving() }
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this).registerReceiver(distanceReceiver, IntentFilter("UPDATE_DISTANCE"))
        updateDistanceText()
        updateTelematicsUI()
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(distanceReceiver)
    }

    private fun loadCars() {
        lifecycleScope.launch {
            carsList = withContext(Dispatchers.IO) { db.carDao().getAllCars() }
            val names = carsList.map { it.name }
            val adapter = ArrayAdapter(this@DriveActivity, android.R.layout.simple_spinner_item, names)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            carSpinner.adapter = adapter

            if (DriveSession.isDriving && DriveSession.selectedCarId != -1) {
                val index = carsList.indexOfFirst { it.id == DriveSession.selectedCarId }
                if (index != -1) carSpinner.setSelection(index)
            }
        }
    }

    private fun updateDistanceText() {
        distanceText.text = getString(R.string.distance_driven, DriveSession.totalDistance)
    }

    private fun updateTelematicsUI() {
        val prefs = getSharedPreferences("app_stats", Context.MODE_PRIVATE)
        val totalKm = prefs.getFloat("total_km", 0f) + prefs.getFloat("current_trip_km", 0f)
        val rankKey = prefs.getString("rank_key", "stats_rank_none") ?: "stats_rank_none"

        val resId = resources.getIdentifier(rankKey, "string", packageName)
        val resolvedRank = if (resId != 0) getString(resId) else getString(R.string.stats_rank_none)

        if (totalKm >= 5.0f) {
            rankText.text = getString(R.string.driver_rank_label, resolvedRank)
        } else {
            rankText.text = getString(R.string.driver_rank_label_empty)
        }

        val progressText = when {
            totalKm < 5.0f -> getString(R.string.stats_progress_lock, 5.0f - totalKm)
            totalKm < 51.0f -> getString(R.string.stats_progress_unlocked, totalKm, 51.0f, getString(R.string.rank_niedzielny))
            totalKm < 101.0f -> getString(R.string.stats_progress_unlocked, totalKm, 101.0f, getString(R.string.rank_baby_driver))
            totalKm < 501.0f -> getString(R.string.stats_progress_unlocked, totalKm, 501.0f, getString(R.string.rank_kubica))
            else -> getString(R.string.stats_max_level)
        }
        kmLeftText.text = progressText
    }

    private fun startDriving(car: Car) {
        val prefs = getSharedPreferences("app_stats", Context.MODE_PRIVATE)
        prefs.edit().putFloat("current_trip_km", 0f).apply()

        DriveSession.isDriving = true
        DriveSession.totalDistance = 0.0
        DriveSession.selectedCarId = car.id

        startButton.isEnabled = false
        stopButton.isEnabled = true
        carSpinner.isEnabled = false
        updateDistanceText()

        val intent = Intent(this, DriveService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopDriving() {
        DriveSession.isDriving = false

        val intent = Intent(this, DriveService::class.java).apply { action = "STOP_DRIVE" }
        startService(intent)

        val selectedCar = carsList.firstOrNull { it.id == DriveSession.selectedCarId }
        selectedCar?.let { car ->
            car.mileage += DriveSession.totalDistance
            lifecycleScope.launch(Dispatchers.IO) {
                db.carDao().update(car)
                withContext(Dispatchers.Main) {
                    loadCars()
                }
            }
        }

        startButton.isEnabled = true
        stopButton.isEnabled = false
        carSpinner.isEnabled = true
        Toast.makeText(this, getString(R.string.driven_total, DriveSession.totalDistance), Toast.LENGTH_LONG).show()
        DriveSession.totalDistance = 0.0
        updateDistanceText()
        updateTelematicsUI()
    }

    private fun checkPermissions(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val postNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
        return fineLocation && postNotif
    }

    private fun requestPermissions() {
        val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        ActivityCompat.requestPermissions(this, perms.toTypedArray(), 100)
    }
}