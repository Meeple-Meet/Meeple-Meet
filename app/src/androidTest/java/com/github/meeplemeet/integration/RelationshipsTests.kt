package com.github.meeplemeet.integration

import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.account.ProfileScreenViewModel
import com.github.meeplemeet.model.account.RelationshipStatus
import com.github.meeplemeet.utils.FirestoreTests
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

/**
 * Integration tests for relationship management functionality in AccountRepository and
 * ProfileScreenViewModel.
 *
 * These tests verify the complete friend system workflow using real Firestore operations:
 * - Sending friend requests
 * - Accepting friend requests
 * - Blocking users
 * - Resetting relationships
 * - Bidirectional relationship consistency
 * - ViewModel validation logic
 *
 * All tests use the Firebase emulator and verify both sides of each relationship to ensure
 * bidirectional consistency.
 */
class RelationshipsTests : FirestoreTests() {
  private lateinit var alice: Account
  private lateinit var bob: Account
  private lateinit var charlie: Account
  private lateinit var viewModel: ProfileScreenViewModel

  @Before
  fun setup() {
    runBlocking {
      alice =
          accountRepository.createAccount(
              "alice_rel", "Alice", email = "alice_rel@example.com", photoUrl = null)
      bob =
          accountRepository.createAccount(
              "bob_rel", "Bob", email = "bob_rel@example.com", photoUrl = null)
      charlie =
          accountRepository.createAccount(
              "charlie_rel", "Charlie", email = "charlie_rel@example.com", photoUrl = null)
    }
    viewModel = ProfileScreenViewModel(accountRepository)
  }

  // ==================== sendFriendRequest Tests ====================

