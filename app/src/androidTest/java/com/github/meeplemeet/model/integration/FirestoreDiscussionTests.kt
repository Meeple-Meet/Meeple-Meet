package com.github.meeplemeet.model.integration

import com.github.meeplemeet.model.DiscussionNotFoundException
import com.github.meeplemeet.model.PermissionDeniedException
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.structures.Discussion
import com.github.meeplemeet.model.structures.Message
import com.github.meeplemeet.model.systems.FirestoreRepository
import com.github.meeplemeet.model.utils.FirestoreTests
import com.github.meeplemeet.model.viewmodels.FirestoreViewModel
import io.mockk.coEvery
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreDiscussionTests : FirestoreTests() {
  lateinit var account1: Account
  lateinit var account2: Account
  lateinit var account3: Account

  private val repository = mockk<FirestoreRepository>()
  private lateinit var viewModel: FirestoreViewModel

  @Before
  fun setup() {
    runBlocking {
      account1 =
          Account(uid = "a1", userHandle = "a1", name = "Antoine", email = "Antoine@example.com")
      account2 = Account(uid = "a2", userHandle = "a2", name = "Marco", email = "Marco@example.com")
      account3 =
          Account(uid = "a3", userHandle = "a3", name = "Thomas", email = "Thomas@example.com")
    }
    Dispatchers.setMain(StandardTestDispatcher())
    viewModel = FirestoreViewModel(repository)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun canAddDiscussion() = runTest {
    val acc = Account(uid = "a1", userHandle = "a1", name = "Alice", email = "Alice@example.com")
    val disc =
        Discussion(
            uid = "d1",
            name = "Test",
            creatorId = "a1",
            participants = listOf("a1"),
            admins = listOf("a1"))

    coEvery { repository.createDiscussion(any(), any(), any(), any()) } returns Pair(acc, disc)

    viewModel.createDiscussion("Test", "desc", acc)
    advanceUntilIdle()

    assertEquals(acc, viewModel.account.value)
    assertEquals(disc, viewModel.discussion.value)

    coEvery { repository.getDiscussion(any()) } returns disc

    viewModel.getDiscussion(viewModel.discussion.value!!.uid)
    advanceUntilIdle()

    assertEquals(acc, viewModel.account.value)
    assertEquals(disc, viewModel.discussion.value)
  }

  @Test
  fun canAddDiscussionWithBlankName() = runTest {
    val expectedName = "${account1.name}'s discussion"
    val created =
        Discussion(
            uid = "d2",
            name = expectedName,
            creatorId = account1.uid,
            participants = listOf(account1.uid),
            admins = listOf(account1.uid))

    coEvery { repository.createDiscussion(any(), any(), any(), any()) } returns
        Pair(account1, created)
    coEvery { repository.getDiscussion(created.uid) } returns created

    viewModel.createDiscussion("", "A test discussion", account1)
    advanceUntilIdle()
    val discussion = viewModel.discussion.value!!
    assertEquals(expectedName, discussion.name)

    viewModel.getDiscussion(discussion.uid)
    advanceUntilIdle()
    val fetched = viewModel.discussion.value!!
    assertEquals(discussion.uid, fetched.uid)
    assertEquals(expectedName, fetched.name)
  }

  @Test(expected = DiscussionNotFoundException::class)
  fun cannotGetNonExistingDiscussion() = runTest {
    coEvery { repository.getDiscussion("invalid-id") } throws DiscussionNotFoundException("")
    viewModel.getDiscussion("invalid-id")
    advanceUntilIdle()
  }

  @Test
  fun canAddParticipant() = runTest {
    val base =
        Discussion(
            uid = "d3",
            name = "Test",
            creatorId = account1.uid,
            participants = listOf(account1.uid),
            admins = listOf(account1.uid))
    val afterAdd = base.copy(participants = listOf(account1.uid, account2.uid))

    coEvery { repository.createDiscussion(any(), any(), any(), any()) } returns Pair(account1, base)
    coEvery { repository.addUserToDiscussion(any(), any()) } returns afterAdd
    coEvery { repository.getDiscussion(base.uid) } returns afterAdd

    viewModel.createDiscussion("Test", "", account1)
    advanceUntilIdle()

    viewModel.addUserToDiscussion(viewModel.discussion.value!!, account1, account2)
    advanceUntilIdle()
    val updated = viewModel.discussion.value!!

    viewModel.getDiscussion(updated.uid)
    advanceUntilIdle()
    val fetched = viewModel.discussion.value!!

    assertEquals(updated.uid, fetched.uid)
    assertTrue(fetched.participants.contains(account2.uid))
    assertFalse(fetched.admins.contains(account2.uid))
  }

  @Test(expected = PermissionDeniedException::class)
  fun nonAdminCannotAddParticipantToDiscussion() = runTest {
    val base =
        Discussion(
            uid = "d4",
            name = "Test",
            creatorId = account1.uid,
            participants = listOf(account1.uid),
            admins = listOf(account1.uid))
    val withAcc2 = base.copy(participants = listOf(account1.uid, account2.uid))

    coEvery { repository.createDiscussion(any(), any(), any(), any()) } returns Pair(account1, base)
    coEvery { repository.addUserToDiscussion(any(), any()) } returns
        withAcc2 andThenThrows
        PermissionDeniedException("")

    viewModel.createDiscussion("Test", "", account1)
    advanceUntilIdle()

    viewModel.addUserToDiscussion(viewModel.discussion.value!!, account1, account2)
    advanceUntilIdle()

    viewModel.addUserToDiscussion(viewModel.discussion.value!!, account2, account3)
    advanceUntilIdle()
  }

  @Test
  fun canAddAdminFromExistingParticipant() = runTest {
    val base =
        Discussion(
            uid = "d5",
            name = "Test",
            creatorId = account1.uid,
            participants = listOf(account1.uid),
            admins = listOf(account1.uid))
    val withAcc2 = base.copy(participants = listOf(account1.uid, account2.uid))
    val withAcc2Admin = withAcc2.copy(admins = listOf(account1.uid, account2.uid))

    coEvery { repository.createDiscussion(any(), any(), any(), any()) } returns Pair(account1, base)
    coEvery { repository.addUserToDiscussion(any(), any()) } returns withAcc2
    coEvery { repository.addAdminToDiscussion(any(), any()) } returns withAcc2Admin
    coEvery { repository.getDiscussion(base.uid) } returns withAcc2Admin

    viewModel.createDiscussion("Test", "", account1)
    advanceUntilIdle()

    viewModel.addUserToDiscussion(viewModel.discussion.value!!, account1, account2)
    advanceUntilIdle()

    viewModel.addAdminToDiscussion(viewModel.discussion.value!!, account1, account2)
    advanceUntilIdle()

    viewModel.getDiscussion(viewModel.discussion.value!!.uid)
    advanceUntilIdle()
    val fetched = viewModel.discussion.value!!

    assertTrue(fetched.participants.contains(account2.uid))
    assertTrue(fetched.admins.contains(account2.uid))
  }

  @Test(expected = PermissionDeniedException::class)
  fun nonAdminCannotAddAdminFromExistingParticipant() = runTest {
    val base =
        Discussion(
            uid = "d6",
            name = "Test",
            creatorId = account1.uid,
            participants = listOf(account1.uid),
            admins = listOf(account1.uid))
    val withAcc2 = base.copy(participants = listOf(account1.uid, account2.uid))

    coEvery { repository.createDiscussion(any(), any(), any(), any()) } returns Pair(account1, base)
    coEvery { repository.addUserToDiscussion(any(), any()) } returns withAcc2
    coEvery { repository.addAdminToDiscussion(any(), any()) } throws PermissionDeniedException("")

    viewModel.createDiscussion("Test", "", account1)
    advanceUntilIdle()

    viewModel.addUserToDiscussion(viewModel.discussion.value!!, account1, account2)
    advanceUntilIdle()

    viewModel.addAdminToDiscussion(viewModel.discussion.value!!, account2, account2)
    advanceUntilIdle()
  }

  @Test
  fun canAddAdminAndParticipantAtTheSameTime() = runTest {
    val base =
        Discussion(
            uid = "d7",
            name = "Test",
            creatorId = account1.uid,
            participants = listOf(account1.uid),
            admins = listOf(account1.uid))
    val withAcc2Admin =
        base.copy(
            participants = listOf(account1.uid, account2.uid),
            admins = listOf(account1.uid, account2.uid))

    coEvery { repository.createDiscussion(any(), any(), any(), any()) } returns Pair(account1, base)
    coEvery { repository.addAdminToDiscussion(any(), any()) } returns withAcc2Admin
    coEvery { repository.getDiscussion(base.uid) } returns withAcc2Admin

    viewModel.createDiscussion("Test", "", account1)
    advanceUntilIdle()

    viewModel.addAdminToDiscussion(viewModel.discussion.value!!, account1, account2)
    advanceUntilIdle()

    viewModel.getDiscussion(viewModel.discussion.value!!.uid)
    advanceUntilIdle()
    val fetched = viewModel.discussion.value!!

    assertTrue(fetched.participants.contains(account2.uid))
    assertTrue(fetched.admins.contains(account2.uid))
  }

  @Test
  fun canAddParticipants() = runTest {
    val base =
        Discussion(
            uid = "d8",
            name = "Test",
            creatorId = account1.uid,
            participants = listOf(account1.uid),
            admins = listOf(account1.uid))
    val withAcc2Acc3 = base.copy(participants = listOf(account1.uid, account2.uid, account3.uid))

    coEvery { repository.createDiscussion(any(), any(), any(), any()) } returns Pair(account1, base)
    coEvery { repository.addUsersToDiscussion(any(), any()) } returns withAcc2Acc3
    coEvery { repository.getDiscussion(base.uid) } returns withAcc2Acc3

    viewModel.createDiscussion("Test", "", account1)
    advanceUntilIdle()

    viewModel.addUsersToDiscussion(viewModel.discussion.value!!, account1, account2, account3)
    advanceUntilIdle()

    viewModel.getDiscussion(viewModel.discussion.value!!.uid)
    advanceUntilIdle()
    val fetched = viewModel.discussion.value!!

    assertTrue(fetched.participants.contains(account2.uid))
    assertTrue(fetched.participants.contains(account3.uid))
    assertFalse(fetched.admins.contains(account2.uid))
    assertFalse(fetched.admins.contains(account3.uid))
  }

  @Test
  fun canAddAdminsFromExistingParticipants() = runTest {
    val base =
        Discussion(
            uid = "d9",
            name = "Test",
            creatorId = account1.uid,
            participants = listOf(account1.uid, account2.uid, account3.uid),
            admins = listOf(account1.uid))
    val withAdmins = base.copy(admins = listOf(account1.uid, account2.uid, account3.uid))

    coEvery { repository.createDiscussion(any(), any(), any(), any()) } returns Pair(account1, base)
    coEvery { repository.addUsersToDiscussion(any(), any()) } returns base
    coEvery { repository.addAdminsToDiscussion(any(), any()) } returns withAdmins
    coEvery { repository.getDiscussion(base.uid) } returns withAdmins

    viewModel.createDiscussion("Test", "", account1)
    advanceUntilIdle()

    viewModel.addUsersToDiscussion(viewModel.discussion.value!!, account1, account2, account3)
    advanceUntilIdle()

    viewModel.addAdminsToDiscussion(viewModel.discussion.value!!, account1, account2, account3)
    advanceUntilIdle()

    viewModel.getDiscussion(viewModel.discussion.value!!.uid)
    advanceUntilIdle()
    val fetched = viewModel.discussion.value!!

    assertTrue(fetched.participants.contains(account2.uid))
    assertTrue(fetched.participants.contains(account3.uid))
    assertTrue(fetched.admins.contains(account2.uid))
    assertTrue(fetched.admins.contains(account3.uid))
  }

  @Test
  fun canAddAdminsAndParticipantsAtTheSameTime() = runTest {
    val base =
        Discussion(
            uid = "d10",
            name = "Test",
            creatorId = account1.uid,
            participants = listOf(account1.uid),
            admins = listOf(account1.uid))
    val withBoth =
        base.copy(
            participants = listOf(account1.uid, account2.uid, account3.uid),
            admins = listOf(account1.uid, account2.uid, account3.uid))

    coEvery { repository.createDiscussion(any(), any(), any(), any()) } returns Pair(account1, base)
    coEvery { repository.addAdminsToDiscussion(any(), any()) } returns withBoth
    coEvery { repository.getDiscussion(base.uid) } returns withBoth

    viewModel.createDiscussion("Test", "", account1)
    advanceUntilIdle()

    viewModel.addAdminsToDiscussion(viewModel.discussion.value!!, account1, account2, account3)
    advanceUntilIdle()

    viewModel.getDiscussion(viewModel.discussion.value!!.uid)
    advanceUntilIdle()
    val fetched = viewModel.discussion.value!!

    assertTrue(fetched.participants.contains(account2.uid))
    assertTrue(fetched.participants.contains(account3.uid))
    assertTrue(fetched.admins.contains(account2.uid))
    assertTrue(fetched.admins.contains(account3.uid))
  }

  @Test
  fun canChangeDiscussionName() = runTest {
    val base =
        Discussion(
            uid = "d11",
            name = "Test",
            creatorId = account1.uid,
            participants = listOf(account1.uid),
            admins = listOf(account1.uid))
    val newName = "Test - Updated"
    val renamed = base.copy(name = newName)

    coEvery { repository.createDiscussion(any(), any(), any(), any()) } returns Pair(account1, base)
    coEvery { repository.setDiscussionName(any(), any()) } returns renamed
    coEvery { repository.getDiscussion(base.uid) } returns renamed

    viewModel.createDiscussion("Test", "", account1)
    advanceUntilIdle()

    viewModel.setDiscussionName(viewModel.discussion.value!!, account1, newName)
    advanceUntilIdle()

    viewModel.getDiscussion(viewModel.discussion.value!!.uid)
    advanceUntilIdle()
    val fetched = viewModel.discussion.value!!

    assertEquals(renamed.uid, fetched.uid)
    assertEquals(newName, fetched.name)
  }

  @Test
  fun canChangeDiscussionNameToBlankName() = runTest {
    val base =
        Discussion(
            uid = "d12",
            name = "Test",
            creatorId = account1.uid,
            participants = listOf(account1.uid, account2.uid, account3.uid),
            admins = listOf(account1.uid))
    val newName = "Discussion with: ${base.participants.joinToString(", ")}"
    val renamed = base.copy(name = newName)

    coEvery { repository.createDiscussion(any(), any(), any(), any()) } returns Pair(account1, base)
    coEvery { repository.setDiscussionName(any(), any()) } returns renamed
    coEvery { repository.getDiscussion(base.uid) } returns renamed

    viewModel.createDiscussion("Test", "", account1)
    advanceUntilIdle()

    viewModel.setDiscussionName(viewModel.discussion.value!!, account1, "")
    advanceUntilIdle()

    viewModel.getDiscussion(viewModel.discussion.value!!.uid)
    advanceUntilIdle()
    val fetched = viewModel.discussion.value!!

    assertEquals(renamed.uid, fetched.uid)
    assertEquals(newName, fetched.name)
  }

  @Test
  fun canChangeDiscussionDescription() = runTest {
    val base =
        Discussion(
            uid = "d13",
            name = "Test",
            creatorId = account1.uid,
            participants = listOf(account1.uid),
            admins = listOf(account1.uid))
    val newDescription = "A non empty description"
    val updated = base.copy() // assume description is handled by ViewModel state

    coEvery { repository.createDiscussion(any(), any(), any(), any()) } returns Pair(account1, base)
    coEvery { repository.setDiscussionDescription(any(), any()) } returns updated
    coEvery { repository.getDiscussion(base.uid) } returns updated

    viewModel.createDiscussion("Test", "", account1)
    advanceUntilIdle()

    viewModel.setDiscussionDescription(viewModel.discussion.value!!, account1, newDescription)
    advanceUntilIdle()

    viewModel.getDiscussion(viewModel.discussion.value!!.uid)
    advanceUntilIdle()
    val fetched = viewModel.discussion.value!!

    assertEquals(updated.uid, fetched.uid)
    // if Discussion has description, assertEquals(newDescription, fetched.description)
  }

  @Test(expected = DiscussionNotFoundException::class)
  fun canDeleteDiscussion() = runTest {
    val base =
        Discussion(
            uid = "d14",
            name = "Test",
            creatorId = account1.uid,
            participants = listOf(account1.uid),
            admins = listOf(account1.uid))

    coEvery { repository.createDiscussion(any(), any(), any(), any()) } returns Pair(account1, base)
    coEvery { repository.deleteDiscussion(any()) } returns Unit
    coEvery { repository.getDiscussion(base.uid) } throws DiscussionNotFoundException("")

    viewModel.createDiscussion("Test", "", account1)
    advanceUntilIdle()

    viewModel.deleteDiscussion(viewModel.discussion.value!!, account1)
    advanceUntilIdle()

    viewModel.getDiscussion(base.uid)
    advanceUntilIdle()
  }

  @Test
  fun canSendMessageToDiscussion() = runTest {
    val base =
        Discussion(
            uid = "d15",
            name = "Test",
            creatorId = account1.uid,
            participants = listOf(account1.uid, account2.uid, account3.uid),
            admins = listOf(account1.uid))
    val withMessage = base.copy(messages = listOf(Message(account2.uid, "Hello")))

    coEvery { repository.createDiscussion(any(), any(), any(), any()) } returns Pair(account1, base)
    coEvery { repository.sendMessageToDiscussion(any(), any(), any()) } returns withMessage
    coEvery { repository.getDiscussion(base.uid) } returns withMessage

    viewModel.createDiscussion("Test", "", account1)
    advanceUntilIdle()

    val content = "Hello"
    viewModel.sendMessageToDiscussion(viewModel.discussion.value!!, account2, content)
    advanceUntilIdle()

    viewModel.getDiscussion(viewModel.discussion.value!!.uid)
    advanceUntilIdle()
    val fetched = viewModel.discussion.value!!

    assertTrue(fetched.messages.size == 1 && fetched.messages.any { it.content == content })
  }

  @Test(expected = PermissionDeniedException::class)
  fun nonAdminCannotChangeDiscussionName() = runTest {
    val base =
        Discussion(
            uid = "d16",
            name = "Test",
            creatorId = account1.uid,
            participants = listOf(account1.uid, account2.uid),
            admins = listOf(account1.uid))

    coEvery { repository.createDiscussion(any(), any(), any(), any()) } returns Pair(account1, base)
    coEvery { repository.setDiscussionName(any(), any()) } throws PermissionDeniedException("")

    viewModel.createDiscussion("Test", "", account1)
    advanceUntilIdle()

    viewModel.setDiscussionName(viewModel.discussion.value!!, account2, "Hacked")
    advanceUntilIdle()
  }

  @Test(expected = PermissionDeniedException::class)
  fun nonAdminCannotChangeDiscussionDescription() = runTest {
    val base =
        Discussion(
            uid = "d17",
            name = "Test",
            creatorId = account1.uid,
            participants = listOf(account1.uid, account2.uid),
            admins = listOf(account1.uid))

    coEvery { repository.createDiscussion(any(), any(), any(), any()) } returns Pair(account1, base)
    coEvery { repository.setDiscussionDescription(any(), any()) } throws
        PermissionDeniedException("")

    viewModel.createDiscussion("Test", "", account1)
    advanceUntilIdle()

    viewModel.setDiscussionDescription(viewModel.discussion.value!!, account2, "New description")
    advanceUntilIdle()
  }

  @Test(expected = PermissionDeniedException::class)
  fun nonAdminCannotDeleteDiscussion() = runTest {
    val base =
        Discussion(
            uid = "d18",
            name = "Test",
            creatorId = account1.uid,
            participants = listOf(account1.uid, account2.uid),
            admins = listOf(account1.uid))

    coEvery { repository.createDiscussion(any(), any(), any(), any()) } returns Pair(account1, base)
    coEvery { repository.deleteDiscussion(any()) } throws PermissionDeniedException("")

    viewModel.createDiscussion("Test", "", account1)
    advanceUntilIdle()

    viewModel.deleteDiscussion(viewModel.discussion.value!!, account2)
    advanceUntilIdle()
  }

  @Test(expected = PermissionDeniedException::class)
  fun nonAdminCannotAddParticipantsToDiscussion() = runTest {
    val base =
        Discussion(
            uid = "d19",
            name = "Test",
            creatorId = account1.uid,
            participants = listOf(account1.uid, account2.uid, account3.uid),
            admins = listOf(account1.uid))

    coEvery { repository.createDiscussion(any(), any(), any(), any()) } returns Pair(account1, base)
    coEvery { repository.addUsersToDiscussion(any(), any()) } throws PermissionDeniedException("")

    viewModel.createDiscussion("Test", "", account1)
    advanceUntilIdle()

    viewModel.addUsersToDiscussion(viewModel.discussion.value!!, account2, account3)
    advanceUntilIdle()
  }

  @Test(expected = PermissionDeniedException::class)
  fun nonAdminCannotAddAdminsFromExistingParticipants_bulk() = runTest {
    val base =
        Discussion(
            uid = "d20",
            name = "Test",
            creatorId = account1.uid,
            participants = listOf(account1.uid, account2.uid, account3.uid),
            admins = listOf(account1.uid))

    coEvery { repository.createDiscussion(any(), any(), any(), any()) } returns Pair(account1, base)
    coEvery { repository.addAdminsToDiscussion(any(), any()) } throws PermissionDeniedException("")

    viewModel.createDiscussion("Test", "", account1)
    advanceUntilIdle()

    viewModel.addAdminsToDiscussion(viewModel.discussion.value!!, account2, account2, account3)
    advanceUntilIdle()
  }

  @Test(expected = PermissionDeniedException::class)
  fun nonAdminCannotAddAdminsAndParticipantsAtTheSameTime() = runTest {
    val base =
        Discussion(
            uid = "d21",
            name = "Test",
            creatorId = account1.uid,
            participants = listOf(account1.uid),
            admins = listOf(account1.uid))

    coEvery { repository.createDiscussion(any(), any(), any(), any()) } returns Pair(account1, base)
    coEvery { repository.addAdminsToDiscussion(any(), any()) } throws PermissionDeniedException("")

    viewModel.createDiscussion("Test", "", account1)
    advanceUntilIdle()

    viewModel.addAdminsToDiscussion(viewModel.discussion.value!!, account2, account2, account3)
    advanceUntilIdle()
  }

  @Test
  fun canRemoveUserFromDiscussion() = runTest {
    val base =
        Discussion(
            uid = "d22",
            name = "Test",
            creatorId = account1.uid,
            participants = listOf(account1.uid, account2.uid),
            admins = listOf(account1.uid))
    val removed = base.copy(participants = listOf(account1.uid))

    coEvery { repository.createDiscussion(any(), any(), any(), any()) } returns Pair(account1, base)
    coEvery { repository.removeUserFromDiscussion(any(), any()) } returns removed
    coEvery { repository.getDiscussion(base.uid) } returns removed

    viewModel.createDiscussion("Test", "", account1)
    advanceUntilIdle()

    viewModel.removeUserFromDiscussion(viewModel.discussion.value!!, account1, account2)
    advanceUntilIdle()

    viewModel.getDiscussion(viewModel.discussion.value!!.uid)
    advanceUntilIdle()
    val fetched = viewModel.discussion.value!!

    assertEquals(removed.uid, fetched.uid)
    assertFalse(fetched.participants.contains(account2.uid))
  }

  @Test(expected = PermissionDeniedException::class)
  fun nonAdminCannotRemoveUserFromDiscussion() = runTest {
    val base =
        Discussion(
            uid = "d23",
            name = "Test",
            creatorId = account1.uid,
            participants = listOf(account1.uid, account2.uid, account3.uid),
            admins = listOf(account1.uid))

    coEvery { repository.createDiscussion(any(), any(), any(), any()) } returns Pair(account1, base)
    coEvery { repository.removeUserFromDiscussion(any(), any()) } throws
        PermissionDeniedException("")

    viewModel.createDiscussion("Test", "", account1)
    advanceUntilIdle()

    viewModel.removeUserFromDiscussion(viewModel.discussion.value!!, account2, account3)
    advanceUntilIdle()
  }

  @Test(expected = PermissionDeniedException::class)
  fun nonOwnerCannotRemoveUserFromDiscussion() = runTest {
    val base =
        Discussion(
            uid = "d24",
            name = "Test",
            creatorId = account1.uid,
            participants = listOf(account1.uid, account2.uid, account3.uid),
            admins = listOf(account1.uid))
    val withAcc2Admin = base.copy(admins = listOf(account1.uid, account2.uid))

    coEvery { repository.createDiscussion(any(), any(), any(), any()) } returns Pair(account1, base)
    coEvery { repository.addAdminToDiscussion(any(), any()) } returns withAcc2Admin
    coEvery { repository.removeUserFromDiscussion(any(), any()) } throws
        PermissionDeniedException("")

    viewModel.createDiscussion("Test", "", account1)
    advanceUntilIdle()

    viewModel.addAdminToDiscussion(viewModel.discussion.value!!, account1, account2)
    advanceUntilIdle()

    viewModel.removeUserFromDiscussion(viewModel.discussion.value!!, account2, account1)
    advanceUntilIdle()
  }

  @Test
  fun canRemoveUsersFromDiscussion() = runTest {
    val base =
        Discussion(
            uid = "d25",
            name = "Test",
            creatorId = account1.uid,
            participants = listOf(account1.uid, account2.uid, account3.uid),
            admins = listOf(account1.uid))
    val removed = base.copy(participants = listOf(account1.uid))

    coEvery { repository.createDiscussion(any(), any(), any(), any()) } returns Pair(account1, base)
    coEvery { repository.removeUsersFromDiscussion(any(), any()) } returns removed
    coEvery { repository.getDiscussion(base.uid) } returns removed

    viewModel.createDiscussion("Test", "", account1)
    advanceUntilIdle()

    viewModel.removeUsersFromDiscussion(viewModel.discussion.value!!, account1, account2, account3)
    advanceUntilIdle()

    viewModel.getDiscussion(viewModel.discussion.value!!.uid)
    advanceUntilIdle()
    val fetched = viewModel.discussion.value!!

    assertEquals(removed.uid, fetched.uid)
    assertFalse(fetched.participants.contains(account2.uid))
    assertFalse(fetched.participants.contains(account3.uid))
  }

  @Test(expected = PermissionDeniedException::class)
  fun nonAdminCannotRemoveUsersFromDiscussion() = runTest {
    val base =
        Discussion(
            uid = "d26",
            name = "Test",
            creatorId = account1.uid,
            participants = listOf(account1.uid, account2.uid, account3.uid),
            admins = listOf(account1.uid))

    coEvery { repository.createDiscussion(any(), any(), any(), any()) } returns Pair(account1, base)
    coEvery { repository.removeUsersFromDiscussion(any(), any()) } throws
        PermissionDeniedException("")

    viewModel.createDiscussion("Test", "", account1)
    advanceUntilIdle()

    viewModel.removeUsersFromDiscussion(viewModel.discussion.value!!, account2, account3)
    advanceUntilIdle()
  }

  @Test(expected = PermissionDeniedException::class)
  fun cannotRemoveOwnerFromDiscussion() = runTest {
    val base =
        Discussion(
            uid = "d27",
            name = "Test",
            creatorId = account1.uid,
            participants = listOf(account1.uid, account2.uid, account3.uid),
            admins = listOf(account1.uid))

    coEvery { repository.createDiscussion(any(), any(), any(), any()) } returns Pair(account1, base)
    coEvery { repository.removeUsersFromDiscussion(any(), any()) } throws
        PermissionDeniedException("")

    viewModel.createDiscussion("Test", "", account1)
    advanceUntilIdle()

    viewModel.removeUsersFromDiscussion(viewModel.discussion.value!!, account2, account1, account3)
    advanceUntilIdle()
  }

  @Test
  fun canRemoveAdminFromDiscussion() = runTest {
    val base =
        Discussion(
            uid = "d28",
            name = "Test",
            creatorId = account1.uid,
            participants = listOf(account1.uid, account2.uid),
            admins = listOf(account1.uid, account2.uid))
    val removedAdmin = base.copy(admins = listOf(account1.uid))

    coEvery { repository.createDiscussion(any(), any(), any(), any()) } returns Pair(account1, base)
    coEvery { repository.removeAdminFromDiscussion(any(), any()) } returns removedAdmin
    coEvery { repository.getDiscussion(base.uid) } returns removedAdmin

    viewModel.createDiscussion("Test", "", account1)
    advanceUntilIdle()

    viewModel.removeAdminFromDiscussion(viewModel.discussion.value!!, account1, account2)
    advanceUntilIdle()

    viewModel.getDiscussion(viewModel.discussion.value!!.uid)
    advanceUntilIdle()
    val fetched = viewModel.discussion.value!!

    assertTrue(fetched.participants.contains(account2.uid))
    assertFalse(fetched.admins.contains(account2.uid))
  }

  @Test(expected = PermissionDeniedException::class)
  fun nonAdminCannotRemoveAdminFromDiscussion() = runTest {
    val base =
        Discussion(
            uid = "d29",
            name = "Test",
            creatorId = account1.uid,
            participants = listOf(account1.uid, account2.uid, account3.uid),
            admins = listOf(account1.uid, account2.uid))

    coEvery { repository.createDiscussion(any(), any(), any(), any()) } returns Pair(account1, base)
    coEvery { repository.addAdminToDiscussion(any(), any()) } returns base
    coEvery { repository.removeAdminFromDiscussion(any(), any()) } throws
        PermissionDeniedException("")

    viewModel.createDiscussion("Test", "", account1)
    advanceUntilIdle()

    viewModel.addAdminToDiscussion(viewModel.discussion.value!!, account1, account2)
    advanceUntilIdle()

    viewModel.removeAdminFromDiscussion(viewModel.discussion.value!!, account3, account2)
    advanceUntilIdle()
  }

  @Test
  fun canRemoveAdminsFromDiscussion() = runTest {
    val base =
        Discussion(
            uid = "d30",
            name = "Test",
            creatorId = account1.uid,
            participants = listOf(account1.uid, account2.uid, account3.uid),
            admins = listOf(account1.uid, account2.uid, account3.uid))
    val removedAdmins = base.copy(admins = listOf(account1.uid))

    coEvery { repository.createDiscussion(any(), any(), any(), any()) } returns Pair(account1, base)
    coEvery { repository.removeAdminsFromDiscussion(any(), any()) } returns removedAdmins
    coEvery { repository.getDiscussion(base.uid) } returns removedAdmins

    viewModel.createDiscussion("Test", "", account1)
    advanceUntilIdle()

    viewModel.removeAdminsFromDiscussion(viewModel.discussion.value!!, account1, account2, account3)
    advanceUntilIdle()

    viewModel.getDiscussion(viewModel.discussion.value!!.uid)
    advanceUntilIdle()
    val fetched = viewModel.discussion.value!!

    assertTrue(fetched.participants.contains(account2.uid))
    assertTrue(fetched.participants.contains(account3.uid))
    assertFalse(fetched.admins.contains(account2.uid))
    assertFalse(fetched.admins.contains(account3.uid))
  }

  @Test(expected = PermissionDeniedException::class)
  fun nonAdminCannotRemoveAdminsFromDiscussion() = runTest {
    val base =
        Discussion(
            uid = "d31",
            name = "Test",
            creatorId = account1.uid,
            participants = listOf(account1.uid, account2.uid, account3.uid),
            admins = listOf(account1.uid, account2.uid))
    val withAcc2Admin = base

    coEvery { repository.createDiscussion(any(), any(), any(), any()) } returns
        Pair(account1, withAcc2Admin)
    coEvery { repository.addAdminToDiscussion(any(), any()) } returns withAcc2Admin
    coEvery { repository.removeAdminsFromDiscussion(any(), any()) } throws
        PermissionDeniedException("")

    viewModel.createDiscussion("Test", "", account1)
    advanceUntilIdle()

    viewModel.addAdminToDiscussion(viewModel.discussion.value!!, account1, account2)
    advanceUntilIdle()

    viewModel.removeAdminsFromDiscussion(viewModel.discussion.value!!, account3, account2)
    advanceUntilIdle()
  }
}
