package com.github.meeplemeet.ui.account

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.account.CreateAccountViewModel
import com.github.meeplemeet.model.auth.AuthenticationViewModel
import com.github.meeplemeet.ui.navigation.BottomNavigationMenu
import com.github.meeplemeet.ui.navigation.MeepleMeetScreen
import com.github.meeplemeet.ui.navigation.NavigationActions
import com.github.meeplemeet.ui.navigation.NavigationTestTags
import com.github.meeplemeet.ui.theme.Dimensions

object ProfileTestTags {
  const val LOG_OUT_BUTTON = "Logout Button"
}

object ProfileScreenUi {
  val extraLargeSpacing = Dimensions.Spacing.extraLarge
  val xxLargePadding = Dimensions.Padding.xxLarge
}

/**
 * Composable function to display the Profile Screen. The screen displays information about your
 * account, as well as allowing you to sign out.
 *
 * @param navigation Navigation actions for screen transitions.
 * @param viewModel ViewModel for authentication-related operations.
 * @param account The current user's account.
 * @param viewModel ViewModel for discussion-related operations.
 * @param onSignOut Callback function to be invoked on sign out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navigation: NavigationActions,
    authViewModel: AuthenticationViewModel = viewModel(),
    createAccountViewModel: CreateAccountViewModel = viewModel(),
    account: Account,
    onSignOut: () -> Unit
) {
  val uiState by authViewModel.uiState.collectAsState()

  // Refresh email verification status when the profile is shown
  LaunchedEffect(account.uid) { authViewModel.refreshEmailVerificationStatus() }

  Scaffold(
      topBar = {
        CenterAlignedTopAppBar(
            title = {
              Text(
                  text = MeepleMeetScreen.Profile.title,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onPrimary,
                  modifier = Modifier.testTag(NavigationTestTags.SCREEN_TITLE))
            })
      },
      bottomBar = {
        BottomNavigationMenu(
            currentScreen = MeepleMeetScreen.Profile,
            onTabSelected = { screen -> navigation.navigateTo(screen) })
      }) { innerPadding ->

        // New content here
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.TopCenter) {
              MainTab(
                  authViewModel = authViewModel,
                  createAccountViewModel = createAccountViewModel,
                  account = account,
                  onFriendsClick = {}, // define if needed
                  onNotificationClick = {}, // define if needed
                  onSignOut = onSignOut,
              )
            }
      }
}
