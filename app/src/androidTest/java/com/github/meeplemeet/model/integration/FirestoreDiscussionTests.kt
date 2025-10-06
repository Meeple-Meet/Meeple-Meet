package com.github.meeplemeet.model.integration

import com.github.meeplemeet.model.DiscussionNotFoundException
import com.github.meeplemeet.model.PermissionDeniedException
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.viewmodels.FirestoreViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import junit.framework.TestCase
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

class FirestoreDiscussionTests {
  private lateinit var viewModels: List<FirestoreViewModel>

  private val account1 = Account("0", "Antoine")
  private val account2 = Account("1", "Marco")
  private val account3 = Account("2", "Thomas")

  companion object {
    @BeforeClass
    @JvmStatic
    fun globalSetUp() {
      FirebaseFirestore.getInstance().useEmulator("10.0.2.2", 8080)
      FirebaseAuth.getInstance().useEmulator("10.0.2.2", 9099)
    }
  }

  @Before
  fun setUp() {
    viewModels = List(10) { FirestoreViewModel() }
  }

  @Test
  fun canAddDiscussion() = runTest {
    val name = "Test"
    val description = "A test discussion"

    val discussion = viewModels[0].createDiscussion(name, description, account1)
    TestCase.assertNotNull(discussion)

    val getDiscussion = viewModels[1].getDiscussion(discussion.uid)
    TestCase.assertNotNull(getDiscussion)

    TestCase.assertEquals(discussion.uid, getDiscussion.uid)
    TestCase.assertEquals(name, getDiscussion.name)
    TestCase.assertEquals(description, getDiscussion.description)
    TestCase.assertTrue(getDiscussion.participants.contains(account1.uid))
    TestCase.assertTrue(getDiscussion.admins.contains(account1.uid))
    TestCase.assertTrue(getDiscussion.messages.isEmpty())
    TestCase.assertNotNull(getDiscussion.createdAt)
  }

  @Test
  fun canAddDiscussionWithBlankName() = runTest {
    val name = "${account1.name}'s discussion"
    val description = "A test discussion"

    val discussion = viewModels[0].createDiscussion("", description, account1)
    TestCase.assertNotNull(discussion)

    val getDiscussion = viewModels[1].getDiscussion(discussion.uid)
    TestCase.assertNotNull(getDiscussion)

    TestCase.assertEquals(discussion.uid, getDiscussion.uid)
    TestCase.assertEquals(name, getDiscussion.name)
  }

  @Test(expected = DiscussionNotFoundException::class)
  fun cannotGetNonExistingDiscussion() = runTest { viewModels[1].getDiscussion("invalid-id") }

  @Test
  fun canAddParticipant() = runTest {
    val discussion = viewModels[0].createDiscussion("Test", "", account1)
    TestCase.assertNotNull(discussion)

    val updated = viewModels[1].addUserToDiscussion(discussion, account1, account2)
    TestCase.assertNotNull(updated)

    val getDiscussion = viewModels[2].getDiscussion(updated.uid)
    TestCase.assertNotNull(getDiscussion)

    TestCase.assertEquals(updated.uid, getDiscussion.uid)
    TestCase.assertTrue(getDiscussion.participants.contains(account2.uid))
    TestCase.assertFalse(getDiscussion.admins.contains(account2.uid))
  }

  @Test(expected = PermissionDeniedException::class)
  fun nonAdminCannotAddParticipantToDiscussion() = runTest {
    val discussion = viewModels[0].createDiscussion("Test", "", account1)
    TestCase.assertNotNull(discussion)

    val updated = viewModels[1].addUserToDiscussion(discussion, account1, account2)
    TestCase.assertNotNull(updated)

    viewModels[2].addUserToDiscussion(discussion, account2, account3)
  }

  @Test
  fun canAddAdminFromExistingParticipant() = runTest {
    val discussion = viewModels[0].createDiscussion("Test", "", account1)
    TestCase.assertNotNull(discussion)

    var updated = viewModels[1].addUserToDiscussion(discussion, account1, account2)
    TestCase.assertNotNull(updated)

    updated = viewModels[2].addAdminToDiscussion(updated, account1, account2)
    TestCase.assertNotNull(updated)

    val getDiscussion = viewModels[3].getDiscussion(updated.uid)
    TestCase.assertNotNull(getDiscussion)

    TestCase.assertEquals(updated.uid, getDiscussion.uid)
    TestCase.assertTrue(getDiscussion.participants.contains(account2.uid))
    TestCase.assertTrue(getDiscussion.admins.contains(account2.uid))
  }

