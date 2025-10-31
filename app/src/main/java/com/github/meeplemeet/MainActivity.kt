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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.meeplemeet.model.viewmodels.AuthViewModel
import com.github.meeplemeet.model.viewmodels.FirestoreHandlesViewModel
import com.github.meeplemeet.model.viewmodels.FirestoreSessionViewModel
import com.github.meeplemeet.model.viewmodels.FirestoreViewModel
import com.github.meeplemeet.model.viewmodels.PostOverviewViewModel
import com.github.meeplemeet.ui.AddDiscussionScreen
import com.github.meeplemeet.ui.AddSessionScreen
import com.github.meeplemeet.ui.CreateAccountScreen
import com.github.meeplemeet.ui.CreatePostScreen
import com.github.meeplemeet.ui.DiscoverSessionsScreen
import com.github.meeplemeet.ui.DiscussionDetailsScreen
import com.github.meeplemeet.ui.DiscussionScreen
import com.github.meeplemeet.ui.DiscussionsOverviewScreen
import com.github.meeplemeet.ui.FeedsOverviewScreen
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
import kotlinx.coroutines.flow.MutableStateFlow

object FirebaseProvider {
  val db: FirebaseFirestore by lazy { Firebase.firestore }
  val auth: FirebaseAuth by lazy { Firebase.auth }
}

