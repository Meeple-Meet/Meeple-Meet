package com.github.meeplemeet.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.viewmodels.FirestoreViewModel
import com.github.meeplemeet.ui.navigation.NavigationActions
import io.mockk.*
import kotlinx.coroutines.test.*
import org.junit.*

class AddDiscussionScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val nav: NavigationActions = mockk(relaxed = true)
    private val vm: FirestoreViewModel = mockk(relaxed = true)
    private val me = Account(uid = "6", name = "Frank")

    /* ---------- semantic matchers ---------- */
    private fun titleField()   = compose.onNodeWithText("Title", substring = true)
    private fun descField()    = compose.onNodeWithText("Description", substring = true)
    private fun searchField()  = compose.onNodeWithText("Add Members", substring = true)
    private fun createBtn()    = compose.onNodeWithText("Create Discussion")
    private fun discardBtn()   = compose.onNodeWithText("Discard")
    private fun backBtn()      = compose.onNodeWithContentDescription("Back")

    /* ---------- helpers ---------- */
    private fun setContent() {
        compose.setContent { AddDiscussionScreen(nav, vm, me) }
    }

    /* ---------- tests ---------- */

    @Test
    fun initial_state_empty_fields_create_disabled() {
        setContent()
        titleField().assertTextContains("")
        descField().assertTextContains("")
        createBtn().assertIsNotEnabled()
    }

    @Test
    fun typing_title_enables_create() {
        setContent()
        titleField().performTextInput("Kotlin")
        createBtn().assertIsEnabled()
    }

    @Test
    fun back_arrow_calls_navigation() {
        setContent()
        backBtn().performClick()
        verify { nav.goBack() }
    }

    @Test
    fun discard_button_calls_navigation() {
        setContent()
        discardBtn().performClick()
        verify { nav.goBack() }
    }

    @Test
    fun search_shows_results_and_adds_member() {
        setContent()
        searchField().performTextInput("bo")
        compose.waitForIdle()
        compose.onNodeWithText("Bob").assertExists().performClick()
        compose.onNodeWithText("Bob").assertExists()
        searchField().assertTextContains("")
    }

    @Test
    fun clear_icon_resets_query() {
        setContent()
        searchField().performTextInput("xyz")
        compose.onNodeWithContentDescription("Clear").performClick()
        searchField().assertTextContains("")
    }

    @Test
    fun remove_member_from_selected_list() {
        setContent()
        searchField().performTextInput("ali")
        compose.waitForIdle()
        compose.onNodeWithText("Alice").performClick()
        compose.onNodeWithContentDescription("Remove").performClick()
        compose.onNodeWithText("Alice").assertDoesNotExist()
    }

    @Test
    fun search_filters_out_current_user() {
        setContent()
        searchField().performTextInput("Fr")
        compose.waitForIdle()
        // Instead of "No results", assert that "Myself" isn't shown
        compose.onAllNodesWithText("Frank").assertCountEquals(0)
    }

    @Test
    fun search_filters_out_already_selected_members() {
        setContent()
        searchField().performTextInput("ali")
        compose.waitForIdle()
        compose.onNodeWithText("Alice").performClick()

        searchField().performTextInput("ali")
        compose.waitForIdle()
        // Instead of "No results", assert that "Alice" is not shown again
        compose.onAllNodesWithText("Alice").assertCountEquals(0)
    }

    @Test
    fun empty_query_closes_dropdown() {
        setContent()
        searchField().performTextInput("x")
        compose.waitForIdle()
        // Results (if any) should appear temporarily
        compose.onAllNodesWithText("x", substring = true)
        searchField().performTextReplacement("")
        compose.waitForIdle()
        // After clearing, no names should appear
        compose.onAllNodesWithText("Alice").assertCountEquals(0)
        compose.onAllNodesWithText("Bob").assertCountEquals(0)
        compose.onAllNodesWithText("Eve").assertCountEquals(0)
    }
}
