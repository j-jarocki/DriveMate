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
        val prefs = getSharedPreferences("app_stats", Context.MODE_PRIVATE)
        val totalKm = prefs.getFloat("total_km", 0f)
        val rankKey = prefs.getString("rank_key", "stats_rank_none") ?: "stats_rank_none"

        val resId = resources.getIdentifier(rankKey, "string", packageName)
        val resolvedRank = if (resId != 0) getString(resId) else getString(R.string.stats_rank_none)

        if (totalKm >= 5.0f) {
            findViewById<TextView>(R.id.mainRankText).text = getString(R.string.main_rank_prefix, resolvedRank)
        } else {
            findViewById<TextView>(R.id.mainRankText).text = getString(R.string.main_rank_lock, 5.0f - totalKm)
        }
    }
}