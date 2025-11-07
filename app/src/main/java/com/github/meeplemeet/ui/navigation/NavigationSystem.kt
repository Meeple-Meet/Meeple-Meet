package com.github.meeplemeet.ui.navigation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.github.meeplemeet.ui.theme.AppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/** Centralizes test tags used in navigation-related UI elements. */
object NavigationTestTags {
  const val BOTTOM_NAVIGATION_MENU = "BottomNavigationMenu"
  const val SCREEN_TITLE = "ScreenTitle"
  const val GO_BACK_BUTTON = "GoBackButton"
  const val SESSIONS_TAB = "SessionsTab"
  const val DISCUSSIONS_TAB = "DiscussionsTab"
  const val DISCOVER_TAB = "DiscoverTab"
  const val PROFILE_TAB = "ProfileTab"
  const val MAP_TAB = "MapTab"
}

/**
 * Defines all navigation destinations (screens) in the Meeple Meet app.
 *
 * This sealed class replaces the previous enum approach, supporting both:
 * 1. Fixed screens (object): top-level destinations or screens without parameters.
 * 2. Parameterized screens (data class): screens that require dynamic arguments (e.g., IDs).
 *
 * ## Properties
 * - `route`: The unique route string used by the [NavHostController] to identify the screen.
 * - `name`: The human-readable name of the screen, used for display in UI components.
 * - `isInBottomBar`: Whether this screen appears in the bottom navigation bar.
 * - `icon`: The [ImageVector] shown in the bottom navigation bar (if applicable).
 * - `testTag`: The Compose testing tag associated with this screen (if applicable).
 *
 * ## Top-level destinations
 * Screens that appear in the bottom navigation bar are marked with `isInBottomBar = true`. For
 * bottom navigation, you can filter:
 * ```kotlin
 * val topLevelScreens = listOf(
 *     MeepleMeetScreen.DiscoverSessions,
 *     MeepleMeetScreen.SessionsOverview,
 *     MeepleMeetScreen.DiscussionsOverview,
 *     MeepleMeetScreen.ProfileScreen
 * )
 * ```
 *
 * ## Parameterized screens
 * Use `data class` when the screen depends on a dynamic parameter, e.g.:
 * ```kotlin
 * val screen = MeepleMeetScreen.DiscussionScreen(discussionId = "abc123")
 * navigationActions.navigateTo(screen)
 * ```
 *
 * The route will be resolved as `"discussion/abc123"` in the NavController.
 *
 * ## How to Add a New Screen
 * 1. Determine if the screen needs parameters:
 *     - **No parameters** → create an `object` inside the sealed class.
 *     - **With parameters** → create a `data class` with the required arguments.
 * 2. Define the screen inside the sealed class: ```kotlin object SignInScreen :
 *    MeepleMeetScreen("sign_in", "Sign In") data class DiscussionScreen(val discussionId: String) :
 *    MeepleMeetScreen("discussion/$discussionId", "Discussion") ```
 * 3. Add the corresponding composable in your NavHost using the screen's `route`: ```kotlin
 *    composable(MeepleMeetScreen.SignInScreen.route) { SignInScreen() }
 *    composable("discussion/{discussionId}") { backStackEntry -> val id =
 *    backStackEntry.arguments?.getString("discussionId")!! DiscussionScreen(discussionId = id)
 *    } ```
 * 4. If the screen is a top-level destination (should appear in the bottom navigation bar):
 *     - Add the **object** (not data class) manually in your BottomNavigationMenu: ```kotlin val
 *       bottomBarScreens = listOf( MeepleMeetScreen.DiscoverSessions,
 *       MeepleMeetScreen.SessionsOverview, MeepleMeetScreen.DiscussionsOverview,
 *       MeepleMeetScreen.ProfileScreen ) ```
 *
 * ## Notes
 * - The sealed class allows for safer type-checking and easier management of dynamic routes.
 * - `route` and `name` should stay aligned for clarity.
 */
enum class MeepleMeetScreen(
    val title: String,
    val inBottomBar: Boolean = false,
    val icon: ImageVector? = null,
    val iconSelected: ImageVector? = null,
    val testTag: String? = null
) {
  SignIn("Sign In"),
  SignUp("Sign Up"),
  CreateAccount("Create your Account"),
  DiscussionsOverview(
      "Discussions",
      true,
      Icons.Outlined.ChatBubbleOutline,
      Icons.Default.ChatBubbleOutline,
      NavigationTestTags.DISCUSSIONS_TAB),
  SessionsOverview(
      "Sessions",
      true,
      Icons.Outlined.Groups,
      Icons.Default.Groups,
      NavigationTestTags.SESSIONS_TAB),
  PostsOverview(
      "Posts",
      true,
      Icons.Outlined.Language,
      Icons.Default.Language,
      NavigationTestTags.DISCOVER_TAB),
  Map("Map", true, Icons.Outlined.Map, Icons.Default.Map, NavigationTestTags.MAP_TAB),
  Profile(
      "Profile",
      true,
      Icons.Outlined.AccountCircle,
      Icons.Default.AccountCircle,
      NavigationTestTags.PROFILE_TAB),
  CreateDiscussion("Create Discussion"),
  Discussion("Discussion"),
  DiscussionDetails("Discussion Details"),
  CreateSession("Create Session"),
  Session("Session"),
  SessionDetails("Session Details"),
  CreatePost("Create post"),
  Post("Post"),
  ShopDetails("Shop Details"),
  CreateShop("Create Shop"),
}

