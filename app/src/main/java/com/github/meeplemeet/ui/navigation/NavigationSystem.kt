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
    const val GO_BACK_BUTTON = "GoBackButton"
    const val TOP_BAR_TITLE = "TopBarTitle"
    const val SESSIONS_TAB = "OverviewTab"
    const val DISCUSSIONS_TAB = "MapTab"
    const val DISCOVER_TAB = "DiscoverTab"
    const val PROFILE_TAB = "ProfileTab"
}

/**
 * Defines all navigation destinations (screens) in the Meeple Meet app.
 *
 * Each entry in this enum represents a screen(or tab) that can be displayed within the app. It
 * centralizes both screen definitions and their metadata, replacing the previous `Tab` and
 * `MeepleMeetScreen` sealed classes.
 *
 * ## Properties
 * - `route`: The unique route string used by the [NavHostController] to identify the screen.
 * - `title`: The human-readable title of the screen, used for display in UI components.
 * - `hasBottomBar`: Whether this screen appears in the bottom navigation bar.
 * - `hasBackButton`: Whether this screen should display a back button in the top bar.
 * - `icon`: The [ImageVector] shown in the bottom navigation bar (if applicable).
 * - `testTag`: The Compose testing tag associated with this screen (if applicable).
 *
 * ## Example
 *
 * ```kotlin
 * enum class MeepleMeetScreen(
 *     val route: String,
 *     val title: String,
 *     val hasBottomBar: Boolean,
 *     val hasBackButton: Boolean,
 *     val icon: ImageVector?,
 *     val testTag: String
 * ) {
 *     DiscoverSessions("discover", "Discover", true, false, Icons.Default.Language, NavigationTestTags.DISCOVER_TAB),
 *     SessionsOverview("sessions_overview", "Sessions", true, false, Icons.Default.Groups, NavigationTestTags.SESSIONS_TAB),
 *     DiscussionsOverview("discussions_overview", "Discussions", true, false, Icons.Default.ChatBubbleOutline, NavigationTestTags.DISCUSSIONS_TAB)
 * }
 * ```
 *
 * ## How to Add a New Screen
 * 1. Add a new entry in this enum with appropriate parameters:
 *     - A unique `route` string.
 *     - A user-facing `title` for display.
 *     - Whether it should appear in the bottom navigation bar (`hasBottomBar`).
 *     - Whether it should show a back button (`hasBackButton`).
 *     - An optional `icon` (if it should appear in the bottom navigation bar) and `testTag` for
 *       Compose UI testing.
 * 2. Add the corresponding composable to your navigation graph (NavHost) using its `route`.
 * 3. If `hasBottomBar` is true, it will automatically appear in [BottomNavigationMenu], which
 *    iterates over [entries] to display all bottom bar items.
 *
 * ## Notes
 * - The enum structure removes the need for manual tab list management.
 * - `route` and `title` should stay aligned for clarity.
 * - Top-level destinations correspond to screens with `hasBottomBar = true`.
 */
enum class MeepleMeetScreen(
    val route: String,
    val title: String,
    val hasBottomBar: Boolean,
    val hasBackButton: Boolean,
    val icon: ImageVector?,
    val testTag: String?
) {
    DiscoverSessions(
        route = "discover",
        title = "Discover",
        hasBottomBar = true,
        hasBackButton = false,
        icon = Icons.Default.Language,
        testTag = NavigationTestTags.DISCOVER_TAB
    ),
    SessionsOverview(
        route = "sessions_overview",
        title = "Sessions",
        hasBottomBar = true,
        hasBackButton = false,
        icon = Icons.Default.Groups,
        testTag = NavigationTestTags.SESSIONS_TAB
    ),
    SessionScreen(
        route = "session/{sessionId}",
        title = "Session",
        hasBottomBar = false,
        hasBackButton = true,
        icon = null,
        testTag = null
    ),
    SessionAddScreen(
        route = "session_add",
        title = "Add Session",
        hasBottomBar = false,
        hasBackButton = true,
        icon = null,
        testTag = null
    ),
    SessionEditScreen(
        route = "session_edit/{sessionId}",
        title = "Edit Session",
        hasBottomBar = false,
        hasBackButton = true,
        icon = null,
        testTag = null
    ),
    DiscussionsOverview(
        route = "discussions_overview",
        title = "Discussions",
        hasBottomBar = true,
        hasBackButton = false,
        icon = Icons.Default.ChatBubbleOutline,
        testTag = NavigationTestTags.DISCUSSIONS_TAB
    ),
    DiscussionScreen(
        route = "discussion/{discussionId}",
        title = "Discussion",
        hasBottomBar = false,
        hasBackButton = true,
        icon = null,
        testTag = null
    ),
    DiscussionAddScreen(
        route = "discussion_add",
        title = "Add Discussion",
        hasBottomBar = false,
        hasBackButton = true,
        icon = null,
        testTag = null
    ),
    DiscussionEditScreen(
        route = "discussion_edit/{discussionId}",
        title = "Edit Discussion",
        hasBottomBar = false,
        hasBackButton = true,
        icon = null,
        testTag = null
    ),
    ProfileScreen(
        route = "profile",
        title = "Profile",
        hasBottomBar = true,
        hasBackButton = false,
        icon = Icons.Default.AccountCircle,
        testTag = NavigationTestTags.PROFILE_TAB
    ),
    SignInScreen(
        route = "sign_in",
        title = "Sign In",
        hasBottomBar = false,
        hasBackButton = false,
        icon = null,
        testTag = null
    ),
    SignUpScreen(
        route = "sign_up",
        title = "Sign Up",
        hasBottomBar = false,
        hasBackButton = true,
        icon = null,
        testTag = null
    );
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
        modifier = modifier
            .fillMaxWidth()
            .testTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU)
    ) {
        MeepleMeetScreen.entries
            .filter { it.hasBottomBar }
            .forEach { screen ->
                NavigationBarItem(
                    icon = { screen.icon?.let { Icon(it, contentDescription = screen.title) } },
                    label = { Text(screen.title) },
                    selected = screen == currentScreen,
                    onClick = { onTabSelected(screen) },
                    modifier = screen.testTag?.let { Modifier.testTag(it) } ?: Modifier
                )
            }
    }
}

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
     * If the destination is already the current top-level destination (hasBottomBar), this call has
     * no effect to avoid redundant navigation.
     *
     * @param screen The target screen to navigate to.
     */
    open fun navigateTo(screen: MeepleMeetScreen) {
        if (screen.hasBottomBar && currentRoute() == screen.route) {
            // If the user is already on the top-level destination, do nothing
            return
        }
        navController.navigate(screen.route) {
            if (screen.hasBottomBar) {
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

@Preview(showBackground = true)
@Composable
fun BottomMenuPreview() {
    BottomNavigationMenu(currentScreen = MeepleMeetScreen.SessionsOverview, onTabSelected = {})
}
