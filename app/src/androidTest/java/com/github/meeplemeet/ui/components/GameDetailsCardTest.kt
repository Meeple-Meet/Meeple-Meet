package com.github.meeplemeet.ui.components

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.meeplemeet.model.shared.game.Game
import com.github.meeplemeet.ui.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameDetailsCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val fakeGame = Game(
        uid = "demo",
        name = "Test Game",
        description = "This is a very long description intended to test the expandable overview feature of the composable. It should initially show a collapsed view and a Read more button.",
        imageURL = "https://via.placeholder.com/600x400",
        minPlayers = 2,
        maxPlayers = 6,
        recommendedPlayers = 4,
        averagePlayTime = 90,
        minAge = 8,
        genres = listOf("economy", "trading", "negotiation")
    )

    @Test
    fun gameDetailsCard_displaysAllElements() {
        composeTestRule.setContent {
            AppTheme {
                GameDetailsCard(game = fakeGame)
            }
        }

        // Check if title is displayed
        composeTestRule.onNodeWithText("Test Game").assertIsDisplayed()

        // Check if overview label is displayed
        composeTestRule.onNodeWithText("Overview:").assertIsDisplayed()

        // Check if initial description is displayed (collapsed)
        composeTestRule.onNodeWithText(fakeGame.description.substring(0, 20), substring = true)
            .assertIsDisplayed()

        // Check if genre chips are displayed
        fakeGame.genres.forEach { genre ->
            composeTestRule.onNodeWithText("#$genre").assertIsDisplayed()
        }

        // Check if the X button is displayed
        composeTestRule.onNodeWithContentDescription("Close").assertIsDisplayed()
    }

    @Test
    fun gameDescription_expandsAndCollapses() {
        composeTestRule.setContent {
            AppTheme {
                GameDetailsCard(game = fakeGame)
            }
        }

        val readMoreButton = composeTestRule.onNodeWithText("Read more…")
        readMoreButton.assertExists().assertIsDisplayed()

        // Click "Read more…" to expand
        readMoreButton.performClick()

        // Now button should show "Read less"
        composeTestRule.onNodeWithText("Read less").assertIsDisplayed()
    }
}