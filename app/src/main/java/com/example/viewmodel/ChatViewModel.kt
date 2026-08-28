package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.ChatMessage
import com.example.model.User
import com.example.util.JitsiHelper
import com.example.util.TranslationManager
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class ChatViewModel : ViewModel() {

    private val firestore = FirebaseFirestore.getInstance()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isTranslating = MutableStateFlow(false)
    val isTranslating: StateFlow<Boolean> = _isTranslating.asStateFlow()

    private val _liveTargetUser = MutableStateFlow<User?>(null)
    val liveTargetUser: StateFlow<User?> = _liveTargetUser.asStateFlow()

    private var currentChatId: String = ""
    private var currentUser: User? = null
    private var targetUser: User? = null
    private var userListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var messagesListener: com.google.firebase.firestore.ListenerRegistration? = null

    fun initializeChat(currentUser: User, targetUser: User) {
        this.currentUser = currentUser
        this.targetUser = targetUser
        _liveTargetUser.value = targetUser

        // Deterministic chat session ID based on sorted UIDs
        currentChatId = if (currentUser.id < targetUser.id) {
            "chat_${currentUser.id}_${targetUser.id}"
        } else {
            "chat_${targetUser.id}_${currentUser.id}"
        }

        listenToTargetUserPresence(targetUser.id)
        listenToMessages()
    }

    private fun listenToTargetUserPresence(targetUserId: String) {
        userListener?.remove()
        userListener = firestore.collection("users").document(targetUserId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                val name = snapshot.getString("name") ?: targetUser?.name ?: ""
                val pic = snapshot.getString("profilePic") ?: targetUser?.profilePic ?: ""
                val lat = snapshot.getDouble("latitude") ?: targetUser?.latitude ?: 0.0
                val lon = snapshot.getDouble("longitude") ?: targetUser?.longitude ?: 0.0
                val country = snapshot.getString("country") ?: targetUser?.country ?: "Indonesia"
                val isOnline = snapshot.getBoolean("isOnline") ?: false
                val lastActive = snapshot.getLong("lastActive") ?: System.currentTimeMillis()

                _liveTargetUser.value = User(
                    id = targetUserId,
                    name = name,
                    profilePic = pic,
                    latitude = lat,
                    longitude = lon,
                    country = country,
                    isOnline = isOnline,
                    lastActive = lastActive
                )
            }
    }

    private fun listenToMessages() {
        if (currentChatId.isBlank()) return
        messagesListener?.remove()

        messagesListener = firestore.collection("chats")
            .document(currentChatId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("ChatViewModel", "Firestore messages error: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val currentUid = currentUser?.id
                    val unreadDocs = mutableListOf<com.google.firebase.firestore.DocumentReference>()

                    val rawMessages = snapshot.documents.mapNotNull { doc ->
                        val id = doc.id
                        val senderId = doc.getString("senderId") ?: ""
                        val senderName = doc.getString("senderName") ?: ""
                        val text = doc.getString("text") ?: ""
                        val languageCode = doc.getString("languageCode") ?: "en"
                        val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                        val read = doc.getBoolean("read") ?: false
                        @Suppress("UNCHECKED_CAST")
                        val readBy = (doc.get("readBy") as? List<*>)?.filterIsInstance<String>() ?: emptyList()

                        // If the message is from the other user and current user has not read it yet
                        if (currentUid != null && senderId != currentUid && (!read || !readBy.contains(currentUid))) {
                            unreadDocs.add(doc.reference)
                        }

                        ChatMessage(
                            id = id,
                            senderId = senderId,
                            senderName = senderName,
                            text = text,
                            languageCode = languageCode,
                            timestamp = timestamp,
                            read = read || (targetUser?.id?.isNotBlank() == true && readBy.contains(targetUser?.id)),
                            readBy = readBy
                        )
                    }

                    // Automatically update read status in Firestore when the user views the chat
                    if (currentUid != null && unreadDocs.isNotEmpty()) {
                        markMessagesAsRead(unreadDocs, currentUid)
                    }

                    // Process on-device translation using Google ML Kit
                    translateIncomingMessages(rawMessages)
                }
            }
    }

    private fun markMessagesAsRead(
        docRefs: List<com.google.firebase.firestore.DocumentReference>,
        userId: String
    ) {
        val batch = firestore.batch()
        for (ref in docRefs) {
            batch.update(ref, "read", true)
            batch.update(ref, "readBy", FieldValue.arrayUnion(userId))
        }
        batch.commit().addOnFailureListener { e ->
            Log.e("ChatViewModel", "Failed to mark messages as read: ${e.message}")
        }
    }

    fun markCurrentChatAsRead() {
        val currentUid = currentUser?.id ?: return
        val unreadList = _messages.value.filter { it.senderId != currentUid && (!it.read || !it.readBy.contains(currentUid)) }
        if (unreadList.isEmpty() || currentChatId.isBlank()) return

        val batch = firestore.batch()
        for (msg in unreadList) {
            val ref = firestore.collection("chats").document(currentChatId).collection("messages").document(msg.id)
            batch.update(ref, "read", true)
            batch.update(ref, "readBy", FieldValue.arrayUnion(currentUid))
        }
        batch.commit().addOnFailureListener { e ->
            Log.e("ChatViewModel", "Failed to mark all as read: ${e.message}")
        }
    }

    private fun translateIncomingMessages(rawMessages: List<ChatMessage>) {
        viewModelScope.launch {
            _isTranslating.value = true
            val deviceLanguage = Locale.getDefault().language

            val processedMessages = rawMessages.map { message ->
                // If message is from recipient and language is different from device language, translate via ML Kit
                if (message.senderId != currentUser?.id && !message.languageCode.equals(deviceLanguage, ignoreCase = true)) {
                    val translatedText = TranslationManager.translateText(
                        text = message.text,
                        sourceLanguageCode = message.languageCode,
                        targetLanguageCode = deviceLanguage
                    )
                    message.copy(translatedText = translatedText)
                } else {
                    message
                }
            }

            _messages.value = processedMessages
            _isTranslating.value = false
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || currentChatId.isBlank() || currentUser == null) return

        val senderLanguageCode = Locale.getDefault().language
        val senderUid = currentUser!!.id
        val senderName = currentUser!!.name
        val trimmedText = text.trim()
        val targetUid = targetUser?.id ?: ""

        val messageMap = hashMapOf(
            "senderId" to senderUid,
            "senderName" to senderName,
            "text" to trimmedText,
            "languageCode" to senderLanguageCode,
            "timestamp" to System.currentTimeMillis(),
            "read" to false,
            "readBy" to listOf(senderUid)
        )

        firestore.collection("chats")
            .document(currentChatId)
            .collection("messages")
            .add(messageMap)
            .addOnSuccessListener {
                // Update chat metadata and notification trigger for target user
                if (targetUid.isNotBlank()) {
                    val chatMeta = mapOf(
                        "lastMessage" to trimmedText,
                        "lastSenderId" to senderUid,
                        "lastSenderName" to senderName,
                        "lastTimestamp" to System.currentTimeMillis(),
                        "participants" to listOf(senderUid, targetUid)
                    )
                    firestore.collection("chats").document(currentChatId).set(chatMeta, SetOptions.merge())

                    // Record notification event for target recipient
                    val notifMap = mapOf(
                        "type" to "chat",
                        "senderId" to senderUid,
                        "senderName" to senderName,
                        "receiverId" to targetUid,
                        "chatId" to currentChatId,
                        "text" to trimmedText,
                        "timestamp" to System.currentTimeMillis()
                    )
                    firestore.collection("notifications").add(notifMap)
                }
            }
            .addOnFailureListener { e ->
                Log.e("ChatViewModel", "Failed to send message: ${e.message}")
            }
    }

    fun initiateCallInvitation(isAudioOnly: Boolean, onComplete: (String) -> Unit = {}) {
        val user = currentUser ?: return
        val target = targetUser ?: return
        val roomId = getJitsiRoomId()
        val callDoc = firestore.collection("calls").document()
        val callId = callDoc.id

        val callData = hashMapOf(
            "id" to callId,
            "callerId" to user.id,
            "callerName" to user.name,
            "callerAvatar" to user.profilePic,
            "receiverId" to target.id,
            "roomId" to roomId,
            "isAudioOnly" to isAudioOnly,
            "timestamp" to System.currentTimeMillis(),
            "status" to "ringing"
        )

        callDoc.set(callData)
            .addOnSuccessListener {
                onComplete(callId)
            }
            .addOnFailureListener { e ->
                Log.e("ChatViewModel", "Failed to create call invitation: ${e.message}")
                onComplete(callId)
            }
    }

    fun endCallInvitation(callId: String) {
        if (callId.isBlank()) return
        firestore.collection("calls").document(callId).update("status", "ended")
            .addOnFailureListener { e ->
                Log.e("ChatViewModel", "Failed to update call status: ${e.message}")
            }
    }

    fun getJitsiRoomId(): String {
        val uid1 = currentUser?.id ?: "user1"
        val uid2 = targetUser?.id ?: "user2"
        return JitsiHelper.generateSecureRoomId(uid1, uid2)
    }

    override fun onCleared() {
        super.onCleared()
        userListener?.remove()
        messagesListener?.remove()
    }
}
