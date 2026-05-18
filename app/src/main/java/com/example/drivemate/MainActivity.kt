package com.example.drivemate

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnStartDrive).setOnClickListener { startActivity(Intent(this, DriveActivity::class.java)) }
        findViewById<Button>(R.id.btnGarage).setOnClickListener { startActivity(Intent(this, GarageActivity::class.java)) }
        findViewById<Button>(R.id.btnClicker).setOnClickListener { startActivity(Intent(this, ClickerActivity::class.java)) }
        findViewById<Button>(R.id.btnSettings).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        val statsBtn = findViewById<Button>(R.id.btnStats)
        statsBtn?.setOnClickListener { startActivity(Intent(this, StatsActivity::class.java)) }
    }

    override fun onResume() {
        super.onResume()
        val rank = getSharedPreferences("app_stats", Context.MODE_PRIVATE).getString("rank", "Brak")
        findViewById<TextView>(R.id.mainRankText).text = "Ranga: $rank"
    }
}