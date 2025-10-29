/** Sections of this file were generated using ChatGPT */
package com.github.meeplemeet.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.viewmodels.FirestoreViewModel
import com.github.meeplemeet.ui.navigation.NavigationActions
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for [DiscussionAddScreen] using the real [FirestoreViewModel] and a seeded repository.
 * These tests interact with the real ViewModel to verify full UI behavior including search
 * suggestions and member selection.
 */
@RunWith(AndroidJUnit4::class)
class AddDiscussionScreenTest {

  /** Compose test rule for setting up and interacting with the Compose UI. */
  @get:Rule val compose = createComposeRule()

  /** Mocked navigation actions to verify back and create callbacks. */
  private val nav: NavigationActions = mockk(relaxed = true)

  /** The current logged-in account used in the tests. */
  private lateinit var me: Account

  /** Another account used for selection and search tests. */
  private lateinit var otherAcc: Account

  /* ---------- Semantic helpers for UI nodes ---------- */

  /** Returns the Title input field node. */
  private fun titleField() = compose.onNodeWithText("Title", substring = true)

  /** Returns the Description input field node. */
  private fun descField() = compose.onNodeWithText("Description", substring = true)

  /** Returns the Add Members search field node. */
  private fun searchField() = compose.onNodeWithText("Add Members", substring = true)

  /** Returns the Create Discussion button node. */
  private fun createBtn() = compose.onNodeWithText("Create Discussion")

  /** Returns the Back button node. */
  private fun backBtn() = compose.onNodeWithContentDescription("Back")

  /** Returns the Discard button node. */
  private fun discardBtn() = compose.onNodeWithText("Discard")

  /* ---------- Repository and ViewModel ---------- */

  /** The real Firestore repository used to seed accounts. */
  private lateinit var repo: com.github.meeplemeet.model.repositories.FirestoreRepository

  /** The real ViewModel used in UI tests. */
  private lateinit var realVm: FirestoreViewModel

  /**
   * Sets up the test environment before each test:
   * - Seeds the repository with multiple accounts.
   * - Initializes the real [FirestoreViewModel].
   * - Sets the Compose content with the screen under test.
   */
  @Before
  fun setup() = runBlocking {
    repo = com.github.meeplemeet.model.repositories.FirestoreRepository()
    repo.createAccount("alice", "Alice", "alice@example.com", null)
    repo.createAccount("bob", "Bob", "bob@example.com", null)
    otherAcc = repo.createAccount("john", "John", "john@example.com", null)
    repo.createAccount("johna", "Johna", "johna@example.com", null)
    repo.createAccount("johnny", "Johnny", "johnny@example.com", null)
    me = repo.createAccount("frank", "Frank", "frank@example.com", null)

    realVm = FirestoreViewModel(repo)
    compose.setContent {
      DiscussionAddScreen(
          onBack = { nav.goBack() },
          onCreate = { nav.goBack() },
          viewModel = realVm,
          currentUser = me)
    }
  }

  /* ---------- Original UI Tests ---------- */

  /** Verifies that initially all fields are empty and the create button is disabled. */
  @Test
  fun initial_state_empty_fields_create_disabled() {
    titleField().assertTextContains("")
    descField().assertTextContains("")
    createBtn().assertIsNotEnabled()
  }

  /** Typing a title enables the Create Discussion button. */
  @Test
  fun typing_title_enables_create() {
    titleField().performTextInput("Kotlin")
    createBtn().assertIsEnabled()
  }

  /** Clicking the back arrow triggers the navigation callback. */
  @Test
  fun back_arrow_calls_navigation() {
    backBtn().performClick()
    coVerify { nav.goBack() }
  }

  /** Clicking the discard button triggers the navigation callback. */
  @Test
  fun discard_button_calls_navigation() {
    discardBtn().performClick()
    coVerify { nav.goBack() }
  }

  /** Clears the Add Members search field when clicking the Clear icon. */
  @Test
  fun clear_icon_resets_query() {
    searchField().performTextInput("xyz")
    compose.onNodeWithContentDescription("Clear").performClick()
    searchField().assertTextContains("")
  }

  /** Adds and then removes a member from the selected list. */
  @Test
  fun remove_member_from_selected_list() {
    searchField().performTextInput("ali")
    compose.waitForIdle()
    compose.onNodeWithText("alice").performClick()
    compose.onNodeWithContentDescription("Remove").performClick()
    compose.onNodeWithText("alice").assertDoesNotExist()
  }

  /** Ensures the current user does not appear in the member search results. */
  @Test
  fun search_filters_out_current_user() {
    searchField().performTextInput("fra")
    compose.waitForIdle()
    compose.onAllNodesWithText("frank").assertCountEquals(0)
  }

  /** Ensures already selected members are filtered out of the search dropdown. */
  @Test
  fun search_filters_out_already_selected_members() {
    searchField().performTextInput("ali")
    compose.waitForIdle()
    compose.onNodeWithText("alice").performClick()

    searchField().performTextInput("ali")
    compose.waitForIdle()
    compose.onAllNodesWithText("alice").filterToOne(hasAnyAncestor(isPopup())).assertDoesNotExist()
  }

  /** Verifies that clearing the search field closes the suggestion dropdown. */
  @Test
  fun empty_query_closes_dropdown() {
    searchField().performTextInput("x")
    compose.waitForIdle()
    searchField().performTextReplacement("")
    compose.waitForIdle()
    compose.onAllNodesWithText("alice").assertCountEquals(0)
    compose.onAllNodesWithText("bob").assertCountEquals(0)
    compose.onAllNodesWithText("john").assertCountEquals(0)
  }

  /* ---------- Search-by-handle Tests ---------- */

  /**
   * Tests that entering a search query shows the correct handle suggestions and excludes unrelated
   * users.
   */
  @OptIn(ExperimentalTestApi::class)
  @Test
  fun searchByHandleShowsCorrectHandles() {
    searchField().performTextInput("john")
    compose.waitUntilAtLeastOneExists(
        hasText("john") and hasAnyAncestor(isPopup()), timeoutMillis = 5_000)

    compose.onNode(hasText("john") and hasAnyAncestor(isPopup())).assertExists()
    compose.onNode(hasText("johna") and hasAnyAncestor(isPopup())).assertExists()
    compose.onNode(hasText("johnny") and hasAnyAncestor(isPopup())).assertExists()

    compose.onAllNodesWithText("alice").filterToOne(hasAnyAncestor(isPopup())).assertDoesNotExist()
    compose.onAllNodesWithText("bob").filterToOne(hasAnyAncestor(isPopup())).assertDoesNotExist()
  }

  /** Verifies that a search query with no matching handles returns no suggestions. */
  @Test
  fun searchByHandleEmptyForNoMatch() {
    searchField().performTextInput("xyz")
    compose.waitForIdle()

    compose.onNodeWithText("alice").assertDoesNotExist()
    compose.onNodeWithText("bob").assertDoesNotExist()
    compose.onNodeWithText("john").assertDoesNotExist()
  }

  /**
   * Tests selecting a member from the search suggestions. The selected member should appear in the
   * UI and the search field should reset.
   */
  @OptIn(ExperimentalTestApi::class)
  @Test
  fun searchByHandleSelectsMember() {
    searchField().performTextInput("jo")
    compose.waitUntilAtLeastOneExists(
        hasText("john") and hasAnyAncestor(isPopup()), timeoutMillis = 5_000)
    compose.waitForIdle()
    compose.onNodeWithText("john").performClick()

    compose.onNodeWithText("john").assertExists()
    searchField().assertTextContains("")
  }
}