/**
 * Displays the bottom navigation bar for top-level destinations.
 *
 * @param currentScreen The currently active [MeepleMeetScreen].
 * @param onTabSelected Callback triggered when a tab is selected.
 * @param modifier Optional [Modifier] for layout customization.
 */
@Composable
fun BottomNavigationMenu(
    currentScreen: MeepleMeetScreen,
    onTabSelected: (MeepleMeetScreen) -> Unit,
    modifier: Modifier = Modifier
) {
  NavigationBar(
      containerColor = AppColors.secondary,
      contentColor = AppColors.textIcons,
      modifier =
          modifier
              .fillMaxWidth()
              .height(64.dp)
              .testTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU)) {
        MeepleMeetScreen.entries
            .filter { it -> it.inBottomBar }
            .forEach { screen ->
              NavigationBarItem(
                  colors =
                      NavigationBarItemColors(
                          selectedIconColor = AppColors.textIcons,
                          selectedTextColor = AppColors.textIcons,
                          selectedIndicatorColor = Color.Transparent,
                          disabledTextColor = Color.Transparent,
                          disabledIconColor = Color.Transparent,
                          unselectedIconColor = AppColors.textIcons.copy(alpha = 0.5f),
                          unselectedTextColor = AppColors.textIcons.copy(alpha = 0.5f)),
                  icon = {
                    val iconToUse =
                        if (screen == currentScreen) screen.iconSelected ?: screen.icon
                        else screen.icon
                    iconToUse?.let { Icon(it, contentDescription = screen.title) }
                  },
                  selected = screen == currentScreen,
                  onClick = { onTabSelected(screen) },
                  modifier = screen.testTag?.let { Modifier.testTag(it) } ?: Modifier)
            }
      }
}

/**
 * Provides high-level navigation actions for the Meeple Meet app.
 *
 * This class wraps the [NavHostController] to simplify navigation logic. It should be used by
 * composable to move between screens.
 *
 * Example usage:
 * ```kotlin
 * val navigation = NavigationActions(navController)
 * navigation.navigateTo(MeepleMeetScreen.SessionsOverview)
 * ```
 *
 * @param navController The [NavHostController] used for Compose navigation.
 */
open class NavigationActions(private val navController: NavHostController) {
  private val scope = MainScope()

  /**
   * Navigates to the specified [screen].
   *
   * If the destination is already the current top-level destination (isInBottomBar), this call has
   * no effect to avoid redundant navigation.
   *
   * @param screen The target screen to navigate to.
   */
  open fun navigateTo(screen: MeepleMeetScreen, popUpTo: Boolean = screen.inBottomBar) {
    scope.launch(Dispatchers.Main) {
      if (currentRoute() == screen.name) {
        // If the user is already on this screen, do nothing to avoid consecutive duplicates
        return@launch
      }
      navController.navigate(screen.name) {
        // Screens available through the bottom navigation bar are top-level destinations.
        // When navigating to one of these, we want to clear the back stack to avoid building
        // up a large stack of destinations as the user switches between them.
        if (popUpTo) {
          launchSingleTop = true
          popUpTo(screen.name) { inclusive = true }
        }

        restoreState = true
      }
    }
  }

  /**
   * Navigates out of the authentication graph and enters the discussions overview screen.
   *
   * This function clears the entire back stack to ensure the user cannot navigate back into the
   * authentication flow (SignIn/SignUp). It should be called once authentication is successfully
   * completed.
   *
   * Typical usage: called after a successful login or account creation.
   */
  open fun navigateOutOfAuthGraph() {
    scope.launch(Dispatchers.Main) {
      navController.navigate(MeepleMeetScreen.DiscussionsOverview.name) {
        popUpTo(0) { inclusive = true } // Empty stack
        launchSingleTop = true
      }
    }
  }

  /** Navigate back to the previous screen, skipping consecutive duplicates. */
  open fun goBack() {
    scope.launch(Dispatchers.Main) { navController.popBackStack() }
  }

  /**
   * Returns the current route displayed by the [NavHostController].
   *
   * @return The route of the currently active screen, or an empty string if unknown.
   */
  open fun currentRoute(): String {
    return navController.currentDestination?.route ?: ""
  }
}
