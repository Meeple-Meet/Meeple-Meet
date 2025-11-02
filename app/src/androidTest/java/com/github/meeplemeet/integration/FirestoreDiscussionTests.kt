package com.github.meeplemeet.integration

import com.github.meeplemeet.model.DiscussionNotFoundException
import com.github.meeplemeet.model.repositories.FirestoreRepository
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.viewmodels.FirestoreViewModel
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
  private lateinit var repository: FirestoreRepository
  private lateinit var viewModel: FirestoreViewModel

  private lateinit var account1: Account
  private lateinit var account2: Account
  private lateinit var account3: Account

  @Before
  fun setup() {
    repository = FirestoreRepository()
    viewModel = FirestoreViewModel(repository)
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
  fun canAddDiscussion() = runBlocking {
    val discussion = repository.createDiscussion("Test", "desc", account1.uid)

    assertEquals("Test", discussion.name)
    assertEquals("desc", discussion.description)
    assertEquals(account1.uid, discussion.creatorId)
    assertTrue(discussion.participants.contains(account1.uid))
    assertTrue(discussion.admins.contains(account1.uid))

    val fetched = repository.getDiscussion(discussion.uid)
    assertEquals(discussion.uid, fetched.uid)
    assertEquals(discussion.name, fetched.name)
  }

  @Test(expected = DiscussionNotFoundException::class)
  fun cannotGetNonExistingDiscussion() = runTest { repository.getDiscussion("invalid-id") }

  @Test
  fun canAddParticipant() = runBlocking {
    val discussion = repository.createDiscussion("Test", "", account1.uid)

    repository.addUserToDiscussion(discussion, account2.uid)

    val updated = repository.getDiscussion(discussion.uid)
    assertTrue(updated.participants.contains(account2.uid))
    assertFalse(updated.admins.contains(account2.uid))
  }

  @Test
  fun canAddAdminFromExistingParticipant() = runBlocking {
    val discussion = repository.createDiscussion("Test", "", account1.uid)
    repository.addUserToDiscussion(discussion, account2.uid)

    val withUser = repository.getDiscussion(discussion.uid)
    repository.addAdminToDiscussion(withUser, account2.uid)

    val updated = repository.getDiscussion(discussion.uid)
    assertTrue(updated.participants.contains(account2.uid))
    assertTrue(updated.admins.contains(account2.uid))
  }

  @Test
  fun canAddAdminAndParticipantAtTheSameTime() = runBlocking {
    val discussion = repository.createDiscussion("Test", "", account1.uid)

    repository.addAdminToDiscussion(discussion, account2.uid)

    val updated = repository.getDiscussion(discussion.uid)
    assertTrue(updated.participants.contains(account2.uid))
    assertTrue(updated.admins.contains(account2.uid))
  }

  @Test
  fun canAddParticipants() = runBlocking {
    val discussion = repository.createDiscussion("Test", "", account1.uid)

    repository.addUsersToDiscussion(discussion, listOf(account2.uid, account3.uid))

    val updated = repository.getDiscussion(discussion.uid)
    assertTrue(updated.participants.contains(account2.uid))
    assertTrue(updated.participants.contains(account3.uid))
    assertFalse(updated.admins.contains(account2.uid))
    assertFalse(updated.admins.contains(account3.uid))
  }

  @Test
  fun canAddAdminsFromExistingParticipants() = runBlocking {
    val discussion = repository.createDiscussion("Test", "", account1.uid)
    repository.addUsersToDiscussion(discussion, listOf(account2.uid, account3.uid))

    val withUsers = repository.getDiscussion(discussion.uid)
    repository.addAdminsToDiscussion(withUsers, listOf(account2.uid, account3.uid))

    val updated = repository.getDiscussion(discussion.uid)
    assertTrue(updated.participants.contains(account2.uid))
    assertTrue(updated.participants.contains(account3.uid))
    assertTrue(updated.admins.contains(account2.uid))
    assertTrue(updated.admins.contains(account3.uid))
  }

  @Test
  fun canAddAdminsAndParticipantsAtTheSameTime() = runBlocking {
    val discussion = repository.createDiscussion("Test", "", account1.uid)

    repository.addAdminsToDiscussion(discussion, listOf(account2.uid, account3.uid))

    val updated = repository.getDiscussion(discussion.uid)
    assertTrue(updated.participants.contains(account2.uid))
    assertTrue(updated.participants.contains(account3.uid))
    assertTrue(updated.admins.contains(account2.uid))
    assertTrue(updated.admins.contains(account3.uid))
  }

  @Test
  fun canChangeDiscussionName() = runBlocking {
    val discussion = repository.createDiscussion("Test", "", account1.uid)

    val newName = "Test - Updated"
    repository.setDiscussionName(discussion.uid, newName)

    val updated = repository.getDiscussion(discussion.uid)
    assertEquals(newName, updated.name)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun canChangeDiscussionNameToBlankName() = runTest {
    val discussion =
        repository.createDiscussion("Test", "", account1.uid, listOf(account2.uid, account3.uid))

    viewModel.setDiscussionName(discussion, account1, "")
    advanceUntilIdle()

    val updated = repository.getDiscussion(discussion.uid)
    val expectedName = "Discussion with: ${discussion.participants.joinToString(", ")}"
    assertEquals(expectedName, updated.name)
  }

  @Test
  fun canChangeDiscussionDescription() = runBlocking {
    val discussion = repository.createDiscussion("Test", "", account1.uid)

    val newDescription = "A non empty description"
    repository.setDiscussionDescription(discussion.uid, newDescription)

    val updated = repository.getDiscussion(discussion.uid)
    assertEquals(newDescription, updated.description)
  }

  @Test(expected = DiscussionNotFoundException::class)
  fun canDeleteDiscussion() = runTest {
    val discussion = repository.createDiscussion("Test", "", account1.uid)

    repository.deleteDiscussion(discussion)
    repository.getDiscussion(discussion.uid)
  }

  @Test
  fun canSendMessageToDiscussion() = runBlocking {
    val discussion =
        repository.createDiscussion("Test", "", account1.uid, listOf(account2.uid, account3.uid))

    val content = "Hello"
    repository.sendMessageToDiscussion(discussion, account2, content)

    val updated = repository.getDiscussion(discussion.uid)
    assertTrue(updated.messages.size == 1 && updated.messages.any { it.content == content })
  }

  @Test
  fun canRemoveUserFromDiscussion() = runBlocking {
    val discussion = repository.createDiscussion("Test", "", account1.uid)
    repository.addUserToDiscussion(discussion, account2.uid)

    val withUser = repository.getDiscussion(discussion.uid)
    repository.removeUserFromDiscussion(withUser, account2.uid)

    val updated = repository.getDiscussion(discussion.uid)
    assertFalse(updated.participants.contains(account2.uid))
  }

  @Test
  fun canRemoveUsersFromDiscussion() = runBlocking {
    val discussion = repository.createDiscussion("Test", "", account1.uid)
    repository.addUsersToDiscussion(discussion, listOf(account2.uid, account3.uid))

    val withUsers = repository.getDiscussion(discussion.uid)
    repository.removeUsersFromDiscussion(withUsers, listOf(account2.uid, account3.uid))

    val updated = repository.getDiscussion(discussion.uid)
    assertFalse(updated.participants.contains(account2.uid))
    assertFalse(updated.participants.contains(account3.uid))
  }

  @Test
  fun canRemoveAdminFromDiscussion() = runBlocking {
    val discussion = repository.createDiscussion("Test", "", account1.uid)
    repository.addAdminToDiscussion(discussion, account2.uid)

    val withAdmin = repository.getDiscussion(discussion.uid)
    repository.removeAdminFromDiscussion(withAdmin, account2.uid)

    val updated = repository.getDiscussion(discussion.uid)
    assertTrue(updated.participants.contains(account2.uid))
    assertFalse(updated.admins.contains(account2.uid))
  }

  @Test
  fun canRemoveAdminsFromDiscussion() = runBlocking {
    val discussion = repository.createDiscussion("Test", "", account1.uid)
    repository.addAdminsToDiscussion(discussion, listOf(account2.uid, account3.uid))

    val withAdmins = repository.getDiscussion(discussion.uid)
    repository.removeAdminsFromDiscussion(withAdmins, listOf(account2.uid, account3.uid))

    val updated = repository.getDiscussion(discussion.uid)
    assertTrue(updated.participants.contains(account2.uid))
    assertTrue(updated.participants.contains(account3.uid))
    assertFalse(updated.admins.contains(account2.uid))
    assertFalse(updated.admins.contains(account3.uid))
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun nonAdminUserCanRemoveThemselves() = runTest {
    val discussion = repository.createDiscussion("Test", "", account1.uid)
    repository.addUserToDiscussion(discussion, account2.uid)

    val withUser = repository.getDiscussion(discussion.uid)
    assertTrue(withUser.participants.contains(account2.uid))

    // account2 is not an admin but should be able to remove themselves
    viewModel.removeUserFromDiscussion(withUser, account2, account2)
    advanceUntilIdle()

    val updated = repository.getDiscussion(discussion.uid)
    assertFalse(updated.participants.contains(account2.uid))
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test(expected = com.github.meeplemeet.model.PermissionDeniedException::class)
  fun nonAdminUserCannotRemoveOtherUsers() = runTest {
    val discussion = repository.createDiscussion("Test", "", account1.uid)
    repository.addUsersToDiscussion(discussion, listOf(account2.uid, account3.uid))

    val withUsers = repository.getDiscussion(discussion.uid)

    // account2 tries to remove account3 - should fail
    viewModel.removeUserFromDiscussion(withUsers, account2, account3)
    advanceUntilIdle()
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun readDiscussionMessagesHandlesNonParticipant() = runTest {
    val discussion = repository.createDiscussion("Test", "", account1.uid)
    repository.sendMessageToDiscussion(discussion, account1, "Test message")

    val withMessage = repository.getDiscussion(discussion.uid)

    // account2 is not a participant, but calling readDiscussionMessages should not throw
    viewModel.readDiscussionMessages(account2, withMessage)
    advanceUntilIdle()

    // No exception should be thrown - method returns early
    assertTrue(true)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun readDiscussionMessagesWorksForParticipants() = runTest {
    val discussion = repository.createDiscussion("Test", "", account1.uid, listOf(account2.uid))
    repository.sendMessageToDiscussion(discussion, account1, "Test message")

    val withMessage = repository.getDiscussion(discussion.uid)

    // Get account2 with the unread preview
    val acc2WithPreviews = repository.getAccount(account2.uid)

    // account2 is a participant and should be able to read messages
    viewModel.readDiscussionMessages(acc2WithPreviews, withMessage)
    advanceUntilIdle()

    // Verify unread count is now 0
    val updatedAccount = repository.getAccount(account2.uid)
    assertEquals(0, updatedAccount.previews[discussion.uid]?.unreadCount ?: -1)
  }
}
