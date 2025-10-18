package com.github.meeplemeet

import android.content.Context
import android.os.Bundle
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.credentials.CredentialManager
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.meeplemeet.model.viewmodels.AuthViewModel
import com.github.meeplemeet.model.viewmodels.FirestoreHandlesViewModel
import com.github.meeplemeet.model.viewmodels.FirestoreSessionViewModel
import com.github.meeplemeet.model.viewmodels.FirestoreViewModel
import com.github.meeplemeet.ui.AddDiscussionScreen
import com.github.meeplemeet.ui.AddSessionScreen
import com.github.meeplemeet.ui.CreateAccountScreen
import com.github.meeplemeet.ui.DiscoverSessionsScreen
import com.github.meeplemeet.ui.DiscussionDetailsScreen
import com.github.meeplemeet.ui.DiscussionScreen
import com.github.meeplemeet.ui.DiscussionsOverviewScreen
import com.github.meeplemeet.ui.ProfileScreen
import com.github.meeplemeet.ui.SessionDetailsScreen
import com.github.meeplemeet.ui.SessionsOverviewScreen
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

@Composable
fun MeepleMeetApp(
    context: Context = LocalContext.current,
    credentialManager: CredentialManager = CredentialManager.create(context),
    authVM: AuthViewModel = viewModel(),
    firestoreVM: FirestoreViewModel = viewModel(),
    handlesVM: FirestoreHandlesViewModel = viewModel()
) {
  val navController = rememberNavController()
  val navigationActions = NavigationActions(navController)

  var discussionId by remember { mutableStateOf("") }
  val discussionFlow = remember(discussionId) { firestoreVM.discussionFlow(discussionId) }
  val discussion by discussionFlow.collectAsState()
  val account by firestoreVM.accountFlow(FirebaseProvider.auth.uid ?: "").collectAsState()

  var nextDestination by remember { mutableStateOf<MeepleMeetScreen?>(null) }
  val handlesError by handlesVM.errorMessage.collectAsState()
  var isAutoLoggingIn by remember { mutableStateOf(false) }

  LaunchedEffect(nextDestination) {
    nextDestination?.let {
      navigationActions.navigateTo(nextDestination!!)
      nextDestination = null
    }
  }

  NavHost(navController = navController, startDestination = MeepleMeetScreen.SignIn.name) {
    composable(MeepleMeetScreen.SignIn.name) {
      LaunchedEffect(account) {
        try {
          FirebaseProvider.auth.currentUser?.let {
            firestoreVM.getAccount(FirebaseProvider.auth.currentUser!!.uid)
          }

          account?.let {
            isAutoLoggingIn = false
            handlesVM.handleForAccountExists(account!!)
            if (handlesError.isBlank()) navigationActions.navigateOutOfAuthGraph()
            else navigationActions.navigateTo(MeepleMeetScreen.CreateAccount)
          }
        } catch (_: Exception) {
          FirebaseProvider.auth.signOut()
        }

        isAutoLoggingIn = FirebaseProvider.auth.currentUser != null
      }

      if (isAutoLoggingIn) LoadingScreen()
      else
          SignInScreen(
              authVM,
              credentialManager = credentialManager,
              onSignUpClick = { navigationActions.navigateTo(MeepleMeetScreen.SignUp) })
    }

    composable(MeepleMeetScreen.SignUp.name) {
      SignUpScreen(
          authVM,
          credentialManager = credentialManager,
          onLogInClick = { navigationActions.navigateTo(MeepleMeetScreen.SignIn) },
          onRegister = { navigationActions.navigateTo(MeepleMeetScreen.CreateAccount) })
    }

    composable(MeepleMeetScreen.CreateAccount.name) {
      CreateAccountScreen(account!!, handlesVM) { navigationActions.navigateOutOfAuthGraph() }
    }

    composable(MeepleMeetScreen.DiscussionsOverview.name) {
      DiscussionsOverviewScreen(
          account!!,
          navigationActions,
          firestoreVM,
          onClickAddDiscussion = { navigationActions.navigateTo(MeepleMeetScreen.AddDiscussion) },
          onSelectDiscussion = {
            discussionId = it.uid
            nextDestination = MeepleMeetScreen.Discussion
          },
      )
    }

    composable(MeepleMeetScreen.Discussion.name) {
      DiscussionScreen(
          account!!,
          discussion!!,
          firestoreVM,
          onBack = { navigationActions.goBack() },
          onOpenDiscussionInfo = {
            navigationActions.navigateTo(MeepleMeetScreen.DiscussionDetails)
          },
          onCreateSessionClick = {
            discussionId = it.uid
            nextDestination =
                if (it.session != null) MeepleMeetScreen.Session else MeepleMeetScreen.AddSession
          },
      )
    }

    composable(MeepleMeetScreen.AddDiscussion.name) {
      AddDiscussionScreen(
          account = account!!,
          viewModel = firestoreVM,
          onBack = { navigationActions.goBack() },
          onCreate = { navigationActions.navigateTo(MeepleMeetScreen.DiscussionsOverview) },
      )
    }

    composable(MeepleMeetScreen.DiscussionDetails.name) {
      DiscussionDetailsScreen(
          account = account!!,
          discussion = discussion!!,
          viewModel = firestoreVM,
          onBack = { navigationActions.goBack() },
          onLeave = { navigationActions.navigateTo(MeepleMeetScreen.DiscussionsOverview) },
          onDelete = { navigationActions.navigateTo(MeepleMeetScreen.DiscussionsOverview) },
      )
    }

    composable(MeepleMeetScreen.AddSession.name) {
      AddSessionScreen(
          account = account!!,
          discussion = discussion!!,
          viewModel = firestoreVM,
          sessionViewModel = FirestoreSessionViewModel(discussion!!),
          onBack = { navigationActions.goBack() })
    }

    composable(MeepleMeetScreen.Session.name) {
      SessionDetailsScreen(
          account = account!!,
          discussion = discussion!!,
          viewModel = firestoreVM,
          sessionViewModel = FirestoreSessionViewModel(discussion!!),
          onBack = { navigationActions.goBack() })
    }

    composable(MeepleMeetScreen.SessionsOverview.name) { SessionsOverviewScreen(navigationActions) }

    composable(MeepleMeetScreen.DiscoverFeeds.name) { DiscoverSessionsScreen(navigationActions) }

    composable(MeepleMeetScreen.Profile.name) {
      ProfileScreen(
          navigation = navigationActions,
          authViewModel = authVM,
          firestoreVM,
          onSignOut = { navigationActions.navigateTo(MeepleMeetScreen.SignIn) })
    }
  }
}

@Composable
private fun LoadingScreen() {
  Box(
      modifier = Modifier.testTag("Loading Screen").fillMaxSize(),
      contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }
}
