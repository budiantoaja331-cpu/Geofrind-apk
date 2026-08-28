package com.example.model

data class CallInvitation(
    val id: String = "",
    val callerId: String = "",
    val callerName: String = "",
    val callerAvatar: String = "",
    val receiverId: String = "",
    val roomId: String = "",
    val isAudioOnly: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "ringing" // "ringing", "accepted", "declined", "missed", "ended"
)
