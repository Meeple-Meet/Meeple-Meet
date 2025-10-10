package com.github.meeplemeet.model.integration

import com.github.meeplemeet.model.AccountNotFoundException
import com.github.meeplemeet.model.DiscussionNotFoundException
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.structures.Message
import com.github.meeplemeet.model.systems.FirestoreRepository
import com.github.meeplemeet.model.utils.FirestoreTests
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
    repository = FirestoreRepository(Firebase.firestore)
    runBlocking {
      testAccount1 = repository.createAccount("Alice")
      testAccount2 = repository.createAccount("Bob")
      testAccount3 = repository.createAccount("Charlie")
    }
  }

  @Test
  fun createDiscussionCreatesNewDiscussion() = runBlocking {
    val (account, discussion) =
        repository.createDiscussion("Test Discussion", "Description", testAccount1.uid)

    assertNotNull(discussion.uid)
    assertEquals("Test Discussion", discussion.name)
    assertEquals("Description", discussion.description)
    assertEquals(testAccount1.uid, discussion.creatorId)
    assertTrue(discussion.participants.contains(testAccount1.uid))
    assertTrue(discussion.admins.contains(testAccount1.uid))
    assertTrue(discussion.messages.isEmpty())
    assertNotNull(account.previews[discussion.uid])
  }

  @Test
  fun getDiscussionRetrievesExistingDiscussion() = runBlocking {
    val (_, created) = repository.createDiscussion("Test", "Desc", testAccount1.uid)

    val fetched = repository.getDiscussion(created.uid)

    assertEquals(created.uid, fetched.uid)
    assertEquals(created.name, fetched.name)
    assertEquals(created.description, fetched.description)
  }

  @Test
  fun getDiscussionThrowsForNonExistentDiscussion() = runBlocking {
    try {
      repository.getDiscussion("nonexistent-id")
      throw AssertionError("Expected DiscussionNotFoundException")
    } catch (e: DiscussionNotFoundException) {
      // Expected
    }
  }

  @Test
  fun setDiscussionNameUpdatesName() = runBlocking {
    val (_, discussion) = repository.createDiscussion("Old Name", "Desc", testAccount1.uid)

    repository.setDiscussionName(discussion.uid, "New Name")
    val updated = repository.getDiscussion(discussion.uid)

    assertEquals("New Name", updated.name)
    assertEquals(discussion.uid, updated.uid)
  }

  @Test
  fun setDiscussionDescriptionUpdatesDescription() = runBlocking {
    val (_, discussion) = repository.createDiscussion("Name", "Old Desc", testAccount1.uid)

    repository.setDiscussionDescription(discussion.uid, "New Description")
    val updated = repository.getDiscussion(discussion.uid)

    assertEquals("New Description", updated.description)
    assertEquals(discussion.uid, updated.uid)
  }

  @Test
  fun deleteDiscussionRemovesDiscussion() = runBlocking {
    val (_, discussion) = repository.createDiscussion("Test", "Desc", testAccount1.uid)

    repository.deleteDiscussion(discussion.uid)

    try {
      repository.getDiscussion(discussion.uid)
      throw AssertionError("Expected DiscussionNotFoundException")
    } catch (e: DiscussionNotFoundException) {
      // Expected
    }
  }

  @Test
  fun addUserToDiscussionAddsParticipant() = runBlocking {
    val (_, discussion) = repository.createDiscussion("Test", "Desc", testAccount1.uid)

    repository.addUserToDiscussion(discussion, testAccount2.uid)
    val updated = repository.getDiscussion(discussion.uid)

    assertTrue(updated.participants.contains(testAccount2.uid))
    assertFalse(updated.admins.contains(testAccount2.uid))
  }

  @Test
  fun removeUserFromDiscussionRemovesParticipant() = runBlocking {
    val (_, discussion) = repository.createDiscussion("Test", "Desc", testAccount1.uid)
    repository.addUserToDiscussion(discussion, testAccount2.uid)
    val withUser = repository.getDiscussion(discussion.uid)

    repository.removeUserFromDiscussion(withUser, testAccount2.uid)
    val updated = repository.getDiscussion(withUser.uid)

    assertFalse(updated.participants.contains(testAccount2.uid))
  }

  @Test
  fun addUsersToDiscussionAddsMultipleParticipants() = runBlocking {
    val (_, discussion) = repository.createDiscussion("Test", "Desc", testAccount1.uid)

    repository.addUsersToDiscussion(discussion, listOf(testAccount2.uid, testAccount3.uid))
    val updated = repository.getDiscussion(discussion.uid)

    assertTrue(updated.participants.contains(testAccount2.uid))
    assertTrue(updated.participants.contains(testAccount3.uid))
  }

  @Test
  fun removeUsersFromDiscussionRemovesMultipleParticipants() = runBlocking {
    val (_, discussion) = repository.createDiscussion("Test", "Desc", testAccount1.uid)
    repository.addUsersToDiscussion(discussion, listOf(testAccount2.uid, testAccount3.uid))
    val withUsers = repository.getDiscussion(discussion.uid)

    repository.removeUsersFromDiscussion(withUsers, listOf(testAccount2.uid, testAccount3.uid))
    val updated = repository.getDiscussion(withUsers.uid)

    assertFalse(updated.participants.contains(testAccount2.uid))
    assertFalse(updated.participants.contains(testAccount3.uid))
  }

  @Test
  fun addAdminToDiscussionAddsAdminAndParticipant() = runBlocking {
    val (_, discussion) = repository.createDiscussion("Test", "Desc", testAccount1.uid)

    repository.addAdminToDiscussion(discussion, testAccount2.uid)
    val updated = repository.getDiscussion(discussion.uid)

    assertTrue(updated.participants.contains(testAccount2.uid))
    assertTrue(updated.admins.contains(testAccount2.uid))
  }

  @Test
  fun removeAdminFromDiscussionRemovesAdminButKeepsParticipant() = runBlocking {
    val (_, discussion) = repository.createDiscussion("Test", "Desc", testAccount1.uid)
    repository.addAdminToDiscussion(discussion, testAccount2.uid)
    val withAdmin = repository.getDiscussion(discussion.uid)

    repository.removeAdminFromDiscussion(withAdmin, testAccount2.uid)
    val updated = repository.getDiscussion(withAdmin.uid)

    assertTrue(updated.participants.contains(testAccount2.uid))
    assertFalse(updated.admins.contains(testAccount2.uid))
  }

  @Test
  fun addAdminsToDiscussionAddsMultipleAdmins() = runBlocking {
    val (_, discussion) = repository.createDiscussion("Test", "Desc", testAccount1.uid)

    repository.addAdminsToDiscussion(discussion, listOf(testAccount2.uid, testAccount3.uid))
    val updated = repository.getDiscussion(discussion.uid)

    assertTrue(updated.participants.contains(testAccount2.uid))
    assertTrue(updated.participants.contains(testAccount3.uid))
    assertTrue(updated.admins.contains(testAccount2.uid))
    assertTrue(updated.admins.contains(testAccount3.uid))
  }

  @Test
  fun removeAdminsFromDiscussionRemovesMultipleAdmins() = runBlocking {
    val (_, discussion) = repository.createDiscussion("Test", "Desc", testAccount1.uid)
    repository.addAdminsToDiscussion(discussion, listOf(testAccount2.uid, testAccount3.uid))
    val withAdmins = repository.getDiscussion(discussion.uid)

    repository.removeAdminsFromDiscussion(withAdmins, listOf(testAccount2.uid, testAccount3.uid))
    val updated = repository.getDiscussion(withAdmins.uid)

    assertTrue(updated.participants.contains(testAccount2.uid))
    assertTrue(updated.participants.contains(testAccount3.uid))
    assertFalse(updated.admins.contains(testAccount2.uid))
    assertFalse(updated.admins.contains(testAccount3.uid))
  }

  @Test
  fun sendMessageToDiscussionAppendsMessage() = runBlocking {
    val (_, discussion) = repository.createDiscussion("Test", "Desc", testAccount1.uid)
    repository.addUserToDiscussion(discussion, testAccount2.uid)

    repository.sendMessageToDiscussion(discussion, testAccount1, "Hello World")
    val updated = repository.getDiscussion(discussion.uid)

    assertEquals(1, updated.messages.size)
    assertEquals("Hello World", updated.messages[0].content)
    assertEquals(testAccount1.uid, updated.messages[0].senderId)
  }

  @Test
  fun createAccountCreatesNewAccount() = runBlocking {
    val account = repository.createAccount("TestUser")

    assertNotNull(account.uid)
    assertEquals("TestUser", account.name)
    assertTrue(account.previews.isEmpty())
  }

  @Test
  fun getAccountRetrievesExistingAccount() = runBlocking {
    val created = repository.createAccount("TestUser")

    val fetched = repository.getAccount(created.uid)

    assertEquals(created.uid, fetched.uid)
    assertEquals(created.name, fetched.name)
  }

  @Test
  fun getAccountThrowsForNonExistentAccount() = runBlocking {
    try {
      repository.getAccount("nonexistent-id")
      throw AssertionError("Expected AccountNotFoundException")
    } catch (e: AccountNotFoundException) {
      // Expected
    }
  }

  @Test
  fun setAccountNameUpdatesName() = runBlocking {
    val account = repository.createAccount("OldName")

    val updated = repository.setAccountName(account.uid, "NewName")

    assertEquals("NewName", updated.name)
    assertEquals(account.uid, updated.uid)
  }

  @Test
  fun deleteAccountRemovesAccount() = runBlocking {
    val account = repository.createAccount("TestUser")

    repository.deleteAccount(account.uid)

    try {
      repository.getAccount(account.uid)
      throw AssertionError("Expected AccountNotFoundException")
    } catch (e: AccountNotFoundException) {
      // Expected
    }
  }

  @Test
  fun readDiscussionMessagesResetsUnreadCount() = runBlocking {
    val (_, discussion) = repository.createDiscussion("Test", "Desc", testAccount1.uid)
    repository.addUserToDiscussion(discussion, testAccount2.uid)
    repository.sendMessageToDiscussion(discussion, testAccount1, "Hello")

    val message = Message(testAccount2.uid, "Read")
    val updated = repository.readDiscussionMessages(testAccount2.uid, discussion.uid, message)

    val preview = updated.previews[discussion.uid]
    assertNotNull(preview)
    assertEquals(0, preview!!.unreadCount)
  }

  @Test
  fun listenDiscussionEmitsUpdates() = runBlocking {
    val (_, discussion) = repository.createDiscussion("Test", "Desc", testAccount1.uid)

    val flow = repository.listenDiscussion(discussion.uid)
    val firstEmission = withTimeout(5000) { flow.first() }

    assertEquals(discussion.uid, firstEmission.uid)
    assertEquals("Test", firstEmission.name)
  }

  @Test
  fun listenMyPreviewsEmitsPreviewUpdates() = runBlocking {
    val (_, discussion) = repository.createDiscussion("Test", "Desc", testAccount1.uid)

    val flow = repository.listenMyPreviews(testAccount1.uid)
    val firstEmission = withTimeout(5000) { flow.first() }

    assertTrue(firstEmission.containsKey(discussion.uid))
    assertNotNull(firstEmission[discussion.uid])
  }
}
