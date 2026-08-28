package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.graphics.toColorInt
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * Data configuration for launching a secure Jitsi Meet conference.
 */
data class JitsiCallConfig(
    val displayName: String = "",
    val avatarUrl: String = "",
    val isAudioOnly: Boolean = false,
    val subject: String = "Panggilan Langsung",
    val serverUrl: String = "https://meet.jit.si"
)

/**
 * Helper object providing launch functions and secure room generator for Jitsi Meet.
 */
object JitsiHelper {

    /**
     * Programmatically generates a secure, deterministic global Room ID by hashing
     * the combined Firebase UIDs of both users with SHA-256.
     * Output format: SecureCall_<16_char_hex>
     */
    fun generateSecureRoomId(uid1: String, uid2: String): String {
        val sortedPair = if (uid1 < uid2) "${uid1}_${uid2}" else "${uid2}_${uid1}"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(sortedPair.toByteArray(Charsets.UTF_8))
        val hexHash = hashBytes.joinToString("") { "%02x".format(it) }.take(16)
        return "SecureCall_${hexHash}"
    }

    /**
     * Launches a secure Jitsi Meet video or audio call for the given roomId and configuration.
     * Features:
     * - Launches native in-app CallActivity with hardware-accelerated WebRTC rendering.
     * - Fallback to Chrome Custom Tabs with M3 indigo branding.
     * - Configures display name, avatar, audio-only mode, and skips pre-join obstacles.
     */
    fun launchJitsiCall(
        context: Context,
        roomId: String,
        config: JitsiCallConfig = JitsiCallConfig()
    ) {
        try {
            // Priority 1: Launch native in-app CallActivity
            com.example.ui.screens.CallActivity.start(
                context = context,
                roomId = roomId,
                displayName = config.displayName,
                avatarUrl = config.avatarUrl,
                isAudioOnly = config.isAudioOnly,
                subject = config.subject
            )
            return
        } catch (_: Exception) {
            // Priority 2: Fallback to Chrome Custom Tabs
        }

        val baseUrl = config.serverUrl.trimEnd('/')
        val encodedRoom = URLEncoder.encode(roomId, "UTF-8")

        // Construct URL with hash parameters for Jitsi Web / Custom Tabs client
        val hashParams = mutableListOf<String>()
        if (config.displayName.isNotBlank()) {
            hashParams.add("userInfo.displayName=\"${URLEncoder.encode(config.displayName, "UTF-8")}\"")
        }
        if (config.avatarUrl.isNotBlank()) {
            hashParams.add("userInfo.avatarURL=\"${URLEncoder.encode(config.avatarUrl, "UTF-8")}\"")
        }
        if (config.isAudioOnly) {
            hashParams.add("config.startWithVideoMuted=true")
        }
        // Seamless in-app call settings
        hashParams.add("config.prejoinPageEnabled=false")
        hashParams.add("config.requireDisplayName=false")
        hashParams.add("config.disableDeepLinking=true")
        hashParams.add("config.enableWelcomePage=false")

        val hashString = if (hashParams.isNotEmpty()) "#${hashParams.joinToString("&")}" else ""
        val fullCallUrl = "$baseUrl/$encodedRoom$hashString"
        val uri = Uri.parse(fullCallUrl)

        try {
            // Setup Chrome Custom Tabs with branded toolbar
            val colorSchemeParams = CustomTabColorSchemeParams.Builder()
                .setToolbarColor("#4F46E5".toColorInt())
                .build()

            val customTabsIntent = CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(colorSchemeParams)
                .setShowTitle(true)
                .setUrlBarHidingEnabled(true)
                .build()

            customTabsIntent.launchUrl(context, uri)
        } catch (_: Exception) {
            // Fallback to standard browser activity
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    /**
     * Quick launcher for high-definition Video Call.
     */
    fun launchVideoCall(
        context: Context,
        roomId: String,
        callerName: String = "",
        callerAvatar: String = ""
    ) {
        launchJitsiCall(
            context = context,
            roomId = roomId,
            config = JitsiCallConfig(
                displayName = callerName,
                avatarUrl = callerAvatar,
                isAudioOnly = false,
                subject = "Video Call"
            )
        )
    }

    /**
     * Quick launcher for Voice-Only Audio Call.
     */
    fun launchAudioCall(
        context: Context,
        roomId: String,
        callerName: String = "",
        callerAvatar: String = ""
    ) {
        launchJitsiCall(
            context = context,
            roomId = roomId,
            config = JitsiCallConfig(
                displayName = callerName,
                avatarUrl = callerAvatar,
                isAudioOnly = true,
                subject = "Panggilan Suara"
            )
        )
    }
}
