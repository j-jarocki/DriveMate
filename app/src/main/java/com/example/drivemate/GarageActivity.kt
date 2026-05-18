package com.example.drivemate
import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.drivemate.data.CarDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class GarageActivity : AppCompatActivity() {

    private lateinit var db: CarDatabase
    private lateinit var carListView: ListView
    private val cars = mutableListOf<Car>()
    private lateinit var adapter: ArrayAdapter<String>

    private var selectedInspectionDate: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_garage)

        db = CarDatabase.getDatabase(this)
        NotificationHelper.createNotificationChannel(this)

        val carNameEdit = findViewById<EditText>(R.id.carName)
        val carMileageEdit = findViewById<EditText>(R.id.carMileage)
        val oilChangeMileageEdit = findViewById<EditText>(R.id.oilChangeMileage)
        val oilIntervalEdit = findViewById<EditText>(R.id.oilIntervalKm)
        val inspectionIntervalEdit = findViewById<EditText>(R.id.inspectionIntervalMonths)
        val lastInspectionBtn = findViewById<Button>(R.id.lastInspectionBtn)
        carListView = findViewById(R.id.carListView)
        val addButton = findViewById<Button>(R.id.addCarButton)

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

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        carListView.adapter = adapter

        addButton.setOnClickListener {
            val name = carNameEdit.text.toString()
            val mileage = carMileageEdit.text.toString().toDoubleOrNull()
            val oilChange = oilChangeMileageEdit.text.toString().toDoubleOrNull()
            val oilInterval = oilIntervalEdit.text.toString().toIntOrNull()
            val inspectionInterval = inspectionIntervalEdit.text.toString().toIntOrNull()
            val inspectionDate = selectedInspectionDate

            if (name.isBlank() || mileage == null || oilChange == null || oilInterval == null || inspectionInterval == null || inspectionDate == null) {
                Toast.makeText(this, getString(R.string.fill_all_fields), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val car = Car(
                name = name,
                mileage = mileage,
                oilChangeMileage = oilChange,
                oilIntervalKm = oilInterval,
                lastInspectionDate = inspectionDate,
                inspectionIntervalMonths = inspectionInterval
            )

            lifecycleScope.launch(Dispatchers.IO) {
                db.carDao().insert(car)
                loadCars()
            }

            carNameEdit.text.clear()
            carMileageEdit.text.clear()
            oilChangeMileageEdit.text.clear()
            oilIntervalEdit.text.clear()
            inspectionIntervalEdit.text.clear()
            lastInspectionBtn.text = getString(R.string.choose_inspection_date)
            selectedInspectionDate = null
        }

        carListView.setOnItemClickListener { _, _, position, _ ->
            val car = cars[position]
            val intent = Intent(this, CarDetailActivity::class.java)
            intent.putExtra("car_id", car.id)
            startActivity(intent)
        }

        carListView.setOnItemLongClickListener { _, _, position, _ ->
            val car = cars[position]
            lifecycleScope.launch(Dispatchers.IO) {
                db.carDao().delete(car)
                loadCars()
            }
            true
        }

        loadCars()
    }

    override fun onResume() {
        super.onResume()
        loadCars()
    }

    private fun loadCars() {
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) { db.carDao().getAllCars() }
            cars.clear()
            cars.addAll(list)
            val displayList = cars.map { car ->
                val sdf = java.text.SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                val dateStr = sdf.format(Date(car.lastInspectionDate))
                getString(R.string.car_list_item, car.name, dateStr)
            }
            adapter.clear()
            adapter.addAll(displayList)
            adapter.notifyDataSetChanged()
        }
    }
}