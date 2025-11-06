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
import com.github.meeplemeet.model.auth.AuthRepository
import com.github.meeplemeet.model.auth.AuthViewModel
import com.github.meeplemeet.model.auth.HandlesRepository
import com.github.meeplemeet.model.auth.HandlesViewModel
import com.github.meeplemeet.model.discussions.DiscussionRepository
import com.github.meeplemeet.model.discussions.DiscussionViewModel
import com.github.meeplemeet.model.map.StorableGeoPinRepository
import com.github.meeplemeet.model.posts.PostRepository
import com.github.meeplemeet.model.sessions.SessionRepository
import com.github.meeplemeet.model.sessions.SessionViewModel
import com.github.meeplemeet.model.shared.game.FirestoreGameRepository
import com.github.meeplemeet.model.shared.location.LocationRepository
import com.github.meeplemeet.model.shared.location.NominatimLocationRepository
import com.github.meeplemeet.model.shops.CreateShopViewModel
import com.github.meeplemeet.model.shops.ShopRepository
import com.github.meeplemeet.model.shops.ShopViewModel
import com.github.meeplemeet.model.space_renter.SpaceRenterRepository
import com.github.meeplemeet.ui.auth.CreateAccountScreen
import com.github.meeplemeet.ui.auth.ProfileScreen
import com.github.meeplemeet.ui.auth.SignInScreen
import com.github.meeplemeet.ui.auth.SignUpScreen
import com.github.meeplemeet.ui.discussions.CreateDiscussionScreen
import com.github.meeplemeet.ui.discussions.DiscussionDetailsScreen
import com.github.meeplemeet.ui.discussions.DiscussionScreen
import com.github.meeplemeet.ui.discussions.DiscussionsOverviewScreen
import com.github.meeplemeet.ui.navigation.MeepleMeetScreen
import com.github.meeplemeet.ui.navigation.NavigationActions
import com.github.meeplemeet.ui.posts.CreatePostScreen
import com.github.meeplemeet.ui.posts.PostScreen
import com.github.meeplemeet.ui.posts.PostsOverviewScreen
import com.github.meeplemeet.ui.sessions.CreateSessionScreen
import com.github.meeplemeet.ui.sessions.SessionDetailsScreen
import com.github.meeplemeet.ui.sessions.SessionsOverviewScreen
import com.github.meeplemeet.ui.shops.CreateShopScreen
import com.github.meeplemeet.ui.shops.ShopDetailsScreen
import com.github.meeplemeet.ui.theme.AppTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient

/**
 * Provider object for Firebase services.
 *
 * This object provides lazily initialized singleton instances of Firebase services to be used
 * throughout the application, ensuring a single source of truth for Firebase connections.
 */
object FirebaseProvider {
  /** Lazily initialized Firebase Firestore instance for database operations. */
  val db: FirebaseFirestore by lazy { Firebase.firestore }

  /** Lazily initialized Firebase Auth instance for authentication operations. */
  val auth: FirebaseAuth by lazy { Firebase.auth }
}

/**
 * Provider object for repository instances.
 *
 * This object provides lazily initialized singleton instances of all repositories used in the
 * application, ensuring consistent data access throughout the app and facilitating dependency
 * injection for ViewModels.
 */
object RepositoryProvider {
  /** Lazily initialized repository for account/authentication operations. */
  val accounts: AuthRepository by lazy { AuthRepository() }

  /** Lazily initialized repository for user handle operations. */
  val handles: HandlesRepository by lazy { HandlesRepository() }

  /** Lazily initialized repository for discussion operations. */
  val discussions: DiscussionRepository by lazy { DiscussionRepository() }

  /** Lazily initialized repository for gaming session operations. */
  val sessions: SessionRepository by lazy { SessionRepository() }

  /** Lazily initialized repository for board game data operations. */
  val games: FirestoreGameRepository by lazy { FirestoreGameRepository() }

  val locations: LocationRepository by lazy {
    NominatimLocationRepository(HttpClientProvider.client)
  }
  val geoPins: StorableGeoPinRepository by lazy { StorableGeoPinRepository() }

  /** Lazily initialized repository for post operations. */
  val posts: PostRepository by lazy { PostRepository() }

  /** Lazily initialized repository for shop operations. */
  val shops: ShopRepository by lazy { ShopRepository() }

  /** Lazily initialized repository for space renter operations. */
  val spaceRenters: SpaceRenterRepository by lazy { SpaceRenterRepository() }
}

object HttpClientProvider {
  var client: OkHttpClient = OkHttpClient()
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
    firestoreVM: DiscussionViewModel = viewModel(),
    handlesVM: HandlesViewModel = viewModel(),
    navController: NavHostController = rememberNavController(),
    shopVM: ShopViewModel = viewModel(),
    createShopVM: CreateShopViewModel = viewModel(),
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
  val sessionVM = remember(discussion) { discussion?.let { SessionViewModel(discussion!!) } }

  var postId by remember { mutableStateOf("") }

