package com.github.meeplemeet.integration

import com.github.meeplemeet.model.repositories.FirestoreRepository
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.utils.FirestoreTests
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Test

class FirestoreListenerTests : FirestoreTests() {
  private lateinit var repository: FirestoreRepository

  private lateinit var account1: Account
  private lateinit var account2: Account
  private lateinit var account3: Account

  @Before
  fun setup() {
    repository = FirestoreRepository(Firebase.firestore)
    runBlocking {
      account1 =
          repository.createAccount(
              "Antoine", "Antoine", email = "antoine_listener@example.com", photoUrl = null)
      account2 =
          repository.createAccount(
              "Marco", "Marco", email = "marco_listener@example.com", photoUrl = null)
      account3 =
          repository.createAccount(
              "Thomas", "Thomas", email = "thomas_listener@example.com", photoUrl = null)
    }
  }

  /*
  @Test
  fun previewsFlow_updatesOnMessage() = runBlocking {
    val content = "Hi"

    // Create discussion with account1 as creator
    val (_, discussion) = repository.createDiscussion("Test", "Description", account1.uid)

    // Start listening to account2's previews BEFORE adding them
    val flow = repository.listenMyPreviews(account2.uid)

    // Add account2 to the discussion
    val discussionWithAccount2 = repository.addUserToDiscussion(discussion, account2.uid)

    // Wait for the preview to be created for account2
    val initial = withTimeout(10000) {
      flow.first { it.containsKey(discussion.uid) }
    }
    // Should have preview with 0 unread initially
    assertEquals(0, initial[discussion.uid]?.unreadCount)

    // Send a message from account1
    repository.sendMessageToDiscussion(discussionWithAccount2, account1, content)

    // Wait for the preview to update with the new message
    val updated = withTimeout(10000) {
      flow.first { it[discussion.uid]?.lastMessage == content }
    }
    val preview = updated[discussion.uid]!!

    assertEquals(content, preview.lastMessage)
    assertEquals(account1.uid, preview.lastMessageSender)
    assertEquals(1, preview.unreadCount)
    assertNotNull(preview.lastMessageAt)
  }

  @Test
  fun previewFlow_singleItem_updates_and_read_resets_unread() = runBlocking {
    // Create discussion with account1 as creator
    val (_, discussion) = repository.createDiscussion("Test", "Description", account1.uid)

    // Start listening to account3's previews BEFORE adding them
    val flow = repository.listenMyPreviews(account3.uid)

    // Add account3 to the discussion
    val discussionWithAccount3 = repository.addUserToDiscussion(discussion, account3.uid)

    // Wait for the preview to be created for account3
    val initial = withTimeout(10000) {
      flow.first { it.containsKey(discussion.uid) }
    }
    // Should have preview with 0 unread initially
    assertEquals(0, initial[discussion.uid]?.unreadCount)

    // Send a message from account1
    val updatedDiscussion = repository.sendMessageToDiscussion(discussionWithAccount3, account1, "Ping")

    // Wait for the preview to update with unread count = 1
    val afterMessageMap = withTimeout(10000) {
      flow.first { it[discussion.uid]?.unreadCount == 1 }
    }
    val afterMessage = afterMessageMap[discussion.uid]!!
    assertEquals("Ping", afterMessage.lastMessage)
    assertEquals(account1.uid, afterMessage.lastMessageSender)
    assertEquals(1, afterMessage.unreadCount)

    // Mark messages as read
    val lastMessage = updatedDiscussion.messages.last()
    repository.readDiscussionMessages(account3.uid, discussion.uid, lastMessage)

    // Wait for the preview to update with unread count = 0
    val afterReadMap = withTimeout(10000) {
      flow.first { it[discussion.uid]?.unreadCount == 0 }
    }
    val afterRead = afterReadMap[discussion.uid]!!
    assertEquals(0, afterRead.unreadCount)
  }
  */

  @Test
  fun discussionFlow_emits_and_updates_on_message() = runBlocking {
    // Create discussion with account1 as creator
    val (_, discussion) = repository.createDiscussion("Test", "Description", account1.uid)

    // Listen to discussion updates
    val flow = repository.listenDiscussion(discussion.uid).filterNotNull()

    // Get initial state (should have no messages)
    val firstSnapshot = withTimeout(10000) { flow.first() }
    assertTrue(firstSnapshot.messages.isEmpty())

    // Add account2 to the discussion
    val discussionWithAccount2 = repository.addUserToDiscussion(discussion, account2.uid)

    // Send a message from account2
    repository.sendMessageToDiscussion(discussionWithAccount2, account2, "Hello")

    // Wait for the discussion to update with the new message
    val afterMessage = withTimeout(10000) { flow.first { it.messages.size == 1 } }
    assertEquals(1, afterMessage.messages.size)
    assertEquals("Hello", afterMessage.messages.last().content)
    assertEquals(account2.uid, afterMessage.messages.last().senderId)
  }
}
