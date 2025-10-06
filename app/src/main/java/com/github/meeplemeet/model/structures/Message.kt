package com.github.meeplemeet.model.structures

import com.google.firebase.Timestamp
import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val senderId: String = "",
    val content: String = "",
    val createdAt: Timestamp = Timestamp.now()
)
