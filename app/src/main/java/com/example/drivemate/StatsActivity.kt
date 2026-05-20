package com.example.drivemate

import android.content.Context
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.drivemate.data.CarDatabase
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StatsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)

        val prefs = getSharedPreferences("app_stats", Context.MODE_PRIVATE)

        val totalKm = prefs.getFloat("total_km", 0f)
        val rankKey = prefs.getString("rank_key", "stats_rank_none") ?: "stats_rank_none"
        val styleRank = prefs.getString("style_rank", "Brak")
        val maxSpeed = prefs.getFloat("top_speed", 0f)

        val resId = resources.getIdentifier(rankKey, "string", packageName)
        val resolvedRank = if (resId != 0) getString(resId) else getString(R.string.stats_rank_none)

        findViewById<TextView>(R.id.txtTotalKm)?.text = getString(R.string.stats_total_km, totalKm)

        if (totalKm >= 5.0f) {
            findViewById<TextView>(R.id.txtRank)?.text = getString(R.string.driver_rank_label, resolvedRank)
            findViewById<TextView>(R.id.txtStyleRank)?.text = getString(R.string.stats_style_rank, styleRank)
        } else {
            findViewById<TextView>(R.id.txtRank)?.text = getString(R.string.driver_rank_label, getString(R.string.stats_rank_none))
            findViewById<TextView>(R.id.txtStyleRank)?.text = getString(R.string.stats_style_rank, getString(R.string.stats_rank_none))
        }

        findViewById<TextView>(R.id.txtMaxSpeed)?.text = getString(R.string.stats_max_speed, maxSpeed)

        val progressText = when {
            totalKm < 5.0f -> getString(R.string.stats_progress_lock, 5.0f - totalKm)
            totalKm < 51.0f -> getString(R.string.stats_progress_unlocked, totalKm, 51.0f, getString(R.string.rank_niedzielny))
            totalKm < 101.0f -> getString(R.string.stats_progress_unlocked, totalKm, 101.0f, getString(R.string.rank_baby_driver))
            totalKm < 501.0f -> getString(R.string.stats_progress_unlocked, totalKm, 501.0f, getString(R.string.rank_kubica))
            else -> getString(R.string.stats_max_level)
        }
        findViewById<TextView>(R.id.txtProgress)?.text = progressText

        val carPrefs = getSharedPreferences("car_stats", Context.MODE_PRIVATE)
        val db = CarDatabase.getDatabase(this)

        lifecycleScope.launch {
            val carsList = withContext(Dispatchers.IO) { db.carDao().getAllCars() }
            var bestCarName = getString(R.string.stats_no_data)
            var maxCarKm = -1f

            for (car in carsList) {
                val km = carPrefs.getFloat("car_${car.id}", 0f)
                if (km > maxCarKm && km > 0f) {
                    maxCarKm = km
                    bestCarName = car.name
                }
            }
            findViewById<TextView>(R.id.txtBestCar)?.text = getString(R.string.stats_popular_car, bestCarName)
        }
    }
}