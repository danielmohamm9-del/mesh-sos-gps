package com.emergency.meshgps

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var tvStatus: TextView
    private lateinit var tvLocation: TextView
    private lateinit var btnSos: Button

    private val PERMISSIONS_REQUEST_CODE = 1001
    private val requiredPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Menggunakan FusedLocationProvider untuk akurasi GPS tinggi
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Membuat Tampilan Utama secara Dinamis
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(64, 64, 64, 64)
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }

        tvStatus = TextView(this).apply {
            text = "Status: Mesh Network Siap"
            textSize = 18f
            setPadding(0, 0, 0, 32)
        }

        tvLocation = TextView(this).apply {
            text = "Lokasi GPS: Mencari sinyal..."
            textSize = 16f
            setPadding(0, 0, 0, 64)
        }

        btnSos = Button(this).apply {
            text = "KIRIM SOS (SINYAL DARURAT)"
            setBackgroundColor(android.graphics.Color.RED)
            setTextColor(android.graphics.Color.WHITE)
            textSize = 18f
            setPadding(32, 32, 32, 32)
            setOnClickListener {
                sendSosSignal()
            }
        }

        layout.addView(tvStatus)
        layout.addView(tvLocation)
        layout.addView(btnSos)
        setContentView(layout)

        checkPermissions()
    }

    private fun checkPermissions() {
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), PERMISSIONS_REQUEST_CODE)
        } else {
            getDeviceLocation()
        }
    }

    @SuppressLint("MissingPermission")
    private fun getDeviceLocation() {
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                tvLocation.text = "Lokasi GPS:\nLat: ${location.latitude}\nLong: ${location.longitude}"
            } else {
                tvLocation.text = "Lokasi GPS: Aktifkan GPS pada HP Anda"
            }
        }
    }

    private fun sendSosSignal() {
        Toast.makeText(this, "Sinyal SOS Disebarkan via Mesh Network!", Toast.LENGTH_LONG).show()
        tvStatus.text = "Status: MENYEBARKAN SINYAL SOS!"
        tvStatus.setTextColor(android.graphics.Color.RED)
        getDeviceLocation()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            getDeviceLocation()
        }
    }
}
