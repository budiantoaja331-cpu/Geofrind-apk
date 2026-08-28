package com.example.model

data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val languageCode: String = "en",
    val timestamp: Long = System.currentTimeMillis(),
    val translatedText: String? = null,
    val read: Boolean = false,
    val readBy: List<String> = emptyList()
)
