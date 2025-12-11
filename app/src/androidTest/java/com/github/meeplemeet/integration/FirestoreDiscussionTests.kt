package com.github.meeplemeet.integration

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import com.github.meeplemeet.model.DiscussionNotFoundException
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.discussions.DiscussionDetailsViewModel
import com.github.meeplemeet.model.discussions.DiscussionViewModel
import com.github.meeplemeet.model.discussions.Message
import com.github.meeplemeet.utils.FirestoreTests
import java.io.File
import java.io.FileOutputStream
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class FirestoreDiscussionTests : FirestoreTests() {
  private var discussionViewModel = DiscussionViewModel()
  private val discussionDetailsViewModel = DiscussionDetailsViewModel()
  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  private lateinit var account1: Account
  private lateinit var account2: Account
  private lateinit var account3: Account

  @Before
  fun setup() {
    runBlocking {
      // Sign in anonymously for Storage authentication
      auth.signInAnonymously().await()

      account1 =
          accountRepository.createAccount(
              "Antoine", "Antoine", email = "Antoine@example.com", photoUrl = null)
      account2 =
          accountRepository.createAccount(
              "Marco", "Marco", email = "Marco@example.com", photoUrl = null)
      account3 =
          accountRepository.createAccount(
              "Thomas", "Thomas", email = "Thomas@example.com", photoUrl = null)
    }
  }

  private fun createTestImage(filename: String, height: Int, color: Int): String {
    val bitmap = Bitmap.createBitmap(800, height, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(color)

    val file = File(context.cacheDir, filename)
    FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out) }
    bitmap.recycle()

    return file.absolutePath
  }

  @Test
  fun canAddDiscussion() = runBlocking {
    val discussion = discussionRepository.createDiscussion("Test", "desc", account1.uid)

    assertEquals("Test", discussion.name)
    assertEquals("desc", discussion.description)
    assertEquals(account1.uid, discussion.creatorId)
    assertTrue(discussion.participants.contains(account1.uid))
    assertTrue(discussion.admins.contains(account1.uid))

    val fetched = discussionRepository.getDiscussion(discussion.uid)
    assertEquals(discussion.uid, fetched.uid)
    assertEquals(discussion.name, fetched.name)
  }

  @Test(expected = DiscussionNotFoundException::class)
  fun cannotGetNonExistingDiscussion() = runTest {
    discussionRepository.getDiscussion("invalid-id")
  }

  @Test
  fun canAddParticipant() = runBlocking {
    val discussion = discussionRepository.createDiscussion("Test", "", account1.uid)

    discussionRepository.addUserToDiscussion(discussion.uid, account2.uid)

    val updated = discussionRepository.getDiscussion(discussion.uid)
    assertTrue(updated.participants.contains(account2.uid))
    assertFalse(updated.admins.contains(account2.uid))
  }

  @Test
  fun canAddAdminFromExistingParticipant() = runBlocking {
    val discussion = discussionRepository.createDiscussion("Test", "", account1.uid)
    discussionRepository.addUserToDiscussion(discussion.uid, account2.uid)

    val withUser = discussionRepository.getDiscussion(discussion.uid)
    discussionRepository.addAdminToDiscussion(withUser, account2.uid)

    val updated = discussionRepository.getDiscussion(discussion.uid)
    assertTrue(updated.participants.contains(account2.uid))
    assertTrue(updated.admins.contains(account2.uid))
  }

  @Test
  fun canAddAdminAndParticipantAtTheSameTime() = runBlocking {
    val discussion = discussionRepository.createDiscussion("Test", "", account1.uid)

    discussionRepository.addAdminToDiscussion(discussion, account2.uid)

    val updated = discussionRepository.getDiscussion(discussion.uid)
    assertTrue(updated.participants.contains(account2.uid))
    assertTrue(updated.admins.contains(account2.uid))
  }

  @Test
  fun canAddParticipants() = runBlocking {
    val discussion = discussionRepository.createDiscussion("Test", "", account1.uid)

    discussionRepository.addUsersToDiscussion(discussion, listOf(account2.uid, account3.uid))

    val updated = discussionRepository.getDiscussion(discussion.uid)
    assertTrue(updated.participants.contains(account2.uid))
    assertTrue(updated.participants.contains(account3.uid))
    assertFalse(updated.admins.contains(account2.uid))
    assertFalse(updated.admins.contains(account3.uid))
  }

  @Test
  fun canAddAdminsFromExistingParticipants() = runBlocking {
    val discussion = discussionRepository.createDiscussion("Test", "", account1.uid)
    discussionRepository.addUsersToDiscussion(discussion, listOf(account2.uid, account3.uid))

    val withUsers = discussionRepository.getDiscussion(discussion.uid)
    discussionRepository.addAdminsToDiscussion(withUsers, listOf(account2.uid, account3.uid))

    val updated = discussionRepository.getDiscussion(discussion.uid)
    assertTrue(updated.participants.contains(account2.uid))
    assertTrue(updated.participants.contains(account3.uid))
    assertTrue(updated.admins.contains(account2.uid))
    assertTrue(updated.admins.contains(account3.uid))
  }

  @Test
  fun canAddAdminsAndParticipantsAtTheSameTime() = runBlocking {
    val discussion = discussionRepository.createDiscussion("Test", "", account1.uid)

    discussionRepository.addAdminsToDiscussion(discussion, listOf(account2.uid, account3.uid))

    val updated = discussionRepository.getDiscussion(discussion.uid)
    assertTrue(updated.participants.contains(account2.uid))
    assertTrue(updated.participants.contains(account3.uid))
    assertTrue(updated.admins.contains(account2.uid))
    assertTrue(updated.admins.contains(account3.uid))
  }

  @Test
  fun canChangeDiscussionName() = runBlocking {
    val discussion = discussionRepository.createDiscussion("Test", "", account1.uid)

    val newName = "Test - Updated"
    discussionRepository.setDiscussionName(discussion.uid, newName)

    val updated = discussionRepository.getDiscussion(discussion.uid)
    assertEquals(newName, updated.name)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun canChangeDiscussionNameToBlankName() = runTest {
    val discussion =
        discussionRepository.createDiscussion(
            "Test", "", account1.uid, listOf(account2.uid, account3.uid))

    discussionDetailsViewModel.setDiscussionName(discussion, account1, "")
    advanceUntilIdle()

    val updated = discussionRepository.getDiscussion(discussion.uid)
    val expectedName = "Discussion with: ${discussion.participants.joinToString(", ")}"
    assertEquals(expectedName, updated.name)
  }

  @Test
  fun canChangeDiscussionDescription() = runBlocking {
    val discussion = discussionRepository.createDiscussion("Test", "", account1.uid)

    val newDescription = "A non empty description"
    discussionRepository.setDiscussionDescription(discussion.uid, newDescription)

    val updated = discussionRepository.getDiscussion(discussion.uid)
    assertEquals(newDescription, updated.description)
  }

  @Test(expected = DiscussionNotFoundException::class)
  fun canDeleteDiscussion() = runTest {
    val discussion = discussionRepository.createDiscussion("Test", "", account1.uid)

    discussionRepository.deleteDiscussion(context, discussion)
    discussionRepository.getDiscussion(discussion.uid)
  }

  @Test
  fun canSendMessageToDiscussion() = runBlocking {
    val discussion =
        discussionRepository.createDiscussion(
            "Test", "", account1.uid, listOf(account2.uid, account3.uid))

    val content = "Hello"
    discussionRepository.sendMessageToDiscussion(discussion, account2, content)

    val messages = discussionRepository.getMessages(discussion.uid)
    assertTrue(messages.size == 1 && messages.any { it.content == content })
  }

  @Test
  fun canRemoveUserFromDiscussion() = runBlocking {
    val discussion = discussionRepository.createDiscussion("Test", "", account1.uid)
    discussionRepository.addUserToDiscussion(discussion.uid, account2.uid)

    val withUser = discussionRepository.getDiscussion(discussion.uid)
    discussionRepository.removeUserFromDiscussion(withUser, account2.uid)

    val updated = discussionRepository.getDiscussion(discussion.uid)
    assertFalse(updated.participants.contains(account2.uid))
  }

  @Test
  fun canRemoveUsersFromDiscussion() = runBlocking {
    val discussion = discussionRepository.createDiscussion("Test", "", account1.uid)
    discussionRepository.addUsersToDiscussion(discussion, listOf(account2.uid, account3.uid))

    val withUsers = discussionRepository.getDiscussion(discussion.uid)
    discussionRepository.removeUsersFromDiscussion(withUsers, listOf(account2.uid, account3.uid))

    val updated = discussionRepository.getDiscussion(discussion.uid)
    assertFalse(updated.participants.contains(account2.uid))
    assertFalse(updated.participants.contains(account3.uid))
  }

  @Test
  fun canRemoveAdminFromDiscussion() = runBlocking {
    val discussion = discussionRepository.createDiscussion("Test", "", account1.uid)
    discussionRepository.addAdminToDiscussion(discussion, account2.uid)

    val withAdmin = discussionRepository.getDiscussion(discussion.uid)
    discussionRepository.removeAdminFromDiscussion(withAdmin, account2.uid)

    val updated = discussionRepository.getDiscussion(discussion.uid)
    assertTrue(updated.participants.contains(account2.uid))
    assertFalse(updated.admins.contains(account2.uid))
  }

  @Test
  fun canRemoveAdminsFromDiscussion() = runBlocking {
    val discussion = discussionRepository.createDiscussion("Test", "", account1.uid)
    discussionRepository.addAdminsToDiscussion(discussion, listOf(account2.uid, account3.uid))

    val withAdmins = discussionRepository.getDiscussion(discussion.uid)
    discussionRepository.removeAdminsFromDiscussion(withAdmins, listOf(account2.uid, account3.uid))

    val updated = discussionRepository.getDiscussion(discussion.uid)
    assertTrue(updated.participants.contains(account2.uid))
    assertTrue(updated.participants.contains(account3.uid))
    assertFalse(updated.admins.contains(account2.uid))
    assertFalse(updated.admins.contains(account3.uid))
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun nonAdminUserCanRemoveThemselves() = runTest {
    val discussion = discussionRepository.createDiscussion("Test", "", account1.uid)
    discussionRepository.addUserToDiscussion(discussion.uid, account2.uid)

    val withUser = discussionRepository.getDiscussion(discussion.uid)
    assertTrue(withUser.participants.contains(account2.uid))

    // account2 is not an admin but should be able to remove themselves
    discussionDetailsViewModel.removeUserFromDiscussion(withUser, account2, account2)
    advanceUntilIdle()

    val updated = discussionRepository.getDiscussion(discussion.uid)
    assertFalse(updated.participants.contains(account2.uid))
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test(expected = com.github.meeplemeet.model.PermissionDeniedException::class)
  fun nonAdminUserCannotRemoveOtherUsers() = runTest {
    val discussion = discussionRepository.createDiscussion("Test", "", account1.uid)
    discussionRepository.addUsersToDiscussion(discussion, listOf(account2.uid, account3.uid))

    val withUsers = discussionRepository.getDiscussion(discussion.uid)

    // account2 tries to remove account3 - should fail
    discussionDetailsViewModel.removeUserFromDiscussion(withUsers, account2, account3)
    advanceUntilIdle()
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun readDiscussionMessagesHandlesNonParticipant() = runTest {
    val discussion = discussionRepository.createDiscussion("Test", "", account1.uid)
    discussionRepository.sendMessageToDiscussion(discussion, account1, "Test message")

    val withMessage = discussionRepository.getDiscussion(discussion.uid)

    // account2 is not a participant, but calling readDiscussionMessages should not throw
    discussionViewModel.readDiscussionMessages(account2, withMessage)
    advanceUntilIdle()

    // No exception should be thrown - method returns early
    assertTrue(true)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun readDiscussionMessagesWorksForParticipants() = runTest {
    val discussion =
        discussionRepository.createDiscussion("Test", "", account1.uid, listOf(account2.uid))
    discussionRepository.sendMessageToDiscussion(discussion, account1, "Test message")

    val withMessage = discussionRepository.getDiscussion(discussion.uid)

    // Get account2 with the unread preview
    val acc2WithPreviews = accountRepository.getAccount(account2.uid)

    // account2 is a participant and should be able to read messages
    discussionViewModel.readDiscussionMessages(acc2WithPreviews, withMessage)
    Thread.sleep(600)

    // Verify unread count is now 0
    val updatedAccount = accountRepository.getAccount(account2.uid)
    assertEquals(0, updatedAccount.previews[discussion.uid]?.unreadCount ?: -1)
  }

  @Test
  fun canGetMessagesFromDiscussion() = runBlocking {
    val discussion = discussionRepository.createDiscussion("Test", "", account1.uid)

    discussionRepository.sendMessageToDiscussion(discussion, account1, "Message 1")
    discussionRepository.sendMessageToDiscussion(discussion, account1, "Message 2")
    discussionRepository.sendMessageToDiscussion(discussion, account1, "Message 3")

    val messages = discussionRepository.getMessages(discussion.uid)

    assertEquals(3, messages.size)
    assertEquals("Message 1", messages[0].content)
    assertEquals("Message 2", messages[1].content)
    assertEquals("Message 3", messages[2].content)
    assertEquals(account1.uid, messages[0].senderId)
  }

  @Test
  fun canGetSpecificMessageById() = runBlocking {
    val discussion = discussionRepository.createDiscussion("Test", "", account1.uid)

    discussionRepository.sendMessageToDiscussion(discussion, account1, "First message")
    val messages = discussionRepository.getMessages(discussion.uid)
    val messageId = messages.first().uid

    val retrievedMessage = discussionRepository.getMessage(discussion.uid, messageId)

    assertNotNull(retrievedMessage)
    assertEquals("First message", retrievedMessage?.content)
    assertEquals(account1.uid, retrievedMessage?.senderId)
    assertEquals(messageId, retrievedMessage?.uid)
  }

  @Test
  fun getMessageReturnsNullForNonExistentMessage() = runBlocking {
    val discussion = discussionRepository.createDiscussion("Test", "", account1.uid)

    val retrievedMessage = discussionRepository.getMessage(discussion.uid, "nonexistent-id")

    assertEquals(null, retrievedMessage)
  }

  @Test
  fun messagesAreOrderedByCreatedAt() = runBlocking {
    val discussion = discussionRepository.createDiscussion("Test", "", account1.uid)

    discussionRepository.sendMessageToDiscussion(discussion, account1, "First")
    Thread.sleep(300)
    discussionRepository.sendMessageToDiscussion(discussion, account1, "Second")
    Thread.sleep(300)
    discussionRepository.sendMessageToDiscussion(discussion, account1, "Third")

    val messages = discussionRepository.getMessages(discussion.uid)

    assertEquals(3, messages.size)
    assertEquals("First", messages[0].content)
    assertEquals("Second", messages[1].content)
    assertEquals("Third", messages[2].content)
  }

  @Test
  fun canListenToMessagesInRealTime() = runBlocking {
    val discussion = discussionRepository.createDiscussion("Test", "", account1.uid)

    discussionRepository.sendMessageToDiscussion(discussion, account1, "Initial message")

    val messagesFlow = discussionRepository.listenMessages(discussion.uid)
    var receivedMessages = listOf<com.github.meeplemeet.model.discussions.Message>()

    val job = launch { messagesFlow.collect { messages -> receivedMessages = messages } }

    delay(1500)

    // Verify we received the initial message
    assertEquals(1, receivedMessages.size)
    assertEquals("Initial message", receivedMessages[0].content)

    // Send another message and verify the flow updates
    discussionRepository.sendMessageToDiscussion(discussion, account1, "New message")
    delay(1500)

    assertEquals(2, receivedMessages.size)
    assertEquals("New message", receivedMessages[1].content)

    job.cancel()
  }

  @Test
  fun canSetDiscussionProfilePictureUrl() = runBlocking {
    val discussion = discussionRepository.createDiscussion("Test", "", account1.uid)
    val profilePictureUrl = "https://example.com/profile.jpg"

    discussionRepository.setDiscussionProfilePictureUrl(discussion.uid, profilePictureUrl)

    val updated = discussionRepository.getDiscussion(discussion.uid)
    assertEquals(profilePictureUrl, updated.profilePictureUrl)
  }

  @Test
  fun canSendPhotoMessageToDiscussion() = runBlocking {
    val discussion =
        discussionRepository.createDiscussion(
            "Test", "", account1.uid, listOf(account2.uid, account3.uid))

    val photoUrl = "https://example.com/photo.jpg"
    val content = "Check out this photo!"

    val messageId =
        discussionRepository.sendPhotoMessageToDiscussion(discussion, account1, content, photoUrl)

    val messages = discussionRepository.getMessages(discussion.uid)
    assertEquals(1, messages.size)
    assertEquals(content, messages[0].content)
    assertEquals(photoUrl, messages[0].photoUrl)
    assertEquals(account1.uid, messages[0].senderId)
    assertEquals(messageId, messages[0].uid)
    assertNull(messages[0].poll)
  }

  @Test
  fun photoMessageUpdatesPreviewsForAllParticipants() = runBlocking {
    val discussion =
        discussionRepository.createDiscussion(
            "Test", "", account1.uid, listOf(account2.uid, account3.uid))

    val photoUrl = "https://example.com/photo.jpg"
    discussionRepository.sendPhotoMessageToDiscussion(discussion, account1, "Photo!", photoUrl)

    Thread.sleep(600)

    // Verify previews are updated with photo emoji
    val acc2Updated = accountRepository.getAccount(account2.uid)
    val acc3Updated = accountRepository.getAccount(account3.uid)

    assertEquals("📷 Photo", acc2Updated.previews[discussion.uid]?.lastMessage)
    assertEquals("📷 Photo", acc3Updated.previews[discussion.uid]?.lastMessage)
    assertEquals(1, acc2Updated.previews[discussion.uid]?.unreadCount)
    assertEquals(1, acc3Updated.previews[discussion.uid]?.unreadCount)

    // Sender should have unread count of 0
    val acc1Updated = accountRepository.getAccount(account1.uid)
    assertEquals(0, acc1Updated.previews[discussion.uid]?.unreadCount)
  }

  @Test
  fun canVoteOnPollUsingDiscussionAndMessageIds() = runBlocking {
    val discussion = discussionRepository.createDiscussion("Test", "", account1.uid)
    discussionRepository.addUserToDiscussion(discussion.uid, account2.uid)

    val pollMessage =
        discussionRepository.createPoll(
            discussion, account1.uid, "What game?", listOf("Catan", "Pandemic"))

    discussionRepository.voteOnPoll(discussion.uid, pollMessage.uid, account2.uid, 0)

    val updatedMessage = discussionRepository.getMessage(discussion.uid, pollMessage.uid)
    assertNotNull(updatedMessage?.poll)
    assertEquals(listOf(0), updatedMessage?.poll?.votes?.get(account2.uid))
  }

  @Test
  fun canRemoveVoteFromPollUsingDiscussionAndMessageIds() = runBlocking {
    val discussion = discussionRepository.createDiscussion("Test", "", account1.uid)
    discussionRepository.addUserToDiscussion(discussion.uid, account2.uid)

    val pollMessage =
        discussionRepository.createPoll(
            discussion, account1.uid, "What game?", listOf("Catan", "Pandemic"), true)

    // Vote on multiple options
    discussionRepository.voteOnPoll(discussion.uid, pollMessage.uid, account2.uid, 0)
    discussionRepository.voteOnPoll(discussion.uid, pollMessage.uid, account2.uid, 1)

    var updatedMessage = discussionRepository.getMessage(discussion.uid, pollMessage.uid)
    assertEquals(listOf(0, 1), updatedMessage?.poll?.votes?.get(account2.uid))

    // Remove vote for option 0
    discussionRepository.removeVoteFromPoll(discussion.uid, pollMessage.uid, account2.uid, 0)

    updatedMessage = discussionRepository.getMessage(discussion.uid, pollMessage.uid)
    assertEquals(listOf(1), updatedMessage?.poll?.votes?.get(account2.uid))
  }

  @Test
  fun createPollReturnsMessageWithUid() = runBlocking {
    val discussion = discussionRepository.createDiscussion("Test", "", account1.uid)

    val pollMessage =
        discussionRepository.createPoll(
            discussion, account1.uid, "What game?", listOf("Catan", "Pandemic"))

    assertNotNull(pollMessage.uid)
    assertTrue(pollMessage.uid.isNotEmpty())
    assertEquals("What game?", pollMessage.content)
    assertNotNull(pollMessage.poll)
    assertEquals("What game?", pollMessage.poll?.question)
    assertEquals(listOf("Catan", "Pandemic"), pollMessage.poll?.options)
  }

  @Test
  fun addUserToDiscussionCreatesPreviewWithLastMessage() = runBlocking {
    val discussion = discussionRepository.createDiscussion("Test", "", account1.uid)

    discussionRepository.sendMessageToDiscussion(discussion, account1, "Welcome!")
    Thread.sleep(300)

    discussionRepository.addUserToDiscussion(discussion.uid, account2.uid)

    val acc2Updated = accountRepository.getAccount(account2.uid)
    val preview = acc2Updated.previews[discussion.uid]

    assertNotNull(preview)
    assertEquals("Welcome!", preview?.lastMessage)
    assertEquals(account1.uid, preview?.lastMessageSender)
  }

  @Test
  fun addUsersToDiscussionCreatesPreviewsWithLastMessage() = runBlocking {
    val discussion = discussionRepository.createDiscussion("Test", "", account1.uid)

    discussionRepository.sendMessageToDiscussion(discussion, account1, "Hello everyone!")
    Thread.sleep(300)

    discussionRepository.addUsersToDiscussion(discussion, listOf(account2.uid, account3.uid))

    val acc2Updated = accountRepository.getAccount(account2.uid)
    val acc3Updated = accountRepository.getAccount(account3.uid)

    assertEquals("Hello everyone!", acc2Updated.previews[discussion.uid]?.lastMessage)
    assertEquals("Hello everyone!", acc3Updated.previews[discussion.uid]?.lastMessage)
    assertEquals(account1.uid, acc2Updated.previews[discussion.uid]?.lastMessageSender)
    assertEquals(account1.uid, acc3Updated.previews[discussion.uid]?.lastMessageSender)
  }

  @Test
  fun viewModelSendMessageWithPhotoUploadsAndSendsMessage() = runTest {
    val discussion =
        discussionRepository.createDiscussion(
            "Test", "", account1.uid, listOf(account2.uid, account3.uid))

    val testImagePath = createTestImage("test_photo.jpg", 600, Color.RED)

    discussionViewModel.sendMessageWithPhoto(
        discussion, account1, "Check this out!", context, testImagePath)

    Thread.sleep(3000) // Wait for upload

    val messages = discussionRepository.getMessages(discussion.uid)
    assertEquals(1, messages.size)
    assertEquals("Check this out!", messages[0].content)
    assertNotNull(messages[0].photoUrl)
    assertTrue(messages[0].photoUrl!!.startsWith("https://"))
    assertEquals(account1.uid, messages[0].senderId)

    // Clean up test image
    File(testImagePath).delete()
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun viewModelVoteOnPollUsesIdsInsteadOfObjects() = runTest {
    val discussion = discussionRepository.createDiscussion("Test", "", account1.uid)
    discussionRepository.addUserToDiscussion(discussion.uid, account2.uid)

    val pollMessage =
        discussionRepository.createPoll(
            discussion, account1.uid, "What game?", listOf("Catan", "Pandemic"))

    discussionViewModel.voteOnPoll(discussion.uid, pollMessage.uid, account2, 0)
    advanceUntilIdle()

    val updatedMessage = discussionRepository.getMessage(discussion.uid, pollMessage.uid)
    assertEquals(listOf(0), updatedMessage?.poll?.votes?.get(account2.uid))
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun viewModelRemoveVoteFromPollUsesIdsInsteadOfObjects() = runTest {
    val discussion = discussionRepository.createDiscussion("Test", "", account1.uid)
    discussionRepository.addUserToDiscussion(discussion.uid, account2.uid)

    val pollMessage =
        discussionRepository.createPoll(
            discussion, account1.uid, "What game?", listOf("Catan", "Pandemic"), true)

    discussionViewModel.voteOnPoll(discussion.uid, pollMessage.uid, account2, 0)
    discussionViewModel.voteOnPoll(discussion.uid, pollMessage.uid, account2, 1)
    advanceUntilIdle()

    var updatedMessage = discussionRepository.getMessage(discussion.uid, pollMessage.uid)
    assertEquals(listOf(0, 1), updatedMessage?.poll?.votes?.get(account2.uid))

    discussionViewModel.removeVoteFromPoll(discussion.uid, pollMessage.uid, account2, 0)
    advanceUntilIdle()

    updatedMessage = discussionRepository.getMessage(discussion.uid, pollMessage.uid)
    assertEquals(listOf(1), updatedMessage?.poll?.votes?.get(account2.uid))
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun viewModelSetDiscussionProfilePictureUploadsAndUpdates() = runTest {
    val discussion = discussionRepository.createDiscussion("Test", "", account1.uid)

    val testImagePath = createTestImage("test_profile.jpg", 800, Color.BLUE)

    discussionDetailsViewModel.setDiscussionProfilePicture(
        discussion, account1, context, testImagePath)
    advanceUntilIdle()

    Thread.sleep(3000) // Wait for upload

    val updated = discussionRepository.getDiscussion(discussion.uid)
    assertNotNull(updated.profilePictureUrl)
    assertTrue(updated.profilePictureUrl!!.startsWith("https://"))

    // Clean up test image
    File(testImagePath).delete()
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test(expected = com.github.meeplemeet.model.PermissionDeniedException::class)
  fun viewModelSetDiscussionProfilePictureRequiresAdmin() = runTest {
    val discussion = discussionRepository.createDiscussion("Test", "", account1.uid)
    discussionRepository.addUserToDiscussion(discussion.uid, account2.uid)

    val testImagePath = createTestImage("test_profile.jpg", 800, Color.BLUE)

    // account2 is not an admin, should throw exception
    discussionDetailsViewModel.setDiscussionProfilePicture(
        discussion, account2, context, testImagePath)

    // Clean up test image
    File(testImagePath).delete()
  }

  @Test
  fun repository_editMessage_lastMessage_updatesContentAndPreview() = runBlocking {
    val discussion =
        discussionRepository.createDiscussion("Test", "", account1.uid, listOf(account2.uid))

    // Send initial message
    discussionRepository.sendMessageToDiscussion(discussion, account1, "Original message")

    // Get the last message
    val lastMessage = discussionRepository.getLastMessage(discussion.uid)!!

    // Edit the message
    discussionRepository.editMessage(discussion, account1, lastMessage.uid, "Edited message")

    // Verify message content updated
    val editedMessage = discussionRepository.getLastMessage(discussion.uid)
    assertNotNull("Edited message should exist", editedMessage)
    assertEquals("Edited message", editedMessage?.content)

    // Verify preview updated for participants
    val updatedAccount = accountRepository.getAccount(account1.uid)
    val preview = updatedAccount.previews[discussion.uid]
    assertNotNull("Preview should exist", preview)
    assertEquals("Edited message", preview?.lastMessage)
  }

  @Test
  fun repository_editMessage_earlierMessage_updatesContentAndMarksEdited() = runBlocking {
    val discussion =
        discussionRepository.createDiscussion("Test", "", account1.uid, listOf(account2.uid))

    // Send two messages
    discussionRepository.sendMessageToDiscussion(discussion, account1, "First message")
    discussionRepository.sendMessageToDiscussion(discussion, account2, "Second message")

    // Get the first message
    var messages = discussionRepository.getMessages(discussion.uid)
    val firstMessage = messages.first { it.content == "First message" }

    // Edit the first message (not the last one)
    discussionRepository.editMessage(discussion, account1, firstMessage.uid, "Edited first")

    // Verify message content updated and marked as edited
    messages = discussionRepository.getMessages(discussion.uid)
    val editedMessage = messages.find { it.uid == firstMessage.uid }
    assertNotNull("Edited message should exist", editedMessage)
    assertEquals("Edited first", editedMessage?.content)
    assertTrue("Message should be marked as edited", editedMessage?.edited == true)

    // Verify preview NOT updated (still shows last message)
    val updatedAccount = accountRepository.getAccount(account2.uid)
    val preview = updatedAccount.previews[discussion.uid]
    assertEquals("Second message", preview?.lastMessage)
  }

  @Test
  fun repository_deleteMessage_removesMessageFromDiscussion() = runBlocking {
    val discussion = discussionRepository.createDiscussion("Test", "", account1.uid)

    // Send a message to delete
    discussionRepository.sendMessageToDiscussion(discussion, account1, "Message to delete")

    // Get the message and count before deletion
    val messageToDelete = discussionRepository.getLastMessage(discussion.uid)!!
    val initialCount = discussionRepository.getMessages(discussion.uid).size

    // Delete the message
    discussionRepository.deleteMessage(discussion.uid, messageToDelete.uid)

    // Verify message is deleted
    val messages = discussionRepository.getMessages(discussion.uid)
    assertEquals(initialCount - 1, messages.size)
    assertTrue("Message should be deleted", messages.none { it.uid == messageToDelete.uid })
  }

  @Test
  fun viewModel_editMessage_validEdit_updatesMessage() = runBlocking {
    val discussion = discussionRepository.createDiscussion("Test", "", account1.uid)

    // Send initial message
    discussionRepository.sendMessageToDiscussion(discussion, account1, "Original")

    // Get the last message
    val message = discussionRepository.getLastMessage(discussion.uid)!!

    // Edit via viewModel
    discussionViewModel.editMessage(discussion, message, account1, "Edited via ViewModel")

    // Wait for async operation
    delay(3000)

    // Verify message updated
    val editedMessage = discussionRepository.getLastMessage(discussion.uid)
    assertNotNull("Edited message should exist", editedMessage)
    assertEquals("Edited via ViewModel", editedMessage?.content)
  }

  @Test(expected = IllegalArgumentException::class)
  fun viewModel_editMessage_wrongSender_throwsException() = runBlocking {
    val discussion =
        discussionRepository.createDiscussion("Test", "", account1.uid, listOf(account2.uid))

    // Send message from account1
    discussionRepository.sendMessageToDiscussion(discussion, account1, "Account1 message")

    // Get the last message
    val message = discussionRepository.getLastMessage(discussion.uid)!!

    // Try to edit as account2 - should throw
    discussionViewModel.editMessage(discussion, message, account2, "Trying to edit")
  }

  @Test(expected = IllegalArgumentException::class)
  fun viewModel_editMessage_blankContent_throwsException() = runBlocking {
    val discussion = discussionRepository.createDiscussion("Test", "", account1.uid)

    // Send initial message
    discussionRepository.sendMessageToDiscussion(discussion, account1, "Original")

    // Get the last message
    val message = discussionRepository.getLastMessage(discussion.uid)!!

    // Try to edit with blank content - should throw
    discussionViewModel.editMessage(discussion, message, account1, "   ")
  }

  @Test
  fun viewModel_deleteMessage_validDelete_removesMessage() = runBlocking {
    val discussion = discussionRepository.createDiscussion("Test", "", account1.uid)

    // Send message to delete
    discussionRepository.sendMessageToDiscussion(discussion, account1, "To delete")

    // Get the message and count before deletion
    val message = discussionRepository.getLastMessage(discussion.uid)!!
    val initialCount = discussionRepository.getMessages(discussion.uid).size

    // Delete via viewModel
    discussionViewModel.deleteMessage(discussion, message, account1)

    // Verify message deleted
    val messages = discussionRepository.getMessages(discussion.uid)
    assertEquals(initialCount - 1, messages.size)
    assertTrue("Message should be deleted", messages.none { it.uid == message.uid })
  }

  @Test(expected = IllegalArgumentException::class)
  fun viewModel_deleteMessage_wrongSender_throwsException() = runBlocking {
    val discussion =
        discussionRepository.createDiscussion("Test", "", account1.uid, listOf(account2.uid))

    // Send message from account1
    discussionRepository.sendMessageToDiscussion(discussion, account1, "Account1 message")

    // Get the last message
    val message = discussionRepository.getLastMessage(discussion.uid)!!

    // Try to delete as account2 - should throw
    discussionViewModel.deleteMessage(discussion, message, account2)
  }
}
