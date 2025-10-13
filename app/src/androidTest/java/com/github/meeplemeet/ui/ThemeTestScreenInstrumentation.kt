package com.github.meeplemeet.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.meeplemeet.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeTestScreenInstrumentation {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun renderThemeTestScreenDark() {
    composeTestRule.setContent { ThemeTestScreen(themeMode = ThemeMode.DARK) }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("themeMode_DARK").assertExists()
    Thread.sleep(5000) // Keep the screen visible for 5 seconds
  }

  @Test
  fun renderThemeTestScreenLight() {
    composeTestRule.setContent { ThemeTestScreen(themeMode = ThemeMode.LIGHT) }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("themeMode_LIGHT").assertExists()
    Thread.sleep(5000) // Keep the screen visible for 5 seconds
  }

  @Test
  fun picksSystemTheme_whenNoArgumentProvided() {
    composeTestRule.setContent { ThemeTestScreen() }

    val darkExists =
        composeTestRule.onAllNodesWithTag("themeMode_DARK").fetchSemanticsNodes().isNotEmpty()

    val lightExists =
        composeTestRule.onAllNodesWithTag("themeMode_LIGHT").fetchSemanticsNodes().isNotEmpty()

    // Assert that one of the possible modes is actually resolved and tagged
    assert(
        darkExists || lightExists,
    )
  }
}
