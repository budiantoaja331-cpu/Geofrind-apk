package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.model.ChatMessage
import com.example.model.User
import com.example.ui.components.PermissionDialog
import com.example.util.JitsiHelper
import com.example.viewmodel.ChatViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    currentUser: User,
    targetUser: User,
    chatViewModel: ChatViewModel,
    onBlockUser: (User) -> Unit = {},
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val messages by chatViewModel.messages.collectAsState()
    val isTranslating by chatViewModel.isTranslating.collectAsState()
    val liveTargetUser by chatViewModel.liveTargetUser.collectAsState()
    val activeUser = liveTargetUser ?: targetUser

    var inputText by remember { mutableStateOf("") }
    var showCallPermissions by remember { mutableStateOf(false) }
    var isAudioOnlyCall by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showBlockConfirmation by remember { mutableStateOf(false) }
    var isCallingActive by remember { mutableStateOf(false) }
    var activeCallId by remember { mutableStateOf("") }

    val listState = rememberLazyListState()

    // Sound effect generator for outgoing message and ringtone
    var ringtonePlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    // Ringtone management when calling
    DisposableEffect(isCallingActive) {
        if (isCallingActive) {
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
                // Fallback tone generator if system ringtone is restricted in emulator
                try {
                    val toneGen = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80)
                    toneGen.startTone(ToneGenerator.TONE_SUP_RINGTONE, 4000)
                } catch (_: Exception) {}
            }
        } else {
            ringtonePlayer?.let {
                try {
                    if (it.isPlaying) it.stop()
                    it.release()
                } catch (_: Exception) {}
            }
            ringtonePlayer = null
        }

        onDispose {
            ringtonePlayer?.let {
                try {
                    if (it.isPlaying) it.stop()
                    it.release()
                } catch (_: Exception) {}
            }
            ringtonePlayer = null
        }
    }

    LaunchedEffect(currentUser, targetUser) {
        chatViewModel.initializeChat(currentUser, targetUser)
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Calling Simulation & Ringtone Popup Dialog
    if (isCallingActive) {
        AlertDialog(
            onDismissRequest = {
                chatViewModel.endCallInvitation(activeCallId)
                isCallingActive = false
            },
            icon = {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4F46E5).copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isAudioOnlyCall) Icons.Default.PhoneInTalk else Icons.Default.Videocam,
                        contentDescription = null,
                        tint = Color(0xFF4F46E5),
                        modifier = Modifier.size(32.dp)
                    )
                }
            },
            title = {
                Text(
                    text = if (isAudioOnlyCall) "Memanggil Suara..." else "Panggilan Video...",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF1E293B)
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = activeUser.name,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = Color(0xFF4F46E5)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Mengirim notifikasi panggilan & nada dering ke ${activeUser.name}...",
                        fontSize = 13.sp,
                        color = Color(0xFF64748B),
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isCallingActive = false
                        val roomId = chatViewModel.getJitsiRoomId()
                        if (isAudioOnlyCall) {
                            JitsiHelper.launchAudioCall(
                                context = context,
                                roomId = roomId,
                                callerName = currentUser.name,
                                callerAvatar = currentUser.profilePic
                            )
                        } else {
                            JitsiHelper.launchVideoCall(
                                context = context,
                                roomId = roomId,
                                callerName = currentUser.name,
                                callerAvatar = currentUser.profilePic
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Buka Meet Sekarang", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        chatViewModel.endCallInvitation(activeCallId)
                        isCallingActive = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFDC2626))
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFFDC2626)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Akhiri Panggilan", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showBlockConfirmation) {
        AlertDialog(
            onDismissRequest = { showBlockConfirmation = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.PersonOff,
                    contentDescription = null,
                    tint = Color(0xFFDC2626),
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Blokir ${activeUser.name}?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF1E293B)
                )
            },
            text = {
                Text(
                    text = "Pengguna ini tidak akan dapat menghubungi Anda lagi dan tidak akan muncul di daftar teman.",
                    fontSize = 14.sp,
                    color = Color(0xFF64748B),
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBlockConfirmation = false
                        onBlockUser(activeUser)
                        onBackClick()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Blokir", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirmation = false }) {
                    Text("Batal", color = Color(0xFF64748B))
                }
            }
        )
    }

    if (showCallPermissions) {
        PermissionDialog(
            permissions = if (isAudioOnlyCall) {
                arrayOf(Manifest.permission.RECORD_AUDIO)
            } else {
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            },
            onPermissionsGranted = {
                showCallPermissions = false
                chatViewModel.initiateCallInvitation(isAudioOnlyCall) { id ->
                    activeCallId = id
                }
                isCallingActive = true
            }
        )
    }

    Scaffold(
        containerColor = Color(0xFFF8FAFC),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Avatar with real-time status badge
                        Box(
                            modifier = Modifier.size(42.dp),
                            contentAlignment = Alignment.BottomEnd
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(Color(0xFFEEF2F6)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (activeUser.profilePic.isNotBlank()) {
                                    AsyncImage(
                                        model = activeUser.profilePic,
                                        contentDescription = activeUser.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.ChatBubbleOutline,
                                        contentDescription = null,
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            // Presence indicator dot
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .padding(2.dp)
                                    .clip(CircleShape)
                                    .background(if (activeUser.isOnline) Color(0xFF10B981) else Color(0xFF94A3B8))
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = activeUser.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (activeUser.isOnline) Color(0xFF10B981) else Color(0xFF94A3B8))
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = com.example.util.TimeUtils.formatPresenceStatus(context, activeUser.isOnline, activeUser.lastActive),
                                    fontSize = 11.sp,
                                    fontWeight = if (activeUser.isOnline) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (activeUser.isOnline) Color(0xFF059669) else Color(0xFF64748B)
                                )
                                if (activeUser.country.isNotBlank()) {
                                    Text(
                                        text = " • ${activeUser.country}",
                                        fontSize = 11.sp,
                                        color = Color(0xFF94A3B8),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (isTranslating) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(10.dp),
                                        strokeWidth = 1.5.dp,
                                        color = Color(0xFF4F46E5)
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("chat_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali",
                            tint = Color(0xFF1E293B)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            isAudioOnlyCall = true
                            showCallPermissions = true
                        },
                        modifier = Modifier.testTag("audio_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Panggilan Suara",
                            tint = Color(0xFF4F46E5)
                        )
                    }
                    IconButton(
                        onClick = {
                            isAudioOnlyCall = false
                            showCallPermissions = true
                        },
                        modifier = Modifier.testTag("video_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = stringResource(R.string.video_call),
                            tint = Color(0xFF4F46E5)
                        )
                    }
                    Box {
                        IconButton(
                            onClick = { showMoreMenu = true },
                            modifier = Modifier.testTag("chat_more_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Menu Lainnya",
                                tint = Color(0xFF64748B)
                            )
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Blokir ${activeUser.name}",
                                        color = Color(0xFFDC2626),
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Block,
                                        contentDescription = null,
                                        tint = Color(0xFFDC2626)
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    showBlockConfirmation = true
                                },
                                modifier = Modifier.testTag("block_user_menu_item")
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFF8FAFC))
        ) {
            // Live Messages List
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (messages.isEmpty()) {
                    EmptyChatState(targetName = targetUser.name)
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = messages,
                            key = { it.id }
                        ) { message ->
                            val isMe = message.senderId == currentUser.id
                            ChatMessageBubble(
                                message = message,
                                isMe = isMe,
                                targetUserId = targetUser.id
                            )
                        }
                    }
                }
            }

            // Input Bar
            Surface(
                shadowElevation = 8.dp,
                color = Color.White,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text(
                                text = "Ketik pesan di sini...",
                                fontSize = 14.sp,
                                color = Color(0xFF94A3B8)
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (inputText.isNotBlank()) {
                                    chatViewModel.sendMessage(inputText)
                                    inputText = ""
                                }
                            }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_message_input"),
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF8FAFC),
                            unfocusedContainerColor = Color(0xFFF8FAFC),
                            focusedBorderColor = Color(0xFF4F46E5),
                            unfocusedBorderColor = Color(0xFFCBD5E1),
                            focusedTextColor = Color(0xFF0F172A),
                            unfocusedTextColor = Color(0xFF0F172A),
                            cursorColor = Color(0xFF4F46E5)
                        ),
                        maxLines = 4
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                chatViewModel.sendMessage(inputText)
                                inputText = ""
                            }
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(if (inputText.isNotBlank()) Color(0xFF4F46E5) else Color(0xFF94A3B8))
                            .testTag("send_message_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Kirim",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    isMe: Boolean,
    targetUserId: String = ""
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val formattedTime = timeFormat.format(Date(message.timestamp))
    val isReadByTarget = isMe && (message.read || (targetUserId.isNotBlank() && message.readBy.contains(targetUserId)))

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        Card(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isMe) 18.dp else 4.dp,
                bottomEnd = if (isMe) 4.dp else 18.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isMe) Color(0xFF4F46E5) else Color.White
            ),
            modifier = Modifier
                .widthIn(max = 290.dp)
                .shadow(
                    elevation = if (isMe) 1.dp else 2.dp,
                    shape = RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (isMe) 18.dp else 4.dp,
                        bottomEnd = if (isMe) 4.dp else 18.dp
                    ),
                    spotColor = Color(0xFF0F172A).copy(alpha = 0.08f)
                )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                // Determine whether a translated version is available and distinct
                val hasTranslation = !message.translatedText.isNullOrBlank() &&
                        !message.translatedText.equals(message.text, ignoreCase = true)
                val primaryText = if (hasTranslation) message.translatedText!! else message.text

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = primaryText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal,
                        color = if (isMe) Color.White else Color(0xFF1E293B),
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (hasTranslation) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = "Terjemahan",
                            tint = if (isMe) Color.White.copy(alpha = 0.85f) else Color(0xFF4F46E5),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // Show original message text discreetly below without exposing technical details
                if (hasTranslation) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.original_label, message.text),
                        fontSize = 11.sp,
                        fontStyle = FontStyle.Italic,
                        color = if (isMe) Color.White.copy(alpha = 0.75f) else Color(0xFF64748B)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Bottom row: Time + Read Receipt checkmark status
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = formattedTime,
                        fontSize = 10.sp,
                        color = if (isMe) Color.White.copy(alpha = 0.7f) else Color(0xFF94A3B8)
                    )

                    if (isMe) {
                        if (isReadByTarget) {
                            // Double checkmark indicating recipient has read the message
                            Icon(
                                imageVector = Icons.Default.DoneAll,
                                contentDescription = "Dibaca",
                                tint = Color(0xFF67E8F9), // Soft luminous sky blue / cyan
                                modifier = Modifier
                                    .size(15.dp)
                                    .testTag("read_receipt_read_${message.id}")
                            )
                        } else {
                            // Single checkmark indicating message sent/delivered
                            Icon(
                                imageVector = Icons.Default.Done,
                                contentDescription = "Terkirim",
                                tint = Color.White.copy(alpha = 0.65f),
                                modifier = Modifier
                                    .size(14.dp)
                                    .testTag("read_receipt_sent_${message.id}")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyChatState(targetName: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            color = Color(0xFFEEF2FF)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.ChatBubbleOutline,
                    contentDescription = null,
                    tint = Color(0xFF4F46E5),
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Kirim Pesan",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E293B)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Kirim pesan untuk memulai percakapan langsung dengan $targetName.",
            fontSize = 13.sp,
            color = Color(0xFF64748B),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}
