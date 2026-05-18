package com.example.drivemate

import android.content.Context
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class StatsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)

        val prefs = getSharedPreferences("app_stats", Context.MODE_PRIVATE)

        val totalKm = prefs.getFloat("total_km", 0f)
        val rank = prefs.getString("rank", "Brak")
        val maxSpeed = DriveSession.maxSpeed * 3.6f

        findViewById<TextView>(R.id.txtTotalKm)?.text = "Łącznie km: %.2f".format(totalKm)
        findViewById<TextView>(R.id.txtRank)?.text = "Ranga: $rank"
        findViewById<TextView>(R.id.txtMaxSpeed)?.text = "V-Max: %.1f km/h".format(maxSpeed)
    }
}