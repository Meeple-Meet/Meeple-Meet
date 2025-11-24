package com.github.meeplemeet.model

import com.github.meeplemeet.model.discussions.Discussion
import com.github.meeplemeet.model.discussions.Message
import com.github.meeplemeet.model.discussions.toPreview
import com.google.firebase.Timestamp
import junit.framework.TestCase.assertEquals
import org.junit.Test

/** Unit tests for the DiscussionPreview toPreview function with optional lastMessage parameter. */
class DiscussionPreviewTest {

  @Test
  fun `toPreview creates preview without lastMessage parameter`() {
    val discussion =
        Discussion(
            uid = "disc1",
            creatorId = "user1",
            name = "Test Discussion",
            description = "A test",
            participants = listOf("user1"),
            admins = listOf("user1"),
            createdAt = Timestamp.now(),
            session = null,
            profilePictureUrl = null)

    val preview = toPreview(discussion.uid)

    assertEquals("disc1", preview.uid)
    assertEquals("", preview.lastMessage)
    assertEquals("", preview.lastMessageSender)
  }

  @Test
  fun `toPreview creates preview with lastMessage parameter`() {
    val timestamp = Timestamp.now()
    val discussion =
        Discussion(
            uid = "disc1",
            creatorId = "user1",
            name = "Test Discussion",
            description = "A test",
            participants = listOf("user1", "user2"),
            admins = listOf("user1"),
            createdAt = timestamp,
            session = null,
            profilePictureUrl = null)

    val lastMessage =
        Message(
            uid = "msg1",
            senderId = "user2",
            content = "Hello everyone!",
            createdAt = timestamp,
            poll = null,
            photoUrl = null)

    val preview = toPreview(discussion.uid, lastMessage)

    assertEquals("disc1", preview.uid)
    assertEquals("Hello everyone!", preview.lastMessage)
    assertEquals("user2", preview.lastMessageSender)
    assertEquals(timestamp, preview.lastMessageAt)
  }

  @Test
  fun `toPreview creates preview with null lastMessage`() {
    val discussion =
        Discussion(
            uid = "disc1",
            creatorId = "user1",
            name = "Test Discussion",
            description = "A test",
            participants = listOf("user1"),
            admins = listOf("user1"),
            createdAt = Timestamp.now(),
            session = null,
            profilePictureUrl = null)

    val preview = toPreview(discussion.uid, null)

    assertEquals("disc1", preview.uid)
    assertEquals("", preview.lastMessage)
    assertEquals("", preview.lastMessageSender)
  }

  @Test
  fun `toPreview preserves discussion ID`() {
    val discussionId = "unique-discussion-id"
    val discussion =
        Discussion(
            uid = discussionId,
            creatorId = "user1",
            name = "Test Discussion",
            description = "A test",
            participants = listOf("user1"),
            admins = listOf("user1"),
            createdAt = Timestamp.now(),
            session = null,
            profilePictureUrl = null)

    val message =
        Message(
            uid = "msg1",
            senderId = "user1",
            content = "Test",
            createdAt = Timestamp.now(),
            poll = null,
            photoUrl = null)

    val preview = toPreview(discussion.uid, message)

    assertEquals(discussionId, preview.uid)
  }

  @Test
  fun `toPreview sets unreadCount to 0`() {
    val discussion =
        Discussion(
            uid = "disc1",
            creatorId = "user1",
            name = "Test Discussion",
            description = "A test",
            participants = listOf("user1"),
            admins = listOf("user1"),
            createdAt = Timestamp.now(),
            session = null,
            profilePictureUrl = null)

    val message =
        Message(
            uid = "msg1",
            senderId = "user1",
            content = "Test",
            createdAt = Timestamp.now(),
            poll = null,
            photoUrl = null)

    val preview = toPreview(discussion.uid, message)

    assertEquals(0, preview.unreadCount)
  }
}
