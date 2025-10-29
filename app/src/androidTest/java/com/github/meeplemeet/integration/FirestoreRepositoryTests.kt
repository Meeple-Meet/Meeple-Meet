package com.github.meeplemeet.integration

import com.github.meeplemeet.model.AccountNotFoundException
import com.github.meeplemeet.model.DiscussionNotFoundException
import com.github.meeplemeet.model.repositories.FirestoreRepository
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.structures.Message
import com.github.meeplemeet.utils.FirestoreTests
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Test

class FirestoreRepositoryTests : FirestoreTests() {
  private lateinit var repository: FirestoreRepository
  private lateinit var testAccount1: Account
  private lateinit var testAccount2: Account
  private lateinit var testAccount3: Account

  @Before
  fun setup() {
    repository = FirestoreRepository()
    runBlocking {
      testAccount1 =
          repository.createAccount("Alice", "Alice", email = "Alice@example.com", photoUrl = null)
      testAccount2 =
          repository.createAccount("Bob", "Bob", email = "Bob@example.com", photoUrl = null)
      testAccount3 =
          repository.createAccount(
              "Charlie", "Charlie", email = "Charlie@example.com", photoUrl = null)
    }
  }

  @Test
  fun createDiscussionCreatesNewDiscussion() = runBlocking {
    val discussion = repository.createDiscussion("Test Discussion", "Description", testAccount1.uid)

    assertNotNull(discussion.uid)
    assertEquals("Test Discussion", discussion.name)
    assertEquals("Description", discussion.description)
    assertEquals(testAccount1.uid, discussion.creatorId)
    assertTrue(discussion.participants.contains(testAccount1.uid))
    assertTrue(discussion.admins.contains(testAccount1.uid))
    assertTrue(discussion.messages.isEmpty())

    val account = repository.getAccount(testAccount1.uid)
    assertNotNull(account.previews[discussion.uid])
  }

  @Test
  fun getDiscussionRetrievesExistingDiscussion() = runBlocking {
    val created = repository.createDiscussion("Test", "Desc", testAccount1.uid)

    val fetched = repository.getDiscussion(created.uid)

    assertEquals(created.uid, fetched.uid)
    assertEquals(created.name, fetched.name)
    assertEquals(created.description, fetched.description)
  }

  @Test(expected = DiscussionNotFoundException::class)
  fun getDiscussionThrowsForNonExistentDiscussion() = runTest {
    repository.getDiscussion("nonexistent-id")
  }

  @Test
  fun setDiscussionNameUpdatesName() = runBlocking {
    val discussion = repository.createDiscussion("Old Name", "Desc", testAccount1.uid)

    repository.setDiscussionName(discussion.uid, "New Name")

    val updated = repository.getDiscussion(discussion.uid)
    assertEquals("New Name", updated.name)
    assertEquals(discussion.uid, updated.uid)
  }

  @Test
  fun setDiscussionDescriptionUpdatesDescription() = runBlocking {
    val discussion = repository.createDiscussion("Name", "Old Desc", testAccount1.uid)

    repository.setDiscussionDescription(discussion.uid, "New Description")

    val updated = repository.getDiscussion(discussion.uid)
    assertEquals("New Description", updated.description)
    assertEquals(discussion.uid, updated.uid)
  }

  @Test(expected = DiscussionNotFoundException::class)
  fun deleteDiscussionRemovesDiscussion() = runTest {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)

