package com.github.meeplemeet.end2end

import android.Manifest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
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
import com.github.meeplemeet.ui.account.FriendsManagementTestTags
import com.github.meeplemeet.ui.account.NotificationsTabTestTags
import com.github.meeplemeet.ui.account.PublicInfoTestTags
import com.github.meeplemeet.ui.navigation.NavigationTestTags
import com.github.meeplemeet.utils.AuthUtils
import com.github.meeplemeet.utils.FirestoreTests
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class E2E_M3 : FirestoreTests() {

    @get:Rule
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
        )

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

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
        val account = accountRepository.createAccount(
            userHandle = handle,
            name = name,
            email = email,
            photoUrl = null
        )
        handlesRepository.createAccountHandle(account.uid, handle)

        accountRepository.updateAccount(account.uid, mapOf(Account::notificationSettings.name to notificationSettings))
        
        return accountRepository.getAccount(account.uid)
    }

    private fun navigateToNotificationsScreen() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(NavigationTestTags.PROFILE_TAB).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(PublicInfoTestTags.ACTION_NOTIFICATIONS).performClick()
        composeTestRule.waitForIdle()
    }

    private fun navigateToFriendsList() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(NavigationTestTags.PROFILE_TAB).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(PublicInfoTestTags.ACTION_FRIENDS).assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun relationshipEndToEnd() {
        val uniqueId = UUID.randomUUID().toString().take(8)
        val aliceHandle = "alice_$uniqueId"
        val bobHandle = "bob_$uniqueId"
        val charlieHandle = "charlie_$uniqueId"

        // 1. Alice signs up (UI)
        runBlocking {
            AuthUtils.apply {
                composeTestRule.signUpUser(
                    email = "alice_$uniqueId@test.com",
                    password = "Password123!",
                    handle = aliceHandle,
                    username = "Alice"
                )
            }
            waitUntilAuthReady()
        }

        // Update Alice's settings to NO_ONE via repo (simulating her preference)
        runBlocking {
            val aliceUid = auth.currentUser!!.uid
            val alice = accountRepository.getAccount(aliceUid)
            accountRepository.updateAccount(alice.uid,mapOf(Account::notificationSettings.name to NotificationSettings.NO_ONE))
        }

        // 2. Create Bob (Friends Only) and Charlie (Everyone) via Repo
        val (bob, charlie) = runBlocking {
            val b = createUserWithSettings(
                "Bob", bobHandle, "bob_$uniqueId@test.com", NotificationSettings.FRIENDS_ONLY
            )
            val c = createUserWithSettings(
                "Charlie", charlieHandle, "charlie_$uniqueId@test.com", NotificationSettings.EVERYONE
            )
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
        composeTestRule.waitForIdle()
        val notifTag = NotificationsTabTestTags.NOTIFICATION_ITEM_PREFIX + notificationId
        composeTestRule.waitUntil(30_000) {
           composeTestRule.onAllNodesWithTag(notifTag).fetchSemanticsNodes().isNotEmpty()
        }
        
        // Open Notification Sheet
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(notifTag).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.waitUntil(30000) {
            try {
                composeTestRule.onNodeWithTag(NotificationsTabTestTags.SHEET_TITLE).assertIsDisplayed()
                true
            } catch (e: Throwable) {
                false
            }
        }

        // Click Accept on Sheet
        composeTestRule.onNodeWithTag(NotificationsTabTestTags.SHEET_ACCEPT_BUTTON).performClick()
        composeTestRule.waitForIdle()
        
        // 5. Bob creates a discussion and adds Alice and Charlie (Repo)
        val discussionTitle = "Relationship Talk $uniqueId"
        runBlocking {
            val aliceUid = auth.currentUser!!.uid
            // Create discussion
            val discussion = discussionRepository.createDiscussion(
                creatorId = bob.uid,
                name = discussionTitle,
                description = "Talking about connections",
                participants = emptyList()
            )
            
            // Add Alice and Charlie
            discussionRepository.addUserToDiscussion(discussion.uid, aliceUid)
            discussionRepository.addUserToDiscussion(discussion.uid, charlie.uid)
        
        // 6. Charlie sends hateful messages (Repo)
            discussionRepository.sendMessageToDiscussion(
                discussion = discussion,
                sender = charlie,
                content = badMessage
            )
        }
        
        // Navigate to discussion to see the message
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(NavigationTestTags.DISCUSSIONS_TAB).performClick()
        composeTestRule.waitForIdle()
        
        // Wait for discussion item
        composeTestRule.waitUntil(10_000) {
            composeTestRule.onAllNodesWithText(discussionTitle).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(discussionTitle).performClick()
        
        // Verify message is visible
        composeTestRule.waitUntil(10_000) {
            composeTestRule.onAllNodesWithText(badMessage).fetchSemanticsNodes().isNotEmpty()
        }
        
        // Go back
        composeTestRule.waitForIdle()
        composeTestRule.waitUntil(3000) {
            try {
                composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON, useUnmergedTree = true).performClick()
                true
            }catch (_: Throwable){
                false
            }

        }

        // 7. Alice blocks Charlie (UI via Friends Screen -> Search)
        composeTestRule.waitForIdle()
        navigateToFriendsList()

        composeTestRule.onNodeWithTag(FriendsManagementTestTags.SEARCH_TEXT_FIELD).performTextInput(charlieHandle)
        composeTestRule.waitForIdle()
        
        // Wait for search result
        composeTestRule.waitUntil(10_000) {
             composeTestRule.onAllNodesWithTag(FriendsManagementTestTags.SEARCH_RESULT_ITEM_PREFIX + charlie.uid, useUnmergedTree = true)
                 .fetchSemanticsNodes().isNotEmpty()
        }
        
        // Click block button
        composeTestRule.onNodeWithTag(FriendsManagementTestTags.SEARCH_RESULT_BLOCK_BUTTON_PREFIX + charlie.uid, useUnmergedTree = true)
            .performClick()
        
        composeTestRule.waitForIdle()

        // Clear search to reset view (optional)
        try {
             composeTestRule.onNodeWithTag(FriendsManagementTestTags.SEARCH_CLEAR).performClick()
        } catch (_: Throwable) {
            // Ignore if clear button not found or needed
        }
        
        // 8. Verify Charlie's messages disappear (UI)
        composeTestRule.onNodeWithTag(NavigationTestTags.DISCUSSIONS_TAB).performClick()
        composeTestRule.waitForIdle()
        
        composeTestRule.onNodeWithText(discussionTitle).performClick()
        composeTestRule.waitForIdle()
        
        // Wait for message to NOT exist
         composeTestRule.waitUntil(20_000) {
             try {
                 composeTestRule.onAllNodesWithText(badMessage).fetchSemanticsNodes().isEmpty()
             }catch(_: Throwable){
                 false
             }
        }
        
        composeTestRule.onNodeWithText(badMessage).assertDoesNotExist()
    }
}