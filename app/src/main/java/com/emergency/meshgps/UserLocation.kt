package com.emergency.meshgps

data class UserLocation(
    val userId: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val status: String = "SOS",
    val timestamp: Long = System.currentTimeMillis()
)
