package com.github.meeplemeet.model.integration

import com.github.meeplemeet.model.utils.FirestoreTests
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreListenerTests : FirestoreTests() {
  /*
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
  fun previewsFlow_updatesOnMessage() = runTest {
    val content = "Hi"
    val discussion = Discussion(
      uid = "d1",
      name = "Test",
      creatorId = account1.uid,
      participants = listOf(account1.uid, account2.uid, account3.uid),
      admins = listOf(account1.uid)
    )

    val previewsFlow = MutableSharedFlow<Map<String, DiscussionPreview>>(replay = 1)
    previewsFlow.tryEmit(emptyMap())

    val a2Preview = DiscussionPreview(
      lastMessage = content,
      lastMessageSender = account1.uid,
      unreadCount = 1
    )

    coEvery { repository.createDiscussion(any(), any(), any(), any()) } returns Pair(account1, discussion)
    coEvery { repository.listenMyPreviews(account2.uid) } returns previewsFlow
    coEvery { repository.sendMessageToDiscussion(discussion, account1, content) } answers {
      previewsFlow.tryEmit(mapOf(discussion.uid to a2Preview))
      discussion
    }

    viewModel.createDiscussion("Test", "", account1)
    advanceUntilIdle()

    val flow = viewModel.previewsFlow(account2.uid)
    val initial = flow.first()
    assertTrue(initial.isEmpty())

    viewModel.sendMessageToDiscussion(discussion, account1, content)
    advanceUntilIdle()

    val updated = flow.first { it.containsKey(discussion.uid) }
    val preview = updated[discussion.uid]!!

    assertEquals(content, preview.lastMessage)
    assertEquals(account1.uid, preview.lastMessageSender)
    assertEquals(1, preview.unreadCount)
    assertNotNull(preview.lastMessageAt)
  }

  @Test
  fun previewFlow_singleItem_updates_and_read_resets_unread() = runTest {
    val discussion = Discussion(
      uid = "d2",
      name = "Test",
      creatorId = account1.uid,
      participants = listOf(account1.uid, account2.uid, account3.uid),
      admins = listOf(account1.uid)
    )

    val previewsFlow = MutableSharedFlow<Map<String, DiscussionPreview>>(replay = 1)
    previewsFlow.tryEmit(emptyMap())

    coEvery { repository.createDiscussion(any(), any(), any(), any()) } returns Pair(account1, discussion)
    coEvery { repository.listenMyPreviews(account3.uid) } returns previewsFlow
    coEvery { repository.getDiscussion(discussion.uid) } returns discussion
    coEvery { repository.sendMessageToDiscussion(discussion, account1, "Ping") } answers {
      previewsFlow.tryEmit(
        mapOf(
          discussion.uid to DiscussionPreview(
            lastMessage = "Ping",
            lastMessageSender = account1.uid,
            unreadCount = 1
          )
        )
      )
      discussion
    }
    coEvery { repository.readDiscussionMessages(account3.uid, discussion.uid, discussion.messages.last()) } answers {
      previewsFlow.tryEmit(
        mapOf(
          discussion.uid to DiscussionPreview(
            lastMessage = "Ping",
            lastMessageSender = account1.uid,
            unreadCount = 0
          )
        )
      )
      account3
    }

    viewModel.createDiscussion("Test", "", account1)
    advanceUntilIdle()

    val flow = viewModel.previewsFlow(account3.uid)
    val initial = flow.first()
    assertTrue(initial.isEmpty())

    viewModel.sendMessageToDiscussion(discussion, account1, "Ping")
    advanceUntilIdle()

    val afterMessageMap = flow.first { it[discussion.uid]?.unreadCount == 1 }
    val afterMessage = afterMessageMap[discussion.uid]!!
    assertEquals("Ping", afterMessage.lastMessage)
    assertEquals(account1.uid, afterMessage.lastMessageSender)
    assertEquals(1, afterMessage.unreadCount)

    viewModel.getDiscussion(discussion.uid)
    advanceUntilIdle()
    viewModel.readDiscussionMessages(account3, viewModel.discussion.value!!)
    advanceUntilIdle()

    val afterReadMap = flow.first { it[discussion.uid]?.unreadCount == 0 }
    val afterRead = afterReadMap[discussion.uid]!!
    assertEquals(0, afterRead.unreadCount)
  }


  @Test
  fun discussionFlow_emits_and_updates_on_message() = runTest {
    val discussionRef = Discussion(
      uid = "d3",
      name = "Test",
      creatorId = account1.uid,
      participants = listOf(account1.uid, account2.uid, account3.uid),
      admins = listOf(account1.uid)
    )

    val dFlow = MutableSharedFlow<Discussion>(replay = 1)
    val initialDiscussion = mockk<Discussion>()
    every { initialDiscussion.messages } returns emptyList()
    dFlow.tryEmit(initialDiscussion)

    val updatedDiscussion = mockk<Discussion>()
    val msg = mockk<com.github.meeplemeet.model.structures.Message>()
    every { msg.content } returns "Hello"
    every { msg.senderId } returns account2.uid
    every { updatedDiscussion.messages } returns listOf(msg)

    coEvery { repository.createDiscussion(any(), any(), any(), any()) } returns Pair(account1, discussionRef)
    coEvery { repository.listenDiscussion(discussionRef.uid) } returns dFlow
    coEvery { repository.sendMessageToDiscussion(discussionRef, account2, "Hello") } answers {
      dFlow.tryEmit(updatedDiscussion)
      updatedDiscussion
    }

    viewModel.createDiscussion("Test", "", account1)
    advanceUntilIdle()

    val flow = viewModel.discussionFlow(discussionRef.uid).filterNotNull()

    val firstSnapshot = flow.first()
    assertTrue(firstSnapshot.messages.isEmpty())

    viewModel.sendMessageToDiscussion(discussionRef, account2, "Hello")
    advanceUntilIdle()

    val afterMessage = flow.first { it.messages.size == 1 }
    assertEquals(1, afterMessage.messages.size)
    assertEquals("Hello", afterMessage.messages.last().content)
    assertEquals(account2.uid, afterMessage.messages.last().senderId)
  }
  */
}
