package com.github.meeplemeet.ui.account

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.account.ProfileScreenViewModel
import com.github.meeplemeet.ui.UiBehaviorConfig
import com.github.meeplemeet.ui.navigation.BottomBarWithVerification
import com.github.meeplemeet.ui.navigation.MeepleMeetScreen
import com.github.meeplemeet.ui.navigation.NavigationActions

/**
 * Composable function to display the Profile Screen. The screen displays information about your
 * account, as well as allowing you to sign out.
 *
 * @param navigation Navigation actions for screen transitions.
 * @param viewModel ViewModel for authentication-related operations.
 * @param account The current user's account.
 * @param viewModel ViewModel for discussion-related operations.
 * @param onSignOutOrDel Callback function to be invoked on sign out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navigation: NavigationActions,
    viewModel: ProfileScreenViewModel = viewModel(),
    account: Account,
    verified: Boolean,
    onSignOutOrDel: () -> Unit,
    onDelete: () -> Unit,
    onFriendClick: () -> Unit,
    onNotificationClick: () -> Unit,
    unreadCount: Int,
    onSpaceRenterClick: (String) -> Unit,
    onShopClick: (String) -> Unit
) {
  // Refresh email verification status when the profile is shown
  LaunchedEffect(account.uid) { viewModel.refreshEmailVerificationStatus() }

  var isInputFocused by remember { mutableStateOf(false) }

  Scaffold(
      bottomBar = {
        val shouldHide = UiBehaviorConfig.hideBottomBarWhenInputFocused
        if (!(shouldHide && isInputFocused)) {
          BottomBarWithVerification(
              currentScreen = MeepleMeetScreen.Profile,
              unreadCount = unreadCount,
              onTabSelected = { screen -> navigation.navigateTo(screen) },
              verified = verified,
              onVerifyClick = { navigation.navigateTo(MeepleMeetScreen.Profile) })
        }
      }) { innerPadding ->
        // New content here
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .padding(
                        PaddingValues(
                            top = innerPadding.calculateTopPadding(),
                            start = innerPadding.calculateStartPadding(LayoutDirection.Ltr),
                            end = innerPadding.calculateEndPadding(LayoutDirection.Ltr),
                            bottom = 0.dp // remove scaffold bottom inset
                            )),
            contentAlignment = Alignment.TopCenter) {
              MainTab(
                  viewModel = viewModel,
                  account = account,
                  onFriendsClick = onFriendClick,
                  onNotificationClick = onNotificationClick,
                  onSignOutOrDel = onSignOutOrDel,
                  onDelete = onDelete,
                  onInputFocusChanged = { isInputFocused = it },
                  onSpaceRenterClick = onSpaceRenterClick,
                  onShopClick = onShopClick)
            }
      }
}
