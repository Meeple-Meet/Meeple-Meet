package com.github.meeplemeet.integration

import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.discussions.DiscussionRepository
import com.github.meeplemeet.utils.FirestoreTests
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

class FirestoreDiscussionPreviewTests : FirestoreTests() {
  private lateinit var repository: DiscussionRepository

  private lateinit var account1: Account
  private lateinit var account2: Account
  private lateinit var account3: Account

  @Before
  fun setup() {
    repository = DiscussionRepository()
    runBlocking {
      account1 =
          repository.createAccount(
              "Antoine", "Antoine", email = "Antoine@example.com", photoUrl = null)
      account2 =
          repository.createAccount("Marco", "Marco", email = "Marco@example.com", photoUrl = null)
      account3 =
          repository.createAccount(
              "Thomas", "Thomas", email = "Thomas@example.com", photoUrl = null)
    }
  }

  @Test
  fun sendingAMessageChangesTheDiscussionPreview() = runBlocking {
    val content = "Hi"

    val discussion =
        repository.createDiscussion("Test", "", account1.uid, listOf(account2.uid, account3.uid))

    repository.sendMessageToDiscussion(discussion, account1, content)

    val acc1 = repository.getAccount(account1.uid)
    val acc2 = repository.getAccount(account2.uid)
    val acc3 = repository.getAccount(account3.uid)

    assertEquals(content, acc1.previews[discussion.uid]!!.lastMessage)
    assertEquals(account1.uid, acc1.previews[discussion.uid]!!.lastMessageSender)
    assertEquals(0, acc1.previews[discussion.uid]!!.unreadCount)

    assertEquals(content, acc2.previews[discussion.uid]!!.lastMessage)
    assertEquals(account1.uid, acc2.previews[discussion.uid]!!.lastMessageSender)
    assertEquals(1, acc2.previews[discussion.uid]!!.unreadCount)

    assertEquals(content, acc3.previews[discussion.uid]!!.lastMessage)
    assertEquals(account1.uid, acc3.previews[discussion.uid]!!.lastMessageSender)
    assertEquals(1, acc3.previews[discussion.uid]!!.unreadCount)
  }

  @Test
  fun sendingMultipleMessagesChangesUnreadCount() = runBlocking {
    val base = "Hi"

    val discussion =
        repository.createDiscussion("Test", "", account1.uid, listOf(account2.uid, account3.uid))

    for (i in 0..10) {
      repository.sendMessageToDiscussion(discussion, account1, "$base$i")
    }

    val acc1 = repository.getAccount(account1.uid)
    val acc2 = repository.getAccount(account2.uid)
    val acc3 = repository.getAccount(account3.uid)

    val last = "${base}10"
    assertEquals(last, acc1.previews[discussion.uid]!!.lastMessage)
    assertEquals(account1.uid, acc1.previews[discussion.uid]!!.lastMessageSender)
    assertEquals(0, acc1.previews[discussion.uid]!!.unreadCount)

    assertEquals(last, acc2.previews[discussion.uid]!!.lastMessage)
    assertEquals(account1.uid, acc2.previews[discussion.uid]!!.lastMessageSender)
    assertEquals(11, acc2.previews[discussion.uid]!!.unreadCount)

    assertEquals(last, acc3.previews[discussion.uid]!!.lastMessage)
    assertEquals(account1.uid, acc3.previews[discussion.uid]!!.lastMessageSender)
    assertEquals(11, acc3.previews[discussion.uid]!!.unreadCount)
  }

  @Test
  fun sendingMessagesFromMultiplePeopleWorksAsExpected() = runBlocking {
    val discussion =
        repository.createDiscussion("Test", "", account1.uid, listOf(account2.uid, account3.uid))

    val last = "How are you ?"
    repository.sendMessageToDiscussion(discussion, account1, "Hi")
    repository.sendMessageToDiscussion(discussion, account2, "Hello")
    repository.sendMessageToDiscussion(discussion, account3, last)

    val acc1 = repository.getAccount(account1.uid)
    val acc2 = repository.getAccount(account2.uid)
    val acc3 = repository.getAccount(account3.uid)

    assertEquals(last, acc3.previews[discussion.uid]!!.lastMessage)
    assertEquals(account3.uid, acc3.previews[discussion.uid]!!.lastMessageSender)
    assertEquals(0, acc3.previews[discussion.uid]!!.unreadCount)

    assertEquals(last, acc2.previews[discussion.uid]!!.lastMessage)
    assertEquals(account3.uid, acc2.previews[discussion.uid]!!.lastMessageSender)
    assertEquals(1, acc2.previews[discussion.uid]!!.unreadCount)

    assertEquals(last, acc1.previews[discussion.uid]!!.lastMessage)
    assertEquals(account3.uid, acc1.previews[discussion.uid]!!.lastMessageSender)
    assertEquals(2, acc1.previews[discussion.uid]!!.unreadCount)
  }
}
