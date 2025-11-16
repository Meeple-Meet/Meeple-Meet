package com.github.meeplemeet.integration

import com.github.meeplemeet.model.DiscussionNotFoundException
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.discussions.DiscussionDetailsViewModel
import com.github.meeplemeet.model.discussions.DiscussionViewModel
import com.github.meeplemeet.utils.FirestoreTests
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class FirestoreDiscussionTests : FirestoreTests() {
  private var discussionViewModel = DiscussionViewModel()
  private val discussionDetailsViewModel = DiscussionDetailsViewModel()

  private lateinit var account1: Account
  private lateinit var account2: Account
  private lateinit var account3: Account

  @Before
  fun setup() {
    runBlocking {
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

    discussionRepository.addUserToDiscussion(discussion, account2.uid)

    val updated = discussionRepository.getDiscussion(discussion.uid)
    assertTrue(updated.participants.contains(account2.uid))
    assertFalse(updated.admins.contains(account2.uid))
  }

  @Test
  fun canAddAdminFromExistingParticipant() = runBlocking {
    val discussion = discussionRepository.createDiscussion("Test", "", account1.uid)
    discussionRepository.addUserToDiscussion(discussion, account2.uid)

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

    discussionRepository.deleteDiscussion(discussion)
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
    discussionRepository.addUserToDiscussion(discussion, account2.uid)

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
    discussionRepository.addUserToDiscussion(discussion, account2.uid)

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
    Thread.sleep(200)

    // Verify unread count is now 0
    val updatedAccount = accountRepository.getAccount(account2.uid)
    assertEquals(0, updatedAccount.previews[discussion.uid]?.unreadCount ?: -1)
  }
}
