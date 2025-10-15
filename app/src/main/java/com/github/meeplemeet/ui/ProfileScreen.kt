package com.github.meeplemeet.ui

import androidx.compose.foundation.layout.Box
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
import com.github.meeplemeet.model.viewmodels.AuthViewModel
import com.github.meeplemeet.model.viewmodels.FirestoreViewModel
import com.github.meeplemeet.ui.navigation.BottomNavigationMenu
import com.github.meeplemeet.ui.navigation.MeepleMeetScreen
import com.github.meeplemeet.ui.navigation.NavigationActions
import com.github.meeplemeet.ui.navigation.NavigationTestTags

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navigation: NavigationActions,
    authViewModel: AuthViewModel,
    firestoreViewModel: FirestoreViewModel,
    onSignOut: () -> Unit
) {
  Scaffold(
      topBar = {
        CenterAlignedTopAppBar(
            title = {
              Text(
                  text = MeepleMeetScreen.ProfileScreen.name,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onPrimary,
                  modifier = Modifier.testTag(NavigationTestTags.SCREEN_TITLE))
            })
      },
      bottomBar = {
        BottomNavigationMenu(
            currentScreen = MeepleMeetScreen.ProfileScreen,
            onTabSelected = { screen -> navigation.navigateTo(screen) })
      }) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.Center) {
              Button(
                  onClick = {
                    firestoreViewModel.signOut()
                    authViewModel.logout()
                    onSignOut()
                  },
                  modifier = Modifier.testTag("Logout Button"),
                  colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                    Text("Sign Out", color = Color.White)
                  }
            }
      }
}
