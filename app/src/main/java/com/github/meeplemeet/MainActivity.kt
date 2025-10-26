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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.credentials.CredentialManager
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.meeplemeet.model.structures.Discussion
import com.github.meeplemeet.model.viewmodels.AuthViewModel
import com.github.meeplemeet.model.viewmodels.FirestoreHandlesViewModel
import com.github.meeplemeet.model.viewmodels.FirestoreSessionViewModel
import com.github.meeplemeet.model.viewmodels.FirestoreViewModel
import com.github.meeplemeet.ui.CreateAccountScreen
import com.github.meeplemeet.ui.CreateSessionScreen
import com.github.meeplemeet.ui.DiscoverSessionsScreen
import com.github.meeplemeet.ui.DiscussionAddScreen
import com.github.meeplemeet.ui.DiscussionScreen
import com.github.meeplemeet.ui.DiscussionSettingScreen
import com.github.meeplemeet.ui.DiscussionsOverviewScreen
import com.github.meeplemeet.ui.ProfileScreen
import com.github.meeplemeet.ui.SessionViewScreen
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

private val startDestination = MeepleMeetScreen.SignInScreen.name

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

  val uiState by authVM.uiState.collectAsState()
  val firestoreVMAccount by firestoreVM.account.collectAsState()
  val handlesError by handlesVM.errorMessage.collectAsState()

  var currentAccount = uiState.account
  var currentDiscussion: Discussion? = null

  /** Fetch current user if already logged in */
  LaunchedEffect(FirebaseProvider.auth.currentUser) {
    try {
      FirebaseProvider.auth.currentUser?.let { firestoreVM.getCurrentAccount() }
    } catch (_: Exception) {
      FirebaseProvider.auth.signOut()
    }
  }

  /** Authentication gate: navigates out of auth graph when an account becomes available */
  LaunchedEffect(firestoreVMAccount) {
    currentAccount = firestoreVMAccount
    currentAccount?.let {
      handlesVM.handleForAccountExists(currentAccount!!)
      if (handlesError.isBlank()) navigationActions.navigateOutOfAuthGraph()
      else navigationActions.navigateTo(MeepleMeetScreen.CreateAccountScreen)
    }
  }
  LaunchedEffect(currentAccount) {
    currentAccount?.let {
      handlesVM.handleForAccountExists(currentAccount!!)
      if (handlesError.isBlank()) navigationActions.navigateOutOfAuthGraph()
      else navigationActions.navigateTo(MeepleMeetScreen.CreateAccountScreen)
    }
  }

  NavHost(navController = navController, startDestination = startDestination) {
    composable(MeepleMeetScreen.SignInScreen.name) {
      SignInScreen(
          viewModel = authVM,
          credentialManager = credentialManager,
          onSignUpClick = { navigationActions.navigateTo(MeepleMeetScreen.SignUpScreen) })
    }

    composable(MeepleMeetScreen.SignUpScreen.name) {
      SignUpScreen(
          viewModel = authVM,
          credentialManager = credentialManager,
          onLogInClick = { navigationActions.navigateTo(MeepleMeetScreen.SignInScreen) },
          onRegister = { navigationActions.navigateTo(MeepleMeetScreen.CreateAccountScreen) })
    }

    composable(MeepleMeetScreen.CreateAccountScreen.name) {
      CreateAccountScreen(
          viewModel = handlesVM,
          currentAccount = currentAccount!!,
          onCreate = { navigationActions.navigateOutOfAuthGraph() })
    }

    composable(MeepleMeetScreen.DiscussionsOverview.name) {
      if (currentAccount != null) {
        DiscussionsOverviewScreen(
            currentUser = currentAccount!!,
            navigation = navigationActions,
            onClickAddDiscussion = {
              navigationActions.navigateTo(MeepleMeetScreen.DiscussionAddScreen)
            },
            onSelectDiscussion = {
              currentDiscussion = it
              navigationActions.navigateTo(MeepleMeetScreen.DiscussionScreen)
            },
            viewModel = firestoreVM)
      } else {
        LoadingScreen()
      }
    }

    composable(MeepleMeetScreen.DiscussionScreen.name) {
      if (currentAccount != null) {
        DiscussionScreen(
            discussionId = currentDiscussion!!.uid,
            currentUser = currentAccount!!,
            onBack = { navigationActions.goBack() },
            onOpenDiscussionInfo = {
              navigationActions.navigateTo(MeepleMeetScreen.DiscussionInfoScreen)
            },
            onCreateSessionClick = {
              currentDiscussion = it
              if (it.session != null) navigationActions.navigateTo(MeepleMeetScreen.SessionScreen)
              else navigationActions.navigateTo(MeepleMeetScreen.SessionAddScreen)
            },
            viewModel = firestoreVM)
      } else {
        LoadingScreen()
      }
    }

    composable(MeepleMeetScreen.DiscussionAddScreen.name) {
      if (currentAccount != null) {
        DiscussionAddScreen(
            onBack = { navigationActions.goBack() },
            onCreate = { navigationActions.navigateTo(MeepleMeetScreen.DiscussionsOverview) },
            currentUser = currentAccount!!,
            handleViewModel = handlesVM,
            viewModel = firestoreVM)
      } else {
        LoadingScreen()
      }
    }

    composable(MeepleMeetScreen.DiscussionInfoScreen.name) {
      if (currentAccount != null) {
        DiscussionSettingScreen(
            discussionId = currentDiscussion!!.uid,
            currentAccount = currentAccount!!,
            onBack = { navigationActions.goBack() },
            onLeave = { navigationActions.navigateTo(MeepleMeetScreen.DiscussionsOverview) },
            onDelete = { navigationActions.navigateTo(MeepleMeetScreen.DiscussionsOverview) },
            handlesViewModel = handlesVM,
            viewModel = firestoreVM)
      } else {
        LoadingScreen()
      }
    }

    composable(MeepleMeetScreen.SessionAddScreen.name) {
      if (currentAccount != null) {
        val discussionId = currentDiscussion!!.uid
        if (discussionId.isBlank()) {
          navigationActions.navigateTo(MeepleMeetScreen.DiscussionsOverview)
        } else {
          CreateSessionScreen(
              viewModel = firestoreVM,
              sessionViewModel = FirestoreSessionViewModel(currentDiscussion!!),
              discussionId = discussionId,
              currentUser = currentAccount!!,
              onBack = { navigationActions.goBack() },
              onCreate = {
                navigationActions.goBack()
                navigationActions.navigateTo(MeepleMeetScreen.SessionScreen)
              })
        }
      } else {
        LoadingScreen()
      }
    }

    composable(MeepleMeetScreen.SessionScreen.name) {
      val discussionId = currentDiscussion!!.uid
      if (discussionId.isBlank()) {
        navigationActions.navigateTo(MeepleMeetScreen.DiscussionsOverview)
      } else {
        SessionViewScreen(
            viewModel = firestoreVM,
            sessionViewModel = FirestoreSessionViewModel(currentDiscussion!!),
            discussionId = discussionId,
            currentUser = currentAccount!!,
            onBack = { navigationActions.goBack() })
      }
    }

    composable(MeepleMeetScreen.SessionsOverview.name) {
      SessionsOverviewScreen(navigation = navigationActions)
    }

    composable(MeepleMeetScreen.DiscoverSessions.name) {
      DiscoverSessionsScreen(navigation = navigationActions)
    }

    composable(MeepleMeetScreen.ProfileScreen.name) {
      ProfileScreen(
          navigation = navigationActions,
          authViewModel = authVM,
          firestoreVM,
          onSignOut = { navigationActions.navigateTo(MeepleMeetScreen.SignInScreen) })
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
