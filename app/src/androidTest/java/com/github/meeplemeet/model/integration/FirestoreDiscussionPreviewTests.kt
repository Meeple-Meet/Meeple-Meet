package com.github.meeplemeet.model.integration

import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.structures.Discussion
import com.github.meeplemeet.model.structures.DiscussionPreview
import com.github.meeplemeet.model.systems.FirestoreRepository
import com.github.meeplemeet.model.utils.FirestoreTests
import com.github.meeplemeet.model.viewmodels.FirestoreViewModel
import io.mockk.coEvery
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreDiscussionPreviewTests : FirestoreTests() {
  private val repository = mockk<FirestoreRepository>()
  private lateinit var viewModel: FirestoreViewModel

  private lateinit var account1: Account
  private lateinit var account2: Account
  private lateinit var account3: Account

  @Before
  fun setup() {
    Dispatchers.setMain(StandardTestDispatcher())
    viewModel = FirestoreViewModel(repository)
    account1 = Account(uid = "a1", name = "Antoine")
    account2 = Account(uid = "a2", name = "Marco")
    account3 = Account(uid = "a3", name = "Thomas")
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun sendingAMessageChangesTheDiscussionPreview() = runTest {
    val content = "Hi"
    val discussion =
        Discussion(
            uid = "d1",
            name = "Test",
            creatorId = account1.uid,
            participants = listOf(account1.uid, account2.uid, account3.uid),
            admins = listOf(account1.uid))

    coEvery { repository.createDiscussion(any(), any(), any(), any()) } returns
        Pair(account1, discussion)
    coEvery { repository.sendMessageToDiscussion(discussion, account1, content) } returns Unit

    val a1Preview =
        DiscussionPreview(lastMessage = content, lastMessageSender = account1.uid, unreadCount = 0)
    val a2Preview =
        DiscussionPreview(lastMessage = content, lastMessageSender = account1.uid, unreadCount = 1)
    val a3Preview =
        DiscussionPreview(lastMessage = content, lastMessageSender = account1.uid, unreadCount = 1)

    coEvery { repository.getAccount(account1.uid) } returns
        account1.copy(previews = mapOf(discussion.uid to a1Preview))
    coEvery { repository.getAccount(account2.uid) } returns
        account2.copy(previews = mapOf(discussion.uid to a2Preview))
    coEvery { repository.getAccount(account3.uid) } returns
        account3.copy(previews = mapOf(discussion.uid to a3Preview))

    viewModel.createDiscussion("Test", "", account1)
    advanceUntilIdle()

    viewModel.sendMessageToDiscussion(discussion, account1, content)
    advanceUntilIdle()

    viewModel.getAccount(account1.uid)
    advanceUntilIdle()
    val acc1 = viewModel.account.value!!
    viewModel.getAccount(account2.uid)
    advanceUntilIdle()
    val acc2 = viewModel.account.value!!
    viewModel.getAccount(account3.uid)
    advanceUntilIdle()
    val acc3 = viewModel.account.value!!

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
  fun sendingMultipleMessagesChangesUnreadCount() = runTest {
    val base = "Hi"
    val discussion =
        Discussion(
            uid = "d2",
            name = "Test",
            creatorId = account1.uid,
            participants = listOf(account1.uid, account2.uid, account3.uid),
            admins = listOf(account1.uid))

    coEvery { repository.createDiscussion(any(), any(), any(), any()) } returns
        Pair(account1, discussion)
    coEvery { repository.sendMessageToDiscussion(discussion, account1, any()) } returns Unit

    val last = "${base}10"
    val a1Preview =
        DiscussionPreview(lastMessage = last, lastMessageSender = account1.uid, unreadCount = 0)
    val a2Preview =
        DiscussionPreview(lastMessage = last, lastMessageSender = account1.uid, unreadCount = 11)
    val a3Preview =
        DiscussionPreview(lastMessage = last, lastMessageSender = account1.uid, unreadCount = 11)

    coEvery { repository.getAccount(account1.uid) } returns
        account1.copy(previews = mapOf(discussion.uid to a1Preview))
    coEvery { repository.getAccount(account2.uid) } returns
        account2.copy(previews = mapOf(discussion.uid to a2Preview))
    coEvery { repository.getAccount(account3.uid) } returns
        account3.copy(previews = mapOf(discussion.uid to a3Preview))

    viewModel.createDiscussion("Test", "", account1)
    advanceUntilIdle()

    for (i in 0..10) {
      viewModel.sendMessageToDiscussion(discussion, account1, "$base$i")
    }
    advanceUntilIdle()

    viewModel.getAccount(account1.uid)
    advanceUntilIdle()
    val acc1 = viewModel.account.value!!
    viewModel.getAccount(account2.uid)
    advanceUntilIdle()
    val acc2 = viewModel.account.value!!
    viewModel.getAccount(account3.uid)
    advanceUntilIdle()
    val acc3 = viewModel.account.value!!

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
  fun sendingMessagesFromMultiplePeopleWorksAsExpected() = runTest {
    val discussion =
        Discussion(
            uid = "d3",
            name = "Test",
            creatorId = account1.uid,
            participants = listOf(account1.uid, account2.uid, account3.uid),
            admins = listOf(account1.uid))

    coEvery { repository.createDiscussion(any(), any(), any(), any()) } returns
        Pair(account1, discussion)
    coEvery { repository.sendMessageToDiscussion(discussion, account1, "Hi") } returns Unit
    coEvery { repository.sendMessageToDiscussion(discussion, account2, "Hello") } returns Unit
    coEvery { repository.sendMessageToDiscussion(discussion, account3, "How are you ?") } returns
        Unit

    val last = "How are you ?"
    val a1Preview =
        DiscussionPreview(lastMessage = last, lastMessageSender = account3.uid, unreadCount = 2)
    val a2Preview =
        DiscussionPreview(lastMessage = last, lastMessageSender = account3.uid, unreadCount = 1)
    val a3Preview =
        DiscussionPreview(lastMessage = last, lastMessageSender = account3.uid, unreadCount = 0)

    coEvery { repository.getAccount(account1.uid) } returns
        account1.copy(previews = mapOf(discussion.uid to a1Preview))
    coEvery { repository.getAccount(account2.uid) } returns
        account2.copy(previews = mapOf(discussion.uid to a2Preview))
    coEvery { repository.getAccount(account3.uid) } returns
        account3.copy(previews = mapOf(discussion.uid to a3Preview))

    viewModel.createDiscussion("Test", "", account1)
    advanceUntilIdle()

    viewModel.sendMessageToDiscussion(discussion, account1, "Hi")
    viewModel.sendMessageToDiscussion(discussion, account2, "Hello")
    viewModel.sendMessageToDiscussion(discussion, account3, last)
    advanceUntilIdle()

    viewModel.getAccount(account1.uid)
    advanceUntilIdle()
    val acc1 = viewModel.account.value!!
    viewModel.getAccount(account2.uid)
    advanceUntilIdle()
    val acc2 = viewModel.account.value!!
    viewModel.getAccount(account3.uid)
    advanceUntilIdle()
    val acc3 = viewModel.account.value!!

    assertEquals(last, acc1.previews[discussion.uid]!!.lastMessage)
    assertEquals(account3.uid, acc1.previews[discussion.uid]!!.lastMessageSender)
    assertEquals(2, acc1.previews[discussion.uid]!!.unreadCount)

    assertEquals(last, acc2.previews[discussion.uid]!!.lastMessage)
    assertEquals(account3.uid, acc2.previews[discussion.uid]!!.lastMessageSender)
    assertEquals(1, acc2.previews[discussion.uid]!!.unreadCount)

    assertEquals(last, acc3.previews[discussion.uid]!!.lastMessage)
    assertEquals(account3.uid, acc3.previews[discussion.uid]!!.lastMessageSender)
    assertEquals(0, acc3.previews[discussion.uid]!!.unreadCount)
  }
}
