package com.survivalcomunicator.app.models

import java.util.UUID

data class User(
    val id: String = UUID.randomUUID().toString(),
    val username: String,
    val publicKey: String,
    val lastSeen: Long = System.currentTimeMillis(),
    val serverAddress: String? = null
)