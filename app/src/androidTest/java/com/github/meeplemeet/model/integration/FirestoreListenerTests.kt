package com.github.meeplemeet.model.integration

import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.utils.FirestoreTests
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class FirestoreListenerTests : FirestoreTests() {
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
  fun previewsFlow_updatesOnMessage() = runTest {
    val content = "Hi"
    val (acc, discussion) = viewModels[0].createDiscussion("Test", "", account1, account2, account3)
    account1 = acc

    val flow = viewModels[1].previewsFlow(account2.uid)
    val initial = flow.first()
    assertTrue(initial.isEmpty())

    viewModels[2].sendMessageToDiscussion(discussion, account1, content)

    val updated = flow.first { it.containsKey(discussion.uid) }
    val preview = updated[discussion.uid]!!

    assertEquals(content, preview.lastMessage)
    assertEquals(account1.uid, preview.lastMessageSender)
    assertEquals(1, preview.unreadCount)
    assertNotNull(preview.lastMessageAt)
  }

  @Test
  fun previewFlow_singleItem_updates_and_read_resets_unread() = runTest {
    val (acc, discussion) = viewModels[0].createDiscussion("Test", "", account1, account2, account3)
    account1 = acc

    val itemFlow = viewModels[1].previewFlow(account3.uid, discussion.uid)
    val initial = itemFlow.first()
    assertEquals(null, initial)

    viewModels[2].sendMessageToDiscussion(discussion, account1, "Ping")

    val afterMessage = itemFlow.first { it != null }!!
    assertEquals("Ping", afterMessage.lastMessage)
    assertEquals(account1.uid, afterMessage.lastMessageSender)
    assertEquals(1, afterMessage.unreadCount)

    // simulate reading
    val fresh = viewModels[3].getDiscussion(discussion.uid)
    viewModels[3].readDiscussionMessages(account3, fresh)

    val afterRead = itemFlow.first { it?.unreadCount == 0 }!!
    assertEquals(0, afterRead.unreadCount)
  }

  @Test
  fun discussionFlow_emits_and_updates_on_message() = runTest {
    val (acc, discussion) = viewModels[0].createDiscussion("Test", "", account1, account2, account3)
    account1 = acc

    val dFlow = viewModels[1].discussionFlow(discussion.uid).filterNotNull()

    val firstSnapshot = dFlow.first()
    assertTrue(firstSnapshot.messages.isEmpty())

    viewModels[2].sendMessageToDiscussion(discussion, account2, "Hello")

    val afterMessage = dFlow.first { it.messages.size == 1 }
    assertEquals(1, afterMessage.messages.size)
    assertEquals("Hello", afterMessage.messages.last().content)
    assertEquals(account2.uid, afterMessage.messages.last().senderId)
  }
}
