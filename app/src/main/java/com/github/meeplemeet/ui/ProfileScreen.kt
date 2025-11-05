package com.github.meeplemeet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.auth.AuthViewModel
import com.github.meeplemeet.model.discussions.DiscussionViewModel
import com.github.meeplemeet.ui.navigation.BottomNavigationMenu
import com.github.meeplemeet.ui.navigation.MeepleMeetScreen
import com.github.meeplemeet.ui.navigation.NavigationActions
import com.github.meeplemeet.ui.navigation.NavigationTestTags

object ProfileTestTags {
  const val LOG_OUT_BUTTON = "Logout Button"
}

/**
 * Composable function to display the Profile Screen. The screen displays information about your
 * account, as well as allowing you to sign out.
 *
 * @param navigation Navigation actions for screen transitions.
 * @param authViewModel ViewModel for authentication-related operations.
 * @param account The current user's account.
 * @param discussionViewModel ViewModel for discussion-related operations.
 * @param onSignOut Callback function to be invoked on sign out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navigation: NavigationActions,
    authViewModel: AuthViewModel,
    account: Account,
    discussionViewModel: DiscussionViewModel,
    onSignOut: () -> Unit
) {
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
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.Center) {
              Column(
                  modifier = Modifier.fillMaxSize().padding(24.dp),
                  verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(text = "Email", style = MaterialTheme.typography.bodyMedium)
                    Text(text = account.email, style = MaterialTheme.typography.bodySmall)

                    Text(text = "Username", style = MaterialTheme.typography.bodyMedium)
                    Text(text = account.name, style = MaterialTheme.typography.bodySmall)

                    Text(text = "Handle", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "@${account.handle}", style = MaterialTheme.typography.bodySmall)

                    Text(text = "Roles", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text =
                            buildString {
                              if (account.shopOwner) append("Shop Owner\n")
                              if (account.spaceRenter) append("Space Renter\n")
                              if (!account.shopOwner && !account.spaceRenter) append("None")
                            },
                        style = MaterialTheme.typography.bodySmall)

                    Spacer(modifier = Modifier.weight(1f))
                  }
              Button(
                  onClick = {
                    onSignOut()
                    discussionViewModel.signOut()
                    authViewModel.signOut()
                  },
                  modifier = Modifier.testTag(ProfileTestTags.LOG_OUT_BUTTON),
                  colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                    Text("Sign Out", color = Color.White)
                  }
            }
      }
}
