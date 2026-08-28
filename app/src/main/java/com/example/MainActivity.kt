package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.model.CallInvitation
import com.example.model.User
import com.example.ui.screens.CallActivity
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.UserListScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.util.LocationHelper
import com.example.util.NotificationHelper
import com.example.viewmodel.AuthViewModel
import com.example.viewmodel.ChatViewModel
import com.example.viewmodel.UserListViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    private val userListViewModel: UserListViewModel by viewModels()
    private val chatViewModel: ChatViewModel by viewModels()

    private var incomingCallListener: ListenerRegistration? = null
    private var activeIncomingCall by mutableStateOf<CallInvitation?>(null)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocationGranted || coarseLocationGranted) {
            authViewModel.refreshUserLocation(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Create notification channels for FCM and local notifications
        NotificationHelper.createNotificationChannels(this)

        requestRequiredPermissions()

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val context = LocalContext.current
                    val currentUser by authViewModel.currentUser.collectAsState()

                    // Sync FCM Token and listen for incoming video/audio calls in real time
                    LaunchedEffect(currentUser) {
                        val user = currentUser
                        if (user != null) {
                            NotificationHelper.syncFcmTokenToFirestore(user.id)
                            listenToIncomingCalls(user.id)
                        } else {
                            incomingCallListener?.remove()
                            incomingCallListener = null
                        }
                    }

                    // Incoming Call Dialog Popup with Ringtone
                    activeIncomingCall?.let { invitation ->
                        IncomingCallDialog(
                            invitation = invitation,
                            onAccept = {
                                NotificationHelper.cancelIncomingCallNotification(context)
                                FirebaseFirestore.getInstance().collection("calls")
                                    .document(invitation.id)
                                    .update("status", "accepted")

                                CallActivity.start(
                                    context = context,
                                    roomId = invitation.roomId,
                                    displayName = currentUser?.name ?: "Pengguna",
                                    avatarUrl = currentUser?.profilePic ?: "",
                                    isAudioOnly = invitation.isAudioOnly,
                                    subject = "Panggilan dengan ${invitation.callerName}"
                                )
                                activeIncomingCall = null
                            },
                            onDecline = {
                                NotificationHelper.cancelIncomingCallNotification(context)
                                FirebaseFirestore.getInstance().collection("calls")
                                    .document(invitation.id)
                                    .update("status", "declined")
                                activeIncomingCall = null
                            }
                        )
                    }

                    GeoFriendsApp(
                        authViewModel = authViewModel,
                        userListViewModel = userListViewModel,
                        chatViewModel = chatViewModel,
                        onRequestLocationPermission = { requestRequiredPermissions() }
                    )
                }
            }
        }
    }

    private fun listenToIncomingCalls(userId: String) {
        incomingCallListener?.remove()
        try {
            incomingCallListener = FirebaseFirestore.getInstance().collection("calls")
                .whereEqualTo("receiverId", userId)
                .whereEqualTo("status", "ringing")
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        Log.w("MainActivity", "Call listener error: ${error.message}")
                        return@addSnapshotListener
                    }

                    val now = System.currentTimeMillis()
                    val validCallDoc = snapshots?.documents?.firstOrNull { doc ->
                        val timestamp = doc.getLong("timestamp") ?: 0L
                        // Call invitation is valid within last 45 seconds
                        (now - timestamp) < 45_000
                    }

                    if (validCallDoc != null) {
                        val invitation = CallInvitation(
                            id = validCallDoc.id,
                            callerId = validCallDoc.getString("callerId") ?: "",
                            callerName = validCallDoc.getString("callerName") ?: "Teman",
                            callerAvatar = validCallDoc.getString("callerAvatar") ?: "",
                            receiverId = userId,
                            roomId = validCallDoc.getString("roomId") ?: "GeoFriendsRoom",
                            isAudioOnly = validCallDoc.getBoolean("isAudioOnly") ?: false,
                            timestamp = validCallDoc.getLong("timestamp") ?: now,
                            status = "ringing"
                        )
                        activeIncomingCall = invitation
                        NotificationHelper.showIncomingCallNotification(this, invitation)
                    } else {
                        if (activeIncomingCall != null) {
                            activeIncomingCall = null
                            NotificationHelper.cancelIncomingCallNotification(this)
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to register call listener: ${e.message}")
        }
    }

    private fun requestRequiredPermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val allGranted = permissionsToRequest.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (!allGranted) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            authViewModel.refreshUserLocation(this)
        }
    }

    override fun onResume() {
        super.onResume()
        authViewModel.setUserOnlineStatus(true)
    }

    override fun onPause() {
        super.onPause()
        authViewModel.setUserOnlineStatus(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        authViewModel.setUserOnlineStatus(false)
        incomingCallListener?.remove()
        incomingCallListener = null
    }
}

