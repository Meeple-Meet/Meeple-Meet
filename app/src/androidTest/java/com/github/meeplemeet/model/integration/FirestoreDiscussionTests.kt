package com.github.meeplemeet.model.integration

import com.github.meeplemeet.model.DiscussionNotFoundException
import com.github.meeplemeet.model.PermissionDeniedException
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.utils.FirestoreTests
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class FirestoreDiscussionTests : FirestoreTests() {
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
  fun canAddDiscussion() = runTest {
    val name = "Test"
    val description = "A test discussion"
    val (acc, discussion) = viewModels[0].createDiscussion(name, description, account1)
    account1 = acc
    val getDiscussion = viewModels[1].getDiscussion(discussion.uid)

    assertEquals(discussion.uid, getDiscussion.uid)
    assertEquals(name, getDiscussion.name)
    assertEquals(description, getDiscussion.description)
    assertTrue(getDiscussion.participants.contains(account1.uid))
    assertTrue(getDiscussion.admins.contains(account1.uid))
    assertTrue(getDiscussion.messages.isEmpty())
    assertNotNull(getDiscussion.createdAt)
  }

  @Test
  fun canAddDiscussionWithBlankName() = runTest {
    val name = "${account1.name}'s discussion"
    val description = "A test discussion"
    val (acc, discussion) = viewModels[0].createDiscussion("", description, account1)
    account1 = acc
    val getDiscussion = viewModels[1].getDiscussion(discussion.uid)

    assertEquals(discussion.uid, getDiscussion.uid)
    assertEquals(name, getDiscussion.name)
  }

  @Test(expected = DiscussionNotFoundException::class)
  fun cannotGetNonExistingDiscussion() = runTest { viewModels[1].getDiscussion("invalid-id") }

  @Test
  fun canAddParticipant() = runTest {
    val (acc, discussion) = viewModels[0].createDiscussion("Test", "", account1)
    account1 = acc
    val updated = viewModels[1].addUserToDiscussion(discussion, account1, account2)
    val getDiscussion = viewModels[2].getDiscussion(updated.uid)

    assertEquals(updated.uid, getDiscussion.uid)
    assertTrue(getDiscussion.participants.contains(account2.uid))
    assertFalse(getDiscussion.admins.contains(account2.uid))
  }

  @Test(expected = PermissionDeniedException::class)
  fun nonAdminCannotAddParticipantToDiscussion() = runTest {
    val (acc, discussion) = viewModels[0].createDiscussion("Test", "", account1)
    account1 = acc
    val updated = viewModels[1].addUserToDiscussion(discussion, account1, account2)
    viewModels[2].addUserToDiscussion(updated, account2, account3)
  }

  @Test
  fun canAddAdminFromExistingParticipant() = runTest {
    val (acc, discussion) = viewModels[0].createDiscussion("Test", "", account1)
    account1 = acc
    var updated = viewModels[1].addUserToDiscussion(discussion, account1, account2)
    updated = viewModels[2].addAdminToDiscussion(updated, account1, account2)
    val getDiscussion = viewModels[3].getDiscussion(updated.uid)

    assertEquals(updated.uid, getDiscussion.uid)
    assertTrue(getDiscussion.participants.contains(account2.uid))
    assertTrue(getDiscussion.admins.contains(account2.uid))
  }

  @Test(expected = PermissionDeniedException::class)
  fun nonAdminCannotAddAdminFromExistingParticipant() = runTest {
    val (acc, discussion) = viewModels[0].createDiscussion("Test", "", account1)
    account1 = acc
    val updated = viewModels[1].addUserToDiscussion(discussion, account1, account2)
    viewModels[2].addAdminToDiscussion(updated, account2, account2)
  }

  @Test
  fun canAddAdminAndParticipantAtTheSameTime() = runTest {
    val (acc, discussion) = viewModels[0].createDiscussion("Test", "", account1)
    account1 = acc
    val updated = viewModels[1].addAdminToDiscussion(discussion, account1, account2)
    val getDiscussion = viewModels[2].getDiscussion(updated.uid)

    assertEquals(updated.uid, getDiscussion.uid)
    assertTrue(getDiscussion.participants.contains(account2.uid))
    assertTrue(getDiscussion.admins.contains(account2.uid))
  }

  @Test
  fun canAddParticipants() = runTest {
    val (acc, discussion) = viewModels[0].createDiscussion("Test", "", account1)
    account1 = acc
    val updated = viewModels[1].addUsersToDiscussion(discussion, account1, account2, account3)
    val getDiscussion = viewModels[2].getDiscussion(updated.uid)

    assertEquals(updated.uid, getDiscussion.uid)
    assertTrue(getDiscussion.participants.contains(account2.uid))
    assertTrue(getDiscussion.participants.contains(account3.uid))
    assertFalse(getDiscussion.admins.contains(account2.uid))
    assertFalse(getDiscussion.admins.contains(account3.uid))
  }

  @Test
  fun canAddAdminsFromExistingParticipants() = runTest {
    val (acc, discussion) = viewModels[0].createDiscussion("Test", "", account1)
    account1 = acc
    val updated = viewModels[1].addUsersToDiscussion(discussion, account1, account2, account3)
    val reupdated = viewModels[2].addAdminsToDiscussion(updated, account1, account2, account3)
    val getDiscussion = viewModels[3].getDiscussion(reupdated.uid)

    assertEquals(reupdated.uid, getDiscussion.uid)
    assertTrue(getDiscussion.participants.contains(account2.uid))
    assertTrue(getDiscussion.participants.contains(account3.uid))
    assertTrue(getDiscussion.admins.contains(account2.uid))
    assertTrue(getDiscussion.admins.contains(account3.uid))
  }

  @Test
  fun canAddAdminsAndParticipantsAtTheSameTime() = runTest {
    val (acc, discussion) = viewModels[0].createDiscussion("Test", "", account1)
    account1 = acc
    val updated = viewModels[1].addAdminsToDiscussion(discussion, account1, account2, account3)
    val getDiscussion = viewModels[2].getDiscussion(updated.uid)

    assertEquals(updated.uid, getDiscussion.uid)
    assertTrue(getDiscussion.participants.contains(account2.uid))
    assertTrue(getDiscussion.participants.contains(account3.uid))
    assertTrue(getDiscussion.admins.contains(account2.uid))
    assertTrue(getDiscussion.admins.contains(account3.uid))
  }

  @Test
  fun canChangeDiscussionName() = runTest {
    val (acc, discussion) = viewModels[0].createDiscussion("Test", "", account1)
    account1 = acc
    val newName = "Test - Updated"
    val updated = viewModels[1].setDiscussionName(discussion, account1, newName)
    val getDiscussion = viewModels[2].getDiscussion(updated.uid)

    assertEquals(updated.uid, getDiscussion.uid)
    assertEquals(updated.name, newName)
  }

  @Test
  fun canChangeDiscussionNameToBlankName() = runTest {
    val (acc, discussion) = viewModels[0].createDiscussion("Test", "", account1)
    account1 = acc
    var updated = viewModels[1].addUsersToDiscussion(discussion, account1, account2, account3)
    val newName = "Discussion with: ${updated.participants.joinToString(", ")}"
    updated = viewModels[1].setDiscussionName(updated, account1, "")
    val getDiscussion = viewModels[2].getDiscussion(updated.uid)

    assertEquals(updated.uid, getDiscussion.uid)
    assertEquals(updated.name, newName)
  }

  @Test
  fun canChangeDiscussionDescription() = runTest {
    val (acc, discussion) = viewModels[0].createDiscussion("Test", "", account1)
    account1 = acc
    val newDescription = "A non empty description"
    val updated = viewModels[1].setDiscussionDescription(discussion, account1, newDescription)
    val getDiscussion = viewModels[2].getDiscussion(discussion.uid)

    assertEquals(updated.uid, getDiscussion.uid)
    assertEquals(newDescription, getDiscussion.description)
  }

  @Test(expected = DiscussionNotFoundException::class)
  fun canDeleteDiscussion() = runTest {
    val (acc, discussion) = viewModels[0].createDiscussion("Test", "", account1)
    account1 = acc
    viewModels[1].deleteDiscussion(discussion, account1)
    viewModels[2].getDiscussion(discussion.uid)
  }

  @Test
  fun canSendMessageToDiscussion() = runTest {
    val (acc, discussion) = viewModels[0].createDiscussion("Test", "", account1)
    account1 = acc
    var updated = viewModels[1].addUsersToDiscussion(discussion, account1, account2, account3)
    val content = "Hello"
    updated = viewModels[2].sendMessageToDiscussion(updated, account2, content)
    val getDiscussion = viewModels[3].getDiscussion(updated.uid)

    assertTrue(getDiscussion.messages.size == 1)
    assertTrue(getDiscussion.messages.any { it.content == content })
  }

  @Test(expected = PermissionDeniedException::class)
  fun nonAdminCannotChangeDiscussionName() = runTest {
    val (acc, discussion) = viewModels[0].createDiscussion("Test", "", account1)
    account1 = acc
    val updated = viewModels[1].addUserToDiscussion(discussion, account1, account2)
    viewModels[2].setDiscussionName(updated, account2, "Hacked")
  }

  @Test(expected = PermissionDeniedException::class)
  fun nonAdminCannotChangeDiscussionDescription() = runTest {
    val (acc, discussion) = viewModels[0].createDiscussion("Test", "", account1)
    account1 = acc
    val updated = viewModels[1].addUserToDiscussion(discussion, account1, account2)
    viewModels[2].setDiscussionDescription(updated, account2, "New description")
  }

  @Test(expected = PermissionDeniedException::class)
  fun nonAdminCannotDeleteDiscussion() = runTest {
    val (acc, discussion) = viewModels[0].createDiscussion("Test", "", account1)
    account1 = acc
    val updated = viewModels[1].addUserToDiscussion(discussion, account1, account2)
    viewModels[2].deleteDiscussion(updated, account2)
  }

  @Test(expected = PermissionDeniedException::class)
  fun nonAdminCannotAddParticipantsToDiscussion() = runTest {
    val (acc, discussion) = viewModels[0].createDiscussion("Test", "", account1)
    account1 = acc
    val updated = viewModels[1].addUserToDiscussion(discussion, account1, account2)
    viewModels[2].addUsersToDiscussion(updated, account2, account3)
  }

  @Test(expected = PermissionDeniedException::class)
  fun nonAdminCannotAddAdminsFromExistingParticipants_bulk() = runTest {
    val (acc, discussion) = viewModels[0].createDiscussion("Test", "", account1)
    account1 = acc
    val updated = viewModels[1].addUsersToDiscussion(discussion, account1, account2, account3)
    viewModels[2].addAdminsToDiscussion(updated, account2, account2, account3)
  }

  @Test(expected = PermissionDeniedException::class)
  fun nonAdminCannotAddAdminsAndParticipantsAtTheSameTime() = runTest {
    val (acc, discussion) = viewModels[0].createDiscussion("Test", "", account1)
    account1 = acc
    viewModels[1].addAdminsToDiscussion(discussion, account2, account2, account3)
  }

  @Test
  fun canRemoveUserFromDiscussion() = runTest {
    val (acc, discussion) = viewModels[0].createDiscussion("Test", "", account1)
    account1 = acc
    var updated = viewModels[1].addUserToDiscussion(discussion, account1, account2)
    updated = viewModels[2].removeUserFromDiscussion(updated, account1, account2)
    val getDiscussion = viewModels[3].getDiscussion(updated.uid)

    assertEquals(updated.uid, getDiscussion.uid)
    assertFalse(getDiscussion.participants.contains(account2.uid))
  }

  @Test(expected = PermissionDeniedException::class)
  fun nonAdminCannotRemoveUserFromDiscussion() = runTest {
    val (acc, discussion) = viewModels[0].createDiscussion("Test", "", account1)
    account1 = acc
    var updated = viewModels[1].addUsersToDiscussion(discussion, account1, account2, account3)
    viewModels[2].removeUserFromDiscussion(updated, account2, account3)
  }

  @Test(expected = PermissionDeniedException::class)
  fun nonOwnerCannotRemoveUserFromDiscussion() = runTest {
    val (acc, discussion) = viewModels[0].createDiscussion("Test", "", account1)
    account1 = acc
    var updated = viewModels[1].addUsersToDiscussion(discussion, account1, account2, account3)
    updated = viewModels[2].addAdminToDiscussion(updated, account1, account2)
    viewModels[3].removeUserFromDiscussion(updated, account2, account1)
  }

  @Test
  fun canRemoveUsersFromDiscussion() = runTest {
    val (acc, discussion) = viewModels[0].createDiscussion("Test", "", account1)
    account1 = acc
    var updated = viewModels[1].addUsersToDiscussion(discussion, account1, account2, account3)
    updated = viewModels[2].removeUsersFromDiscussion(updated, account1, account2, account3)
    val getDiscussion = viewModels[3].getDiscussion(updated.uid)

    assertEquals(updated.uid, getDiscussion.uid)
    assertFalse(getDiscussion.participants.contains(account2.uid))
    assertFalse(getDiscussion.participants.contains(account3.uid))
  }

  @Test(expected = PermissionDeniedException::class)
  fun nonAdminCannotRemoveUsersFromDiscussion() = runTest {
    val (acc, discussion) = viewModels[0].createDiscussion("Test", "", account1)
    account1 = acc
    val updated = viewModels[1].addUsersToDiscussion(discussion, account1, account2, account3)
    viewModels[2].removeUsersFromDiscussion(updated, account2, account3)
  }

  @Test(expected = PermissionDeniedException::class)
  fun cannotRemoveOwnerFromDiscussion() = runTest {
    val (acc, discussion) = viewModels[0].createDiscussion("Test", "", account1)
    account1 = acc
    val updated = viewModels[1].addUsersToDiscussion(discussion, account1, account2, account3)
    viewModels[2].removeUsersFromDiscussion(updated, account2, account1, account3)
  }

  @Test
  fun canRemoveAdminFromDiscussion() = runTest {
    val (acc, discussion) = viewModels[0].createDiscussion("Test", "", account1)
    account1 = acc
    var updated = viewModels[1].addAdminToDiscussion(discussion, account1, account2)
    updated = viewModels[2].removeAdminToDiscussion(updated, account1, account2)
    val getDiscussion = viewModels[3].getDiscussion(updated.uid)

    assertEquals(updated.uid, getDiscussion.uid)
    assertTrue(getDiscussion.participants.contains(account2.uid))
    assertFalse(getDiscussion.admins.contains(account2.uid))
  }

  @Test(expected = PermissionDeniedException::class)
  fun nonAdminCannotRemoveAdminFromDiscussion() = runTest {
    val (acc, discussion) = viewModels[0].createDiscussion("Test", "", account1)
    account1 = acc
    var updated = viewModels[1].addUsersToDiscussion(discussion, account1, account2, account3)
    updated = viewModels[2].addAdminToDiscussion(updated, account1, account2)
    viewModels[3].removeAdminToDiscussion(updated, account3, account2)
  }

  @Test
  fun canRemoveAdminsFromDiscussion() = runTest {
    val (acc, discussion) = viewModels[0].createDiscussion("Test", "", account1)
    account1 = acc
    var updated = viewModels[1].addAdminsToDiscussion(discussion, account1, account2, account3)
    updated = viewModels[2].removeAdminsToDiscussion(updated, account1, account2, account3)
    val getDiscussion = viewModels[3].getDiscussion(updated.uid)

    assertEquals(updated.uid, getDiscussion.uid)
    assertTrue(getDiscussion.participants.contains(account2.uid))
    assertTrue(getDiscussion.participants.contains(account3.uid))
    assertFalse(getDiscussion.admins.contains(account2.uid))
    assertFalse(getDiscussion.admins.contains(account3.uid))
  }

  @Test(expected = PermissionDeniedException::class)
  fun nonAdminCannotRemoveAdminsFromDiscussion() = runTest {
    val (acc, discussion) = viewModels[0].createDiscussion("Test", "", account1)
    account1 = acc
    var updated = viewModels[1].addUsersToDiscussion(discussion, account1, account2, account3)
    updated = viewModels[2].addAdminToDiscussion(updated, account1, account2)
    viewModels[3].removeAdminsToDiscussion(updated, account3, account2)
  }
}
