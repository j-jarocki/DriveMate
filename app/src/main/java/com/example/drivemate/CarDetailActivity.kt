package com.example.drivemate

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.drivemate.data.CarDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class CarDetailActivity : AppCompatActivity() {

    private lateinit var db: CarDatabase
    private var carId: Int = 0
    private lateinit var car: Car

    private lateinit var nameEdit: EditText
    private lateinit var mileageEdit: EditText
    private lateinit var oilChangeMileageEdit: EditText
    private lateinit var oilIntervalEdit: EditText
    private lateinit var inspectionIntervalEdit: EditText
    private lateinit var lastInspectionBtn: Button
    private lateinit var updateOilBtn: Button
    private lateinit var updateInspectionBtn: Button
    private lateinit var saveBtn: Button

    private var selectedInspectionDate: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_car_detail)

        db = CarDatabase.getDatabase(this)
        carId = intent.getIntExtra("car_id", 0)

        nameEdit = findViewById(R.id.carNameEdit)
        mileageEdit = findViewById(R.id.carMileageEdit)
        oilChangeMileageEdit = findViewById(R.id.oilChangeMileageEdit)
        oilIntervalEdit = findViewById(R.id.oilIntervalEdit)
        inspectionIntervalEdit = findViewById(R.id.inspectionIntervalEdit)
        lastInspectionBtn = findViewById(R.id.lastInspectionBtn)
        updateOilBtn = findViewById(R.id.updateOilBtn)
        updateInspectionBtn = findViewById(R.id.updateInspectionBtn)
        saveBtn = findViewById(R.id.saveCarBtn)

        loadCar()

        lastInspectionBtn.setOnClickListener {
            val cal = Calendar.getInstance()
            val dpd = DatePickerDialog(this, { _, year, month, dayOfMonth ->
                cal.set(year, month, dayOfMonth, 0, 0, 0)
                cal.set(Calendar.MILLISECOND, 0)
                selectedInspectionDate = cal.timeInMillis
                val dateStr = String.format("%02d-%02d-%04d", dayOfMonth, month+1, year)
                lastInspectionBtn.text = getString(R.string.last_inspection_date, dateStr)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
            dpd.show()
        }

        updateOilBtn.setOnClickListener {
            val currentMileage = mileageEdit.text.toString().toDoubleOrNull()
            if (currentMileage != null) {
                oilChangeMileageEdit.setText(currentMileage.toString())
                Toast.makeText(this, getString(R.string.oil_mileage_updated), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.enter_current_mileage), Toast.LENGTH_SHORT).show()
            }
        }

        updateInspectionBtn.setOnClickListener {
            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            selectedInspectionDate = today
            val dateStr = String.format("%02d-%02d-%04d",
                Calendar.getInstance().get(Calendar.DAY_OF_MONTH),
                Calendar.getInstance().get(Calendar.MONTH)+1,
                Calendar.getInstance().get(Calendar.YEAR))
            lastInspectionBtn.text = getString(R.string.last_inspection_date, dateStr)
            Toast.makeText(this, getString(R.string.inspection_date_updated), Toast.LENGTH_SHORT).show()
        }

        saveBtn.setOnClickListener {
            saveCar()
        }
    }

    private fun loadCar() {
        lifecycleScope.launch {
            val cars = withContext(Dispatchers.IO) { db.carDao().getAllCars() }
            car = cars.first { it.id == carId }

            nameEdit.setText(car.name)
            mileageEdit.setText(car.mileage.toString())
            oilChangeMileageEdit.setText(car.oilChangeMileage.toString())
            oilIntervalEdit.setText(car.oilIntervalKm.toString())
            inspectionIntervalEdit.setText(car.inspectionIntervalMonths.toString())

            val cal = Calendar.getInstance().apply { timeInMillis = car.lastInspectionDate }
            val dateStr = String.format("%02d-%02d-%04d",
                cal.get(Calendar.DAY_OF_MONTH),
                cal.get(Calendar.MONTH)+1,
                cal.get(Calendar.YEAR))
            lastInspectionBtn.text = getString(R.string.last_inspection_date, dateStr)
            selectedInspectionDate = car.lastInspectionDate
        }
    }

    private fun saveCar() {
        val name = nameEdit.text.toString()
        val mileage = mileageEdit.text.toString().toDoubleOrNull()
        val oilChange = oilChangeMileageEdit.text.toString().toDoubleOrNull()
        val oilInterval = oilIntervalEdit.text.toString().toIntOrNull()
        val inspectionInterval = inspectionIntervalEdit.text.toString().toIntOrNull()
        val inspectionDate = selectedInspectionDate

        if (name.isBlank() || mileage == null || oilChange == null || oilInterval == null || inspectionInterval == null || inspectionDate == null) {
            Toast.makeText(this, getString(R.string.fill_all_fields), Toast.LENGTH_SHORT).show()
            return
        }

        val updatedCar = car.copy(
            name = name,
            mileage = mileage,
            oilChangeMileage = oilChange,
            oilIntervalKm = oilInterval,
            inspectionIntervalMonths = inspectionInterval,
            lastInspectionDate = inspectionDate,
            lastOilNotificationDate = null,
            lastInspectionNotificationDate = null
        )

        lifecycleScope.launch(Dispatchers.IO) {
            db.carDao().update(updatedCar)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@CarDetailActivity, getString(R.string.saved), Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}