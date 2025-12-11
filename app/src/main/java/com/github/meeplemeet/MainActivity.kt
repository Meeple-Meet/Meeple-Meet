package com.github.meeplemeet
// AI was used for this file

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.meeplemeet.model.MainActivityViewModel
import com.github.meeplemeet.model.account.AccountRepository
import com.github.meeplemeet.model.account.HandlesRepository
import com.github.meeplemeet.model.auth.AuthenticationRepository
import com.github.meeplemeet.model.discussions.DiscussionRepository
import com.github.meeplemeet.model.images.ImageRepository
import com.github.meeplemeet.model.map.MarkerPreviewRepository
import com.github.meeplemeet.model.map.PinType
import com.github.meeplemeet.model.map.StorableGeoPinRepository
import com.github.meeplemeet.model.navigation.LocalNavigationVM
import com.github.meeplemeet.model.navigation.NavigationViewModel
import com.github.meeplemeet.model.offline.OfflineModeManager
import com.github.meeplemeet.model.posts.PostRepository
import com.github.meeplemeet.model.sessions.SessionRepository
import com.github.meeplemeet.model.shared.game.CloudBggGameRepository
import com.github.meeplemeet.model.shared.game.GameRepository
import com.github.meeplemeet.model.shared.location.LocationRepository
import com.github.meeplemeet.model.shared.location.NominatimLocationRepository
import com.github.meeplemeet.model.shops.Shop
import com.github.meeplemeet.model.shops.ShopRepository
import com.github.meeplemeet.model.space_renter.SpaceRenter
import com.github.meeplemeet.model.space_renter.SpaceRenterRepository
import com.github.meeplemeet.ui.MapScreen
import com.github.meeplemeet.ui.account.CreateAccountScreen
import com.github.meeplemeet.ui.account.FriendsScreen
import com.github.meeplemeet.ui.account.NotificationsTab
import com.github.meeplemeet.ui.account.ProfileScreen
import com.github.meeplemeet.ui.auth.OnBoardPage
import com.github.meeplemeet.ui.auth.OnBoardingScreen
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
import com.github.meeplemeet.ui.sessions.SessionEditScreen
import com.github.meeplemeet.ui.sessions.SessionScreen
import com.github.meeplemeet.ui.sessions.SessionsOverviewScreen
import com.github.meeplemeet.ui.shops.CreateShopScreen
import com.github.meeplemeet.ui.shops.ShopDetailsScreen
import com.github.meeplemeet.ui.shops.ShopScreen
import com.github.meeplemeet.ui.space_renter.CreateSpaceRenterScreen
import com.github.meeplemeet.ui.space_renter.EditSpaceRenterScreen
import com.github.meeplemeet.ui.space_renter.SpaceRenterScreen
import com.github.meeplemeet.ui.theme.AppTheme
import com.github.meeplemeet.ui.theme.ThemeMode
import com.github.meeplemeet.utils.KeyboardUtils
import com.google.android.gms.maps.MapsInitializer
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.functions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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

  /** Lazily initialized Firebase Storage instance for storage operations. */
  val storage: FirebaseStorage by lazy { Firebase.storage }

  /** Lazily initialized Firebase Functions instance for cloud functions operations. */
  val functions: FirebaseFunctions by lazy { Firebase.functions }
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
  val authentication: AuthenticationRepository by lazy { AuthenticationRepository() }

  /** Lazily initialized repository for user handle operations. */
  val handles: HandlesRepository by lazy { HandlesRepository() }

  /** Lazily initialized repository for account operations. */
  val accounts: AccountRepository by lazy { AccountRepository() }

  /** Lazily initialized repository for discussion operations. */
  val discussions: DiscussionRepository by lazy { DiscussionRepository() }

  /** Lazily initialized repository for gaming session operations. */
  val sessions: SessionRepository by lazy { SessionRepository() }

  /** Lazily initialized repository for board game data operations. */
  val games: GameRepository by lazy {
    if (BuildConfig.DEBUG) {
      FirestoreGameRepository()
    } else {
      CloudBggGameRepository()
    }
  }

  /** Lazily initialized repository for location operations. */
  val locations: LocationRepository by lazy { NominatimLocationRepository() }

  /** Lazily initialized repository for geo pin operations. */
  val geoPins: StorableGeoPinRepository by lazy { StorableGeoPinRepository() }

  /** Lazily initialized repository for marker preview operations. */
  val markerPreviews: MarkerPreviewRepository by lazy { MarkerPreviewRepository() }

  /** Lazily initialized repository for post operations. */
  val posts: PostRepository by lazy { PostRepository() }

  /** Lazily initialized repository for shop operations. */
  val shops: ShopRepository by lazy { ShopRepository() }

  /** Lazily initialized repository for space renter operations. */
  val spaceRenters: SpaceRenterRepository by lazy { SpaceRenterRepository() }

  /** Lazily initialized repository for image operations. */
  val images: ImageRepository by lazy { ImageRepository() }
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
  // Simple coroutine scope for sync operations
  val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    MapsInitializer.initialize(applicationContext)

    // TODO: Switch to build-type based AppCheck provider once infra is ready.
    // Currently using DebugAppCheckProviderFactory for all builds.
    // Uncomment the conditional block below when Play Integrity infra is available:
    //
    // Firebase.appCheck.installAppCheckProviderFactory(
    //     if (BuildConfig.DEBUG) DebugAppCheckProviderFactory.getInstance()
    //     else PlayIntegrityAppCheckProviderFactory.getInstance()
    // )

    Firebase.appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())

    OfflineModeManager.start(applicationContext)

    val inTests =
        try {
          Class.forName("androidx.test.espresso.Espresso")
          true
        } catch (e: ClassNotFoundException) {
          false
        }

    setContent { MeepleMeetApp(inTests = inTests) }
  }

  override fun onDestroy() {
    KeyboardUtils.detach(this)
    super.onDestroy()
  }
}

