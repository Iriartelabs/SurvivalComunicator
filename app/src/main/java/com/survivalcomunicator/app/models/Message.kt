package com.survivalcomunicator.app.models

import java.util.UUID

data class Message(
    val id: String = UUID.randomUUID().toString(),
    val senderId: String,
    val recipientId: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val isDelivered: Boolean = false,
    val type: MessageType = MessageType.TEXT
)

enum class MessageType {
    TEXT,
    VOICE,
    IMAGE,
    WALKIE_TALKIE
}