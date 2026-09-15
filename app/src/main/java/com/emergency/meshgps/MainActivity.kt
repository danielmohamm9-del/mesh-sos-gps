package com.emergency.meshgps

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.preference.PreferenceManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.emergency.meshgps.databinding.ActivityMainBinding
import com.google.android.gms.location.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var meshManager: MeshNetworkManager
    
    private val myUserId = UUID.randomUUID().toString().substring(0, 5)
    private var currentLocation: UserLocation? = null
    private val activeMarkers = mutableMapOf<String, Marker>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupMap()
        checkAndRequestPermissions()

        meshManager = MeshNetworkManager(this) { receivedLoc ->
            runOnUiThread {
                updateOrAddMapMarker(receivedLoc)
            }
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        startGPSUpdates()

        binding.btnBroadcast.setOnClickListener {
            currentLocation?.let { loc ->
                meshManager.broadcastLocation(loc)
                Toast.makeText(this, "Lokasi berhasil dipancarkan!", Toast.LENGTH_SHORT).show()
            } ?: run {
                Toast.makeText(this@MainActivity, "GPS Belum mengunci koordinat!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupMap() {
        binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
        binding.mapView.setBuiltInZoomControls(true)
        binding.mapView.setMultiTouchControls(true)
        binding.mapView.controller.setZoom(17.0)
    }

    private fun startGPSUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateIntervalMillis(2000)
            .build()

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    val loc = locationResult.lastLocation ?: return
                    currentLocation = UserLocation(
                        userId = myUserId,
                        name = "Korban-$myUserId",
                        latitude = loc.latitude,
                        longitude = loc.longitude
                    )

                    val myPoint = GeoPoint(loc.latitude, loc.longitude)
                    binding.mapView.controller.setCenter(myPoint)
                    updateOrAddMapMarker(currentLocation!!)
                    
                    binding.tvStatus.text = "GPS Terkunci: ${loc.latitude}, ${loc.longitude}"
                }
            }, Looper.getMainLooper())
        }
    }

    private fun updateOrAddMapMarker(userLoc: UserLocation) {
        val point = GeoPoint(userLoc.latitude, userLoc.longitude)

        if (activeMarkers.containsKey(userLoc.userId)) {
            activeMarkers[userLoc.userId]?.position = point
        } else {
            val marker = Marker(binding.mapView)
            marker.position = point
            marker.title = "${userLoc.name} (${userLoc.status})"
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            binding.mapView.overlays.add(marker)
            activeMarkers[userLoc.userId] = marker
        }
        binding.mapView.invalidate()
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 101)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            meshManager.startMeshNetwork("User-$myUserId")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        meshManager.stopMesh()
    }
}
