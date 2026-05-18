package com.example.drivemate

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cars")
data class Car(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    var mileage: Double,
    val lastInspectionDate: Long,
    val inspectionIntervalMonths: Int,
    var oilChangeMileage: Double,
    var oilIntervalKm: Int,
    var lastOilNotificationDate: Long? = null,
    var lastInspectionNotificationDate: Long? = null,
    var engineLevel: Int = 1,
    var suspensionLevel: Int = 1,
    var hasNitro: Boolean = false
)