package com.github.meeplemeet.model.structures

import com.google.firebase.Timestamp
import kotlinx.serialization.Serializable

data class Discussion(
    val uid: String,
    val name: String,
    val description: String = "",
    val messages: List<Message> = emptyList(),
    val participants: List<String> = emptyList(),
    val admins: List<String> = emptyList(),
    val createdAt: Timestamp = Timestamp.now(),
)

@Serializable
data class DiscussionNoUid(
    val name: String = "",
    val description: String = "",
    val messages: List<Message> = emptyList(),
    val participants: List<String> = emptyList(),
    val admins: List<String> = emptyList(),
    val createdAt: Timestamp = Timestamp.now(),
)

fun toNoUid(discussion: Discussion): DiscussionNoUid =
    DiscussionNoUid(
        discussion.name,
        discussion.description,
        discussion.messages,
        discussion.participants,
        discussion.admins,
        discussion.createdAt)

fun fromNoUid(id: String, discussionNoUid: DiscussionNoUid): Discussion =
    Discussion(
        id,
        discussionNoUid.name,
        discussionNoUid.description,
        discussionNoUid.messages,
        discussionNoUid.participants,
        discussionNoUid.admins,
        discussionNoUid.createdAt)