  var shopId by remember { mutableStateOf("") }

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
        CreateAccountScreen(
            account!!,
            firestoreVM,
            handlesVM,
            onCreate = { navigationActions.navigateOutOfAuthGraph() },
            onBack = {
              firestoreVM.signOut()
              authVM.signOut()
              FirebaseProvider.auth.signOut()
              navigationActions.goBack()
            })
      } else {
        LoadingScreen()
      }
    }

    composable(MeepleMeetScreen.DiscussionsOverview.name) {
      DiscussionsOverviewScreen(
          account!!,
          navigationActions,
          firestoreVM,
          onClickAddDiscussion = {
            navigationActions.navigateTo(MeepleMeetScreen.CreateDiscussion)
          },
          onSelectDiscussion = {
            discussionId = it.uid
            navigationActions.navigateTo(MeepleMeetScreen.Discussion)
          },
      )
    }

    composable(MeepleMeetScreen.CreateDiscussion.name) {
      CreateDiscussionScreen(
          account = account!!,
          viewModel = firestoreVM,
          onBack = { navigationActions.goBack() },
          onCreate = { navigationActions.navigateTo(MeepleMeetScreen.DiscussionsOverview) },
          handleViewModel = handlesVM)
    }

    composable(MeepleMeetScreen.Discussion.name) {
      if (discussion != null) {
        if (discussion!!.participants.contains(account!!.uid))
            DiscussionScreen(
                account!!,
                discussion!!,
                firestoreVM,
                onBack = { navigationActions.navigateTo(MeepleMeetScreen.DiscussionsOverview) },
                onOpenDiscussionInfo = {
                  navigationActions.navigateTo(MeepleMeetScreen.DiscussionDetails)
                },
                onCreateSessionClick = {
                  discussionId = it.uid
                  if (it.session == null && sessionVM != null) sessionVM.clearGameSearch()
                  navigationActions.navigateTo(
                      if (it.session != null) MeepleMeetScreen.Session
                      else MeepleMeetScreen.CreateSession)
                },
            )
        else navigationActions.navigateTo(MeepleMeetScreen.DiscussionsOverview)
      } else LoadingScreen()
    }

    composable(MeepleMeetScreen.DiscussionDetails.name) {
      if (discussion != null && discussion!!.participants.contains(account!!.uid))
          DiscussionDetailsScreen(
              account = account!!,
              discussion = discussion!!,
              viewModel = firestoreVM,
              onBack = { navigationActions.goBack() },
              onLeave = {
                discussionId = ""
                navigationActions.navigateTo(MeepleMeetScreen.DiscussionsOverview)
              },
              onDelete = {
                discussionId = ""
                navigationActions.navigateTo(MeepleMeetScreen.DiscussionsOverview)
              },
              handlesViewModel = handlesVM)
      else navigationActions.navigateTo(MeepleMeetScreen.DiscussionsOverview)
    }

    composable(MeepleMeetScreen.CreateSession.name) {
      sessionVM?.let {
        CreateSessionScreen(
            account = account!!,
            discussion = discussion!!,
            viewModel = firestoreVM,
            sessionViewModel = sessionVM,
            onBack = { navigationActions.goBack() })
      } ?: LoadingScreen()
    }

    composable(MeepleMeetScreen.Session.name) {
      sessionVM?.let {
        if (discussion!!.session != null &&
            discussion!!.session!!.participants.contains(account!!.uid))
            SessionDetailsScreen(
                account = account!!,
                discussion = discussion!!,
                viewModel = firestoreVM,
                sessionViewModel = sessionVM,
                onBack = { navigationActions.goBack() })
        else navigationActions.navigateTo(MeepleMeetScreen.Discussion)
      } ?: LoadingScreen()
    }

    composable(MeepleMeetScreen.SessionsOverview.name) { SessionsOverviewScreen(navigationActions) }

    composable(MeepleMeetScreen.PostsOverview.name) {
      PostsOverviewScreen(
          navigation = navigationActions,
          discussionViewModel = firestoreVM,
          onClickAddPost = { navigationActions.navigateTo(MeepleMeetScreen.CreatePost) },
          onSelectPost = {
            postId = it.id
            navigationActions.navigateTo(MeepleMeetScreen.Post)
          })
    }

    composable(MeepleMeetScreen.Post.name) {
      PostScreen(
          account = account!!,
          postId = postId,
          usersViewModel = firestoreVM,
          onBack = { navigationActions.goBack() })
    }

    composable(MeepleMeetScreen.CreatePost.name) {
      CreatePostScreen(
          account = account!!,
          onPost = { navigationActions.navigateTo(MeepleMeetScreen.PostsOverview) },
          onDiscard = { navigationActions.navigateTo(MeepleMeetScreen.PostsOverview) },
          onBack = { navigationActions.goBack() })
    }

    composable(MeepleMeetScreen.Profile.name) {
      account?.let {
        ProfileScreen(
            navigation = navigationActions,
            authViewModel = authVM,
            discussionViewModel = firestoreVM,
            account = account!!,
            onSignOut = {
              navigationActions.navigateTo(MeepleMeetScreen.SignIn)
              signedOut = true
            })
      } ?: navigationActions.navigateTo(MeepleMeetScreen.SignIn)
    }
    composable(MeepleMeetScreen.ShopDetails.name) {
      if (shopId.isNotEmpty()) {
        ShopDetailsScreen(
            account = account!!,
            shopId = shopId,
            onBack = { navigationActions.goBack() },
            onEdit = {
              // Navigate to shop edit screen (not implemented here)
            },
            viewModel = shopVM)
      } else {
        LoadingScreen()
      }
    }
    composable(MeepleMeetScreen.CreateShop.name) {
      CreateShopScreen(
          owner = account!!,
          onBack = { navigationActions.goBack() },
          onCreated = { /* TODO */},
          viewModel = createShopVM)
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
