package com.emergency.meshgps

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.gson.Gson
import java.nio.charset.StandardCharsets

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
            .addOnSuccessListener {
                showToast("Advertising Aktif: Menunggu perangkat terdekat...")
            }
            .addOnFailureListener { e ->
                showToast("Gagal Advertising: ${e.localizedMessage}")
            }
    }

    fun startDiscovery() {
        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(strategy).build()
        Nearby.getConnectionsClient(context)
            .startDiscovery(serviceId, endpointDiscoveryCallback, discoveryOptions)
            .addOnSuccessListener {
                showToast("Discovery Aktif: Mencari node mesh...")
            }
            .addOnFailureListener { e ->
                showToast("Gagal Discovery: ${e.localizedMessage}")
            }
    }

    fun broadcastSosSignal(latitude: Double, longitude: Double, senderName: String = "Node_${Build.MODEL}") {
        val sosData = SosPayload(senderName, latitude, longitude)
        val jsonString = gson.toJson(sosData)
        val payload = Payload.fromBytes(jsonString.toByteArray(StandardCharsets.UTF_8))

        if (connectedEndpoints.isEmpty()) {
            showToast("⚠️ Belum terhubung ke node lain! Mengulangi pencarian...")
            startDiscovery()
            startAdvertising(senderName)
            return
        }

        for (endpointId in connectedEndpoints) {
            Nearby.getConnectionsClient(context)
                .sendPayload(endpointId, payload)
                .addOnSuccessListener {
                    showToast("✅ SOS Terkirim ke: $endpointId")
                }
                .addOnFailureListener { e ->
                    showToast("❌ Gagal Kirim SOS ke $endpointId: ${e.localizedMessage}")
                }
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            showToast("Node Ditemukan: ${info.endpointName}. Meringkas koneksi...")
            Nearby.getConnectionsClient(context)
                .requestConnection("Node_${Build.MODEL}", endpointId, connectionLifecycleCallback)
        }

        override fun onEndpointLost(endpointId: String) {
            showToast("Koneksi Node Terputus: $endpointId")
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Nearby.getConnectionsClient(context).acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                connectedEndpoints.add(endpointId)
                showToast("🤝 MESH TERHUBUNG DENGAN: $endpointId")
            }
        }

        override fun onDisconnected(endpointId: String) {
            connectedEndpoints.remove(endpointId)
            showToast("Terputus dari Node: $endpointId")
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
                } catch (e: Exception) {
                    showToast("Pesan Mentah Diterima: $jsonString")
                }
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
