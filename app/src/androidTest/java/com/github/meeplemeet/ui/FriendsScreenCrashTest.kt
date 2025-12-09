package com.github.meeplemeet.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.github.meeplemeet.model.MainActivityViewModel
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.account.FriendsScreenViewModel
import com.github.meeplemeet.model.navigation.LocalNavigationVM
import com.github.meeplemeet.ui.account.FriendsManagementTestTags
import com.github.meeplemeet.ui.account.FriendsScreen
import com.github.meeplemeet.ui.theme.AppTheme
import com.github.meeplemeet.ui.theme.ThemeMode
import com.github.meeplemeet.utils.FirestoreTests
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class FriendsScreenCrashTest : FirestoreTests() {

  @get:Rule val compose = createComposeRule()

  private lateinit var viewModel: FriendsScreenViewModel
  private lateinit var navViewModel: MainActivityViewModel
  private lateinit var currentUser: Account

  @Before
  fun setup() {
    viewModel = FriendsScreenViewModel(accountRepository, handlesRepository)
    navViewModel = MainActivityViewModel(accountRepository)

    runBlocking {
      val suffix = System.currentTimeMillis()

      currentUser =
          accountRepository.createAccount(
              userHandle = "crash_test_user_$suffix",
              name = "Crash Test User",
              email = "crash_$suffix@meeple.test",
              photoUrl = null)

      val friendToDelete =
          accountRepository.createAccount(
              userHandle = "to_be_deleted_$suffix",
              name = "Deleted User",
              email = "deleted_$suffix@meeple.test",
              photoUrl = null)

      accountRepository.sendFriendRequest(currentUser, friendToDelete.uid)
      accountRepository.acceptFriendRequest(friendToDelete.uid, currentUser.uid)

      // Now delete the friend account
      accountRepository.deleteAccount(friendToDelete.uid)

      currentUser = accountRepository.getAccount(currentUser.uid)
    }
  }

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun friendsScreen_missingFriend_doesNotCrash() {
    compose.setContent {
      CompositionLocalProvider(LocalNavigationVM provides navViewModel) {
        AppTheme(themeMode = ThemeMode.DARK) {
          FriendsScreen(
              account = currentUser,
              viewModel = viewModel,
              onBack = {},
          )
        }
      }
    }

    compose.waitUntilAtLeastOneExists(
        hasTestTag(FriendsManagementTestTags.SCREEN_ROOT),
        timeoutMillis = 5_000,
    )

    compose.onNodeWithTag(FriendsManagementTestTags.SCREEN_ROOT).assertIsDisplayed()
  }
}
