package com.emergency.meshgps

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var meshManager: MeshNetworkManager

    private lateinit var tvStatus: TextView
    private lateinit var mapView: MapView
    private lateinit var btnSos: Button

    private var myMarker: Marker? = null
    private var sosMarker: Marker? = null

    private val PERMISSIONS_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        tvStatus = TextView(this).apply {
            text = "Status: Memeriksa Izin Perangkat..."
            textSize = 15f
            setTextColor(Color.DKGRAY)
            setPadding(0, 0, 0, 16)
        }

        mapView = MapView(this).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        btnSos = Button(this).apply {
            text = "KIRIM SOS (SINYAL DARURAT)"
            setBackgroundColor(Color.RED)
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(16, 24, 16, 24)
            setOnClickListener {
                triggerSosPayload()
            }
        }

        layout.addView(tvStatus)
        layout.addView(mapView)
        layout.addView(btnSos)
        setContentView(layout)

        meshManager = MeshNetworkManager(this) { sender, lat, lng ->
            showSosOnMap(sender, lat, lng)
        }

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
        tvStatus.text = "Status: Mesh Network Standby & Mencari Node..."
        tvStatus.setTextColor(Color.BLUE)

        fusedLocationClient.lastLocation.addOnSuccessListener { loc: Location? ->
            if (loc != null) {
                val myPoint = GeoPoint(loc.latitude, loc.longitude)
                mapView.controller.setZoom(17.0)
                mapView.controller.setCenter(myPoint)

                if (myMarker == null) {
                    myMarker = Marker(mapView)
                    myMarker?.title = "Lokasi Saya"
                    mapView.overlays.add(myMarker)
                }
                myMarker?.position = myPoint
                mapView.invalidate()
            }
        }

        meshManager.startAdvertising("Node_${Build.MODEL}")
        meshManager.startDiscovery()
    }

    @SuppressLint("MissingPermission")
    private fun triggerSosPayload() {
        btnSos.isEnabled = false
        btnSos.text = "MENGIRIM SINYAL..."
        tvStatus.text = "Status: MENYEBARKAN SINYAL SOS!"
        tvStatus.setTextColor(Color.RED)

        fusedLocationClient.lastLocation.addOnSuccessListener { loc: Location? ->
            btnSos.isEnabled = true
            btnSos.text = "KIRIM SOS (SINYAL DARURAT)"

            if (loc != null) {
                val lat = loc.latitude
                val long = loc.longitude

                meshManager.broadcastSosSignal(lat, long, "Node_${Build.MODEL}")

                val myPoint = GeoPoint(lat, long)
                mapView.controller.animateTo(myPoint)
                Toast.makeText(this, "Memicu SOS pada Lat: $lat, Lng: $long", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "⚠️ Gagal mengambil lokasi GPS. Pastikan GPS aktif!", Toast.LENGTH_LONG).show()
            }
        }.addOnFailureListener { e ->
            btnSos.isEnabled = true
            btnSos.text = "KIRIM SOS (SINYAL DARURAT)"
            Toast.makeText(this, "Error GPS: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showSosOnMap(sender: String, lat: Double, lng: Double) {
        runOnUiThread {
            val sosPoint = GeoPoint(lat, lng)

            if (sosMarker == null) {
                sosMarker = Marker(mapView)
                mapView.overlays.add(sosMarker)
            }

            sosMarker?.position = sosPoint
            sosMarker?.title = "🚨 BAHAYA SOS: $sender"
            sosMarker?.snippet = "Lat: $lat, Lng: $lng"
            sosMarker?.showInfoWindow()

            mapView.controller.animateTo(sosPoint)
            mapView.controller.setZoom(18.0)
            mapView.invalidate()

            tvStatus.text = "🚨 SINYAL SOS DITERIMA DARI: $sender"
            tvStatus.setTextColor(Color.RED)
            Toast.makeText(this, "🚨 KORBAN TERMUDI DI PETA!", Toast.LENGTH_LONG).show()
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

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
}