  @Test(expected = PermissionDeniedException::class)
  fun nonAdminCannotAddAdminFromExistingParticipant() = runTest {
    val discussion = viewModels[0].createDiscussion("Test", "", account1)
    TestCase.assertNotNull(discussion)

    val updated = viewModels[1].addUserToDiscussion(discussion, account1, account2)
    TestCase.assertNotNull(updated)

    viewModels[2].addAdminToDiscussion(updated, account2, account2)
  }

  @Test
  fun canAddAdminAndParticipantAtTheSameTime() = runTest {
    val discussion = viewModels[0].createDiscussion("Test", "", account1)
    TestCase.assertNotNull(discussion)

    val updated = viewModels[1].addAdminToDiscussion(discussion, account1, account2)
    TestCase.assertNotNull(updated)

    val getDiscussion = viewModels[2].getDiscussion(updated.uid)
    TestCase.assertNotNull(getDiscussion)

    TestCase.assertEquals(updated.uid, getDiscussion.uid)
    TestCase.assertTrue(getDiscussion.participants.contains(account2.uid))
    TestCase.assertTrue(getDiscussion.admins.contains(account2.uid))
  }

  @Test
  fun canAddParticipants() = runTest {
    val discussion = viewModels[0].createDiscussion("Test", "", account1)
    TestCase.assertNotNull(discussion)

    val updated = viewModels[1].addUsersToDiscussion(discussion, account1, account2, account3)
    TestCase.assertNotNull(updated)

    val getDiscussion = viewModels[2].getDiscussion(updated.uid)
    TestCase.assertNotNull(getDiscussion)

    TestCase.assertEquals(updated.uid, getDiscussion.uid)
    TestCase.assertTrue(getDiscussion.participants.contains(account2.uid))
    TestCase.assertTrue(getDiscussion.participants.contains(account3.uid))
    TestCase.assertFalse(getDiscussion.admins.contains(account2.uid))
    TestCase.assertFalse(getDiscussion.admins.contains(account3.uid))
  }

  @Test
  fun canAddAdminsFromExistingParticipants() = runTest {
    val discussion = viewModels[0].createDiscussion("Test", "", account1)
    TestCase.assertNotNull(discussion)

    val updated = viewModels[1].addUsersToDiscussion(discussion, account1, account2, account3)
    TestCase.assertNotNull(updated)

    val reupdated = viewModels[2].addAdminsToDiscussion(updated, account1, account2, account3)
    TestCase.assertNotNull(reupdated)

    val getDiscussion = viewModels[3].getDiscussion(reupdated.uid)
    TestCase.assertNotNull(getDiscussion)

    TestCase.assertEquals(reupdated.uid, getDiscussion.uid)
    TestCase.assertTrue(getDiscussion.participants.contains(account2.uid))
    TestCase.assertTrue(getDiscussion.participants.contains(account3.uid))
    TestCase.assertTrue(getDiscussion.admins.contains(account2.uid))
    TestCase.assertTrue(getDiscussion.admins.contains(account3.uid))
  }

  @Test
  fun canAddAdminsAndParticipantsAtTheSameTime() = runTest {
    val discussion = viewModels[0].createDiscussion("Test", "", account1)
    TestCase.assertNotNull(discussion)

    val updated = viewModels[1].addAdminsToDiscussion(discussion, account1, account2, account3)
    TestCase.assertNotNull(updated)

    val getDiscussion = viewModels[2].getDiscussion(updated.uid)
    TestCase.assertNotNull(getDiscussion)

    TestCase.assertEquals(updated.uid, getDiscussion.uid)
    TestCase.assertTrue(getDiscussion.participants.contains(account2.uid))
    TestCase.assertTrue(getDiscussion.participants.contains(account3.uid))
    TestCase.assertTrue(getDiscussion.admins.contains(account2.uid))
    TestCase.assertTrue(getDiscussion.admins.contains(account3.uid))
  }

  @Test
  fun canChangeDiscussionName() = runTest {
    val discussion = viewModels[0].createDiscussion("Test", "", account1)
    TestCase.assertNotNull(discussion)

    val newName = "Test - Updated"
    val updated = viewModels[1].setDiscussionName(discussion, account1, newName)
    TestCase.assertNotNull(updated)

    val getDiscussion = viewModels[2].getDiscussion(updated.uid)
    TestCase.assertNotNull(getDiscussion)

    TestCase.assertEquals(updated.uid, getDiscussion.uid)
    TestCase.assertEquals(updated.name, newName)
  }

  @Test
  fun canChangeDiscussionNameToBlankName() = runTest {
    val discussion = viewModels[0].createDiscussion("Test", "", account1)
    TestCase.assertNotNull(discussion)

    var updated = viewModels[1].addUsersToDiscussion(discussion, account1, account2, account3)
    TestCase.assertNotNull(updated)
    val newName = "Discussion with: ${updated.participants.joinToString(", ")}"
    updated = viewModels[1].setDiscussionName(updated, account1, "")
    TestCase.assertNotNull(updated)

    val getDiscussion = viewModels[2].getDiscussion(updated.uid)
    TestCase.assertNotNull(getDiscussion)

    TestCase.assertEquals(updated.uid, getDiscussion.uid)
    TestCase.assertEquals(updated.name, newName)
  }

