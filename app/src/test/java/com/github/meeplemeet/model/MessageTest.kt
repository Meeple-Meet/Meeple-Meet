package com.github.meeplemeet.model

import com.github.meeplemeet.model.discussions.Message
import com.github.meeplemeet.model.discussions.MessageNoUid
import com.github.meeplemeet.model.discussions.Poll
import com.github.meeplemeet.model.discussions.fromNoUid
import com.github.meeplemeet.model.discussions.toNoUid
import com.google.firebase.Timestamp
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import org.junit.Test

/** Unit tests for the Message and MessageNoUid data classes and conversion functions. */
class MessageTest {

  @Test
  fun `Message can be created with text content only`() {
    val message =
        Message(
            uid = "msg1",
            senderId = "user1",
            content = "Hello world",
            createdAt = Timestamp.now(),
            poll = null,
            photoUrl = null)

    assertEquals("msg1", message.uid)
    assertEquals("user1", message.senderId)
    assertEquals("Hello world", message.content)
    assertNull(message.poll)
    assertNull(message.photoUrl)
  }

  @Test
  fun `Message can be created with a poll`() {
    val poll = Poll(question = "Test?", options = listOf("A", "B"))
    val message =
        Message(
            uid = "msg1",
            senderId = "user1",
            content = "Vote on this",
            createdAt = Timestamp.now(),
            poll = poll,
            photoUrl = null)

    assertNotNull(message.poll)
    assertEquals("Test?", message.poll?.question)
    assertNull(message.photoUrl)
  }

  @Test
  fun `Message can be created with a photo`() {
    val message =
        Message(
            uid = "msg1",
            senderId = "user1",
            content = "Check this out",
            createdAt = Timestamp.now(),
            poll = null,
            photoUrl = "https://example.com/photo.jpg")

    assertNotNull(message.photoUrl)
    assertEquals("https://example.com/photo.jpg", message.photoUrl)
    assertNull(message.poll)
  }

  @Test(expected = IllegalArgumentException::class)
  fun `Message cannot have both poll and photoUrl`() {
    val poll = Poll(question = "Test?", options = listOf("A", "B"))
    Message(
        uid = "msg1",
        senderId = "user1",
        content = "Invalid",
        createdAt = Timestamp.now(),
        poll = poll,
        photoUrl = "https://example.com/photo.jpg")
  }

  @Test
  fun `MessageNoUid can be created with text content only`() {
    val messageNoUid =
        MessageNoUid(
            senderId = "user1",
            content = "Hello world",
            createdAt = Timestamp.now(),
            poll = null,
            photoUrl = null)

    assertEquals("user1", messageNoUid.senderId)
    assertEquals("Hello world", messageNoUid.content)
    assertNull(messageNoUid.poll)
    assertNull(messageNoUid.photoUrl)
  }

  @Test
  fun `MessageNoUid can be created with a poll`() {
    val poll = Poll(question = "Test?", options = listOf("A", "B"))
    val messageNoUid =
        MessageNoUid(
            senderId = "user1",
            content = "Vote on this",
            createdAt = Timestamp.now(),
            poll = poll,
            photoUrl = null)

    assertNotNull(messageNoUid.poll)
    assertEquals("Test?", messageNoUid.poll?.question)
    assertNull(messageNoUid.photoUrl)
  }

  @Test
  fun `MessageNoUid can be created with a photo`() {
    val messageNoUid =
        MessageNoUid(
            senderId = "user1",
            content = "Check this out",
            createdAt = Timestamp.now(),
            poll = null,
            photoUrl = "https://example.com/photo.jpg")

    assertNotNull(messageNoUid.photoUrl)
    assertEquals("https://example.com/photo.jpg", messageNoUid.photoUrl)
    assertNull(messageNoUid.poll)
  }

  @Test(expected = IllegalArgumentException::class)
  fun `MessageNoUid cannot have both poll and photoUrl`() {
    val poll = Poll(question = "Test?", options = listOf("A", "B"))
    MessageNoUid(
        senderId = "user1",
        content = "Invalid",
        createdAt = Timestamp.now(),
        poll = poll,
        photoUrl = "https://example.com/photo.jpg")
  }

