package com.github.meeplemeet.model.structures

import com.google.firebase.Timestamp
import kotlinx.serialization.Serializable

data class DiscussionPreview(
    val uid: String = "",
    val lastMessage: String = "",
    val lastMessageSender: String = "",
    val lastMessageAt: Timestamp = Timestamp.now(),
    val unreadCount: Int = 0
)

@Serializable
data class DiscussionPreviewNoUid(
    val lastMessage: String = "",
    val lastMessageSender: String = "",
    val lastMessageAt: Timestamp = Timestamp.now(),
    val unreadCount: Int = 0
)

fun toNoUid(discussionPreview: DiscussionPreview): DiscussionPreviewNoUid =
    DiscussionPreviewNoUid(
        discussionPreview.lastMessage,
        discussionPreview.lastMessageSender,
        discussionPreview.lastMessageAt,
        discussionPreview.unreadCount)

fun fromNoUid(id: String, discussionPreviewNoUid: DiscussionPreviewNoUid): DiscussionPreview =
    DiscussionPreview(
        id,
        discussionPreviewNoUid.lastMessage,
        discussionPreviewNoUid.lastMessageSender,
        discussionPreviewNoUid.lastMessageAt,
        discussionPreviewNoUid.unreadCount)
