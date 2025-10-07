package com.github.meeplemeet.ui.navigation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
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

/**
 * Defines the bottom navigation menu for the MeepleMeet app using [NavigationBar] and
 * [NavigationBarItem].
 *
 * ## Tabs
 * The navigation menu is based on the [Tab] sealed class. Each tab has:
 * - `name`: The display name shown in the navigation bar.
 * - `icon`: The [ImageVector] icon displayed in the navigation bar.
 * - `destination`: The [MeepleMeetScreen] corresponding to this tab. Currently set to `null`
 *   because the exact screens are not defined yet. You should replace `null` with the proper screen
 *   reference once screens are implemented.
 * - `testTag`: The Compose testing tag to identify the navigation item in UI tests.
 *
 * The current default tabs are:
 * 1. [Tab.DiscoverSessions]
 * 2. [Tab.SessionsOverview]
 * 3. [Tab.DiscussionsOverview]
 *
 * ## How to Add a New Tab
 * 1. Add a new object to the [Tab] sealed class with appropriate `name`, `icon`, `destination`, and
 *    `testTag`.
 * 2. Add the new tab to the `tabs` list in the order you want it displayed in the navigation bar.
 * 3. Update any navigation handling logic to handle the new [MeepleMeetScreen] destination.
 *
 * ## Usage
 * Use the [BottomNavigationMenu] composable in your screen layout:
 * ```kotlin
 * BottomNavigationMenu(
 *     currentTab = Tab.SessionsOverview,
 *     onTabSelected = { tab ->
 *         // handle tab selection, e.g., navigate to tab.destination
 *     }
 * )
 * ```
 *
 * ## Notes
 * - `currentTab` should always match one of the tabs in the `tabs` list.
 * - `onTabSelected` is a callback invoked when the user taps a tab.
 * - Remember to replace `null` in `destination` with actual [MeepleMeetScreen] references when
 *   screens are implemented.
 */
sealed class Tab(
    val name: String,
    val icon: ImageVector,
    val destination: MeepleMeetScreen?,
    val testTag: String
) {
  object SessionsOverview : Tab("Sessions", Icons.Default.Groups, null, "")

  object DiscussionsOverview : Tab("Discussions", Icons.Default.ChatBubbleOutline, null, "")

  object DiscoverSessions : Tab("Discover", Icons.Default.Language, null, "")
}

private val tabs = listOf(Tab.DiscoverSessions, Tab.SessionsOverview, Tab.DiscussionsOverview)

@Composable
fun BottomNavigationMenu(
    currentTab: Tab,
    onTabSelected: (Tab) -> Unit,
    modifier: Modifier = Modifier
) {
  // TODO: Update colors when full MaterialTheme is implemented
  NavigationBar(
      modifier = modifier.fillMaxWidth().testTag(""),
      content = {
        tabs.forEach { tab ->
          NavigationBarItem(
              icon = { Icon(tab.icon, contentDescription = tab.name) },
              label = { Text(tab.name) },
              selected = tab == currentTab,
              onClick = { onTabSelected(tab) },
              modifier = Modifier.testTag(tab.testTag))
        }
      })
}

@Preview(showBackground = true)
@Composable
fun BottomMenuPreview() {
  BottomNavigationMenu(currentTab = Tab.SessionsOverview, onTabSelected = {})
}
