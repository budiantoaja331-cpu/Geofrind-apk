package com.example.model

data class User(
    val id: String = "",
    val name: String = "",
    val profilePic: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val country: String = "",
    val distanceMeters: Double = 0.0,
    val isOnline: Boolean = true,
    val lastActive: Long = System.currentTimeMillis(),
    val blockedUsers: List<String> = emptyList(),
    val fcmToken: String = ""
)

