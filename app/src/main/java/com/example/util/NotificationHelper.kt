package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.model.CallInvitation
import com.example.ui.screens.CallActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging

object NotificationHelper {

    const val CHANNEL_CHAT_ID = "geofriends_chat_channel"
    const val CHANNEL_CHAT_NAME = "Pesan Obrolan"

    const val CHANNEL_CALL_ID = "geofriends_call_channel"
    const val CHANNEL_CALL_NAME = "Panggilan Suara & Video"

    private const val NOTIFICATION_CALL_ID = 9001

    /**
     * Initializes Android Notification Channels (API 26+)
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            // Chat notification channel
            val chatChannel = NotificationChannel(
                CHANNEL_CHAT_ID,
                CHANNEL_CHAT_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi pesan obrolan baru"
                enableVibration(true)
                enableLights(true)
            }

            // Call notification channel with ringtone & max priority
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .build()

            val callChannel = NotificationChannel(
                CHANNEL_CALL_ID,
                CHANNEL_CALL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi permintaan panggilan masuk"
                setSound(ringtoneUri, audioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)
                enableLights(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            notificationManager.createNotificationChannel(chatChannel)
            notificationManager.createNotificationChannel(callChannel)
        }
    }

    /**
     * Syncs current FCM token to Firestore for the authenticated user.
     */
    fun syncFcmTokenToFirestore(userId: String? = null) {
        val uid = userId ?: FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                if (!token.isNullOrBlank()) {
                    Log.d("NotificationHelper", "FCM Token retrieved: $token")
                    try {
                        FirebaseFirestore.getInstance().collection("users").document(uid).set(
                            mapOf("fcmToken" to token),
                            SetOptions.merge()
                        )
                    } catch (e: Exception) {
                        Log.w("NotificationHelper", "Failed to update FCM token in Firestore: ${e.message}")
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.w("NotificationHelper", "Failed to get FCM token: ${e.message}")
            }
    }

    /**
     * Shows a local/FCM notification for an incoming chat message.
     */
    fun showChatNotification(
        context: Context,
        senderId: String,
        senderName: String,
        messageText: String
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "chat")
            putExtra("target_user_id", senderId)
            putExtra("target_user_name", senderName)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            senderId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(context, CHANNEL_CHAT_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(senderName)
            .setContentText(messageText)
            .setAutoCancel(true)
            .setSound(soundUri)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        val notificationId = (senderId.hashCode() and 0x7FFFFFFF)
        notificationManager.notify(notificationId, notification)
    }

    /**
     * Shows a high-priority Heads-up notification with action buttons for an incoming video/audio call request.
     */
    fun showIncomingCallNotification(
        context: Context,
        callInvitation: CallInvitation
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        // Accept Call Intent (launches CallActivity)
        val acceptIntent = Intent(context, CallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(CallActivity.EXTRA_ROOM_ID, callInvitation.roomId)
            putExtra(CallActivity.EXTRA_DISPLAY_NAME, callInvitation.receiverId)
            putExtra(CallActivity.EXTRA_AVATAR_URL, "")
            putExtra(CallActivity.EXTRA_IS_AUDIO_ONLY, callInvitation.isAudioOnly)
            putExtra(CallActivity.EXTRA_SUBJECT, "Panggilan dari ${callInvitation.callerName}")
        }

        val acceptPendingIntent = PendingIntent.getActivity(
            context,
            callInvitation.id.hashCode(),
            acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        // Decline Call Intent (launches MainActivity and dismisses)
        val declineIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("action", "decline_call")
            putExtra("call_id", callInvitation.id)
        }

        val declinePendingIntent = PendingIntent.getActivity(
            context,
            (callInvitation.id + "_decline").hashCode(),
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val callType = if (callInvitation.isAudioOnly) "Panggilan Suara" else "Panggilan Video"

        val builder = NotificationCompat.Builder(context, CHANNEL_CALL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setContentTitle("$callType Masuk")
            .setContentText("${callInvitation.callerName} sedang memanggil Anda...")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(ringtoneUri)
            .setVibrate(longArrayOf(0, 1000, 500, 1000, 500, 1000))
            .setAutoCancel(true)
            .setOngoing(true)
            .setContentIntent(acceptPendingIntent)
            .setFullScreenIntent(acceptPendingIntent, true)
            .addAction(
                android.R.drawable.ic_menu_call,
                "Terima",
                acceptPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Tolak",
                declinePendingIntent
            )

        notificationManager.notify(NOTIFICATION_CALL_ID, builder.build())
    }

    /**
     * Clears any active incoming call notification.
     */
    fun cancelIncomingCallNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancel(NOTIFICATION_CALL_ID)
    }
}