  @Test
  fun `toNoUid correctly converts Message to MessageNoUid`() {
    val timestamp = Timestamp.now()
    val message =
        Message(
            uid = "msg1",
            senderId = "user1",
            content = "Test message",
            createdAt = timestamp,
            poll = null,
            photoUrl = null)

    val messageNoUid = toNoUid(message)

    assertEquals("user1", messageNoUid.senderId)
    assertEquals("Test message", messageNoUid.content)
    assertEquals(timestamp, messageNoUid.createdAt)
    assertNull(messageNoUid.poll)
    assertNull(messageNoUid.photoUrl)
  }

  @Test
  fun `toNoUid preserves poll data`() {
    val poll = Poll(question = "Test?", options = listOf("A", "B"))
    val timestamp = Timestamp.now()
    val message =
        Message(
            uid = "msg1",
            senderId = "user1",
            content = "Poll message",
            createdAt = timestamp,
            poll = poll,
            photoUrl = null)

    val messageNoUid = toNoUid(message)

    assertNotNull(messageNoUid.poll)
    assertEquals("Test?", messageNoUid.poll?.question)
    assertEquals(listOf("A", "B"), messageNoUid.poll?.options)
  }

  @Test
  fun `toNoUid preserves photoUrl`() {
    val timestamp = Timestamp.now()
    val message =
        Message(
            uid = "msg1",
            senderId = "user1",
            content = "Photo message",
            createdAt = timestamp,
            poll = null,
            photoUrl = "https://example.com/photo.jpg")

    val messageNoUid = toNoUid(message)

    assertEquals("https://example.com/photo.jpg", messageNoUid.photoUrl)
  }

  @Test
  fun `fromNoUid correctly converts MessageNoUid to Message`() {
    val timestamp = Timestamp.now()
    val messageNoUid =
        MessageNoUid(
            senderId = "user1",
            content = "Test message",
            createdAt = timestamp,
            poll = null,
            photoUrl = null)

    val message = fromNoUid("msg123", messageNoUid)

    assertEquals("msg123", message.uid)
    assertEquals("user1", message.senderId)
    assertEquals("Test message", message.content)
    assertEquals(timestamp, message.createdAt)
    assertNull(message.poll)
    assertNull(message.photoUrl)
  }

  @Test
  fun `fromNoUid preserves poll data`() {
    val poll = Poll(question = "Test?", options = listOf("A", "B"))
    val timestamp = Timestamp.now()
    val messageNoUid =
        MessageNoUid(
            senderId = "user1",
            content = "Poll message",
            createdAt = timestamp,
            poll = poll,
            photoUrl = null)

    val message = fromNoUid("msg123", messageNoUid)

    assertNotNull(message.poll)
    assertEquals("Test?", message.poll?.question)
    assertEquals(listOf("A", "B"), message.poll?.options)
  }

  @Test
  fun `fromNoUid preserves photoUrl`() {
    val timestamp = Timestamp.now()
    val messageNoUid =
        MessageNoUid(
            senderId = "user1",
            content = "Photo message",
            createdAt = timestamp,
            poll = null,
            photoUrl = "https://example.com/photo.jpg")

    val message = fromNoUid("msg123", messageNoUid)

    assertEquals("https://example.com/photo.jpg", message.photoUrl)
  }

  @Test
  fun `round trip conversion preserves all data`() {
    val timestamp = Timestamp.now()
    val originalMessage =
        Message(
            uid = "msg1",
            senderId = "user1",
            content = "Original message",
            createdAt = timestamp,
            poll = null,
            photoUrl = "https://example.com/photo.jpg")

    val messageNoUid = toNoUid(originalMessage)
    val reconstructed = fromNoUid("msg1", messageNoUid)

    assertEquals(originalMessage.uid, reconstructed.uid)
    assertEquals(originalMessage.senderId, reconstructed.senderId)
    assertEquals(originalMessage.content, reconstructed.content)
    assertEquals(originalMessage.createdAt, reconstructed.createdAt)
    assertEquals(originalMessage.poll, reconstructed.poll)
    assertEquals(originalMessage.photoUrl, reconstructed.photoUrl)
  }
}