  @Test
  fun canChangeDiscussionDescription() = runTest {
    val discussion = viewModels[0].createDiscussion("Test", "", account1)
    TestCase.assertNotNull(discussion)

    val newDescription = "A non empty discussion"
    val updated = viewModels[1].setDiscussionDescription(discussion, account1, newDescription)
    TestCase.assertNotNull(updated)

    val getDiscussion = viewModels[2].getDiscussion(updated.uid)
    TestCase.assertNotNull(getDiscussion)

    TestCase.assertEquals(updated.uid, getDiscussion.uid)
    TestCase.assertEquals(updated.description, newDescription)
  }

  @Test(expected = DiscussionNotFoundException::class)
  fun canDeleteDiscussion() = runTest {
    val discussion = viewModels[0].createDiscussion("Test", "", account1)
    TestCase.assertNotNull(discussion)

    viewModels[1].deleteDiscussion(discussion, account1)
    viewModels[2].getDiscussion(discussion.uid)
  }

  @Test
  fun canSendMessageToDiscussion() = runTest {
    val discussion = viewModels[0].createDiscussion("Test", "", account1)
    TestCase.assertNotNull(discussion)

    var updated = viewModels[1].addUsersToDiscussion(discussion, account1, account2, account3)
    TestCase.assertNotNull(updated)

    val content = "Hello"
    updated = viewModels[2].sendMessageToDiscussion(updated, account2, content)
    TestCase.assertNotNull(updated)

    val getDiscussion = viewModels[3].getDiscussion(updated.uid)
    TestCase.assertNotNull(getDiscussion)

    TestCase.assertTrue(getDiscussion.messages.size == 1)
    TestCase.assertTrue(getDiscussion.messages.any { it.content == content })
  }

  @Test(expected = PermissionDeniedException::class)
  fun nonAdminCannotChangeDiscussionName() = runTest {
    val discussion = viewModels[0].createDiscussion("Test", "", account1)
    TestCase.assertNotNull(discussion)

    val updated = viewModels[1].addUserToDiscussion(discussion, account1, account2)
    TestCase.assertNotNull(updated)

    viewModels[2].setDiscussionName(updated, account2, "Hacked")
  }

  @Test(expected = PermissionDeniedException::class)
  fun nonAdminCannotChangeDiscussionDescription() = runTest {
    val discussion = viewModels[0].createDiscussion("Test", "", account1)
    TestCase.assertNotNull(discussion)

    val updated = viewModels[1].addUserToDiscussion(discussion, account1, account2)
    TestCase.assertNotNull(updated)

    viewModels[2].setDiscussionDescription(updated, account2, "New description")
  }

  @Test(expected = PermissionDeniedException::class)
  fun nonAdminCannotDeleteDiscussion() = runTest {
    val discussion = viewModels[0].createDiscussion("Test", "", account1)
    TestCase.assertNotNull(discussion)

    val updated = viewModels[1].addUserToDiscussion(discussion, account1, account2)
    TestCase.assertNotNull(updated)

    viewModels[2].deleteDiscussion(updated, account2)
  }

  @Test(expected = PermissionDeniedException::class)
  fun nonAdminCannotAddParticipantsToDiscussion() = runTest {
    val discussion = viewModels[0].createDiscussion("Test", "", account1)
    TestCase.assertNotNull(discussion)

    val updated = viewModels[1].addUserToDiscussion(discussion, account1, account2)
    TestCase.assertNotNull(updated)

    viewModels[2].addUsersToDiscussion(updated, account2, account3)
  }

  @Test(expected = PermissionDeniedException::class)
  fun nonAdminCannotAddAdminsFromExistingParticipants_bulk() = runTest {
    val discussion = viewModels[0].createDiscussion("Test", "", account1)
    TestCase.assertNotNull(discussion)

    val updated = viewModels[1].addUsersToDiscussion(discussion, account1, account2, account3)
    TestCase.assertNotNull(updated)

    viewModels[2].addAdminsToDiscussion(updated, account2, account2, account3)
  }

  @Test(expected = PermissionDeniedException::class)
  fun nonAdminCannotAddAdminsAndParticipantsAtTheSameTime() = runTest {
    val discussion = viewModels[0].createDiscussion("Test", "", account1)
    TestCase.assertNotNull(discussion)

    viewModels[1].addAdminsToDiscussion(discussion, account2, account2, account3)
  }
}