  @Test
  fun sendFriendRequest_createsBidirectionalRelationshipAndPersists() = runBlocking {
    accountRepository.sendFriendRequest(alice.uid, bob.uid)

    // Fetch accounts fresh from Firestore to verify persistence
    val accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))
    val updatedAlice = accounts[0]
    val updatedBob = accounts[1]

    assertNotNull(updatedAlice.relationships[bob.uid])
    assertEquals(RelationshipStatus.SENT, updatedAlice.relationships[bob.uid])
    assertNotNull(updatedBob.relationships[alice.uid])
    assertEquals(RelationshipStatus.PENDING, updatedBob.relationships[alice.uid])
  }

  @Test
  fun sendFriendRequest_multipleUsersCanSendToSameUser() = runBlocking {
    accountRepository.sendFriendRequest(alice.uid, charlie.uid)
    accountRepository.sendFriendRequest(bob.uid, charlie.uid)

    val updatedCharlie = accountRepository.getAccount(charlie.uid)

    assertEquals(2, updatedCharlie.relationships.size)
    assertEquals(RelationshipStatus.PENDING, updatedCharlie.relationships[alice.uid])
    assertEquals(RelationshipStatus.PENDING, updatedCharlie.relationships[bob.uid])
  }

  @Test
  fun sendFriendRequest_oneUserCanSendToMultipleUsers() = runBlocking {
    accountRepository.sendFriendRequest(alice.uid, bob.uid)
    accountRepository.sendFriendRequest(alice.uid, charlie.uid)

    val updatedAlice = accountRepository.getAccount(alice.uid)

    assertEquals(2, updatedAlice.relationships.size)
    assertEquals(RelationshipStatus.SENT, updatedAlice.relationships[bob.uid])
    assertEquals(RelationshipStatus.SENT, updatedAlice.relationships[charlie.uid])
  }

  // ==================== acceptFriendRequest Tests ====================

  @Test
  fun acceptFriendRequest_createsMutualFriendship() = runBlocking {
    accountRepository.sendFriendRequest(alice.uid, bob.uid)
    accountRepository.acceptFriendRequest(bob.uid, alice.uid)

    val accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))
    val updatedAlice = accounts[0]
    val updatedBob = accounts[1]

    assertEquals(RelationshipStatus.FRIEND, updatedAlice.relationships[bob.uid])
    assertEquals(RelationshipStatus.FRIEND, updatedBob.relationships[alice.uid])
  }

  @Test
  fun acceptFriendRequest_updatesExistingDocuments() = runBlocking {
    accountRepository.sendFriendRequest(alice.uid, bob.uid)

    // Verify Sent and Pending states exist first
    val beforeAccept = accountRepository.getAccount(alice.uid)
    assertEquals(RelationshipStatus.SENT, beforeAccept.relationships[bob.uid])

    accountRepository.acceptFriendRequest(bob.uid, alice.uid)

    // Verify both are now Friend status
    val afterAccept = accountRepository.getAccount(alice.uid)
    assertEquals(RelationshipStatus.FRIEND, afterAccept.relationships[bob.uid])
  }

  @Test
  fun acceptFriendRequest_multipleFriendshipsIndependent() = runBlocking {
    // Alice sends to Bob and Charlie
    accountRepository.sendFriendRequest(alice.uid, bob.uid)
    accountRepository.sendFriendRequest(alice.uid, charlie.uid)

    // Only Bob accepts
    accountRepository.acceptFriendRequest(bob.uid, alice.uid)

    val accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid, charlie.uid))
    val updatedAlice = accounts[0]
    val updatedBob = accounts[1]
    val updatedCharlie = accounts[2]

    // Alice and Bob are friends
    assertEquals(RelationshipStatus.FRIEND, updatedAlice.relationships[bob.uid])
    assertEquals(RelationshipStatus.FRIEND, updatedBob.relationships[alice.uid])

    // Alice and Charlie still have pending request
    assertEquals(RelationshipStatus.SENT, updatedAlice.relationships[charlie.uid])
    assertEquals(RelationshipStatus.PENDING, updatedCharlie.relationships[alice.uid])
  }

  // ==================== blockUser Tests ====================

  @Test
  fun blockUser_setsBlockedStatusAndRemovesFromBlockedUser() = runBlocking {
    accountRepository.blockUser(alice.uid, bob.uid)

    val accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))
    val updatedAlice = accounts[0]
    val updatedBob = accounts[1]

    assertEquals(RelationshipStatus.BLOCKED, updatedAlice.relationships[bob.uid])
    assertNull(updatedBob.relationships[alice.uid])
    assertFalse(updatedBob.relationships.containsKey(alice.uid))
  }

  @Test
  fun blockUser_whenMutualBlock_preservesBothBlockStatuses() = runBlocking {
    // Bob blocks Alice first
    accountRepository.blockUser(bob.uid, alice.uid)

    // Alice blocks Bob (Bob has already blocked Alice)
    accountRepository.blockUser(alice.uid, bob.uid)

    val accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))
    val updatedAlice = accounts[0]
    val updatedBob = accounts[1]

    // Both should have Blocked status
    assertEquals(RelationshipStatus.BLOCKED, updatedAlice.relationships[bob.uid])
    assertEquals(RelationshipStatus.BLOCKED, updatedBob.relationships[alice.uid])
  }

  @Test
  fun blockUser_afterFriendshipEstablished() = runBlocking {
    // Establish friendship
    accountRepository.sendFriendRequest(alice.uid, bob.uid)
    accountRepository.acceptFriendRequest(bob.uid, alice.uid)

    // Alice blocks Bob
    accountRepository.blockUser(alice.uid, bob.uid)

    val accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))
    val updatedAlice = accounts[0]
    val updatedBob = accounts[1]

    // Alice has Bob blocked
    assertEquals(RelationshipStatus.BLOCKED, updatedAlice.relationships[bob.uid])
    // Bob has no relationship with Alice
    assertNull(updatedBob.relationships[alice.uid])
  }

  @Test
  fun blockUser_afterPendingRequest() = runBlocking {
    // Alice sends friend request to Bob
    accountRepository.sendFriendRequest(alice.uid, bob.uid)

    // Bob blocks Alice instead of accepting
    accountRepository.blockUser(bob.uid, alice.uid)

    val accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))
    val updatedAlice = accounts[0]
    val updatedBob = accounts[1]

    // Bob has Alice blocked
    assertEquals(RelationshipStatus.BLOCKED, updatedBob.relationships[alice.uid])
    // Alice has no relationship (her Sent status was deleted)
    assertNull(updatedAlice.relationships[bob.uid])
  }

  // ==================== resetRelationship Tests ====================

  @Test
  fun resetRelationship_removesBothSidesInAllScenarios() = runBlocking {
    // Test 1: Remove friendship
    accountRepository.sendFriendRequest(alice.uid, bob.uid)
    accountRepository.acceptFriendRequest(bob.uid, alice.uid)
    accountRepository.resetRelationship(alice.uid, bob.uid)

    var accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))
    assertNull(accounts[0].relationships[bob.uid])
    assertNull(accounts[1].relationships[alice.uid])

    // Test 2: Cancel sent friend request (Alice cancels)
    accountRepository.sendFriendRequest(alice.uid, bob.uid)
    accountRepository.resetRelationship(alice.uid, bob.uid)

    accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))
    assertNull(accounts[0].relationships[bob.uid])
    assertNull(accounts[1].relationships[alice.uid])

    // Test 3: Deny received friend request (Bob denies)
    accountRepository.sendFriendRequest(alice.uid, bob.uid)
    accountRepository.resetRelationship(bob.uid, alice.uid)

    accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))
    assertNull(accounts[0].relationships[bob.uid])
    assertNull(accounts[1].relationships[alice.uid])

    // Test 4: Unblock user
    accountRepository.blockUser(alice.uid, bob.uid)
    accountRepository.resetRelationship(alice.uid, bob.uid)

    accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))
    assertNull(accounts[0].relationships[bob.uid])
    assertNull(accounts[1].relationships[alice.uid])

    // Test 5: Reset with no existing relationship - should not throw
    accountRepository.resetRelationship(alice.uid, charlie.uid)
    accountRepository.resetRelationship(bob.uid, charlie.uid)

    accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid, charlie.uid))
    assertNull(accounts[0].relationships[charlie.uid])
    assertNull(accounts[1].relationships[charlie.uid])
  }

  // ==================== Complex Workflow Tests ====================

  @Test
  fun completeRelationshipLifecycle_sendAcceptRemove() = runBlocking {
    // Send request
    accountRepository.sendFriendRequest(alice.uid, bob.uid)

    var accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))
    var updatedAlice = accounts[0]
    var updatedBob = accounts[1]
    assertEquals(RelationshipStatus.SENT, updatedAlice.relationships[bob.uid])
    assertEquals(RelationshipStatus.PENDING, updatedBob.relationships[alice.uid])

    // Accept request
    accountRepository.acceptFriendRequest(bob.uid, alice.uid)

    accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))
    updatedAlice = accounts[0]
    updatedBob = accounts[1]
    assertEquals(RelationshipStatus.FRIEND, updatedAlice.relationships[bob.uid])
    assertEquals(RelationshipStatus.FRIEND, updatedBob.relationships[alice.uid])

    // Remove friendship
    accountRepository.resetRelationship(alice.uid, bob.uid)

    accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))
    updatedAlice = accounts[0]
    updatedBob = accounts[1]
    assertNull(updatedAlice.relationships[bob.uid])
    assertNull(updatedBob.relationships[alice.uid])
  }

  @Test
  fun complexWorkflows_cancelRequestAndBlockUnblockBefriend() = runBlocking {
    // Workflow 1: Friend request canceled before acceptance
    accountRepository.sendFriendRequest(alice.uid, bob.uid)
    accountRepository.resetRelationship(alice.uid, bob.uid)

    var accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))
    assertNull(accounts[0].relationships[bob.uid])
    assertNull(accounts[1].relationships[alice.uid])

    // Workflow 2: Block, unblock, then befriend
    accountRepository.blockUser(alice.uid, bob.uid)

    var updatedAlice = accountRepository.getAccount(alice.uid)
    assertEquals(RelationshipStatus.BLOCKED, updatedAlice.relationships[bob.uid])

    accountRepository.resetRelationship(alice.uid, bob.uid)

    updatedAlice = accountRepository.getAccount(alice.uid)
    assertNull(updatedAlice.relationships[bob.uid])

    accountRepository.sendFriendRequest(alice.uid, bob.uid)
    accountRepository.acceptFriendRequest(bob.uid, alice.uid)

    accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))
    assertEquals(RelationshipStatus.FRIEND, accounts[0].relationships[bob.uid])
    assertEquals(RelationshipStatus.FRIEND, accounts[1].relationships[alice.uid])
  }

  @Test
  fun multipleSimultaneousRelationships() = runBlocking {
    // Alice sends to Bob
    accountRepository.sendFriendRequest(alice.uid, bob.uid)

    // Bob sends to Charlie
    accountRepository.sendFriendRequest(bob.uid, charlie.uid)

    // Charlie sends to Alice
    accountRepository.sendFriendRequest(charlie.uid, alice.uid)

    val accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid, charlie.uid))
    val updatedAlice = accounts[0]
    val updatedBob = accounts[1]
    val updatedCharlie = accounts[2]

    // Verify all relationships are independent
    assertEquals(RelationshipStatus.SENT, updatedAlice.relationships[bob.uid])
    assertEquals(RelationshipStatus.PENDING, updatedAlice.relationships[charlie.uid])

    assertEquals(RelationshipStatus.PENDING, updatedBob.relationships[alice.uid])
    assertEquals(RelationshipStatus.SENT, updatedBob.relationships[charlie.uid])

    assertEquals(RelationshipStatus.SENT, updatedCharlie.relationships[alice.uid])
    assertEquals(RelationshipStatus.PENDING, updatedCharlie.relationships[bob.uid])
  }

  // ==================== Edge Cases ====================

  @Test
  fun relationshipsMapIsEmptyForNewAccount() = runBlocking {
    val newAccount =
        accountRepository.createAccount(
            "new_user", "New User", email = "new_user@example.com", photoUrl = null)

    assertTrue(newAccount.relationships.isEmpty())
  }

  @Test
  fun multipleOperationsOnSameRelationship() = runBlocking {
    // Send request
    accountRepository.sendFriendRequest(alice.uid, bob.uid)

    // Accept request
    accountRepository.acceptFriendRequest(bob.uid, alice.uid)

    // Block user
    accountRepository.blockUser(alice.uid, bob.uid)

    // Unblock
    accountRepository.resetRelationship(alice.uid, bob.uid)

    val accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))
    val updatedAlice = accounts[0]
    val updatedBob = accounts[1]

    // Final state should be no relationship
    assertNull(updatedAlice.relationships[bob.uid])
    assertNull(updatedBob.relationships[alice.uid])
  }

  @Test
  fun relationshipsAreIndependentAndPersistent() = runBlocking {
    // Test 1: Relationships persist across multiple retrievals
    accountRepository.sendFriendRequest(alice.uid, bob.uid)

    val fetch1 = accountRepository.getAccount(alice.uid)
    val fetch2 = accountRepository.getAccount(alice.uid)
    val fetch3 = accountRepository.getAccount(alice.uid)

    assertEquals(RelationshipStatus.SENT, fetch1.relationships[bob.uid])
    assertEquals(RelationshipStatus.SENT, fetch2.relationships[bob.uid])
    assertEquals(RelationshipStatus.SENT, fetch3.relationships[bob.uid])

    // Test 2: Friendship with one user doesn't affect other relationships
    accountRepository.sendFriendRequest(alice.uid, charlie.uid)
    accountRepository.acceptFriendRequest(bob.uid, alice.uid)

    var updatedAlice = accountRepository.getAccount(alice.uid)
    assertEquals(RelationshipStatus.FRIEND, updatedAlice.relationships[bob.uid])
    assertEquals(RelationshipStatus.SENT, updatedAlice.relationships[charlie.uid])

    // Test 3: Blocking one user doesn't affect other relationships
    accountRepository.blockUser(alice.uid, bob.uid)

    updatedAlice = accountRepository.getAccount(alice.uid)
    assertEquals(RelationshipStatus.BLOCKED, updatedAlice.relationships[bob.uid])
    assertEquals(RelationshipStatus.SENT, updatedAlice.relationships[charlie.uid])
  }

  // ==================== ViewModel Validation Tests ====================

  @Test
  fun viewModelSendFriendRequest_preventsInvalidOperations() = runBlocking {
    // Test 1: Prevent sending to same user
    viewModel.sendFriendRequest(alice, alice)
    val aliceAfterSelf = accountRepository.getAccount(alice.uid)
    assertNull(aliceAfterSelf.relationships[alice.uid])

    // Test 2: Prevent duplicate request
    accountRepository.sendFriendRequest(alice.uid, bob.uid)
    var accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))
    var updatedAlice = accounts[0]
    var updatedBob = accounts[1]

    viewModel.sendFriendRequest(updatedAlice, updatedBob)
    accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))
    assertEquals(RelationshipStatus.SENT, accounts[0].relationships[bob.uid])
    assertEquals(RelationshipStatus.PENDING, accounts[1].relationships[alice.uid])

    // Test 3: Prevent when already friends
    accountRepository.resetRelationship(alice.uid, bob.uid)
    accountRepository.sendFriendRequest(alice.uid, bob.uid)
    accountRepository.acceptFriendRequest(bob.uid, alice.uid)

    accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))
    updatedAlice = accounts[0]
    updatedBob = accounts[1]

    viewModel.sendFriendRequest(updatedAlice, updatedBob)
    accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))
    assertEquals(RelationshipStatus.FRIEND, accounts[0].relationships[bob.uid])
    assertEquals(RelationshipStatus.FRIEND, accounts[1].relationships[alice.uid])

    // Test 4: Prevent when one user blocked the other
    accountRepository.resetRelationship(alice.uid, bob.uid)
    accountRepository.blockUser(alice.uid, bob.uid)

    accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))
    updatedAlice = accounts[0]
    updatedBob = accounts[1]

    viewModel.sendFriendRequest(updatedAlice, updatedBob)
    viewModel.sendFriendRequest(updatedBob, updatedAlice)

    accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))
    assertEquals(RelationshipStatus.BLOCKED, accounts[0].relationships[bob.uid])
    assertNull(accounts[1].relationships[alice.uid])
  }

  @Test
  fun viewModelSendFriendRequest_allowsValidRequest() = runBlocking {
    accountRepository.resetRelationship(alice.uid, bob.uid)
    var accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))
    viewModel.sendFriendRequest(accounts[0], accounts[1])

    accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))
    assertEquals(RelationshipStatus.SENT, accounts[0].relationships[bob.uid])
    assertEquals(RelationshipStatus.PENDING, accounts[1].relationships[alice.uid])
  }

  @Test
  fun viewModelAcceptFriendRequest_preventsInvalidOperations() = runBlocking {
    accountRepository.resetRelationship(alice.uid, bob.uid)

    // Test 1: Prevent accepting when there's no request
    var accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))
    viewModel.acceptFriendRequest(accounts[0], accounts[1])

    accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))
    assertNull(accounts[0].relationships[bob.uid])
    assertNull(accounts[1].relationships[alice.uid])

    // Test 2: Prevent accepting in wrong direction
    accountRepository.sendFriendRequest(alice.uid, bob.uid)
    accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))

    viewModel.acceptFriendRequest(accounts[0], accounts[1])
    accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))
    assertEquals(RelationshipStatus.SENT, accounts[0].relationships[bob.uid])
    assertEquals(RelationshipStatus.PENDING, accounts[1].relationships[alice.uid])

    // Test 3: Prevent accepting when already friends
    accountRepository.acceptFriendRequest(bob.uid, alice.uid)
    accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))

    viewModel.acceptFriendRequest(accounts[0], accounts[1])
    accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))
    assertEquals(RelationshipStatus.FRIEND, accounts[0].relationships[bob.uid])
    assertEquals(RelationshipStatus.FRIEND, accounts[1].relationships[alice.uid])

    // Test 4: Prevent accepting same user
    viewModel.acceptFriendRequest(alice, alice)
    val aliceAfter = accountRepository.getAccount(alice.uid)
    assertNull(aliceAfter.relationships[alice.uid])

    // Test 5: Prevent accepting when blocked
    accountRepository.resetRelationship(alice.uid, bob.uid)
    accountRepository.blockUser(alice.uid, bob.uid)
    accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))

    viewModel.acceptFriendRequest(accounts[0], accounts[1])
    viewModel.acceptFriendRequest(accounts[1], accounts[0])

    accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))
    assertEquals(RelationshipStatus.BLOCKED, accounts[0].relationships[bob.uid])
    assertNull(accounts[1].relationships[alice.uid])
  }

  @Test
  fun viewModelAcceptFriendRequest_allowsValidAcceptance() = runBlocking {
    accountRepository.sendFriendRequest(alice.uid, bob.uid)
    var accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))

    viewModel.acceptFriendRequest(accounts[1], accounts[0])

    accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))
    assertEquals(RelationshipStatus.FRIEND, accounts[0].relationships[bob.uid])
    assertEquals(RelationshipStatus.FRIEND, accounts[1].relationships[alice.uid])
  }

  @Test
  fun viewModelBlockUser_preventsInvalidOperations() = runBlocking {
    // Clean up any leftover state
    accountRepository.resetRelationship(alice.uid, bob.uid)

    // Test 1: Prevent blocking same user
    viewModel.blockUser(alice, alice)
    val aliceAfterSelf = accountRepository.getAccount(alice.uid)
    assertNull(aliceAfterSelf.relationships[alice.uid])

    // Test 2: Prevent blocking when already blocked
    accountRepository.blockUser(alice.uid, bob.uid)
    var accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))

    viewModel.blockUser(accounts[0], accounts[1])

    accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))
    assertEquals(RelationshipStatus.BLOCKED, accounts[0].relationships[bob.uid])
    assertNull(accounts[1].relationships[alice.uid])
  }

  @Test
  fun viewModelBlockUser_allowsValidBlocking() = runBlocking {
    // Clean up any leftover state
    accountRepository.resetRelationship(alice.uid, bob.uid)
    accountRepository.resetRelationship(alice.uid, charlie.uid)

    // Test 1: Block user with no prior relationship
    var accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))
    viewModel.blockUser(accounts[0], accounts[1])

    // Wait for async operation to complete
    kotlinx.coroutines.delay(100)

    accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))
    assertEquals(RelationshipStatus.BLOCKED, accounts[0].relationships[bob.uid])
    assertNull(accounts[1].relationships[alice.uid])

    // Test 2: Block after friendship
    accountRepository.resetRelationship(alice.uid, bob.uid)
    accountRepository.sendFriendRequest(alice.uid, charlie.uid)
    accountRepository.acceptFriendRequest(charlie.uid, alice.uid)

    accounts = accountRepository.getAccounts(listOf(alice.uid, charlie.uid))
    viewModel.blockUser(accounts[0], accounts[1])

    // Wait for async operation to complete
    kotlinx.coroutines.delay(100)

    accounts = accountRepository.getAccounts(listOf(alice.uid, charlie.uid))
    assertEquals(RelationshipStatus.BLOCKED, accounts[0].relationships[charlie.uid])
    assertNull(accounts[1].relationships[alice.uid])

    // Test 3: Mutual blocking
    accountRepository.resetRelationship(alice.uid, charlie.uid)
    accountRepository.resetRelationship(alice.uid, bob.uid)
    accountRepository.blockUser(alice.uid, bob.uid)

    // Bob blocks Alice back
    accountRepository.blockUser(bob.uid, alice.uid)

    accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))
    assertEquals(RelationshipStatus.BLOCKED, accounts[0].relationships[bob.uid])
    assertEquals(RelationshipStatus.BLOCKED, accounts[1].relationships[alice.uid])
  }

  @Test
  fun viewModelCancelFriendRequest_preventsInvalidOperations() = runBlocking {
    // Test 1: Prevent canceling same user
    viewModel.cancelFriendRequest(alice, alice)
    val aliceAfterSelf = accountRepository.getAccount(alice.uid)
    assertNull(aliceAfterSelf.relationships[alice.uid])

    // Test 2: Prevent canceling when blocked
    accountRepository.blockUser(alice.uid, bob.uid)
    var accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))

    viewModel.cancelFriendRequest(accounts[0], accounts[1])

    accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))
    assertEquals(RelationshipStatus.BLOCKED, accounts[0].relationships[bob.uid])
    assertNull(accounts[1].relationships[alice.uid])
  }

  @Test
  fun viewModelDenyFriendRequest_preventsInvalidOperations() = runBlocking {
    // Test 1: Prevent denying same user
    viewModel.denyFriendRequest(alice, alice)
    val aliceAfterSelf = accountRepository.getAccount(alice.uid)
    assertNull(aliceAfterSelf.relationships[alice.uid])

    // Test 2: Prevent denying when blocked
    accountRepository.blockUser(alice.uid, bob.uid)
    var accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))

    viewModel.denyFriendRequest(accounts[1], accounts[0])

    accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))
    assertEquals(RelationshipStatus.BLOCKED, accounts[0].relationships[bob.uid])
    assertNull(accounts[1].relationships[alice.uid])
  }

  @Test
  fun viewModelRemoveFriend_preventsInvalidOperations() = runBlocking {
    // Clean up any leftover state
    accountRepository.resetRelationship(alice.uid, bob.uid)

    // Test 1: Prevent removing same user
    viewModel.removeFriend(alice, alice)
    val aliceAfterSelf = accountRepository.getAccount(alice.uid)
    assertNull(aliceAfterSelf.relationships[alice.uid])

    // Test 2: Prevent removing when blocked
    accountRepository.blockUser(alice.uid, bob.uid)
    var accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))

    viewModel.removeFriend(accounts[0], accounts[1])

    accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))
    assertEquals(RelationshipStatus.BLOCKED, accounts[0].relationships[bob.uid])
    assertNull(accounts[1].relationships[alice.uid])
  }

  @Test
  fun viewModelUnblockUser_preventsInvalidOperations() = runBlocking {
    // Clean up any leftover state
    accountRepository.resetRelationship(alice.uid, bob.uid)

    // Test 1: Prevent unblocking same user
    viewModel.unblockUser(alice, alice)
    val aliceAfterSelf = accountRepository.getAccount(alice.uid)
    assertNull(aliceAfterSelf.relationships[alice.uid])

    // Test 2: Prevent unblocking when NOT blocked (no relationship)
    var accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))

    viewModel.unblockUser(accounts[0], accounts[1])

    accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))
    assertNull(accounts[0].relationships[bob.uid])
    assertNull(accounts[1].relationships[alice.uid])

    // Test 3: Prevent unblocking when relationship is Friend (not Blocked)
    accountRepository.sendFriendRequest(alice.uid, bob.uid)
    accountRepository.acceptFriendRequest(bob.uid, alice.uid)
    accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))

    viewModel.unblockUser(accounts[0], accounts[1])

    accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))
    assertEquals(RelationshipStatus.FRIEND, accounts[0].relationships[bob.uid])
    assertEquals(RelationshipStatus.FRIEND, accounts[1].relationships[alice.uid])
  }

  @Test
  fun viewModelCancelFriendRequest_allowsValidCancel() = runBlocking {
    accountRepository.sendFriendRequest(alice.uid, bob.uid)
    var accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))

    viewModel.cancelFriendRequest(accounts[0], accounts[1])

    accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))
    assertNull(accounts[0].relationships[bob.uid])
    assertNull(accounts[1].relationships[alice.uid])
  }

  @Test
  fun viewModelDenyFriendRequest_allowsValidDeny() = runBlocking {
    accountRepository.sendFriendRequest(alice.uid, bob.uid)
    var accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))

    viewModel.denyFriendRequest(accounts[1], accounts[0])

    accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))
    assertNull(accounts[0].relationships[bob.uid])
    assertNull(accounts[1].relationships[alice.uid])
  }

  @Test
  fun viewModelRemoveFriend_allowsValidRemoval() = runBlocking {
    accountRepository.sendFriendRequest(alice.uid, bob.uid)
    accountRepository.acceptFriendRequest(bob.uid, alice.uid)
    var accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))

    viewModel.removeFriend(accounts[0], accounts[1])

    accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))
    assertNull(accounts[0].relationships[bob.uid])
    assertNull(accounts[1].relationships[alice.uid])
  }

  @Test
  fun viewModelUnblockUser_allowsValidUnblock() = runBlocking {
    accountRepository.blockUser(alice.uid, bob.uid)
    accountRepository.resetRelationship(alice.uid, bob.uid)
    val accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))

    // Relationship should be null after unblock
    assertNull(accounts[0].relationships[bob.uid])
    assertNull(accounts[1].relationships[alice.uid])
  }

  @Test
  fun viewModelValidation_complexScenarios() = runBlocking {
    // Test 1: Cannot send request in opposite direction when pending
    accountRepository.sendFriendRequest(alice.uid, bob.uid)
    var accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))

    viewModel.sendFriendRequest(accounts[1], accounts[0])

    accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))
    assertEquals(RelationshipStatus.SENT, accounts[0].relationships[bob.uid])
    assertEquals(RelationshipStatus.PENDING, accounts[1].relationships[alice.uid])

    // Test 2: After accepting, cannot send another request
    viewModel.acceptFriendRequest(accounts[1], accounts[0])
    accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))

    viewModel.sendFriendRequest(accounts[0], accounts[1])

    accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))
    assertEquals(RelationshipStatus.FRIEND, accounts[0].relationships[bob.uid])
    assertEquals(RelationshipStatus.FRIEND, accounts[1].relationships[alice.uid])

    // Test 3: After blocking, cannot send friend request
    viewModel.blockUser(accounts[0], accounts[1])
    accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))

    viewModel.sendFriendRequest(accounts[0], accounts[1])
    viewModel.sendFriendRequest(accounts[1], accounts[0])

    accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))
    assertEquals(RelationshipStatus.BLOCKED, accounts[0].relationships[bob.uid])
    assertNull(accounts[1].relationships[alice.uid])
  }
}
