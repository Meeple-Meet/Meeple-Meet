package com.github.meeplemeet.end2end

import android.Manifest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.github.meeplemeet.MainActivity
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.account.NotificationSettings
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.ui.account.CreateAccountTestTags
import com.github.meeplemeet.ui.account.FriendsManagementTestTags
import com.github.meeplemeet.ui.account.NotificationsTabTestTags
import com.github.meeplemeet.ui.account.PublicInfoTestTags
import com.github.meeplemeet.ui.auth.OnBoardingTestTags
import com.github.meeplemeet.ui.auth.SignInScreenTestTags
import com.github.meeplemeet.ui.auth.SignUpScreenTestTags
import com.github.meeplemeet.ui.navigation.NavigationTestTags
import com.github.meeplemeet.ui.sessions.SessionViewerTestTags
import com.github.meeplemeet.ui.sessions.SessionsOverviewScreenTestTags
import com.github.meeplemeet.utils.AuthUtils
import com.github.meeplemeet.utils.AuthUtils.waitUntilWithCatch
import com.github.meeplemeet.utils.FirestoreTests
import com.google.firebase.Timestamp
import java.util.UUID
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class E2E_M3 : FirestoreTests() {

  @get:Rule
  val permissionRule: GrantPermissionRule =
      GrantPermissionRule.grant(
          Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

  @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

  private suspend fun retryUntil(
      timeoutMs: Long = 30_000,
      intervalMs: Long = 500,
      predicate: suspend () -> Boolean
  ) {
    try {
      withTimeout(timeoutMs) {
        while (!predicate()) {
          if (intervalMs > 0) delay(intervalMs)
        }
      }
    } catch (e: TimeoutCancellationException) {
      throw AssertionError("Condition not met within ${timeoutMs}ms", e)
    }
  }

  private suspend fun waitUntilAuthReady() = retryUntil { auth.currentUser != null }

  private suspend fun createUserWithSettings(
      name: String,
      handle: String,
      email: String,
      notificationSettings: NotificationSettings
  ): Account {
    // Create basic account
    val account =
        accountRepository.createAccount(
            userHandle = handle, name = name, email = email, photoUrl = null)
    handlesRepository.createAccountHandle(account.uid, handle)

    accountRepository.updateAccount(
        account.uid, mapOf(Account::notificationSettings.name to notificationSettings))

    return accountRepository.getAccount(account.uid)
  }

  private fun navigateToNotificationsScreen() {
    composeTestRule.waitForIdle()
    composeTestRule.waitUntilWithCatch({
      composeTestRule.onNodeWithTag(NavigationTestTags.PROFILE_TAB).performClick()
      true
    })
    composeTestRule.waitForIdle()
    composeTestRule.waitUntilWithCatch({
      composeTestRule.onNodeWithTag(PublicInfoTestTags.ACTION_NOTIFICATIONS).performClick()
      true
    })
    composeTestRule.waitForIdle()
  }

  private fun navigateToFriendsList() {
    composeTestRule.waitForIdle()
    composeTestRule.waitUntilWithCatch({
      composeTestRule.onNodeWithTag(NavigationTestTags.PROFILE_TAB).performClick()
      true
    })
    composeTestRule.waitForIdle()
    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(PublicInfoTestTags.ACTION_FRIENDS)
          .assertIsDisplayed()
          .performClick()
      true
    })
    composeTestRule.waitForIdle()
  }

  private fun aliceSignUp(email: String, password: String, handle: String, username: String) {
    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(SignInScreenTestTags.SIGN_UP_BUTTON)
          .assertExists()
          .performClick()
      true
    })
    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(SignUpScreenTestTags.EMAIL_FIELD)
          .assertExists()
          .performTextInput(email)
      true
    })
    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(SignUpScreenTestTags.PASSWORD_FIELD)
          .assertExists()
          .performTextInput(password)
      true
    })
    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(SignUpScreenTestTags.CONFIRM_PASSWORD_FIELD)
          .assertExists()
          .performTextInput(password)
      true
    })
    composeTestRule.waitForIdle()

    AuthUtils.apply { composeTestRule.closeKeyboardSafely() }

    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(SignUpScreenTestTags.SIGN_UP_BUTTON)
          .assertExists()
          .assertIsEnabled()
          .performClick()
      true
    })

    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onAllNodesWithTag(CreateAccountTestTags.SUBMIT_BUTTON, useUnmergedTree = true)
          .fetchSemanticsNodes()
          .isNotEmpty()
    })

    // Fill Create Account
    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(CreateAccountTestTags.HANDLE_FIELD, useUnmergedTree = true)
          .assertExists()
          .performTextInput(handle)
      true
    })
    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(CreateAccountTestTags.USERNAME_FIELD, useUnmergedTree = true)
          .assertExists()
          .performTextInput(username)
      true
    })
    AuthUtils.apply { composeTestRule.closeKeyboardSafely() }
    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(CreateAccountTestTags.SUBMIT_BUTTON, useUnmergedTree = true)
          .assertExists()
          .assertIsEnabled()
          .performClick()
      true
    })

    // Skip the OnBoarding screen
    composeTestRule.waitUntilWithCatch({
      composeTestRule.onNodeWithTag(OnBoardingTestTags.SKIP_BUTTON).assertExists().performClick()
      true
    })

    composeTestRule.waitForIdle()

    // Backend convergence
    runBlocking {
      waitUntilAuthReady()
      retryUntil {
        val doc = handlesRepository.collection.document(handle).get().await()
        doc.exists() && doc.getString("accountId")?.isNotBlank() == true
      }
    }

    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onAllNodesWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU)
          .fetchSemanticsNodes()
          .isNotEmpty()
    })
  }

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun relationshipEndToEnd() {
    val uniqueId = UUID.randomUUID().toString().take(8)
    val aliceHandle = "alice_$uniqueId"
    val bobHandle = "bob_$uniqueId"
    val charlieHandle = "charlie_$uniqueId"

    // 1. Alice signs up (UI)
    aliceSignUp(
        email = "alice_$uniqueId@test.com",
        password = "Password123!",
        handle = aliceHandle,
        username = "Alice")

    // Update Alice's settings to NO_ONE via repo (simulating her preference)
    runBlocking {
      val aliceUid = auth.currentUser!!.uid
      val alice = accountRepository.getAccount(aliceUid)
      accountRepository.updateAccount(
          alice.uid, mapOf(Account::notificationSettings.name to NotificationSettings.NO_ONE))
    }

    // 2. Create Bob (Friends Only) and Charlie (Everyone) via Repo
    val (bob, charlie) =
        runBlocking {
          val b =
              createUserWithSettings(
                  "Bob", bobHandle, "bob_$uniqueId@test.com", NotificationSettings.FRIENDS_ONLY)
          val c =
              createUserWithSettings(
                  "Charlie",
                  charlieHandle,
                  "charlie_$uniqueId@test.com",
                  NotificationSettings.EVERYONE)
          Pair(b, c)
        }

    // 3. Bob sends friend request to Alice (Repo)
    val badMessage = "You are stupid *bad words*"
    var notificationId = ""
    runBlocking {
      // Need Alice's current state from repo
      val aliceUid = auth.currentUser!!.uid
      val aliceAccount = accountRepository.getAccount(aliceUid)
      accountRepository.sendFriendRequest(bob, aliceAccount.uid)
      accountRepository.sendFriendRequestNotification(receiverId = aliceAccount.uid, sender = bob)

      // Get the notification ID
      retryUntil {
        val notifs = accountRepository.getAccount(aliceUid).notifications
        val found = notifs.find { it.senderId == bob.uid }
        if (found != null) {
          notificationId = found.uid
          true
        } else {
          false
        }
      }
    }

    // 4. Alice accepts friend request (UI via Notification)
    navigateToNotificationsScreen()

    // Wait for notification from Bob using the ID
    val notifTag = NotificationsTabTestTags.NOTIFICATION_ITEM_PREFIX + notificationId
    composeTestRule.waitUntilWithCatch({
      composeTestRule.onNodeWithTag(notifTag).assertIsDisplayed()
      true
    })

    // Open Notification Sheet
    composeTestRule.waitUntilWithCatch({
      composeTestRule.onNodeWithTag(notifTag).performClick()
      true
    })
    composeTestRule.waitUntilWithCatch({
      composeTestRule.onNodeWithTag(NotificationsTabTestTags.SHEET_TITLE).assertIsDisplayed()
      true
    })

    // Click Accept on Sheet
    composeTestRule.waitUntilWithCatch({
      composeTestRule.onNodeWithTag(NotificationsTabTestTags.SHEET_ACCEPT_BUTTON).performClick()
      true
    })
    composeTestRule.waitForIdle()

    // 5. Bob creates a discussion and adds Alice and Charlie (Repo)
    val discussionTitle = "Relationship Talk $uniqueId"
    runBlocking {
      val aliceUid = auth.currentUser!!.uid
      // Create discussion
      val discussion =
          discussionRepository.createDiscussion(
              creatorId = bob.uid,
              name = discussionTitle,
              description = "Talking about connections",
              participants = emptyList())

      // Add Alice and Charlie
      discussionRepository.addUserToDiscussion(discussion.uid, aliceUid)
      discussionRepository.addUserToDiscussion(discussion.uid, charlie.uid)

      // 6. Charlie sends hateful messages (Repo)
      discussionRepository.sendMessageToDiscussion(
          discussion = discussion, sender = charlie, content = badMessage)
    }

    // Navigate to discussion to see the message
    composeTestRule.waitUntilWithCatch({
      composeTestRule.onNodeWithTag(NavigationTestTags.DISCUSSIONS_TAB).performClick()
      true
    })

    // Wait for discussion item
    composeTestRule.waitUntilWithCatch({
      composeTestRule.onNodeWithText(discussionTitle).assertIsDisplayed().performClick()
      true
    })

    // Verify message is visible
    composeTestRule.waitUntilWithCatch({
      composeTestRule.onNodeWithText(badMessage).assertIsDisplayed()
      true
    })

    // Go back
    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON, useUnmergedTree = true)
          .performClick()
      true
    })

    navigateToFriendsList()

    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(FriendsManagementTestTags.SEARCH_TEXT_FIELD)
          .performTextInput(charlieHandle)
      true
    })

    // Wait for search result and Click block button
    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(
              FriendsManagementTestTags.SEARCH_RESULT_BLOCK_BUTTON_PREFIX + charlie.uid,
              useUnmergedTree = true)
          .assertIsDisplayed()
          .performClick()
      true
    })

    // Clear search to reset view (optional)
    composeTestRule.waitUntilWithCatch({
      composeTestRule.onNodeWithTag(FriendsManagementTestTags.SEARCH_CLEAR).performClick()
      true
    })

    // 8. Verify Charlie's messages disappear (UI)
    composeTestRule.waitUntilWithCatch({
      composeTestRule.onNodeWithTag(NavigationTestTags.DISCUSSIONS_TAB).performClick()
      true
    })

    composeTestRule.waitUntilWithCatch({
      composeTestRule.onNodeWithText(discussionTitle).performClick()
      true
    })

    // Wait for message to NOT exist
    composeTestRule.waitUntilWithCatch({
      composeTestRule.onAllNodesWithText(badMessage).fetchSemanticsNodes().isEmpty()
    })

    composeTestRule.waitUntilWithCatch({
      composeTestRule.onNodeWithText(badMessage).assertDoesNotExist()
      true
    })
    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON, useUnmergedTree = true)
          .performClick()
      true
    })
  }

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun sessionEndToEnd() {
    val uniqueId = UUID.randomUUID().toString().take(8)
    val aliceHandle = "alice_$uniqueId"
    val bobHandle = "bob_$uniqueId"
    val discussionTitle = "Session Discussion $uniqueId"

    // 1. Alice signs up (UI)
    aliceSignUp(
        email = "alice_$uniqueId@test.com",
        password = "Password123!",
        handle = aliceHandle,
        username = "Alice")

    // 2. Create Bob and a discussion via Repo
    val bob = runBlocking {
      val b =
          createUserWithSettings(
              "Bob", bobHandle, "bob_$uniqueId@test.com", NotificationSettings.FRIENDS_ONLY)
      val aliceUid = auth.currentUser!!.uid
      val discussion =
          discussionRepository.createDiscussion(
              creatorId = b.uid,
              name = discussionTitle,
              description = "Discussion for sessions",
              participants = listOf(b.uid, aliceUid))
      b
    }

    // 3. Bob creates a session (Repo)
    val initialSessionName = "Board Game Night $uniqueId"
    val updatedSessionName = "Cozy Catan $uniqueId"
    val gameId = "g_catan"
    val gameName = "Catan"

    runBlocking {
      val discussions =
          discussionRepository.collection.whereEqualTo("name", discussionTitle).get().await()
      val discussionId = discussions.documents.first().id

      sessionRepository.createSession(
          discussionId = discussionId,
          name = initialSessionName,
          gameId = gameId,
          gameName = gameName,
          date = Timestamp.now(),
          location = Location(name = "Bob's House"),
          participants = arrayOf(bob.uid, auth.currentUser!!.uid))
    }

    // 4. Alice opens session overview (UI)
    composeTestRule.waitUntilWithCatch({
      composeTestRule.onNodeWithTag(NavigationTestTags.SESSIONS_TAB).performClick()
      true
    })

    // 5. Alice checks session and selects it (UI)
    composeTestRule.waitUntilWithCatch({
      composeTestRule.onNodeWithText(initialSessionName).assertIsDisplayed().performClick()
      true
    })

    // Check that we are on the right screen (Session Details Card)
    composeTestRule.waitUntilWithCatch({
      composeTestRule.onNodeWithText(initialSessionName).assertIsDisplayed()
      composeTestRule.onNodeWithText("Bob's House").assertIsDisplayed()
      true
    })

    // 6. Bob edits the session (Repo)
    runBlocking {
      val discussions =
          discussionRepository.collection.whereEqualTo("name", discussionTitle).get().await()
      val discussionId = discussions.documents.first().id

      sessionRepository.updateSession(
          discussionId = discussionId,
          name = updatedSessionName,
          gameId = gameId,
          gameName = gameName)
    }

    // 7. Alice sees changes (UI)
    composeTestRule.waitUntilWithCatch({
      composeTestRule.onNodeWithText(updatedSessionName).assertIsDisplayed()
      true
    })

    // Close the details card
    composeTestRule.waitUntilWithCatch(
        {
          composeTestRule
              .onNodeWithTag(SessionViewerTestTags.TOP_BAR_BACK, useUnmergedTree = true)
              .performClick()
          true
        },
        20_000)
    composeTestRule.waitUntilWithCatch({
      composeTestRule.onNodeWithTag(SessionsOverviewScreenTestTags.TEST_TAG_HISTORY).assertExists()
      true
    })

    // 8. Bob archives the session (Repo)
    val archivedSessionId = UUID.randomUUID().toString()
    runBlocking {
      val discussions =
          discussionRepository.collection.whereEqualTo("name", discussionTitle).get().await()
      val discussionId = discussions.documents.first().id
      sessionRepository.archiveSession(discussionId, archivedSessionId, null)
    }

    // 9. Alice opens history and clicks popup (UI)
    composeTestRule.waitUntilWithCatch({
      composeTestRule.onNodeWithTag(SessionsOverviewScreenTestTags.TEST_TAG_HISTORY).performClick()
      true
    })

    // Wait for history card to appear
    val historyCardTag = "historyCard_$gameId"
    composeTestRule.waitUntilWithCatch({
      composeTestRule.onNodeWithTag(historyCardTag, useUnmergedTree = true).assertIsDisplayed()
      true
    })

    composeTestRule.waitUntilWithCatch({
      composeTestRule.onNodeWithTag(historyCardTag, useUnmergedTree = true).performClick()
      true
    })

    // Verify popup (SessionDetailsCard) is shown
    composeTestRule.waitUntilWithCatch({
      composeTestRule
          .onAllNodesWithText(updatedSessionName, useUnmergedTree = true, substring = true)
          .assertCountEquals(2)
      true
    })
  }
}
