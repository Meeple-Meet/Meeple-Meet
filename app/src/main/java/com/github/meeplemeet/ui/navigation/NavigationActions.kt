package com.github.meeplemeet.ui.navigation

import androidx.navigation.NavHostController

/**
 * Represents a destination (screen) in the Meeple Meet navigation system.
 *
 * Each screen should be defined as an `object` extending this sealed class. Example:
 * ```kotlin
 * object SessionsOverview : MeepleMeetScreen(
 *     route = "sessions_overview",
 *     name = "Sessions Overview",
 *     isTopLevelDestination = true
 * )
 * ```
 *
 * @property route The unique route string used by the navigation controller.
 * @property name The human-readable name of the screen.
 * @property isTopLevelDestination Whether the screen is a top-level destination (appears in the
 *   bottom navigation bar).
 */
sealed class MeepleMeetScreen(
    val route: String,
    val name: String,
    val isTopLevelDestination: Boolean = false
) {}

/**
 * Provides high-level navigation actions for the Meeple Meet app.
 *
 * This class wraps the [NavHostController] to simplify navigation logic. It should be used by
 * composables to move between screens.
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
   * If the destination is already the current top-level destination, this call has no effect to
   * avoid redundant navigation.
   *
   * @param screen The target screen to navigate to.
   */
  open fun navigateTo(screen: MeepleMeetScreen) {
    if (screen.isTopLevelDestination && currentRoute() == screen.route) {
      // If the user is already on the top-level destination, do nothing
      return
    }
    navController.navigate(screen.route) {
      if (screen.isTopLevelDestination) {
        launchSingleTop = true
        popUpTo(screen.route) { inclusive = true }
      }

      restoreState = true
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
