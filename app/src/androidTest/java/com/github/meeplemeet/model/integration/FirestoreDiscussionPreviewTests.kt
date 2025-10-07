package com.github.meeplemeet.model.integration

import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.utils.FirestoreTests
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class FirestoreDiscussionPreviewTests : FirestoreTests() {
  lateinit var account1: Account
  lateinit var account2: Account
  lateinit var account3: Account

  @Before
  fun setup() {
    runBlocking {
      account1 = viewModels[0].createAccount("Antoine")
      account2 = viewModels[0].createAccount("Marco")
      account3 = viewModels[0].createAccount("Thomas")
    }
  }

  @Test
  fun sendingAMessageChangesTheDiscussionPreview() = runTest {
    val content = "Hi"
    var (acc, discussion) = viewModels[0].createDiscussion("Test", "", account1, account2, account3)
    account1 = acc
    discussion = viewModels[1].sendMessageToDiscussion(discussion, account1, content)

    account1 = viewModels[0].getAccount(account1.uid)
    account2 = viewModels[1].getAccount(account2.uid)
    account3 = viewModels[2].getAccount(account3.uid)

    assertEquals(content, account1.previews[discussion.uid]!!.lastMessage)
    assertEquals(account1.uid, account1.previews[discussion.uid]!!.lastMessageSender)
    assertEquals(0, account1.previews[discussion.uid]!!.unreadCount)

    assertEquals(content, account2.previews[discussion.uid]!!.lastMessage)
    assertEquals(account1.uid, account2.previews[discussion.uid]!!.lastMessageSender)
    assertEquals(1, account2.previews[discussion.uid]!!.unreadCount)

    assertEquals(content, account3.previews[discussion.uid]!!.lastMessage)
    assertEquals(account1.uid, account3.previews[discussion.uid]!!.lastMessageSender)
    assertEquals(1, account3.previews[discussion.uid]!!.unreadCount)
  }

  @Test
  fun sendingMultipleMessagesChangesUnreadCount() = runTest {
    val content = "Hi"
    var (acc, discussion) = viewModels[0].createDiscussion("Test", "", account1, account2, account3)
    account1 = acc

    for (i in 0..10) {
      discussion = viewModels[1].sendMessageToDiscussion(discussion, account1, "${content}${i}")
    }

    account1 = viewModels[0].getAccount(account1.uid)
    account2 = viewModels[1].getAccount(account2.uid)
    account3 = viewModels[2].getAccount(account3.uid)

    assertEquals("${content}10", account1.previews[discussion.uid]!!.lastMessage)
    assertEquals(account1.uid, account1.previews[discussion.uid]!!.lastMessageSender)
    assertEquals(0, account1.previews[discussion.uid]!!.unreadCount)

    assertEquals("${content}10", account2.previews[discussion.uid]!!.lastMessage)
    assertEquals(account1.uid, account2.previews[discussion.uid]!!.lastMessageSender)
    assertEquals(11, account2.previews[discussion.uid]!!.unreadCount)

    assertEquals("${content}10", account3.previews[discussion.uid]!!.lastMessage)
    assertEquals(account1.uid, account3.previews[discussion.uid]!!.lastMessageSender)
    assertEquals(11, account3.previews[discussion.uid]!!.unreadCount)
  }

  @Test
  fun sendingMessagesFromMultiplePeopleWorksAsExpected() = runTest {
    var (acc, discussion) = viewModels[0].createDiscussion("Test", "", account1, account2, account3)
    account1 = acc
    discussion = viewModels[1].sendMessageToDiscussion(discussion, account1, "Hi")
    discussion = viewModels[2].sendMessageToDiscussion(discussion, account2, "Hello")
    discussion = viewModels[3].sendMessageToDiscussion(discussion, account3, "How are you ?")

    account1 = viewModels[0].getAccount(account1.uid)
    account2 = viewModels[1].getAccount(account2.uid)
    account3 = viewModels[2].getAccount(account3.uid)

    assertEquals("How are you ?", account1.previews[discussion.uid]!!.lastMessage)
    assertEquals(account3.uid, account1.previews[discussion.uid]!!.lastMessageSender)
    assertEquals(2, account1.previews[discussion.uid]!!.unreadCount)

    assertEquals("How are you ?", account2.previews[discussion.uid]!!.lastMessage)
    assertEquals(account3.uid, account2.previews[discussion.uid]!!.lastMessageSender)
    assertEquals(1, account2.previews[discussion.uid]!!.unreadCount)

    assertEquals("How are you ?", account3.previews[discussion.uid]!!.lastMessage)
    assertEquals(account3.uid, account3.previews[discussion.uid]!!.lastMessageSender)
    assertEquals(0, account3.previews[discussion.uid]!!.unreadCount)
  }
}
