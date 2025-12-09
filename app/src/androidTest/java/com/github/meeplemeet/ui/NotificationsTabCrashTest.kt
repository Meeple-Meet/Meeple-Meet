package com.github.meeplemeet.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.meeplemeet.model.MainActivityViewModel
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.account.NotificationsViewModel
import com.github.meeplemeet.model.navigation.LocalNavigationVM
import com.github.meeplemeet.ui.account.NotificationsTab
import com.github.meeplemeet.ui.account.NotificationsTabTestTags
import com.github.meeplemeet.ui.theme.AppTheme
import com.github.meeplemeet.ui.theme.ThemeMode
import com.github.meeplemeet.utils.FirestoreTests
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class NotificationsTabCrashTest : FirestoreTests() {

  @get:Rule val compose = createComposeRule()

  private lateinit var viewModel: NotificationsViewModel
  private lateinit var navViewModel: MainActivityViewModel
  private lateinit var currentUser: Account

  @Before
  fun setup() {
    viewModel = NotificationsViewModel(accountRepository, handlesRepository)
    navViewModel = MainActivityViewModel(accountRepository)

    runBlocking {
      val suffix = System.currentTimeMillis()

      // Create current user
      currentUser =
          accountRepository.createAccount(
              userHandle = "notif_crash_user_$suffix",
              name = "Notif Crash User",
              email = "notif_crash_$suffix@meeple.test",
              photoUrl = null)

      val senderToDelete =
          accountRepository.createAccount(
              userHandle = "sender_to_delete_$suffix",
              name = "Sender To Delete",
              email = "sender_delete_$suffix@meeple.test",
              photoUrl = null)

      // Send friend request
      accountRepository.sendFriendRequest(senderToDelete, currentUser.uid)
      accountRepository.sendFriendRequestNotification(currentUser.uid, senderToDelete)

      // Delete the sender
      accountRepository.deleteAccount(senderToDelete.uid)

      // Refresh current user to get the notification
      currentUser = accountRepository.getAccount(currentUser.uid)
    }
  }

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun notificationsTab_notificationFromDeletedUser_isDeleted() {
    val initialUser = runBlocking { accountRepository.getAccount(currentUser.uid) }
    assert(initialUser.notifications.isNotEmpty()) { "Notification should exist initially" }
    val notificationId = initialUser.notifications.first().uid

    compose.setContent {
      CompositionLocalProvider(LocalNavigationVM provides navViewModel) {
        AppTheme(themeMode = ThemeMode.DARK) {
          NotificationsTab(
              account = currentUser,
              viewModel = viewModel,
              onBack = {},
          )
        }
      }
    }

    compose.waitUntilAtLeastOneExists(
        hasTestTag(NotificationsTabTestTags.HEADER_TITLE),
        timeoutMillis = 5_000,
    )

    runBlocking {
      var notificationExists = true
      val startTime = System.currentTimeMillis()
      while (notificationExists && System.currentTimeMillis() - startTime < 5000) {
        val updatedUser = accountRepository.getAccount(currentUser.uid)
        notificationExists = updatedUser.notifications.any { it.uid == notificationId }
      }
      compose.waitUntil { !notificationExists }
    }
  }
}
