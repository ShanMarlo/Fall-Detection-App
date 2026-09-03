package com.example.falldetectionapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.telephony.SmsManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import kotlin.math.sqrt
import kotlin.math.abs

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private var freeFallDetected = false
    private var impactDetected = false
    private var impactTime: Long = 0

    private val FREE_FALL_THRESHOLD = 3
    private val IMPACT_THRESHOLD = 25
    private val STILL_THRESHOLD = 1.5
    private val VERIFY_TIME = 4000

    private val phoneNumber = "7845312676" // change this

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        accelerometer?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val magnitude = sqrt((x * x + y * y + z * z).toDouble())

        val currentTime = System.currentTimeMillis()

        // Step 1: Free fall
        if (magnitude < FREE_FALL_THRESHOLD) {

            freeFallDetected = true
        }

        // Step 2: Impact
        if (freeFallDetected && magnitude > IMPACT_THRESHOLD) {

            impactDetected = true
            impactTime = currentTime

            Toast.makeText(this, "Fall impact detected", Toast.LENGTH_SHORT).show()
        }

        // Step 3: Inactivity
        if (impactDetected) {

            val movement = abs(magnitude - 9.8)

            // If phone stays mostly still
            if (movement < 2) {

                if (currentTime - impactTime > VERIFY_TIME) {

                    sendEmergencySMS()

                    Toast.makeText(this, "Emergency confirmed!", Toast.LENGTH_LONG).show()

                    freeFallDetected = false
                    impactDetected = false
                }

            } else {

                // Allow movement for first 1 second after impact
                if (currentTime - impactTime > 1000) {

                    Toast.makeText(this, "Movement detected. False alarm.", Toast.LENGTH_SHORT).show()

                    freeFallDetected = false
                    impactDetected = false
                }
            }
        } }

    private fun sendEmergencySMS() {

        val location = getLocation()

        val message = if (location != null) {
            "Emergency! Fall detected.\nLocation: https://maps.google.com/?q=${location.latitude},${location.longitude}"
        } else {
            "Emergency! Fall detected."
        }

        try {

            val smsManager = SmsManager.getDefault()

            smsManager.sendTextMessage(phoneNumber, null, message, null, null)

            Toast.makeText(this, "SMS sent to caretaker", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {

            Toast.makeText(this, "SMS failed", Toast.LENGTH_LONG).show()
        }
    }

    private fun getLocation(): Location? {

        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        var location: Location? =
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)

        if (location == null) {
            location =
                locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        }

        return location
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onResume() {
        super.onResume()

        accelerometer?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }
}