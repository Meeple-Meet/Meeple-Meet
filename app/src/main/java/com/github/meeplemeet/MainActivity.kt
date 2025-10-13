package com.github.meeplemeet

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.github.meeplemeet.model.repositories.AuthRepository
import com.github.meeplemeet.model.viewmodels.AuthViewModel
import com.github.meeplemeet.model.viewmodels.FirestoreViewModel
import com.github.meeplemeet.ui.DiscussionAddScreen
import com.github.meeplemeet.ui.DiscussionInfoScreen
import com.github.meeplemeet.ui.DiscussionScreen
import com.github.meeplemeet.ui.DiscussionsOverviewScreen
import com.github.meeplemeet.ui.SignInScreen
import com.github.meeplemeet.ui.SignUpScreen
import com.github.meeplemeet.ui.navigation.MeepleMeetScreen
import com.github.meeplemeet.ui.navigation.NavigationActions
import com.github.meeplemeet.ui.theme.AppTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore

object FirebaseProvider {
  val db: FirebaseFirestore by lazy { Firebase.firestore }
  val auth: FirebaseAuth by lazy { Firebase.auth }
}

/**
 * `MainActivity` is the entry point of the application. It sets up the content view with the
 * `onCreate` methods. You can run the app by running the `app` configuration in Android Studio. NB:
 * Make sure you have an Android emulator running or a physical device connected.
 */
class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent { AppTheme { Surface(modifier = Modifier.fillMaxSize()) { MeepleMeetApp() } } }
  }
}

private val startDestination = MeepleMeetScreen.SignInScreen.name

@Composable
fun MeepleMeetApp(
    context: Context = LocalContext.current,
    credentialManager: CredentialManager = CredentialManager.create(context),
    viewModel: FirestoreViewModel = viewModel()
) {
  val navController = rememberNavController()
  val navigationActions = NavigationActions(navController)

  val currentAccount by viewModel.account.collectAsState()

  /** Fetch current user if already logged in */
  LaunchedEffect(FirebaseAuth.getInstance().currentUser) {
    FirebaseAuth.getInstance().currentUser?.let { viewModel.getCurrentAccount() }
  }

  /** Authentification gate */
  LaunchedEffect(currentAccount) {
    Log.d("MeepleMeetApp", "currentAccount changed: $currentAccount")
    currentAccount?.let {
      Log.d("MeepleMeetApp", "Navigating to DiscussionsOverview")
      navigationActions.navigateTo(MeepleMeetScreen.DiscussionsOverview)
    }
  }

  NavHost(navController = navController, startDestination = startDestination) {

    /** Auth graph */
    navigation(
        startDestination = MeepleMeetScreen.SignInScreen.route,
        route = MeepleMeetScreen.SignInScreen.name) {
          composable(MeepleMeetScreen.SignInScreen.route) {
            SignInScreen(
                viewModel = AuthViewModel(AuthRepository()),
                credentialManager = credentialManager,
                onSignUpClick = { navigationActions.navigateTo(MeepleMeetScreen.SignUpScreen) })
          }
          composable(MeepleMeetScreen.SignUpScreen.route) {
            SignUpScreen(
                viewModel = AuthViewModel(AuthRepository()),
                credentialManager = credentialManager,
                onLogInClick = { navigationActions.navigateTo(MeepleMeetScreen.SignInScreen) },
            )
          }
        }

    /** Discussions graph */
    navigation(
        startDestination = MeepleMeetScreen.DiscussionsOverview.route,
        route = MeepleMeetScreen.DiscussionsOverview.name) {
          composable(MeepleMeetScreen.DiscussionsOverview.route) {
            if (currentAccount != null) {
              DiscussionsOverviewScreen(
                  currentUser = currentAccount!!,
                  navigation = navigationActions,
                  onSelectDiscussion = {
                    navigationActions.navigateTo(MeepleMeetScreen.DiscussionScreen(it.uid))
                  })
            } else {
              LoadingScreen()
            }
          }
          composable(MeepleMeetScreen.Routes.DISCUSSION) { backStackEntry ->
            if (currentAccount != null) {
              DiscussionScreen(
                  discussionId = backStackEntry.arguments?.getString("discussionId") ?: "",
                  currentUser = currentAccount!!,
                  onBack = { navigationActions.goBack() },
                  onOpenDiscussionInfo = {
                    navigationActions.navigateTo(MeepleMeetScreen.DiscussionInfoScreen(it.uid))
                  })
            } else {
              LoadingScreen()
            }
          }
          composable(MeepleMeetScreen.DiscussionAddScreen.route) {
            if (currentAccount != null) {
              DiscussionAddScreen(
                  onBack = { navigationActions.goBack() },
                  onCreate = { navigationActions.navigateTo(MeepleMeetScreen.DiscussionsOverview) },
                  currentUser = currentAccount!!)
            } else {
              LoadingScreen()
            }
          }
          composable(MeepleMeetScreen.Routes.DISCUSSION_INFO) { backStackEntry ->
            DiscussionInfoScreen(
                discussionId = backStackEntry.arguments?.getString("discussionId") ?: "",
                onBack = { navigationActions.goBack() },
                onLeave = { navigationActions.navigateTo(MeepleMeetScreen.DiscussionsOverview) },
                onDelete = { navigationActions.navigateTo(MeepleMeetScreen.DiscussionsOverview) })
          }
        }

    /** Sessions graph */
    // TODO: Add sessions graph here when screens are implemented
  }
}

@Composable
private fun LoadingScreen() {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    CircularProgressIndicator()
  }
}
