package com.emergency.meshgps

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var meshManager: MeshNetworkManager

    private lateinit var tvStatus: TextView
    private lateinit var tvLocation: TextView
    private lateinit var btnSos: Button

    private val PERMISSIONS_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        meshManager = MeshNetworkManager(this)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 64, 64, 64)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        tvStatus = TextView(this).apply {
            text = "Status: Mesh Network Standby"
            textSize = 18f
            setTextColor(Color.DKGRAY)
            setPadding(0, 0, 0, 24)
        }

        tvLocation = TextView(this).apply {
            text = "Lokasi GPS: Mengambil data..."
            textSize = 15f
            setPadding(0, 0, 0, 48)
        }

        btnSos = Button(this).apply {
            text = "KIRIM SOS (SINYAL DARURAT)"
            setBackgroundColor(Color.RED)
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(32, 32, 32, 32)
            setOnClickListener {
                triggerSosPayload()
            }
        }

        layout.addView(tvStatus)
        layout.addView(tvLocation)
        layout.addView(btnSos)
        setContentView(layout)

        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSIONS_REQUEST_CODE)
        } else {
            initServices()
        }
    }

    @SuppressLint("MissingPermission")
    private fun initServices() {
        fusedLocationClient.lastLocation.addOnSuccessListener { loc: Location? ->
            if (loc != null) {
                tvLocation.text = "Lokasi GPS:\nLat: ${loc.latitude}\nLong: ${loc.longitude}"
            } else {
                tvLocation.text = "Lokasi GPS: Sinyal tidak ditemukan"
            }
        }

        meshManager.startAdvertising("SOS_Node")
        meshManager.startDiscovery()
    }

    @SuppressLint("MissingPermission")
    private fun triggerSosPayload() {
        fusedLocationClient.lastLocation.addOnSuccessListener { loc: Location? ->
            val lat = loc?.latitude ?: 0.0
            val long = loc?.longitude ?: 0.0
            
            tvStatus.text = "Status: MENYEBARKAN SINYAL SOS!"
            tvStatus.setTextColor(Color.RED)

            meshManager.broadcastSosSignal(lat, long, "SOS_Node")
            Toast.makeText(this, "Paket SOS Disiarkan via Mesh Network!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            initServices()
        }
    }
}