class MainActivityViewModelFactory(private val inTests: Boolean) : ViewModelProvider.Factory {
  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    if (modelClass.isAssignableFrom(MainActivityViewModel::class.java)) {
      return MainActivityViewModel(inTests = inTests) as T
    }
    throw IllegalArgumentException("Unknown ViewModel class")
  }
}

@Composable
fun MeepleMeetApp(
    inTests: Boolean = false,
    viewModel: MainActivityViewModel = viewModel(factory = MainActivityViewModelFactory(inTests)),
    context: Context = LocalContext.current,
    navController: NavHostController = rememberNavController(),
) {
  val credentialManager = remember { CredentialManager.create(context) }
  val navigationActions = NavigationActions(navController)
  var signedOut by remember { mutableStateOf(false) }

  var accountId by remember { mutableStateOf(FirebaseProvider.auth.currentUser?.uid ?: "") }
  val accountFlow =
      remember(accountId, signedOut) {
        if (!signedOut) viewModel.accountFlow(accountId, context) else MutableStateFlow(null)
      }
  val account by accountFlow.collectAsStateWithLifecycle()

  var discussionId by remember { mutableStateOf("") }
  val discussionFlow =
      remember(discussionId, signedOut) {
        if (!signedOut) viewModel.discussionFlow(discussionId, context) else MutableStateFlow(null)
      }
  val discussion by discussionFlow.collectAsStateWithLifecycle()

  var postId by remember { mutableStateOf("") }
  var shopId by remember { mutableStateOf("") }
  var shop by remember { mutableStateOf<Shop?>(null) }

  var spaceId by remember { mutableStateOf("") }
  var spaceRenter by remember { mutableStateOf<SpaceRenter?>(null) }

  var userLocation by remember {
    mutableStateOf<com.github.meeplemeet.model.shared.location.Location?>(null)
  }

  val online by OfflineModeManager.hasInternetConnection.collectAsStateWithLifecycle()
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  // Track previous online state and sync when it changes from false to true
  var wasOnline by remember { mutableStateOf(online) }
  val activity = context as? MainActivity

  LaunchedEffect(online) {
    // Only sync when transitioning from offline to online
    if (online && !wasOnline) {
      activity?.syncScope?.launch { viewModel.syncOfflineData() }
    }
    wasOnline = online
  }
  DisposableEffect(Unit) {
    val listener = FirebaseAuth.AuthStateListener { accountId = it.currentUser?.uid ?: "" }
    FirebaseProvider.auth.addAuthStateListener(listener)
    onDispose { FirebaseProvider.auth.removeAuthStateListener(listener) }
  }

  val navigationViewModel: NavigationViewModel = viewModel()
  LaunchedEffect(accountId) { navigationViewModel.startListening(accountId) }

  // Sync email from Firebase Auth to Firestore when user logs in
  // This is the optimal place to sync because:
  // 1. User has just logged in (possibly with new email after verification)
  // 2. Happens once per login session
  // 3. Ensures Firestore is up-to-date with Firebase Auth before any screen is shown
  LaunchedEffect(account) {
    if (account != null) {
      RepositoryProvider.authentication.syncEmailToFirestore()
    }
  }

  AppTheme(themeMode = account?.themeMode ?: ThemeMode.SYSTEM_DEFAULT) {
    Surface(modifier = Modifier.fillMaxSize()) {
      CompositionLocalProvider(LocalNavigationVM provides navigationViewModel) {
        NavHost(navController = navController, startDestination = MeepleMeetScreen.SignIn.name) {
          composable(MeepleMeetScreen.SignIn.name) {
            LaunchedEffect(account) {
              if (account != null && FirebaseProvider.auth.currentUser != null) {
                val exists =
                    RepositoryProvider.handles.handleForAccountExists(
                        account!!.uid, account!!.handle)

                if (exists) navigationActions.navigateOutOfAuthGraph()
                else navigationActions.navigateTo(MeepleMeetScreen.CreateAccount)
              }
            }

            if (FirebaseProvider.auth.currentUser != null) LoadingScreen()
            else
                SignInScreen(
                    credentialManager = credentialManager,
                    onSignUpClick = { navigationActions.navigateTo(MeepleMeetScreen.SignUp) },
                    onSignIn = { signedOut = false })
          }

          composable(MeepleMeetScreen.SignUp.name) {
            SignUpScreen(
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
                  onCreate = { navigationActions.navigateTo(MeepleMeetScreen.OnBoarding) },
                  onBack = {
                    viewModel.signOut()
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
                uiState.isEmailVerified,
                navigationActions,
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
                onBack = { navigationActions.goBack() },
                onCreate = { navigationActions.navigateTo(MeepleMeetScreen.DiscussionsOverview) })
          }

          composable(MeepleMeetScreen.Discussion.name) {
            if (discussion != null) {
              if (discussion!!.participants.contains(account!!.uid))
                  DiscussionScreen(
                      account!!,
                      discussion!!,
                      uiState.isEmailVerified,
                      onBack = {
                        navigationActions.navigateTo(MeepleMeetScreen.DiscussionsOverview)
                      },
                      onOpenDiscussionInfo = {
                        navigationActions.navigateTo(MeepleMeetScreen.DiscussionDetails)
                      },
                      onCreateSessionClick = {
                        discussionId = it.uid
                        navigationActions.navigateTo(
                            if (it.session != null) MeepleMeetScreen.SessionViewer
                            else MeepleMeetScreen.CreateSession)
                      },
                      onVerifyClick = { navigationActions.navigateTo(MeepleMeetScreen.Profile) })
              else navigationActions.navigateTo(MeepleMeetScreen.DiscussionsOverview)
            } else LoadingScreen()
          }

          composable(MeepleMeetScreen.DiscussionDetails.name) {
            if (discussion != null && discussion!!.participants.contains(account!!.uid))
                DiscussionDetailsScreen(
                    account = account!!,
                    discussion = discussion!!,
                    onBack = { navigationActions.goBack() },
                    onLeave = {
                      discussionId = ""
                      navigationActions.navigateTo(MeepleMeetScreen.DiscussionsOverview)
                    },
                    onDelete = {
                      discussionId = ""
                      navigationActions.navigateTo(MeepleMeetScreen.DiscussionsOverview)
                    })
            else navigationActions.navigateTo(MeepleMeetScreen.DiscussionsOverview)
          }

          composable(MeepleMeetScreen.CreateSession.name) {
            CreateSessionScreen(
                account = account!!,
                discussion = discussion!!,
                onBack = { navigationActions.goBack() })
          }

          composable(MeepleMeetScreen.Session.name) {
            if (discussion == null) {
              LoadingScreen()
            } else if (discussion!!.session != null &&
                discussion!!.session!!.participants.contains(account!!.uid)) {
              SessionEditScreen(
                  account = account!!,
                  discussion = discussion!!,
                  onBack = { navigationActions.goBack() })
            } else {
              navigationActions.navigateTo(MeepleMeetScreen.Discussion)
            }
          }

          composable(MeepleMeetScreen.SessionsOverview.name) {
            SessionsOverviewScreen(
                navigation = navigationActions,
                account = account!!,
                verified = uiState.isEmailVerified,
                onSelectSession = {
                  discussionId = it
                  navigationActions.navigateTo(MeepleMeetScreen.SessionViewer)
                })
          }

          composable(MeepleMeetScreen.PostsOverview.name) {
            PostsOverviewScreen(
                verified = uiState.isEmailVerified,
                navigation = navigationActions,
                account = account!!,
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
                verified = uiState.isEmailVerified,
                onBack = { navigationActions.goBack() },
                onVerifyClick = { navigationActions.navigateTo(MeepleMeetScreen.Profile) })
          }

          composable(MeepleMeetScreen.CreatePost.name) {
            CreatePostScreen(
                account = account!!,
                onPost = { navigationActions.navigateTo(MeepleMeetScreen.PostsOverview) },
                onDiscard = { navigationActions.navigateTo(MeepleMeetScreen.PostsOverview) },
                onBack = { navigationActions.goBack() })
          }

          composable(MeepleMeetScreen.Map.name) {
            MapScreen(
                account = account!!,
                verified = uiState.isEmailVerified,
                navigation = navigationActions,
                account = account!!,
                onUserLocationChange = { userLocation = it },
                onFABCLick = { geoPin ->
                  when (geoPin) {
                    PinType.SHOP -> {
                      navigationActions.navigateTo(MeepleMeetScreen.CreateShop)
                    }
                    PinType.SPACE -> {
                      navigationActions.navigateTo(MeepleMeetScreen.CreateSpaceRenter)
                    }
                    PinType.SESSION -> {}
                  }
                },
                onRedirect = { geoPin ->
                  when (geoPin.type) {
                    PinType.SHOP -> {
                      shopId = geoPin.uid
                      navigationActions.navigateTo(MeepleMeetScreen.ShopDetails)
                    }
                    PinType.SPACE -> {
                      spaceId = geoPin.uid
                      navigationActions.navigateTo(MeepleMeetScreen.SpaceDetails)
                    }
                    PinType.SESSION -> {
                      discussionId = geoPin.uid
                      println(geoPin.uid)
                      navigationActions.navigateTo(MeepleMeetScreen.SessionViewer)
                    }
                  }
                })
          }

          composable(MeepleMeetScreen.Profile.name) {
            account?.let {
              ProfileScreen(
                  navigation = navigationActions,
                  account = account!!,
                  verified = uiState.isEmailVerified,
                  onSignOutOrDel = {
                    navigationActions.navigateTo(MeepleMeetScreen.SignIn)
                    signedOut = true
                  },
                  onDelete = {
                    // Sign out the user before deleting his account, avoiding an infinite loading
                    // screen
                    FirebaseProvider.auth.signOut()
                  },
                  onFriendClick = { navigationActions.navigateTo(MeepleMeetScreen.Friends) },
                  onNotificationClick = {
                    navigationActions.navigateTo(MeepleMeetScreen.NotificationsTab)
                  })
            } ?: navigationActions.navigateTo(MeepleMeetScreen.SignIn)
          }

          composable(MeepleMeetScreen.NotificationsTab.name) {
            account?.let {
              NotificationsTab(
                  account = account!!,
                  verified = uiState.isEmailVerified,
                  navigationActions,
                  onBack = { navigationActions.goBack() })
            }
          }

          composable(MeepleMeetScreen.ShopDetails.name) {
            if (shopId.isNotEmpty()) {
              ShopScreen(
                  account = account!!,
                  shopId = shopId,
                  onBack = { navigationActions.goBack() },
                  onEdit = {
                    shop = it
                    navigationActions.navigateTo(MeepleMeetScreen.EditShop, popUpTo = false)
                  })
            } else {
              LoadingScreen()
            }
          }

          composable(MeepleMeetScreen.CreateShop.name) {
            CreateShopScreen(
                owner = account!!,
                online = online,
                userLocation = userLocation,
                onBack = { navigationActions.goBack() })
          }

          composable(MeepleMeetScreen.EditShop.name) {
            if (shop != null) {
              ShopDetailsScreen(
                  owner = account!!,
                  shop = shop!!,
                  onBack = { navigationActions.goBack() },
                  online = online,
                  onSaved = { navigationActions.goBack() })
            } else {
              LoadingScreen()
            }
          }
          composable(MeepleMeetScreen.CreateSpaceRenter.name) {
            CreateSpaceRenterScreen(
                owner = account!!,
                online = online,
                userLocation = userLocation,
                onBack = { navigationActions.goBack() },
                onCreated = { navigationActions.goBack() })
          }

          composable(MeepleMeetScreen.SpaceDetails.name) {
            if (spaceId.isNotEmpty()) {
              SpaceRenterScreen(
                  account = account!!,
                  spaceId = spaceId,
                  onBack = { navigationActions.goBack() },
                  onEdit = {
                    spaceRenter = it
                    navigationActions.navigateTo(MeepleMeetScreen.EditSpaceRenter, popUpTo = false)
                  })
            } else {
              LoadingScreen()
            }
          }
          composable(MeepleMeetScreen.EditSpaceRenter.name) {
            if (spaceRenter != null) {
              EditSpaceRenterScreen(
                  owner = account!!,
                  spaceRenter = spaceRenter!!,
                  onBack = { navigationActions.goBack() },
                  onUpdated = { navigationActions.goBack() },
                  online = online)
            } else {
              LoadingScreen()
            }
          }

          // OnBoarding Screen
          composable(MeepleMeetScreen.OnBoarding.name) {
            val pages =
                listOf(
                    OnBoardPage(
                        image = R.drawable.onboarding_session_discussion,
                        title = "Welcome to MeepleMeet",
                        description = "Discover events and meet new people."),
                    OnBoardPage(
                        image = R.drawable.onboarding_session_discussion,
                        title = "Create Sessions",
                        description = "Use the discussion screen to create game sessions."),
                    OnBoardPage(
                        image = R.drawable.session_logo,
                        title = "Game Sessions",
                        description = "Organize gaming meetups"),
                    OnBoardPage(
                        image = R.drawable.onboarding_session_discussion,
                        title = "Community Posts",
                        description = "Share with the community"),
                    OnBoardPage(
                        image = R.drawable.onboarding_session_discussion,
                        title = "Explore Nearby",
                        description = "Find activities near you."),
                    OnBoardPage(R.drawable.logo_clear, "Let's Go!", "Ready to start?"))
            OnBoardingScreen(
                pages = pages,
                onSkip = { navigationActions.navigateTo(MeepleMeetScreen.DiscussionsOverview) },
                onFinished = { navigationActions.navigateTo(MeepleMeetScreen.DiscussionsOverview) })
          }

          composable(MeepleMeetScreen.Friends.name) {
            account?.let { currentAccount ->
              FriendsScreen(
                  account = currentAccount,
                  verified = uiState.isEmailVerified,
                  navigationActions = navigationActions,
                  onBack = { navigationActions.goBack() })
            } ?: navigationActions.navigateTo(MeepleMeetScreen.SignIn)
          }

          composable(MeepleMeetScreen.SessionViewer.name) {
            if (discussion == null) {
              LoadingScreen()
            } else if (discussion!!.session != null &&
                discussion!!.session!!.participants.contains(account!!.uid)) {
              SessionScreen(
                  account = account!!,
                  discussion = discussion!!,
                  onBack = { navigationActions.goBack() },
                  onEditClick = { navigationActions.navigateTo(MeepleMeetScreen.Session) })
            } else {
              navigationActions.navigateTo(MeepleMeetScreen.Discussion)
            }
          }
        }
      }
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
