package com.emergency.meshgps

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.gson.Gson
import java.nio.charset.StandardCharsets
import java.util.UUID

data class SosPayload(
    val senderName: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long = System.currentTimeMillis()
)

class MeshNetworkManager(
    private val context: Context,
    private val onSosReceived: (sender: String, lat: Double, lng: Double) -> Unit
) {

    private val strategy = Strategy.P2P_CLUSTER
    private val serviceId = "com.emergency.meshgps.SERVICE_ID"
    private val connectedEndpoints = mutableSetOf<String>()
    private val gson = Gson()

    fun startAdvertising(userName: String = "Node_${Build.MODEL}") {
        val advertisingOptions = AdvertisingOptions.Builder().setStrategy(strategy).build()
        Nearby.getConnectionsClient(context)
            .startAdvertising(userName, serviceId, connectionLifecycleCallback, advertisingOptions)
    }

    fun startDiscovery() {
        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(strategy).build()
        Nearby.getConnectionsClient(context)
            .startDiscovery(serviceId, endpointDiscoveryCallback, discoveryOptions)
    }

    fun broadcastSosSignal(latitude: Double, longitude: Double, senderName: String = "Node_${Build.MODEL}") {
        val sosData = SosPayload(senderName, latitude, longitude)
        val jsonString = gson.toJson(sosData)
        val payload = Payload.fromBytes(jsonString.toByteArray(StandardCharsets.UTF_8))

        // 1. Kirim ke Perangkat Mesh Lainnya (HP ke HP)
        for (endpointId in connectedEndpoints) {
            Nearby.getConnectionsClient(context).sendPayload(endpointId, payload)
        }

        // 2. Kirim Langsung ke Laptop Posko via Bluetooth RFCOMM jika ada
        sendSosToLaptop(latitude, longitude)
    }

    @SuppressLint("MissingPermission")
    private fun sendSosToLaptop(latitude: Double, longitude: Double) {
        Thread {
            try {
                val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                val pairedDevices = bluetoothAdapter?.bondedDevices

                pairedDevices?.forEach { device ->
                    try {
                        val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // Serial Port Profile
                        val socket = device.createRfcommSocketToServiceRecord(uuid)
                        socket.connect()

                        val sosData = SosPayload("Node_${Build.MODEL}", latitude, longitude)
                        val jsonString = gson.toJson(sosData)

                        socket.outputStream.write(jsonString.toByteArray(StandardCharsets.UTF_8))
                        socket.outputStream.flush()
                        socket.close()
                        showToast("✅ Terkirim ke Laptop Posko (${device.name})")
                    } catch (_: Exception) {
                        // Perangkat bukan server posko, lanjut ke perangkat berikutnya
                    }
                }
            } catch (e: Exception) {
                showToast("⚠️ Transmisi Laptop: ${e.localizedMessage}")
            }
        }.start()
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Nearby.getConnectionsClient(context)
                .requestConnection("Node_${Build.MODEL}", endpointId, connectionLifecycleCallback)
        }
        override fun onEndpointLost(endpointId: String) {}
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Nearby.getConnectionsClient(context).acceptConnection(endpointId, payloadCallback)
        }
        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) connectedEndpoints.add(endpointId)
        }
        override fun onDisconnected(endpointId: String) {
            connectedEndpoints.remove(endpointId)
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes()
            if (bytes != null) {
                val jsonString = String(bytes, StandardCharsets.UTF_8)
                try {
                    val sosData = gson.fromJson(jsonString, SosPayload::class.java)
                    onSosReceived(sosData.senderName, sosData.latitude, sosData.longitude)
                } catch (_: Exception) {}
            }
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    private fun showToast(msg: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }
}
