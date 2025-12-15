package com.github.meeplemeet.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.github.meeplemeet.model.MainActivityViewModel
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.account.Notification
import com.github.meeplemeet.model.account.NotificationNoUid
import com.github.meeplemeet.model.account.NotificationType
import com.github.meeplemeet.model.account.NotificationsViewModel
import com.github.meeplemeet.model.discussions.Discussion
import com.github.meeplemeet.model.sessions.Session
import com.github.meeplemeet.model.shared.game.GameNoUid
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.ui.account.NotificationsTab
import com.github.meeplemeet.ui.account.NotificationsTabTestTags
import com.github.meeplemeet.ui.navigation.NavigationActions
import com.github.meeplemeet.ui.theme.AppTheme
import com.github.meeplemeet.utils.Checkpoint
import com.github.meeplemeet.utils.FirestoreTests
import com.google.firebase.Timestamp
import io.mockk.mockk
import java.util.Date
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class NotificationsTabTest : FirestoreTests() {

  @get:Rule val compose = createComposeRule()
  @get:Rule val ck = Checkpoint.Rule()

  private fun checkpoint(name: String, block: () -> Unit) = ck.ck(name, block)

  private lateinit var mockNavigation: NavigationActions
  private lateinit var viewModel: NotificationsViewModel
  private lateinit var navViewModel: MainActivityViewModel
  private lateinit var currentUser: Account
  private lateinit var otherUser: Account
  private lateinit var discussion: Discussion
  private lateinit var session: Session
  private lateinit var friendRequestNotification: Notification
  private lateinit var discussionNotification: Notification
  private lateinit var sessionNotification: Notification

  // --- Helpers ---
  private fun headerTitle() = compose.onNodeWithTag(NotificationsTabTestTags.HEADER_TITLE)

  private fun notificationList() = compose.onNodeWithTag(NotificationsTabTestTags.NOTIFICATION_LIST)

  private fun filterChip(filter: String) =
      compose.onNodeWithTag(NotificationsTabTestTags.FILTER_CHIP_PREFIX + filter)

  private fun notificationItem(uid: String) =
      compose.onNodeWithTag(NotificationsTabTestTags.NOTIFICATION_ITEM_PREFIX + uid)

  private fun unreadDot(uid: String) =
      compose.onNodeWithTag(NotificationsTabTestTags.UNREAD_DOT_PREFIX + uid)

  private fun emptyStateText() = compose.onNodeWithTag(NotificationsTabTestTags.EMPTY_STATE_TEXT)

  private fun sheetTitle() = compose.onNodeWithTag(NotificationsTabTestTags.SHEET_TITLE)

  private fun sheetDescription() = compose.onNodeWithTag(NotificationsTabTestTags.SHEET_DESCRIPTION)

  private fun sheetAcceptButton() =
      compose.onNodeWithTag(NotificationsTabTestTags.SHEET_ACCEPT_BUTTON)

  private fun sheetDeclineButton() =
      compose.onNodeWithTag(NotificationsTabTestTags.SHEET_DECLINE_BUTTON)

  private fun sheetCloseButton() = compose.onNodeWithContentDescription("Close")

  @Before
  fun setup() {
    viewModel =
        NotificationsViewModel(
            accountRepository = accountRepository,
            handlesRepository = handlesRepository,
            imageRepository = imageRepository,
            discussionRepository = discussionRepository)
    navViewModel = MainActivityViewModel(inTests = true, accountRepository = accountRepository)

    runBlocking {
      // Create current user
      currentUser =
          accountRepository.createAccount(
              userHandle = "currentUser",
              name = "Current User",
              email = "current@test.com",
              photoUrl = null)

      // Create other user for friend request
      otherUser =
          accountRepository.createAccount(
              userHandle = "otherUser",
              name = "Other User",
              email = "other@test.com",
              photoUrl = null)

      // Create a real discussion for the notification
      discussion =
          discussionRepository.createDiscussion(
              creatorId = otherUser.uid,
              name = "Board Game Night",
              description = "Let's play some games!")

      // Create a Game for the session
      val gameId = "game_1"
      val game =
          GameNoUid(
              name = "Catan", description = "Trade, build, settle", minPlayers = 3, maxPlayers = 4)
      db.collection("games").document(gameId).set(game).await()

      val sessionDiscussion =
          discussionRepository.createDiscussion(
              creatorId = otherUser.uid,
              name = "Catan Session Chat",
              description = "Chat for Catan")

      // Create a Session and update the discussion
      session =
          sessionRepository
              .createSession(
                  sessionDiscussion.uid,
                  "Catan Session",
                  gameId,
                  game.name,
                  Timestamp(Date(System.currentTimeMillis() + 86400000)),
                  location = Location(0.0, 0.0, "Game Store"),
                  rentalId = null,
                  participants = arrayOf(otherUser.uid))
              .session!!

      session =
          Session(
              name = "Catan Session",
              gameId = gameId,
              date = Timestamp(Date(System.currentTimeMillis() + 86400000)), // Tomorrow
              location = Location(0.0, 0.0, "Game Store"),
              participants = listOf(otherUser.uid))

      // Update discussion with session
      db.collection("discussions")
          .document(sessionDiscussion.uid)
          .update("session", session)
          .await()

      // Create notifications
      val now = System.currentTimeMillis()
      friendRequestNotification =
          Notification(
              uid = "n1",
              senderId = otherUser.uid,
              receiverId = currentUser.uid,
              read = false,
              type = NotificationType.FRIEND_REQUEST,
              sentAt = Timestamp(Date(now)))

      discussionNotification =
          Notification(
              uid = "n2",
              discussionId = discussion.uid,
              receiverId = currentUser.uid,
              read = true,
              type = NotificationType.JOIN_DISCUSSION,
              sentAt = Timestamp(Date(now - 3600000)) // 1 hour ago
              )

      sessionNotification =
          Notification(
              uid = "n3",
              discussionId = sessionDiscussion.uid,
              receiverId = currentUser.uid,
              read = false,
              type = NotificationType.JOIN_SESSION,
              sentAt = Timestamp(Date(now - 7200000)) // 2 hours ago
              )

      // Seed notifications in Firestore
      val notificationsRef =
          db.collection("accounts").document(currentUser.uid).collection("notifications")

      val notifications =
          listOf(friendRequestNotification, discussionNotification, sessionNotification)

      notifications.forEach { notif ->
        notificationsRef
            .document(notif.uid)
            .set(
                NotificationNoUid(
                    senderId = notif.senderId,
                    discussionId = notif.discussionId,
                    read = notif.read,
                    type = notif.type,
                    sentAt = notif.sentAt))
            .await()
      }

      // Update current user with notifications
      currentUser = currentUser.copy(notifications = notifications)

      mockNavigation = mockk(relaxed = true)
    }
  }

  @Test
  fun smoke_all_notifications_tests() {
    compose.setContent {
      AppTheme {
        NotificationsTab(
            account = currentUser,
            viewModel = viewModel,
            onBack = {},
            verified = true,
            navigationActions = mockNavigation,
            unreadCount = currentUser.notifications.count { it -> !it.read },
        )
      }
    }

    checkpoint("Initial State") {
      headerTitle().assertIsDisplayed().assertTextContains("Notifications")
      notificationList().assertIsDisplayed()
    }

    checkpoint("Notifications List Content") {
      // Wait a bit for composition
      compose.waitForIdle()

      notificationItem(friendRequestNotification.uid).assertIsDisplayed()
      notificationItem(discussionNotification.uid).assertIsDisplayed()
      notificationItem(sessionNotification.uid).assertIsDisplayed()
    }

    checkpoint("Filter Chips Displayed") {
      // Print all nodes to debug
      compose.onRoot().printToLog("NOTIFICATION_SCREEN")

      // Filter chips might be in a scrollable view, use unmerged tree
      filterChip("ALL").assertExists()
      filterChip("UNREAD").assertExists()
      filterChip("FRIEND_REQUESTS").assertExists()
      filterChip("DISCUSSIONS").assertExists()
      filterChip("SESSIONS").assertExists()
    }

    checkpoint("Filter - Unread") {
      filterChip("UNREAD").performClick()
      compose.waitForIdle()

      notificationItem(friendRequestNotification.uid).assertExists()
      notificationItem(sessionNotification.uid).assertExists()
      notificationItem(discussionNotification.uid).assertDoesNotExist()

      // Reset to ALL
      filterChip("ALL").performClick()
      compose.waitForIdle()
    }

    checkpoint("Filter - Friend Requests") {
      filterChip("FRIEND_REQUESTS").performClick()
      compose.waitForIdle()

      notificationItem(friendRequestNotification.uid).assertExists()
      notificationItem(discussionNotification.uid).assertDoesNotExist()
      notificationItem(sessionNotification.uid).assertDoesNotExist()

      // Reset to ALL
      filterChip("ALL").performClick()
      compose.waitForIdle()
    }

    checkpoint("Filter - Discussions") {
      // Scroll filter row to reveal DISCUSSIONS chip if needed
      filterChip("ALL").performTouchInput { swipeLeft() }
      compose.waitForIdle()

      filterChip("DISCUSSIONS").performClick()
      compose.waitForIdle()

      notificationItem(discussionNotification.uid).assertExists()
      notificationItem(friendRequestNotification.uid).assertDoesNotExist()
      notificationItem(sessionNotification.uid).assertDoesNotExist()

      // Reset to ALL
      filterChip("ALL").performClick()
      compose.waitForIdle()
    }

    checkpoint("Filter - Sessions") {
      // Scroll filter row multiple times to ensure SESSIONS chip is visible (it's at end of list)
      filterChip("ALL").performTouchInput { swipeLeft() }
      compose.waitForIdle()
      filterChip("UNREAD").performTouchInput { swipeLeft() }
      compose.waitForIdle()

      filterChip("SESSIONS").performClick()
      compose.waitForIdle()

      notificationItem(sessionNotification.uid).assertExists()
      notificationItem(friendRequestNotification.uid).assertDoesNotExist()
      notificationItem(discussionNotification.uid).assertDoesNotExist()

      // Reset to ALL - scroll back first
      filterChip("SESSIONS").performTouchInput { swipeRight() }
      compose.waitForIdle()
      filterChip("DISCUSSIONS").performTouchInput { swipeRight() }
      compose.waitForIdle()
      filterChip("ALL").performClick()
      compose.waitForIdle()
    }

    checkpoint("Open Friend Request Notification Sheet") {
      notificationItem(friendRequestNotification.uid).performClick()
      compose.waitForIdle()

      compose.waitUntil(5000) {
        try {
          sheetTitle().assertIsDisplayed()
          true
        } catch (e: AssertionError) {
          false
        }
      }

      sheetTitle().assertTextContains(otherUser.name)
      sheetAcceptButton().assertIsDisplayed()
      sheetDeclineButton().assertIsDisplayed()

      // Close the sheet
      sheetCloseButton().performClick()
      compose.waitForIdle()
    }

    checkpoint("Open Discussion Notification Sheet") {
      notificationItem(discussionNotification.uid).performClick()
      compose.waitForIdle()

      compose.waitUntil(5000) {
        try {
          sheetTitle().assertIsDisplayed()
          true
        } catch (e: AssertionError) {
          false
        }
      }

      sheetTitle().assertTextContains(discussion.name)
      sheetDescription().assertTextContains(discussion.description)
      sheetAcceptButton().assertIsDisplayed()
      sheetDeclineButton().assertIsDisplayed()

      // Close the sheet
      sheetCloseButton().performClick()
      compose.waitForIdle()
    }

    checkpoint("Open Session Notification Sheet") {
      notificationItem(sessionNotification.uid).performClick()
      compose.waitForIdle()

      compose.waitUntil(5000) {
        try {
          sheetTitle().assertIsDisplayed()
          true
        } catch (e: AssertionError) {
          false
        }
      }

      sheetTitle().assertTextContains(session.name)
      sheetAcceptButton().assertIsDisplayed()
      sheetDeclineButton().assertIsDisplayed()

      // Close the sheet
      sheetCloseButton().performClick()
      compose.waitForIdle()
    }
  }

  @Test
  fun smoke_empty_state() {
    val emptyAccount = currentUser.copy(notifications = emptyList())

    compose.setContent {
      AppTheme {
        NotificationsTab(
            account = emptyAccount,
            viewModel = viewModel,
            verified = true,
            navigationActions = mockNavigation,
            onBack = {},
            unreadCount = currentUser.notifications.count { it -> !it.read },
        )
      }
    }

    checkpoint("Empty State Displayed") {
      emptyStateText().assertIsDisplayed().assertTextContains("You have no notifications yet.")
    }

    checkpoint("Empty State - Filter Unread") {
      filterChip("UNREAD").performClick()
      compose.waitForIdle()
      emptyStateText()
          .assertIsDisplayed()
          .assertTextContains("You're all caught up! No unread notifications.")
    }

    checkpoint("Empty State - Filter Sessions") {
      // Scroll filter row multiple times to reveal SESSIONS chip
      filterChip("ALL").performTouchInput { swipeLeft() }
      compose.waitForIdle()
      filterChip("UNREAD").performTouchInput { swipeLeft() }
      compose.waitForIdle()

      filterChip("SESSIONS").performClick()
      compose.waitForIdle()
      emptyStateText().assertExists().assertTextContains("No session invitations.")
    }
  }
}