const val LOADING_SCREEN_TAG = "Loading Screen"

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
    authVM: AuthViewModel = viewModel(),
    firestoreVM: FirestoreViewModel = viewModel(),
    handlesVM: FirestoreHandlesViewModel = viewModel(),
    postsOverviewVM: PostOverviewViewModel = viewModel(),
    navController: NavHostController = rememberNavController()
) {
  val credentialManager = remember { CredentialManager.create(context) }
  val navigationActions = NavigationActions(navController)
  var signedOut by remember { mutableStateOf(false) }

  var accountId by remember { mutableStateOf(FirebaseProvider.auth.currentUser?.uid ?: "") }
  val accountFlow =
      remember(accountId, signedOut) {
        if (!signedOut) firestoreVM.accountFlow(accountId) else MutableStateFlow(null)
      }
  val account by accountFlow.collectAsStateWithLifecycle()

  var discussionId by remember { mutableStateOf("") }
  val discussionFlow =
      remember(discussionId, signedOut) {
        if (!signedOut) firestoreVM.discussionFlow(discussionId) else MutableStateFlow(null)
      }
  val discussion by discussionFlow.collectAsStateWithLifecycle()
  val sessionRepo =
      remember(discussion) { discussion?.let { FirestoreSessionViewModel(discussion!!) } }

  DisposableEffect(Unit) {
    val listener = FirebaseAuth.AuthStateListener { accountId = it.currentUser?.uid ?: "" }
    FirebaseProvider.auth.addAuthStateListener(listener)
    onDispose { FirebaseProvider.auth.removeAuthStateListener(listener) }
  }

  NavHost(navController = navController, startDestination = MeepleMeetScreen.SignIn.name) {
    composable(MeepleMeetScreen.SignIn.name) {
      LaunchedEffect(account) {
        if (account != null && FirebaseProvider.auth.currentUser != null) {
          val exists =
              handlesVM::repository.get().handleForAccountExists(account!!.uid, account!!.handle)

          if (exists) navigationActions.navigateOutOfAuthGraph()
          else navigationActions.navigateTo(MeepleMeetScreen.CreateAccount)
        }
      }

      if (FirebaseProvider.auth.currentUser != null) LoadingScreen()
      else
          SignInScreen(
              authVM,
              credentialManager = credentialManager,
              onSignUpClick = { navigationActions.navigateTo(MeepleMeetScreen.SignUp) },
              onSignIn = { signedOut = false })
    }

    composable(MeepleMeetScreen.SignUp.name) {
      SignUpScreen(
          authVM,
          credentialManager = credentialManager,
          onLogInClick = { navigationActions.navigateTo(MeepleMeetScreen.SignIn) },
          onRegister = {
            signedOut = false
            navigationActions.navigateTo(MeepleMeetScreen.CreateAccount)
          })
    }

    composable(MeepleMeetScreen.CreateAccount.name) {
      if (account != null) {
        CreateAccountScreen(account!!, firestoreVM, handlesVM) {
          navigationActions.navigateOutOfAuthGraph()
        }
      } else {
        LoadingScreen()
      }
    }

    composable(MeepleMeetScreen.DiscussionsOverview.name) {
      DiscussionsOverviewScreen(
          account!!,
          navigationActions,
          firestoreVM,
          onClickAddDiscussion = { navigationActions.navigateTo(MeepleMeetScreen.AddDiscussion) },
          onSelectDiscussion = {
            discussionId = it.uid
            navigationActions.navigateTo(MeepleMeetScreen.Discussion)
          },
      )
    }

    composable(MeepleMeetScreen.Discussion.name) {
      discussion?.let {
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
              navigationActions.navigateTo(
                  if (it.session != null) MeepleMeetScreen.Session else MeepleMeetScreen.AddSession)
            },
        )
      } ?: LoadingScreen()
    }

    composable(MeepleMeetScreen.AddDiscussion.name) {
      AddDiscussionScreen(
          account = account!!,
          viewModel = firestoreVM,
          onBack = { navigationActions.goBack() },
          onCreate = { navigationActions.navigateTo(MeepleMeetScreen.DiscussionsOverview) },
          handleViewModel = handlesVM)
    }

    composable(MeepleMeetScreen.DiscussionDetails.name) {
      DiscussionDetailsScreen(
          account = account!!,
          discussion = discussion!!,
          viewModel = firestoreVM,
          onBack = { navigationActions.goBack() },
          onLeave = { navigationActions.navigateTo(MeepleMeetScreen.DiscussionsOverview) },
          onDelete = { navigationActions.navigateTo(MeepleMeetScreen.DiscussionsOverview) },
          handlesViewModel = handlesVM)
    }

    composable(MeepleMeetScreen.SessionsOverview.name) { SessionsOverviewScreen(navigationActions) }

    composable(MeepleMeetScreen.Session.name) {
      sessionRepo?.let {
        SessionDetailsScreen(
            account = account!!,
            discussion = discussion!!,
            viewModel = firestoreVM,
            sessionViewModel = sessionRepo,
            onBack = { navigationActions.goBack() })
      } ?: LoadingScreen()
    }

    composable(MeepleMeetScreen.AddSession.name) {
      sessionRepo?.let {
        AddSessionScreen(
            account = account!!,
            discussion = discussion!!,
            viewModel = firestoreVM,
            sessionViewModel = sessionRepo,
            onBack = { navigationActions.goBack() })
      } ?: LoadingScreen()
    }

    // TODO: To be replaced with the right DiscoverFeedsScreen/FeedsOverviewScren
    composable(MeepleMeetScreen.DiscoverFeeds.name) { DiscoverSessionsScreen(navigationActions) }

    // TODO: change callback destination if the screen name is changed
    composable(MeepleMeetScreen.CreatePost.name) {
      CreatePostScreen(
          account = account!!,
          onPost = { navigationActions.navigateTo(MeepleMeetScreen.DiscoverFeeds) },
          onDiscard = { navigationActions.navigateTo(MeepleMeetScreen.DiscoverFeeds) },
          onBack = { navigationActions.goBack() })
    }
    // TODO: Add post selection callback
    composable(MeepleMeetScreen.FeedsOverview.name) {
      FeedsOverviewScreen(
          account = account!!,
          navigation = navigationActions,
          firestoreViewModel = firestoreVM,
          postOverviewVM = postsOverviewVM,
          onClickAddPost = { navigationActions.navigateTo(MeepleMeetScreen.CreatePost) },
          onSelectPost = {})
    }

    composable(MeepleMeetScreen.Profile.name) {
      ProfileScreen(
          navigation = navigationActions,
          authViewModel = authVM,
          firestoreVM,
          onSignOut = {
            signedOut = true
            navigationActions.navigateTo(MeepleMeetScreen.SignIn)
          })
    }
  }
}

@Composable
private fun LoadingScreen() {
  Box(
      modifier = Modifier.testTag(LOADING_SCREEN_TAG).fillMaxSize(),
      contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }
}
