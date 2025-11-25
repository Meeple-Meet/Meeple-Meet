package com.github.meeplemeet.integration

import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.account.NotificationType
import com.github.meeplemeet.model.account.ProfileScreenViewModel
import com.github.meeplemeet.model.account.RelationshipStatus
import com.github.meeplemeet.utils.FirestoreTests
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

/**
 * Integration tests for notification functionality in AccountRepository and ProfileScreenViewModel.
 */
class NotificationTests : FirestoreTests() {

  private lateinit var alice: Account
  private lateinit var bob: Account
  private lateinit var charlie: Account
  private lateinit var viewModel: ProfileScreenViewModel

  @Before
  fun setup() {
    runBlocking {
      alice =
          accountRepository.createAccount(
              "alice_notif", "Alice", email = "alice_notif@example.com", photoUrl = null)
      bob =
          accountRepository.createAccount(
              "bob_notif", "Bob", email = "bob_notif@example.com", photoUrl = null)
      charlie =
          accountRepository.createAccount(
              "charlie_notif", "Charlie", email = "charlie_notif@example.com", photoUrl = null)

      // Clean up any existing relationships and notifications from previous tests
      cleanupRelationshipsAndNotifications()
    }

    // Uses only accountRepository; other dependencies use their defaults
    viewModel = ProfileScreenViewModel(accountRepository)
  }

