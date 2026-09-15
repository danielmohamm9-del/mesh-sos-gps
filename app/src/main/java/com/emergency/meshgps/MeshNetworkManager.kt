package com.emergency.meshgps

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.gson.Gson
import java.nio.charset.StandardCharsets

class MeshNetworkManager(
    private val context: Context,
    private val onLocationReceived: (UserLocation) -> Unit
) {
    private val SERVICE_ID = "com.emergency.meshgps.SERVICE_ID"
    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val connectedEndpoints = mutableSetOf<String>()
    private val gson = Gson()

    fun startMeshNetwork(userName: String) {
        startAdvertising(userName)
        startDiscovery()
    }

    private fun startAdvertising(userName: String) {
        val options = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        connectionsClient.startAdvertising(
            userName,
            SERVICE_ID,
            connectionLifecycleCallback,
            options
        )
    }

    private fun startDiscovery() {
        val options = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        connectionsClient.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            options
        )
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            connectionsClient.requestConnection("NODE", endpointId, connectionLifecycleCallback)
        }

        override fun onEndpointLost(endpointId: String) {
            connectedEndpoints.remove(endpointId)
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                connectedEndpoints.add(endpointId)
            }
        }

        override fun onDisconnected(endpointId: String) {
            connectedEndpoints.remove(endpointId)
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val bytes = payload.asBytes() ?: return
                val jsonStr = String(bytes, StandardCharsets.UTF_8)
                try {
                    val location = gson.fromJson(jsonStr, UserLocation::class.java)
                    onLocationReceived(location)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    fun broadcastLocation(location: UserLocation) {
        if (connectedEndpoints.isEmpty()) return
        val jsonStr = gson.toJson(location)
        val payload = Payload.fromBytes(jsonStr.toByteArray(StandardCharsets.UTF_8))
        connectionsClient.sendPayload(connectedEndpoints.toList(), payload)
    }

    fun stopMesh() {
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
    }
}