    repository.deleteDiscussion(discussion)
    repository.getDiscussion(discussion.uid)
  }

  @Test
  fun addUserToDiscussionAddsParticipant() = runBlocking {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)

    repository.addUserToDiscussion(discussion, testAccount2.uid)

    val updated = repository.getDiscussion(discussion.uid)
    assertTrue(updated.participants.contains(testAccount2.uid))
    assertFalse(updated.admins.contains(testAccount2.uid))
  }

  @Test
  fun removeUserFromDiscussionRemovesParticipant() = runBlocking {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)
    repository.addUserToDiscussion(discussion, testAccount2.uid)

    val withUser = repository.getDiscussion(discussion.uid)
    repository.removeUserFromDiscussion(withUser, testAccount2.uid)

    val updated = repository.getDiscussion(discussion.uid)
    assertFalse(updated.participants.contains(testAccount2.uid))
  }

  @Test
  fun addUsersToDiscussionAddsMultipleParticipants() = runBlocking {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)

    repository.addUsersToDiscussion(discussion, listOf(testAccount2.uid, testAccount3.uid))

    val updated = repository.getDiscussion(discussion.uid)
    assertTrue(updated.participants.contains(testAccount2.uid))
    assertTrue(updated.participants.contains(testAccount3.uid))
  }

  @Test
  fun removeUsersFromDiscussionRemovesMultipleParticipants() = runBlocking {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)
    repository.addUsersToDiscussion(discussion, listOf(testAccount2.uid, testAccount3.uid))

    val withUsers = repository.getDiscussion(discussion.uid)
    repository.removeUsersFromDiscussion(withUsers, listOf(testAccount2.uid, testAccount3.uid))

    val updated = repository.getDiscussion(discussion.uid)
    assertFalse(updated.participants.contains(testAccount2.uid))
    assertFalse(updated.participants.contains(testAccount3.uid))
  }

  @Test
  fun addAdminToDiscussionAddsAdminAndParticipant() = runBlocking {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)

    repository.addAdminToDiscussion(discussion, testAccount2.uid)

    val updated = repository.getDiscussion(discussion.uid)
    assertTrue(updated.participants.contains(testAccount2.uid))
    assertTrue(updated.admins.contains(testAccount2.uid))
  }

  @Test
  fun removeAdminFromDiscussionRemovesAdminButKeepsParticipant() = runBlocking {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)
    repository.addAdminToDiscussion(discussion, testAccount2.uid)

    val withAdmin = repository.getDiscussion(discussion.uid)
    repository.removeAdminFromDiscussion(withAdmin, testAccount2.uid)

    val updated = repository.getDiscussion(discussion.uid)
    assertTrue(updated.participants.contains(testAccount2.uid))
    assertFalse(updated.admins.contains(testAccount2.uid))
  }

  @Test
  fun addAdminsToDiscussionAddsMultipleAdmins() = runBlocking {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)

    repository.addAdminsToDiscussion(discussion, listOf(testAccount2.uid, testAccount3.uid))

    val updated = repository.getDiscussion(discussion.uid)
    assertTrue(updated.participants.contains(testAccount2.uid))
    assertTrue(updated.participants.contains(testAccount3.uid))
    assertTrue(updated.admins.contains(testAccount2.uid))
    assertTrue(updated.admins.contains(testAccount3.uid))
  }

  @Test
  fun removeAdminsFromDiscussionRemovesMultipleAdmins() = runBlocking {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)
    repository.addAdminsToDiscussion(discussion, listOf(testAccount2.uid, testAccount3.uid))

    val withAdmins = repository.getDiscussion(discussion.uid)
    repository.removeAdminsFromDiscussion(withAdmins, listOf(testAccount2.uid, testAccount3.uid))

    val updated = repository.getDiscussion(discussion.uid)
    assertTrue(updated.participants.contains(testAccount2.uid))
    assertTrue(updated.participants.contains(testAccount3.uid))
    assertFalse(updated.admins.contains(testAccount2.uid))
    assertFalse(updated.admins.contains(testAccount3.uid))
  }

  @Test
  fun sendMessageToDiscussionAppendsMessage() = runBlocking {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)
    repository.addUserToDiscussion(discussion, testAccount2.uid)

    repository.sendMessageToDiscussion(discussion, testAccount1, "Hello World")

    val updated = repository.getDiscussion(discussion.uid)
    assertEquals(1, updated.messages.size)
    assertEquals("Hello World", updated.messages[0].content)
    assertEquals(testAccount1.uid, updated.messages[0].senderId)
  }

  @Test
  fun createAccountCreatesNewAccount() = runBlocking {
    val account =
        repository.createAccount(
            "TestUser", "TestUser", email = "TestUser@example.com", photoUrl = null)

    assertNotNull(account.uid)
    assertEquals("TestUser", account.name)
    assertTrue(account.previews.isEmpty())
  }

  @Test
  fun getAccountRetrievesExistingAccount() = runBlocking {
    val created =
        repository.createAccount(
            "TestUser", "TestUser", email = "TestUser@example.com", photoUrl = null)

    val fetched = repository.getAccount(created.uid)

    assertEquals(created.uid, fetched.uid)
    assertEquals(created.name, fetched.name)
  }

  @Test(expected = AccountNotFoundException::class)
  fun getAccountThrowsForNonExistentAccount() = runTest { repository.getAccount("nonexistent-id") }

  @Test
  fun setAccountNameUpdatesName() = runBlocking {
    val account =
        repository.createAccount(
            "OldName", "OldName", email = "OldName@example.com", photoUrl = null)

    repository.setAccountName(account.uid, "NewName")

    val updated = repository.getAccount(account.uid)
    assertEquals("NewName", updated.name)
    assertEquals(account.uid, updated.uid)
  }

  @Test(expected = AccountNotFoundException::class)
  fun deleteAccountRemovesAccount() = runTest {
    val account =
        repository.createAccount(
            "TestUser", "TestUser", email = "TestUser@example.com", photoUrl = null)

    repository.deleteAccount(account.uid)
    repository.getAccount(account.uid)
  }

  @Test
  fun readDiscussionMessagesResetsUnreadCount() = runBlocking {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)
    repository.addUserToDiscussion(discussion, testAccount2.uid)
    repository.sendMessageToDiscussion(discussion, testAccount1, "Hello")

    val message = Message(testAccount2.uid, "Read")
    repository.readDiscussionMessages(testAccount2.uid, discussion.uid, message)

    val updated = repository.getAccount(testAccount2.uid)
    val preview = updated.previews[discussion.uid]
    assertNotNull(preview)
    assertEquals(0, preview!!.unreadCount)
  }

  @Test
  fun listenDiscussionEmitsUpdates() = runBlocking {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)

    val flow = repository.listenDiscussion(discussion.uid)
    val firstEmission = withTimeout(5000) { flow.first() }

    assertEquals(discussion.uid, firstEmission.uid)
    assertEquals("Test", firstEmission.name)
  }

  @Test
  fun listenAccountEmitsPreviewUpdates() = runBlocking {
    val discussion = repository.createDiscussion("Test", "Desc", testAccount1.uid)

    val flow = repository.listenAccount(testAccount1.uid)
    val firstEmission = withTimeout(5000) { flow.first() }

    assertTrue(firstEmission.previews.containsKey(discussion.uid))
    assertNotNull(firstEmission.previews[discussion.uid])
  }
}