@Composable
fun IncomingCallDialog(
    invitation: CallInvitation,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val context = LocalContext.current
    var ringtonePlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    // Ringing sound on incoming call
    DisposableEffect(Unit) {
        try {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING)
                        .build()
                )
                setDataSource(context, ringtoneUri)
                isLooping = true
                prepare()
                start()
            }
            ringtonePlayer = player
        } catch (_: Exception) {
            try {
                val toneGen = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80)
                toneGen.startTone(ToneGenerator.TONE_SUP_RINGTONE, 3000)
            } catch (_: Exception) {}
        }

        onDispose {
            try {
                ringtonePlayer?.stop()
                ringtonePlayer?.release()
            } catch (_: Exception) {}
            ringtonePlayer = null
        }
    }

    AlertDialog(
        onDismissRequest = onDecline,
        icon = {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF10B981).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (invitation.isAudioOnly) Icons.Default.PhoneInTalk else Icons.Default.Videocam,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(36.dp)
                )
            }
        },
        title = {
            Text(
                text = if (invitation.isAudioOnly) "Panggilan Suara Masuk" else "Panggilan Video Masuk",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color(0xFF0F172A),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = invitation.callerName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Color(0xFF4F46E5)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Sedang memanggil Anda melalui Jitsi Meet terenkripsi...",
                    fontSize = 13.sp,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Jawab", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDecline,
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444))
            ) {
                Icon(Icons.Default.CallEnd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Tolak", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun GeoFriendsApp(
    authViewModel: AuthViewModel,
    userListViewModel: UserListViewModel,
    chatViewModel: ChatViewModel,
    onRequestLocationPermission: () -> Unit
) {
    val currentUser by authViewModel.currentUser.collectAsState()
    var selectedTargetUser by remember { mutableStateOf<User?>(null) }
    var isViewingProfile by remember { mutableStateOf(false) }

    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            onRequestLocationPermission()
        }
    }

    Crossfade(
        targetState = Triple(currentUser, selectedTargetUser, isViewingProfile),
        label = "AppScreenTransition"
    ) { (user, target, viewingProfile) ->
        when {
            user == null -> {
                LoginScreen(
                    viewModel = authViewModel,
                    onLoginSuccess = { /* Managed reactively by currentUser flow */ }
                )
            }
            viewingProfile -> {
                ProfileScreen(
                    authViewModel = authViewModel,
                    onBackClick = { isViewingProfile = false }
                )
            }
            target == null -> {
                UserListScreen(
                    authViewModel = authViewModel,
                    userListViewModel = userListViewModel,
                    onUserClick = { selectedUser ->
                        selectedTargetUser = selectedUser
                    },
                    onProfileClick = {
                        isViewingProfile = true
                    }
                )
            }
            else -> {
                ChatScreen(
                    currentUser = user,
                    targetUser = target,
                    chatViewModel = chatViewModel,
                    onBlockUser = { blocked ->
                        authViewModel.blockUser(blocked.id)
                    },
                    onBackClick = {
                        selectedTargetUser = null
                    }
                )
            }
        }
    }
}
