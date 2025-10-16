package com.github.meeplemeet.ui.navigation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController

/** Centralizes test tags used in navigation-related UI elements. */
object NavigationTestTags {
  const val BOTTOM_NAVIGATION_MENU = "BottomNavigationMenu"
  const val SCREEN_TITLE = "ScreenTitle"
  const val GO_BACK_BUTTON = "GoBackButton"
  const val SESSIONS_TAB = "SessionsTab"
  const val DISCUSSIONS_TAB = "DiscussionsTab"
  const val DISCOVER_TAB = "DiscoverTab"
  const val PROFILE_TAB = "ProfileTab"
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
sealed class MeepleMeetScreen(
    val route: String,
    val name: String,
    val isInBottomBar: Boolean = false,
    val icon: ImageVector? = null,
    val testTag: String? = null
) {
  /** Top-level destinations */
  object DiscoverSessions :
      MeepleMeetScreen(
          route = "discover",
          name = "Discover",
          isInBottomBar = true,
          icon = Icons.Default.Language,
          testTag = NavigationTestTags.DISCOVER_TAB)

  object SessionsOverview :
      MeepleMeetScreen(
          route = "sessions_overview",
          name = "Sessions",
          isInBottomBar = true,
          icon = Icons.Default.Groups,
          testTag = NavigationTestTags.SESSIONS_TAB)

  object DiscussionsOverview :
      MeepleMeetScreen(
          route = "discussions_overview",
          name = "Discussions",
          isInBottomBar = true,
          icon = Icons.Default.ChatBubbleOutline,
          testTag = NavigationTestTags.DISCUSSIONS_TAB)

  object ProfileScreen :
      MeepleMeetScreen(
          route = "profile",
          name = "Profile",
          isInBottomBar = true,
          icon = Icons.Default.AccountCircle,
          testTag = NavigationTestTags.PROFILE_TAB)

  /** Authentication screens */
  object SignInScreen : MeepleMeetScreen(route = "sign_in", name = "Sign In")

  object SignUpScreen : MeepleMeetScreen(route = "sign_up", name = "Sign Up")

  /** Parameterized screens */
  data class SessionScreen(val sessionId: String) :
      MeepleMeetScreen(route = "session/$sessionId", name = "Session")

  data class SessionAddScreen(val sessionId: String) :
      MeepleMeetScreen(route = "session_add/$sessionId", name = "Add Session")

  data class SessionInfoScreen(val sessionId: String) :
      MeepleMeetScreen(route = "session_info/$sessionId", name = "Session Details")

  data class DiscussionScreen(val discussionId: String) :
      MeepleMeetScreen(route = "discussion/$discussionId", name = "Discussion")

  object DiscussionAddScreen : MeepleMeetScreen(route = "discussion_add", name = "Add Discussion")

  data class DiscussionInfoScreen(val discussionId: String) :
      MeepleMeetScreen(route = "discussion_info/$discussionId", name = "Discussion Details")

  object Routes {
    const val SESSION_ADD = "session_add/{discussionId}"
    const val SESSION = "session/{discussionId}"
    const val SESSION_INFO = "session_info/{sessionId}"
    const val DISCUSSION = "discussion/{discussionId}"
    const val DISCUSSION_INFO = "discussion_info/{discussionId}"
  }
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
  // TODO: Update colors when full MaterialTheme is implemented
  NavigationBar(
      modifier = modifier.fillMaxWidth().testTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU)) {
        listOf(
                MeepleMeetScreen.DiscussionsOverview,
                MeepleMeetScreen.SessionsOverview,
                MeepleMeetScreen.DiscoverSessions,
                MeepleMeetScreen.ProfileScreen)
            .forEach { screen ->
              NavigationBarItem(
                  icon = { screen.icon?.let { Icon(it, contentDescription = screen.name) } },
                  label = { Text(screen.name) },
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

  /**
   * Navigates to the specified [screen].
   *
   * If the destination is already the current top-level destination (isInBottomBar), this call has
   * no effect to avoid redundant navigation.
   *
   * @param screen The target screen to navigate to.
   */
  open fun navigateTo(screen: MeepleMeetScreen) {
    if (screen.isInBottomBar && currentRoute() == screen.route) {
      // If the user is already on the top-level destination, do nothing
      return
    }
    navController.navigate(screen.route) {
      // Screens available through the bottom navigation bar are top-level destinations.
      // When navigating to one of these, we want to clear the back stack to avoid building
      // up a large stack of destinations as the user switches between them.
      if (screen.isInBottomBar) {
        launchSingleTop = true
        popUpTo(screen.route) { inclusive = true }
      }

      restoreState = true
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
    navController.navigate(MeepleMeetScreen.DiscussionsOverview.route) {
      popUpTo(0) { inclusive = true } // Empty stack
      launchSingleTop = true
    }
  }

  /** Navigate back to the previous screen. */
  open fun goBack() {
    navController.popBackStack()
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

@Preview(showBackground = true)
@Composable
fun BottomMenuPreview() {
  BottomNavigationMenu(currentScreen = MeepleMeetScreen.SessionsOverview, onTabSelected = {})
}
