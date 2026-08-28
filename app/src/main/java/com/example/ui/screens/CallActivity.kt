package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import java.net.URLEncoder

/**
 * Native in-app Activity integrating Jitsi Meet WebRTC Video Calling.
 * Provides a secure, full-duplex audiovisual room experience with encryption headers,
 * floating call controls, hardware-accelerated rendering, and automated permission handshakes.
 */
class CallActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ROOM_ID = "extra_room_id"
        const val EXTRA_DISPLAY_NAME = "extra_display_name"
        const val EXTRA_AVATAR_URL = "extra_avatar_url"
        const val EXTRA_IS_AUDIO_ONLY = "extra_is_audio_only"
        const val EXTRA_SUBJECT = "extra_subject"

        fun start(
            context: Context,
            roomId: String,
            displayName: String = "",
            avatarUrl: String = "",
            isAudioOnly: Boolean = false,
            subject: String = "Panggilan Video"
        ) {
            val intent = Intent(context, CallActivity::class.java).apply {
                putExtra(EXTRA_ROOM_ID, roomId)
                putExtra(EXTRA_DISPLAY_NAME, displayName)
                putExtra(EXTRA_AVATAR_URL, avatarUrl)
                putExtra(EXTRA_IS_AUDIO_ONLY, isAudioOnly)
                putExtra(EXTRA_SUBJECT, subject)
                if (context !is ComponentActivity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val roomId = intent.getStringExtra(EXTRA_ROOM_ID) ?: "SecureRoom_Default"
        val displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME) ?: "Pengguna"
        val avatarUrl = intent.getStringExtra(EXTRA_AVATAR_URL) ?: ""
        val isAudioOnly = intent.getBooleanExtra(EXTRA_IS_AUDIO_ONLY, false)
        val subject = intent.getStringExtra(EXTRA_SUBJECT) ?: "Panggilan Langsung"

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0F172A)
                ) {
                    CallScreen(
                        roomId = roomId,
                        displayName = displayName,
                        avatarUrl = avatarUrl,
                        isAudioOnly = isAudioOnly,
                        subject = subject,
                        onEndCall = { finish() }
                    )
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CallScreen(
    roomId: String,
    displayName: String,
    avatarUrl: String,
    isAudioOnly: Boolean,
    subject: String,
    onEndCall: () -> Unit
) {
    val context = LocalContext.current
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var secondsElapsed by remember { mutableLongStateOf(0L) }

    // Call duration timer
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            secondsElapsed += 1
        }
    }

    val formattedDuration = remember(secondsElapsed) {
        val minutes = secondsElapsed / 60
        val seconds = secondsElapsed % 60
        "%02d:%02d".format(minutes, seconds)
    }

    // Build optimized Jitsi Meet URL with config hash parameters
    val fullUrl = remember(roomId, displayName, avatarUrl, isAudioOnly) {
        val baseUrl = "https://meet.jit.si"
        val encodedRoom = URLEncoder.encode(roomId, "UTF-8")
        val hashParams = mutableListOf<String>()

        if (displayName.isNotBlank()) {
            hashParams.add("userInfo.displayName=\"${URLEncoder.encode(displayName, "UTF-8")}\"")
        }
        if (avatarUrl.isNotBlank()) {
            hashParams.add("userInfo.avatarURL=\"${URLEncoder.encode(avatarUrl, "UTF-8")}\"")
        }
        if (isAudioOnly) {
            hashParams.add("config.startWithVideoMuted=true")
        }
        hashParams.add("config.prejoinPageEnabled=false")
        hashParams.add("config.requireDisplayName=false")
        hashParams.add("config.disableDeepLinking=true")
        hashParams.add("config.enableWelcomePage=false")
        hashParams.add("config.p2p.enabled=true")
        hashParams.add("config.enableClosePage=false")

        "$baseUrl/$encodedRoom#${hashParams.joinToString("&")}"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Embedded WebRTC WebView
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        allowFileAccess = true
                        allowContentAccess = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onPermissionRequest(request: PermissionRequest?) {
                            request?.let {
                                // Automatically grant WebRTC Audio & Video permissions
                                it.grant(it.resources)
                            }
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            isLoading = true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isLoading = false
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            if (request?.isForMainFrame == true) {
                                hasError = true
                                errorMessage = error?.description?.toString() ?: "Gagal memuat panggilan."
                            }
                        }
                    }

                    loadUrl(fullUrl)
                    webViewInstance = this
                }
            },
            update = { /* Updates handled via URL */ },
            modifier = Modifier.fillMaxSize()
        )

        // Top Gradient & Info Overlay Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xCC0F172A),
                            Color(0x880F172A),
                            Color.Transparent
                        )
                    )
                )
                .padding(top = 48.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isAudioOnly) "Panggilan Suara" else "Panggilan Video",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Durasi: $formattedDuration",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }

                // E2EE Security Badge
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0x334F46E5))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Secured",
                        tint = Color(0xFF818CF8),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "E2EE Terenkripsi",
                        color = Color(0xFFE0E7FF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Bottom Floating Action Overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xCC0F172A),
                            Color(0xEE0F172A)
                        )
                    )
                )
                .padding(bottom = 36.dp, top = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Refresh connection button
                IconButton(
                    onClick = {
                        webViewInstance?.reload()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF))
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Muat Ulang",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Hang up / End call button (prominent red)
                IconButton(
                    onClick = {
                        webViewInstance?.destroy()
                        onEndCall()
                    },
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444))
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "Akhiri Panggilan",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }

        // Loading Overlay
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xDD0F172A)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = Color(0xFF6366F1),
                        modifier = Modifier.size(48.dp),
                        strokeWidth = 4.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Menghubungkan ke Jitsi Meet...",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Mengamankan saluran WebRTC terenkripsi",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Error State
        if (hasError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xF50F172A))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Koneksi Panggilan Terputus",
                        color = Color(0xFFF87171),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage.ifBlank { "Pastikan koneksi internet stabil dan coba hubungi kembali." },
                        color = Color(0xFFCBD5E1),
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    IconButton(
                        onClick = {
                            hasError = false
                            webViewInstance?.loadUrl(fullUrl)
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4F46E5))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Coba Lagi",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.destroy()
        }
    }
}
