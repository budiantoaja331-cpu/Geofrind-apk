package com.example.util

import android.util.Log
import com.example.model.CallInvitation
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Firebase Cloud Messaging Service for handling push notifications
 * for incoming chat messages and video/audio call requests.
 */
class GeoFriendsMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCMService", "Refreshed FCM Token: $token")

        val currentUid = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUid != null) {
            try {
                FirebaseFirestore.getInstance().collection("users").document(currentUid).set(
                    mapOf("fcmToken" to token),
                    SetOptions.merge()
                )
            } catch (e: Exception) {
                Log.w("FCMService", "Failed to update token on Firestore: ${e.message}")
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("FCMService", "From: ${remoteMessage.from}")

        // Check data payload
        val data = remoteMessage.data
        if (data.isNotEmpty()) {
            val type = data["type"] ?: "chat"
            when (type) {
                "chat", "message" -> {
                    val senderId = data["senderId"] ?: ""
                    val senderName = data["senderName"] ?: "Teman"
                    val messageText = data["text"] ?: data["message"] ?: "Pesan baru"
                    NotificationHelper.showChatNotification(
                        context = this,
                        senderId = senderId,
                        senderName = senderName,
                        messageText = messageText
                    )
                }
                "call", "video_call", "audio_call" -> {
                    val callId = data["callId"] ?: System.currentTimeMillis().toString()
                    val callerId = data["callerId"] ?: ""
                    val callerName = data["callerName"] ?: "Teman"
                    val callerAvatar = data["callerAvatar"] ?: ""
                    val roomId = data["roomId"] ?: "GeoFriendsRoom"
                    val isAudioOnly = data["isAudioOnly"]?.toBoolean() ?: false

                    val invitation = CallInvitation(
                        id = callId,
                        callerId = callerId,
                        callerName = callerName,
                        callerAvatar = callerAvatar,
                        receiverId = FirebaseAuth.getInstance().currentUser?.uid ?: "",
                        roomId = roomId,
                        isAudioOnly = isAudioOnly,
                        timestamp = System.currentTimeMillis(),
                        status = "ringing"
                    )
                    NotificationHelper.showIncomingCallNotification(this, invitation)
                }
            }
        }

        // Check if notification payload is present (fallback)
        remoteMessage.notification?.let {
            val title = it.title ?: "GeoFriends"
            val body = it.body ?: ""
            val senderId = data["senderId"] ?: ""
            NotificationHelper.showChatNotification(
                context = this,
                senderId = senderId,
                senderName = title,
                messageText = body
            )
        }
    }
}
