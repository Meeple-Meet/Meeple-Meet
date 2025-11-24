package com.github.meeplemeet.integration

import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.account.NotificationType
import com.github.meeplemeet.model.account.ProfileScreenViewModel
import com.github.meeplemeet.model.account.RelationshipStatus
import com.github.meeplemeet.utils.FirestoreTests
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

/**
 * Integration tests for notification functionality in AccountRepository and ProfileScreenViewModel.
 *
 * These tests verify the complete notification system workflow using real Firestore operations:
 * - Sending friend request notifications
 * - Sending discussion invitation notifications
 * - Sending session invitation notifications
 * - Reading notifications
 * - Deleting notifications
 * - Executing notifications (accepting friend requests, joining discussions, joining sessions)
 * - Real-time notification updates via listenAccount
 * - ViewModel validation logic for notification operations
 *
 * All tests use the Firebase emulator and verify that notifications are properly created, stored,
 * and can be retrieved through both direct fetching and real-time listeners.
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
    viewModel = ProfileScreenViewModel(accountRepository)
  }

  /**
   * Cleans up all relationships and notifications between test accounts.
   *
   * This helper method removes all existing relationships and notifications between alice, bob, and
   * charlie to ensure each test starts with a clean slate. This prevents state leakage between
   * tests that could cause flaky or incorrect test results.
   *
   * The cleanup process:
   * 1. Resets all bidirectional relationships between the three test accounts
   * 2. Fetches current account states to get all existing notifications
   * 3. Deletes each notification from each account
   *
   * This method should be called in setUp or at the beginning of tests that require isolated
   * notification state.
   */
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
  fun sendFriendRequestNotification_createsNotificationAndPersists() = runBlocking {
    accountRepository.sendFriendRequestNotification(bob.uid, alice)

    val updatedBob = accountRepository.getAccount(bob.uid)

    assertEquals(1, updatedBob.notifications.size)
    val notification = updatedBob.notifications[0]
    assertNotNull(notification.uid)
    assertEquals(alice.uid, notification.senderOrDiscussionId)
    assertEquals(bob.uid, notification.receiverId)
    assertEquals(NotificationType.FriendRequest, notification.type)
    assertFalse(notification.read)
  }

  @Test
  fun sendFriendRequest_automaticallySendsNotification() = runBlocking {
    accountRepository.sendFriendRequest(alice, bob.uid)

    val updatedBob = accountRepository.getAccount(bob.uid)

    assertEquals(1, updatedBob.notifications.size)
    val notification = updatedBob.notifications[0]
    assertEquals(alice.uid, notification.senderOrDiscussionId)
    assertEquals(NotificationType.FriendRequest, notification.type)
  }

  @Test
  fun sendFriendRequestNotification_multipleUsersCanSendToSameUser() = runBlocking {
    accountRepository.sendFriendRequestNotification(charlie.uid, alice)
    accountRepository.sendFriendRequestNotification(charlie.uid, bob)

    val updatedCharlie = accountRepository.getAccount(charlie.uid)

    assertEquals(2, updatedCharlie.notifications.size)
    val notificationFromAlice =
        updatedCharlie.notifications.find { it.senderOrDiscussionId == alice.uid }
    val notificationFromBob =
        updatedCharlie.notifications.find { it.senderOrDiscussionId == bob.uid }

    assertNotNull(notificationFromAlice)
    assertNotNull(notificationFromBob)
    assertEquals(NotificationType.FriendRequest, notificationFromAlice!!.type)
    assertEquals(NotificationType.FriendRequest, notificationFromBob!!.type)
  }

  @Test
  fun sendFriendRequestNotification_oneUserCanSendToMultipleUsers() = runBlocking {
    accountRepository.sendFriendRequestNotification(bob.uid, alice)
    accountRepository.sendFriendRequestNotification(charlie.uid, alice)

    val updatedBob = accountRepository.getAccount(bob.uid)
    val updatedCharlie = accountRepository.getAccount(charlie.uid)

    assertEquals(1, updatedBob.notifications.size)
    assertEquals(alice.uid, updatedBob.notifications[0].senderOrDiscussionId)

    assertEquals(1, updatedCharlie.notifications.size)
    assertEquals(alice.uid, updatedCharlie.notifications[0].senderOrDiscussionId)
  }

  // ==================== sendJoinDiscussionNotification Tests ====================

  @Test
  fun sendJoinDiscussionNotification_createsNotificationAndPersists() = runBlocking {
    val discussion =
        discussionRepository.createDiscussion(
            name = "Test Discussion",
            description = "A test discussion",
            creatorId = alice.uid,
            participants = listOf(alice.uid))

    accountRepository.sendJoinDiscussionNotification(bob.uid, discussion)

    val updatedBob = accountRepository.getAccount(bob.uid)

    assertEquals(1, updatedBob.notifications.size)
    val notification = updatedBob.notifications[0]
    assertEquals(discussion.uid, notification.senderOrDiscussionId)
    assertEquals(bob.uid, notification.receiverId)
    assertEquals(NotificationType.JoinDiscussion, notification.type)
    assertFalse(notification.read)
  }

  @Test
  fun sendJoinDiscussionNotification_multipleUsersCanBeInvitedToSameDiscussion() = runBlocking {
    val discussion =
        discussionRepository.createDiscussion(
            name = "Group Discussion",
            description = "A discussion for everyone",
            creatorId = alice.uid,
            participants = listOf(alice.uid))

    accountRepository.sendJoinDiscussionNotification(bob.uid, discussion)
    accountRepository.sendJoinDiscussionNotification(charlie.uid, discussion)

    val updatedBob = accountRepository.getAccount(bob.uid)
    val updatedCharlie = accountRepository.getAccount(charlie.uid)

    assertEquals(1, updatedBob.notifications.size)
    assertEquals(discussion.uid, updatedBob.notifications[0].senderOrDiscussionId)
    assertEquals(NotificationType.JoinDiscussion, updatedBob.notifications[0].type)

    assertEquals(1, updatedCharlie.notifications.size)
    assertEquals(discussion.uid, updatedCharlie.notifications[0].senderOrDiscussionId)
    assertEquals(NotificationType.JoinDiscussion, updatedCharlie.notifications[0].type)
  }

  @Test
  fun sendJoinDiscussionNotification_oneUserCanBeInvitedToMultipleDiscussions() = runBlocking {
    val discussion1 =
        discussionRepository.createDiscussion(
            name = "Discussion 1", description = "First", creatorId = alice.uid)
    val discussion2 =
        discussionRepository.createDiscussion(
            name = "Discussion 2", description = "Second", creatorId = alice.uid)

    accountRepository.sendJoinDiscussionNotification(bob.uid, discussion1)
    accountRepository.sendJoinDiscussionNotification(bob.uid, discussion2)

    val updatedBob = accountRepository.getAccount(bob.uid)

    assertEquals(2, updatedBob.notifications.size)
    val notif1 = updatedBob.notifications.find { it.senderOrDiscussionId == discussion1.uid }
    val notif2 = updatedBob.notifications.find { it.senderOrDiscussionId == discussion2.uid }

    assertNotNull(notif1)
    assertNotNull(notif2)
    assertEquals(NotificationType.JoinDiscussion, notif1!!.type)
    assertEquals(NotificationType.JoinDiscussion, notif2!!.type)
  }

  // ==================== sendJoinSessionNotification Tests ====================

  @Test
  fun sendJoinSessionNotification_createsNotificationAndPersists() = runBlocking {
    val discussion =
        discussionRepository.createDiscussion(
            name = "Session Discussion",
            description = "For session testing",
            creatorId = alice.uid,
            participants = listOf(alice.uid))

    accountRepository.sendJoinSessionNotification(bob.uid, discussion)

    val updatedBob = accountRepository.getAccount(bob.uid)

    assertEquals(1, updatedBob.notifications.size)
    val notification = updatedBob.notifications[0]
    assertEquals(discussion.uid, notification.senderOrDiscussionId)
    assertEquals(bob.uid, notification.receiverId)
    assertEquals(NotificationType.JoinSession, notification.type)
    assertFalse(notification.read)
  }

  @Test
  fun sendJoinSessionNotification_multipleUsersCanBeInvitedToSameSession() = runBlocking {
    val discussion =
        discussionRepository.createDiscussion(
            name = "Gaming Session", description = "Play games", creatorId = alice.uid)

    accountRepository.sendJoinSessionNotification(bob.uid, discussion)
    accountRepository.sendJoinSessionNotification(charlie.uid, discussion)

    val updatedBob = accountRepository.getAccount(bob.uid)
    val updatedCharlie = accountRepository.getAccount(charlie.uid)

    assertEquals(1, updatedBob.notifications.size)
    assertEquals(NotificationType.JoinSession, updatedBob.notifications[0].type)

    assertEquals(1, updatedCharlie.notifications.size)
    assertEquals(NotificationType.JoinSession, updatedCharlie.notifications[0].type)
  }

  // ==================== readNotification Tests ====================

  @Test
  fun readNotification_marksNotificationAsRead() = runBlocking {
    accountRepository.sendFriendRequestNotification(bob.uid, alice)

    var updatedBob = accountRepository.getAccount(bob.uid)
    val notification = updatedBob.notifications[0]
    assertFalse(notification.read)

    accountRepository.readNotification(bob.uid, notification.uid)

    updatedBob = accountRepository.getAccount(bob.uid)
    val readNotification = updatedBob.notifications[0]
    assertTrue(readNotification.read)
  }

  @Test
  fun readNotification_onlyMarksSpecificNotification() = runBlocking {
    accountRepository.sendFriendRequestNotification(bob.uid, alice)
    accountRepository.sendFriendRequestNotification(bob.uid, charlie)

    var updatedBob = accountRepository.getAccount(bob.uid)
    assertEquals(2, updatedBob.notifications.size)
    val firstNotification = updatedBob.notifications[0]
    val secondNotification = updatedBob.notifications[1]

    accountRepository.readNotification(bob.uid, firstNotification.uid)

    updatedBob = accountRepository.getAccount(bob.uid)
    val firstRead = updatedBob.notifications.find { it.uid == firstNotification.uid }
    val secondRead = updatedBob.notifications.find { it.uid == secondNotification.uid }

    assertTrue(firstRead!!.read)
    assertFalse(secondRead!!.read)
  }

  @Test
  fun readNotification_canReadMultipleTimes() = runBlocking {
    accountRepository.sendFriendRequestNotification(bob.uid, alice)

    val notification = accountRepository.getAccount(bob.uid).notifications[0]

    accountRepository.readNotification(bob.uid, notification.uid)
    accountRepository.readNotification(bob.uid, notification.uid)
    accountRepository.readNotification(bob.uid, notification.uid)

    val updatedBob = accountRepository.getAccount(bob.uid)
    assertTrue(updatedBob.notifications[0].read)
  }

  // ==================== deleteNotification Tests ====================

  @Test
  fun deleteNotification_removesNotification() = runBlocking {
    accountRepository.sendFriendRequestNotification(bob.uid, alice)

    var updatedBob = accountRepository.getAccount(bob.uid)
    assertEquals(1, updatedBob.notifications.size)
    val notification = updatedBob.notifications[0]

    accountRepository.deleteNotification(bob.uid, notification.uid)

    updatedBob = accountRepository.getAccount(bob.uid)
    assertEquals(0, updatedBob.notifications.size)
  }

  @Test
  fun deleteNotification_onlyDeletesSpecificNotification() = runBlocking {
    accountRepository.sendFriendRequestNotification(bob.uid, alice)
    accountRepository.sendFriendRequestNotification(bob.uid, charlie)

    var updatedBob = accountRepository.getAccount(bob.uid)
    assertEquals(2, updatedBob.notifications.size)
    val firstNotification =
        updatedBob.notifications.find { notification ->
          notification.senderOrDiscussionId == alice.uid
        }!!

    accountRepository.deleteNotification(bob.uid, firstNotification.uid)

    updatedBob = accountRepository.getAccount(bob.uid)
    assertEquals(1, updatedBob.notifications.size)
    assertEquals(charlie.uid, updatedBob.notifications[0].senderOrDiscussionId)
  }

  @Test
  fun deleteNotification_multipleDeletions() = runBlocking {
    accountRepository.sendFriendRequestNotification(bob.uid, alice)
    accountRepository.sendFriendRequestNotification(bob.uid, charlie)

    var updatedBob = accountRepository.getAccount(bob.uid)
    val firstNotification = updatedBob.notifications[0]
    val secondNotification = updatedBob.notifications[1]

    accountRepository.deleteNotification(bob.uid, firstNotification.uid)
    accountRepository.deleteNotification(bob.uid, secondNotification.uid)

    updatedBob = accountRepository.getAccount(bob.uid)
    assertEquals(0, updatedBob.notifications.size)
  }

  // ==================== Notification Execution Tests ====================

  @Test
  fun executeNotification_friendRequest_acceptsFriendship() = runBlocking {
    accountRepository.sendFriendRequest(alice, bob.uid)

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
    accountRepository.sendFriendRequest(alice, bob.uid)

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

    // Send friend request (which creates notification)
    accountRepository.sendFriendRequest(alice, bob.uid)

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

    val friendNotif = updatedBob.notifications.find { it.type == NotificationType.FriendRequest }
    val discussionNotif =
        updatedBob.notifications.find { it.type == NotificationType.JoinDiscussion }
    val sessionNotif = updatedBob.notifications.find { it.type == NotificationType.JoinSession }

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
}
