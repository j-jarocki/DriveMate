package com.example.drivemate

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*

class ClickerActivity : AppCompatActivity() {

    private var money = 0
    private var carLevel = 0
    private var passiveIncomeJob: Job? = null

    // Dane modeli
    private val carNames = arrayOf("Opel Corsa", "Volkswagen Golf GTI", "Nissan 350z", "Lancer Evo")
    private val nextCarCosts = arrayOf(0, 1000, 10000, 100000)
    private val multipliers = arrayOf(1, 2, 5, 10)
    private val passiveRates = arrayOf(0, 1, 5, 20)
    private val nitroCosts = arrayOf(500, 5000, 50000, 500000)
    private val carImages = arrayOf(
        R.drawable.opel_corsa,
        R.drawable.golf_gti,
        R.drawable.nissan_350z,
        R.drawable.lancer_evo
    )

    // Stan ulepszeń
    private var engineLvl = 1
    private var suspensionLvl = 1
    private var hasNitro = false
    private var hasTurbo = false

    private lateinit var moneyText: TextView
    private lateinit var carImage: ImageView
    private lateinit var clickButton: Button
    private lateinit var nextCarButton: Button
    private lateinit var engineButton: Button
    private lateinit var suspensionButton: Button
    private lateinit var nitroButton: Button
    private lateinit var turboButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_clicker)

        moneyText = findViewById(R.id.moneyText)
        carImage = findViewById(R.id.carImage)
        clickButton = findViewById(R.id.clickButton)
        nextCarButton = findViewById(R.id.nextCarButton)
        engineButton = findViewById(R.id.engineButton)
        suspensionButton = findViewById(R.id.suspensionButton)
        nitroButton = findViewById(R.id.nitroButton)
        turboButton = findViewById(R.id.turboButton)

        loadGameState()

        clickButton.setOnClickListener {
            money += calculateClickPower()
            saveGameState()
            updateUI()
        }

        nextCarButton.setOnClickListener {
            val nextLevel = carLevel + 1
            if (money >= nextCarCosts[nextLevel]) {
                money -= nextCarCosts[nextLevel]
                carLevel = nextLevel
                resetUpgradesForNewCar()
                saveGameState()
                updateUI()
            } else {
                Toast.makeText(this, getString(R.string.not_enough_money), Toast.LENGTH_SHORT).show()
            }
        }

        engineButton.setOnClickListener {
            val cost = getEngineCost()
            if (money >= cost) {
                money -= cost
                engineLvl++
                saveGameState()
                updateUI()
            } else Toast.makeText(this, getString(R.string.not_enough_money), Toast.LENGTH_SHORT).show()
        }

        suspensionButton.setOnClickListener {
            val cost = getSuspensionCost()
            if (money >= cost) {
                money -= cost
                suspensionLvl++
                saveGameState()
                updateUI()
            } else Toast.makeText(this, getString(R.string.not_enough_money), Toast.LENGTH_SHORT).show()
        }

        nitroButton.setOnClickListener {
            val cost = nitroCosts[carLevel]
            if (money >= cost) {
                money -= cost
                hasNitro = true
                saveGameState()
                updateUI()
            } else Toast.makeText(this, getString(R.string.not_enough_money), Toast.LENGTH_SHORT).show()
        }

        turboButton.setOnClickListener {
            val cost = nitroCosts[carLevel] * 2
            if (money >= cost) {
                money -= cost
                hasTurbo = true
                saveGameState()
                updateUI()
            } else Toast.makeText(this, getString(R.string.not_enough_money), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        startPassiveIncome()
    }

    override fun onPause() {
        super.onPause()
        passiveIncomeJob?.cancel()
    }

    private fun startPassiveIncome() {
        passiveIncomeJob = lifecycleScope.launch {
            while (isActive) {
                delay(1000)
                val income = passiveRates[carLevel]
                if (income > 0) {
                    money += income
                    saveGameState()
                    withContext(Dispatchers.Main) {
                        updateUI()
                    }
                }
            }
        }
    }

    private fun calculateClickPower(): Int {
        val base = multipliers[carLevel]
        var power = base
        power += (engineLvl - 1) * base
        power += (suspensionLvl - 1) * base
        if (hasNitro) power += 5 * base
        if (hasTurbo) power += 10 * base
        return power
    }

    private fun getEngineCost() = (100 * multipliers[carLevel] * Math.pow(1.6, (engineLvl - 1).toDouble())).toInt()
    private fun getSuspensionCost() = (80 * multipliers[carLevel] * Math.pow(1.5, (suspensionLvl - 1).toDouble())).toInt()

    private fun loadGameState() {
        val prefs = getSharedPreferences("clicker_final", Context.MODE_PRIVATE)
        money = prefs.getInt("money", 0)
        carLevel = prefs.getInt("carLevel", 0)
        engineLvl = prefs.getInt("engine_lvl_$carLevel", 1)
        suspensionLvl = prefs.getInt("suspension_lvl_$carLevel", 1)
        hasNitro = prefs.getBoolean("nitro_$carLevel", false)
        hasTurbo = prefs.getBoolean("turbo_$carLevel", false)
        updateUI()
    }

    private fun saveGameState() {
        getSharedPreferences("clicker_final", Context.MODE_PRIVATE).edit().apply {
            putInt("money", money)
            putInt("carLevel", carLevel)
            putInt("engine_lvl_$carLevel", engineLvl)
            putInt("suspension_lvl_$carLevel", suspensionLvl)
            putBoolean("nitro_$carLevel", hasNitro)
            putBoolean("turbo_$carLevel", hasTurbo)
            apply()
        }
    }

    private fun resetUpgradesForNewCar() {
        engineLvl = 1
        suspensionLvl = 1
        hasNitro = false
        hasTurbo = false
    }

    private fun updateUI() {
        val passiveRate = passiveRates[carLevel]
        val moneyBase = getString(R.string.money_text, money)
        moneyText.text = if (passiveRate > 0) "$moneyBase\n" + getString(R.string.passive_income, passiveRate) else moneyBase

        carImage.setImageResource(carImages[carLevel])
        clickButton.text = "${getString(R.string.btn_click)} (+${calculateClickPower()} $)"

        if (carLevel < carNames.size - 1) {
            val next = carLevel + 1
            nextCarButton.text = "${getString(R.string.buy)} ${carNames[next]} (${nextCarCosts[next]} $)"
            nextCarButton.visibility = View.VISIBLE
        } else {
            nextCarButton.visibility = View.GONE
        }

        engineButton.text = getString(R.string.engine_lvl, engineLvl, getEngineCost())
        suspensionButton.text = getString(R.string.suspension_lvl, suspensionLvl, getSuspensionCost())

        nitroButton.text = if (hasNitro) "NITRO ON" else getString(R.string.buy_nitro, nitroCosts[carLevel])
        nitroButton.isEnabled = !hasNitro

        val turboCost = nitroCosts[carLevel] * 2
        turboButton.text = if (hasTurbo) "TURBO ON" else getString(R.string.buy_turbo, turboCost)
        turboButton.isEnabled = !hasTurbo
    }
}