  /** Cleans up all relationships and notifications between test accounts. */
  private suspend fun cleanupRelationshipsAndNotifications() {
    // Reset all relationships between test accounts
    accountRepository.resetRelationship(alice.uid, bob.uid)
    accountRepository.resetRelationship(alice.uid, charlie.uid)
    accountRepository.resetRelationship(bob.uid, alice.uid)
    accountRepository.resetRelationship(bob.uid, charlie.uid)
    accountRepository.resetRelationship(charlie.uid, alice.uid)
    accountRepository.resetRelationship(charlie.uid, bob.uid)

    // Delete all notifications for each account
    val accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid, charlie.uid))
    for (account in accounts) {
      for (notification in account.notifications) {
        accountRepository.deleteNotification(account.uid, notification.uid)
      }
    }
  }

  // ==================== sendFriendRequestNotification Tests ====================

  @Test
  fun friendRequestNotification_createsAndPersistsWithCorrectFields() = runBlocking {
    accountRepository.sendFriendRequestNotification(bob.uid, alice)

    val updatedBob = accountRepository.getAccount(bob.uid)

    assertEquals(1, updatedBob.notifications.size)
    val notification = updatedBob.notifications[0]
    assertNotNull(notification.uid)
    assertEquals(alice.uid, notification.senderOrDiscussionId)
    assertEquals(bob.uid, notification.receiverId)
    assertEquals(NotificationType.FRIEND_REQUEST, notification.type)
    assertFalse(notification.read)
  }

  @Test
  fun friendRequestNotification_supportsMultipleSendersAndReceivers() = runBlocking {
    // Multiple users send to Charlie
    accountRepository.sendFriendRequestNotification(charlie.uid, alice)
    accountRepository.sendFriendRequestNotification(charlie.uid, bob)

    // Alice sends to multiple users
    accountRepository.sendFriendRequestNotification(bob.uid, alice)

    val updatedCharlie = accountRepository.getAccount(charlie.uid)
    val updatedBob = accountRepository.getAccount(bob.uid)

    // Charlie received from Alice and Bob
    assertEquals(2, updatedCharlie.notifications.size)
    val notificationFromAlice =
        updatedCharlie.notifications.find { it.senderOrDiscussionId == alice.uid }
    val notificationFromBob =
        updatedCharlie.notifications.find { it.senderOrDiscussionId == bob.uid }
    assertNotNull(notificationFromAlice)
    assertNotNull(notificationFromBob)
    assertEquals(NotificationType.FRIEND_REQUEST, notificationFromAlice!!.type)
    assertEquals(NotificationType.FRIEND_REQUEST, notificationFromBob!!.type)

    // Bob received from Alice
    assertEquals(1, updatedBob.notifications.size)
    assertEquals(alice.uid, updatedBob.notifications[0].senderOrDiscussionId)
  }

  // ==================== sendJoinDiscussionNotification Tests ====================

  @Test
  fun discussionInviteNotification_createsAndSupportsMultipleInvitations() = runBlocking {
    val discussion1 =
        discussionRepository.createDiscussion(
            name = "Discussion 1",
            description = "First discussion",
            creatorId = alice.uid,
            participants = listOf(alice.uid))
    val discussion2 =
        discussionRepository.createDiscussion(
            name = "Discussion 2", description = "Second discussion", creatorId = alice.uid)

    // Test basic creation with correct fields
    accountRepository.sendJoinDiscussionNotification(bob.uid, discussion1)
    var updatedBob = accountRepository.getAccount(bob.uid)
    assertEquals(1, updatedBob.notifications.size)
    assertEquals(discussion1.uid, updatedBob.notifications[0].senderOrDiscussionId)
    assertEquals(bob.uid, updatedBob.notifications[0].receiverId)
    assertEquals(NotificationType.JOIN_DISCUSSION, updatedBob.notifications[0].type)
    assertFalse(updatedBob.notifications[0].read)

    // Test multiple users invited to same discussion
    accountRepository.sendJoinDiscussionNotification(charlie.uid, discussion1)
    val updatedCharlie = accountRepository.getAccount(charlie.uid)
    assertEquals(1, updatedCharlie.notifications.size)
    assertEquals(discussion1.uid, updatedCharlie.notifications[0].senderOrDiscussionId)
    assertEquals(NotificationType.JOIN_DISCUSSION, updatedCharlie.notifications[0].type)

    // Test one user invited to multiple discussions
    accountRepository.sendJoinDiscussionNotification(bob.uid, discussion2)
    updatedBob = accountRepository.getAccount(bob.uid)
    assertEquals(2, updatedBob.notifications.size)
    val notif1 = updatedBob.notifications.find { it.senderOrDiscussionId == discussion1.uid }
    val notif2 = updatedBob.notifications.find { it.senderOrDiscussionId == discussion2.uid }
    assertNotNull(notif1)
    assertNotNull(notif2)
    assertEquals(NotificationType.JOIN_DISCUSSION, notif1!!.type)
    assertEquals(NotificationType.JOIN_DISCUSSION, notif2!!.type)
  }

  // ==================== sendJoinSessionNotification Tests ====================

  @Test
  fun sessionInviteNotification_createsAndSupportsMultipleInvitations() = runBlocking {
    val discussion =
        discussionRepository.createDiscussion(
            name = "Gaming Session",
            description = "For session testing",
            creatorId = alice.uid,
            participants = listOf(alice.uid))

    // Test basic creation with correct fields
    accountRepository.sendJoinSessionNotification(bob.uid, discussion)
    val updatedBob = accountRepository.getAccount(bob.uid)
    assertEquals(1, updatedBob.notifications.size)
    val notification = updatedBob.notifications[0]
    assertEquals(discussion.uid, notification.senderOrDiscussionId)
    assertEquals(bob.uid, notification.receiverId)
    assertEquals(NotificationType.JOIN_SESSION, notification.type)
    assertFalse(notification.read)

    // Test multiple users can be invited to same session
    accountRepository.sendJoinSessionNotification(charlie.uid, discussion)
    val updatedCharlie = accountRepository.getAccount(charlie.uid)
    assertEquals(1, updatedCharlie.notifications.size)
    assertEquals(NotificationType.JOIN_SESSION, updatedCharlie.notifications[0].type)
    assertEquals(discussion.uid, updatedCharlie.notifications[0].senderOrDiscussionId)
  }

  // ==================== readNotification Tests ====================

  @Test
  fun readNotification_worksCorrectlyForSingleAndMultipleNotifications() = runBlocking {
    accountRepository.sendFriendRequestNotification(bob.uid, alice)
    accountRepository.sendFriendRequestNotification(bob.uid, charlie)

    var updatedBob = accountRepository.getAccount(bob.uid)
    assertEquals(2, updatedBob.notifications.size)
    val firstNotification = updatedBob.notifications[0]
    val secondNotification = updatedBob.notifications[1]
    assertFalse(firstNotification.read)
    assertFalse(secondNotification.read)

    // Test marking specific notification as read
    accountRepository.readNotification(bob.uid, firstNotification.uid)
    updatedBob = accountRepository.getAccount(bob.uid)
    val firstRead = updatedBob.notifications.find { it.uid == firstNotification.uid }
    val secondRead = updatedBob.notifications.find { it.uid == secondNotification.uid }
    assertTrue(firstRead!!.read)
    assertFalse(secondRead!!.read)

    // Test idempotency - reading multiple times doesn't change state
    accountRepository.readNotification(bob.uid, firstNotification.uid)
    accountRepository.readNotification(bob.uid, firstNotification.uid)
    updatedBob = accountRepository.getAccount(bob.uid)
    assertTrue(updatedBob.notifications.find { it.uid == firstNotification.uid }!!.read)
  }

  // ==================== deleteNotification Tests ====================

  @Test
  fun deleteNotification_removesCorrectNotifications() = runBlocking {
    accountRepository.sendFriendRequestNotification(bob.uid, alice)
    accountRepository.sendFriendRequestNotification(bob.uid, charlie)

    var updatedBob = accountRepository.getAccount(bob.uid)
    assertEquals(2, updatedBob.notifications.size)
    val firstNotification = updatedBob.notifications.find { it.senderOrDiscussionId == alice.uid }!!
    val secondNotification =
        updatedBob.notifications.find { it.senderOrDiscussionId == charlie.uid }!!

    // Test deleting specific notification
    accountRepository.deleteNotification(bob.uid, firstNotification.uid)
    updatedBob = accountRepository.getAccount(bob.uid)
    assertEquals(1, updatedBob.notifications.size)
    assertEquals(charlie.uid, updatedBob.notifications[0].senderOrDiscussionId)

    // Test deleting remaining notification
    accountRepository.deleteNotification(bob.uid, secondNotification.uid)
    updatedBob = accountRepository.getAccount(bob.uid)
    assertEquals(0, updatedBob.notifications.size)
  }

  // ==================== Notification Execution Tests ====================

  @Test
  fun executeNotification_friendRequest_acceptsFriendship() = runBlocking {
    accountRepository.sendFriendRequest(alice.uid, bob.uid)
    accountRepository.sendFriendRequestNotification(bob.uid, alice)

    val updatedBob = accountRepository.getAccount(bob.uid)
    val notification = updatedBob.notifications[0]

    notification.execute()

    val accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))
    assertEquals(RelationshipStatus.FRIEND, accounts[0].relationships[bob.uid])
    assertEquals(RelationshipStatus.FRIEND, accounts[1].relationships[alice.uid])
  }

  @Test
  fun executeNotification_joinDiscussion_addsUserToDiscussion() = runBlocking {
    val discussion =
        discussionRepository.createDiscussion(
            name = "Test Discussion",
            description = "Testing",
            creatorId = alice.uid,
            participants = listOf(alice.uid))

    accountRepository.sendJoinDiscussionNotification(bob.uid, discussion)

    val updatedBob = accountRepository.getAccount(bob.uid)
    val notification = updatedBob.notifications[0]

    notification.execute()

    val updatedDiscussion = discussionRepository.getDiscussion(discussion.uid)
    assertTrue(updatedDiscussion.participants.contains(bob.uid))
  }

  @Test
  fun executeNotification_joinSession_addsUserToSession() = runBlocking {
    val discussion =
        discussionRepository.createDiscussion(
            name = "Session Test", description = "Testing", creatorId = alice.uid)

    // Create a session for this discussion
    val testLocation = com.github.meeplemeet.model.shared.location.Location(46.5197, 6.5665, "EPFL")
    val testTimestamp = com.google.firebase.Timestamp.now()
    sessionRepository.createSession(
        discussion.uid, "Game Night", "game123", testTimestamp, testLocation, alice.uid)

    accountRepository.sendJoinSessionNotification(bob.uid, discussion)

    val updatedBob = accountRepository.getAccount(bob.uid)
    val notification = updatedBob.notifications[0]

    notification.execute()

    val session = sessionRepository.getSession(discussion.uid)
    assertNotNull(session)
    assertTrue(session!!.participants.contains(bob.uid))
  }

  @Test
  fun executeNotification_isIdempotent() = runBlocking {
    accountRepository.sendFriendRequest(alice.uid, bob.uid)
    accountRepository.sendFriendRequestNotification(bob.uid, alice)

    val updatedBob = accountRepository.getAccount(bob.uid)
    val notification = updatedBob.notifications[0]

    notification.execute()
    notification.execute()
    notification.execute()

    val accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))
    assertEquals(RelationshipStatus.FRIEND, accounts[0].relationships[bob.uid])
    assertEquals(RelationshipStatus.FRIEND, accounts[1].relationships[alice.uid])
  }

  // ==================== listenAccount with Notifications Tests ====================

  @Test
  fun listenAccount_receivesNotificationUpdates() = runBlocking {
    val accountFlow = accountRepository.listenAccount(bob.uid)

    // Wait for initial emission with no notifications
    val initialAccount = accountFlow.first()
    assertEquals(0, initialAccount.notifications.size)

    // Send a notification
    accountRepository.sendFriendRequestNotification(bob.uid, alice)

    // Wait for update with notification
    delay(500)
    val updatedAccount = accountFlow.first()
    assertEquals(1, updatedAccount.notifications.size)
    assertEquals(alice.uid, updatedAccount.notifications[0].senderOrDiscussionId)
  }

  @Test
  fun listenAccount_receivesMultipleNotificationUpdates() = runBlocking {
    val accountFlow = accountRepository.listenAccount(bob.uid)

    val initialAccount = accountFlow.first()
    assertEquals(0, initialAccount.notifications.size)

    accountRepository.sendFriendRequestNotification(bob.uid, alice)
    delay(500)
    var updatedAccount = accountFlow.first()
    assertEquals(1, updatedAccount.notifications.size)

    accountRepository.sendFriendRequestNotification(bob.uid, charlie)
    delay(500)
    updatedAccount = accountFlow.first()
    assertEquals(2, updatedAccount.notifications.size)
  }

  @Test
  fun listenAccount_receivesNotificationDeletion() = runBlocking {
    accountRepository.sendFriendRequestNotification(bob.uid, alice)

    val accountFlow = accountRepository.listenAccount(bob.uid)
    var currentAccount = accountFlow.first()
    assertEquals(1, currentAccount.notifications.size)
    val notification = currentAccount.notifications[0]

    accountRepository.deleteNotification(bob.uid, notification.uid)
    delay(500)

    currentAccount = accountFlow.first()
    assertEquals(0, currentAccount.notifications.size)
  }

  @Test
  fun listenAccount_receivesNotificationReadStatus() = runBlocking {
    accountRepository.sendFriendRequestNotification(bob.uid, alice)

    val accountFlow = accountRepository.listenAccount(bob.uid)
    var currentAccount = accountFlow.first()
    val notification = currentAccount.notifications[0]
    assertFalse(notification.read)

    accountRepository.readNotification(bob.uid, notification.uid)
    delay(500)

    currentAccount = accountFlow.first()
    assertTrue(currentAccount.notifications[0].read)
  }

  // ==================== ViewModel Notification Tests ====================

  @Test
  fun viewModelReadNotification_preventsInvalidRead() = runBlocking {
    cleanupRelationshipsAndNotifications()

    accountRepository.sendFriendRequestNotification(bob.uid, alice)

    val updatedBob = accountRepository.getAccount(bob.uid)
    val notification = updatedBob.notifications[0]

    // Try to read notification from wrong account
    viewModel.readNotification(alice, notification)

    // Verify notification was NOT marked as read
    val bobAfter = accountRepository.getAccount(bob.uid)
    assertFalse(bobAfter.notifications[0].read)
  }

  @Test
  fun viewModelReadNotification_allowsValidRead() = runBlocking {
    cleanupRelationshipsAndNotifications()

    accountRepository.sendFriendRequestNotification(bob.uid, alice)

    var updatedBob = accountRepository.getAccount(bob.uid)
    val notification = updatedBob.notifications[0]

    viewModel.readNotification(updatedBob, notification)

    // Wait for async operation
    delay(500)

    updatedBob = accountRepository.getAccount(bob.uid)
    assertTrue(updatedBob.notifications[0].read)
  }

  @Test
  fun viewModelDeleteNotification_preventsInvalidDeletion() = runBlocking {
    cleanupRelationshipsAndNotifications()

    accountRepository.sendFriendRequestNotification(bob.uid, alice)

    val updatedBob = accountRepository.getAccount(bob.uid)
    val notification = updatedBob.notifications[0]

    // Try to delete notification from wrong account
    viewModel.deleteNotification(alice, notification)

    // Verify notification was NOT deleted
    val bobAfter = accountRepository.getAccount(bob.uid)
    assertEquals(1, bobAfter.notifications.size)
  }

  @Test
  fun viewModelDeleteNotification_allowsValidDeletion() = runBlocking {
    cleanupRelationshipsAndNotifications()

    accountRepository.sendFriendRequestNotification(bob.uid, alice)

    var updatedBob = accountRepository.getAccount(bob.uid)
    val notification = updatedBob.notifications[0]

    viewModel.deleteNotification(updatedBob, notification)

    // Wait for async operation
    delay(500)

    updatedBob = accountRepository.getAccount(bob.uid)
    assertEquals(0, updatedBob.notifications.size)
  }

  // ==================== Complex Workflow Tests ====================

  @Test
  fun completeNotificationWorkflow_sendReadExecuteDelete() = runBlocking {
    cleanupRelationshipsAndNotifications()

    // Send friend request and notification
    accountRepository.sendFriendRequest(alice.uid, bob.uid)
    accountRepository.sendFriendRequestNotification(bob.uid, alice)

    var updatedBob = accountRepository.getAccount(bob.uid)
    assertEquals(1, updatedBob.notifications.size)
    var notification = updatedBob.notifications[0]
    assertFalse(notification.read)

    // Read the notification
    accountRepository.readNotification(bob.uid, notification.uid)
    updatedBob = accountRepository.getAccount(bob.uid)
    notification = updatedBob.notifications[0]
    assertTrue(notification.read)

    // Execute the notification (accept friend request)
    notification.execute()
    val accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))
    assertEquals(RelationshipStatus.FRIEND, accounts[0].relationships[bob.uid])
    assertEquals(RelationshipStatus.FRIEND, accounts[1].relationships[alice.uid])

    // Delete the notification
    accountRepository.deleteNotification(bob.uid, notification.uid)
    updatedBob = accountRepository.getAccount(bob.uid)
    assertEquals(0, updatedBob.notifications.size)
  }

  @Test
  fun mixedNotificationTypes_independentManagement() = runBlocking {
    cleanupRelationshipsAndNotifications()

    val discussion =
        discussionRepository.createDiscussion(
            name = "Mixed Test", description = "Testing", creatorId = alice.uid)

    accountRepository.sendFriendRequestNotification(bob.uid, alice)
    accountRepository.sendJoinDiscussionNotification(bob.uid, discussion)
    accountRepository.sendJoinSessionNotification(bob.uid, discussion)

    val updatedBob = accountRepository.getAccount(bob.uid)
    assertEquals(3, updatedBob.notifications.size)

    val friendNotif = updatedBob.notifications.find { it.type == NotificationType.FRIEND_REQUEST }
    val discussionNotif =
        updatedBob.notifications.find { it.type == NotificationType.JOIN_DISCUSSION }
    val sessionNotif = updatedBob.notifications.find { it.type == NotificationType.JOIN_SESSION }

    assertNotNull(friendNotif)
    assertNotNull(discussionNotif)
    assertNotNull(sessionNotif)

    // Read only the friend request notification
    accountRepository.readNotification(bob.uid, friendNotif!!.uid)

    val afterRead = accountRepository.getAccount(bob.uid)
    val friendNotifAfter = afterRead.notifications.find { it.uid == friendNotif.uid }
    val discussionNotifAfter = afterRead.notifications.find { it.uid == discussionNotif!!.uid }
    val sessionNotifAfter = afterRead.notifications.find { it.uid == sessionNotif!!.uid }

    assertTrue(friendNotifAfter!!.read)
    assertFalse(discussionNotifAfter!!.read)
    assertFalse(sessionNotifAfter!!.read)
  }

  // ==================== Edge Cases ====================

  @Test
  fun notificationsListIsEmptyForNewAccount() = runBlocking {
    val newAccount =
        accountRepository.createAccount(
            "new_notif_user", "New User", email = "new_notif@example.com", photoUrl = null)

    assertTrue(newAccount.notifications.isEmpty())
  }

  @Test
  fun getAccount_withGetAllDataFalse_returnsEmptyNotifications() = runBlocking {
    // Uses the two-argument overload of getAccount
    accountRepository.sendFriendRequestNotification(bob.uid, alice)

    val bobWithData = accountRepository.getAccount(bob.uid, getAllData = true)
    val bobWithoutData = accountRepository.getAccount(bob.uid, getAllData = false)

    assertEquals(1, bobWithData.notifications.size)
    assertEquals(0, bobWithoutData.notifications.size)
  }

  @Test
  fun notificationsArePersistentAcrossMultipleRetrieval() = runBlocking {
    accountRepository.sendFriendRequestNotification(bob.uid, alice)

    val fetch1 = accountRepository.getAccount(bob.uid)
    val fetch2 = accountRepository.getAccount(bob.uid)
    val fetch3 = accountRepository.getAccount(bob.uid)

    assertEquals(1, fetch1.notifications.size)
    assertEquals(1, fetch2.notifications.size)
    assertEquals(1, fetch3.notifications.size)

    assertEquals(fetch1.notifications[0].uid, fetch2.notifications[0].uid)
    assertEquals(fetch2.notifications[0].uid, fetch3.notifications[0].uid)
  }

  @Test
  fun notificationsAreIndependentBetweenAccounts() = runBlocking {
    accountRepository.sendFriendRequestNotification(bob.uid, alice)
    accountRepository.sendFriendRequestNotification(charlie.uid, alice)

    val updatedBob = accountRepository.getAccount(bob.uid)
    val updatedCharlie = accountRepository.getAccount(charlie.uid)
    val updatedAlice = accountRepository.getAccount(alice.uid)

    assertEquals(1, updatedBob.notifications.size)
    assertEquals(1, updatedCharlie.notifications.size)
    assertEquals(0, updatedAlice.notifications.size)

    // Reading Bob's notification doesn't affect Charlie's
    accountRepository.readNotification(bob.uid, updatedBob.notifications[0].uid)

    val bobAfterRead = accountRepository.getAccount(bob.uid)
    val charlieAfterRead = accountRepository.getAccount(charlie.uid)

    assertTrue(bobAfterRead.notifications[0].read)
    assertFalse(charlieAfterRead.notifications[0].read)
  }

  @Test
  fun notification_executedFlagPreventsMultipleExecutions() = runBlocking {
    accountRepository.sendFriendRequest(alice.uid, bob.uid)
    accountRepository.sendFriendRequestNotification(bob.uid, alice)

    val notification = accountRepository.getAccount(bob.uid).notifications[0]
    assertFalse(notification.executed())

    // Execute first time
    notification.execute()

    // The executed flag is set in memory (not persisted to Firestore)
    assertTrue(notification.executed())

    // Verify friendship created
    var accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))
    assertEquals(RelationshipStatus.FRIEND, accounts[0].relationships[bob.uid])
    assertEquals(RelationshipStatus.FRIEND, accounts[1].relationships[alice.uid])

    // Reset relationships to test if second execution on the SAME notification object would
    // recreate
    accountRepository.resetRelationship(alice.uid, bob.uid)
    accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))
    assertNull(accounts[0].relationships[bob.uid])
    assertNull(accounts[1].relationships[bob.uid])

    // Execute again on SAME notification object - should be idempotent (no action)
    notification.execute()

    // Relationships should still be null (execution was idempotent)
    accounts = accountRepository.getAccounts(listOf(alice.uid, bob.uid))
    assertNull(accounts[0].relationships[bob.uid])
    assertNull(accounts[1].relationships[bob.uid])
  }
}
