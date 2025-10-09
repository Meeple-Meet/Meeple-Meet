package com.github.meeplemeet.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.viewmodels.FirestoreViewModel
import com.github.meeplemeet.ui.navigation.NavigationActions
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * Tests for the AddDiscussionScreen UI. Verifies initial state, input behavior, member search, and
 * navigation actions.
 */
class AddDiscussionScreenTest {

  /** Compose test rule for running Compose UI tests */
  @get:Rule
  val compose = createComposeRule()

  /** Mocked navigation actions */
  private val nav: NavigationActions = mockk(relaxed = true)

  /** Mocked ViewModel for testing */
  private val vm: FirestoreViewModel = mockk(relaxed = true)

  /** Current user for the screen */
  private val me = Account(uid = "6", name = "Frank")

  // ---------- Semantic matchers ----------

  /** Returns the title input field node */
  private fun titleField() = compose.onNodeWithText("Title", substring = true)

  /** Returns the description input field node */
  private fun descField() = compose.onNodeWithText("Description", substring = true)

  /** Returns the add members search field node */
  private fun searchField() = compose.onNodeWithText("Add Members", substring = true)

  /** Returns the create discussion button node */
  private fun createBtn() = compose.onNodeWithText("Create Discussion")

  /** Returns the discard button node */
  private fun discardBtn() = compose.onNodeWithText("Discard")

  /** Returns the back button node */
  private fun backBtn() = compose.onNodeWithContentDescription("Back")

  private lateinit var onCreate: suspend (String, String, Account, List<Account>) -> Unit

  // ---------- Helpers ----------

  /** Sets the Compose content for the AddDiscussionScreen */
  private fun setContent() {
    onCreate = { title: String, desc: String, creator: Account, members: List<Account> ->
      vm.createDiscussion(title, desc, creator, *members.toTypedArray())
      nav.goBack()
    }
    compose.setContent {
      AddDiscussionScreen(onBack = { nav.goBack() }, onCreate = onCreate, vm, me)
    }
  }

  // ---------- Tests ----------

  /** Verifies initial state: empty fields and disabled create button */
  @Test
  fun initial_state_empty_fields_create_disabled() {
    setContent()
    titleField().assertTextContains("")
    descField().assertTextContains("")
    createBtn().assertIsNotEnabled()
  }

  /** Verifies typing a title enables the create button */
  @Test
  fun typing_title_enables_create() {
    setContent()
    titleField().performTextInput("Kotlin")
    createBtn().assertIsEnabled()
  }

  /** Verifies back arrow calls navigation.goBack() */
  @Test
  fun back_arrow_calls_navigation() {
    setContent()
    backBtn().performClick()
      verify { nav.goBack() }
  }

  /** Verifies discard button calls navigation.goBack() */
  @Test
  fun discard_button_calls_navigation() {
    setContent()
    discardBtn().performClick()
      verify { nav.goBack() }
  }

  /** Verifies member search displays results and adds a member */
  @Test
  fun search_shows_results_and_adds_member() {
    setContent()
    searchField().performTextInput("bo")
    compose.waitForIdle()
    compose.onNodeWithText("Bob").assertExists().performClick()
    compose.onNodeWithText("Bob").assertExists()
    searchField().assertTextContains("")
  }

  /** Verifies that the clear icon resets the search query */
  @Test
  fun clear_icon_resets_query() {
    setContent()
    searchField().performTextInput("xyz")
    compose.onNodeWithContentDescription("Clear").performClick()
    searchField().assertTextContains("")
  }

  /** Verifies removing a member from the selected list works correctly */
  @Test
  fun remove_member_from_selected_list() {
    setContent()
    searchField().performTextInput("ali")
    compose.waitForIdle()
    compose.onNodeWithText("Alice").performClick()
    compose.onNodeWithContentDescription("Remove").performClick()
    compose.onNodeWithText("Alice").assertDoesNotExist()
  }

  /** Verifies that the current user is filtered out of search results */
  @Test
  fun search_filters_out_current_user() {
    setContent()
    searchField().performTextInput("Fr")
    compose.waitForIdle()
    compose.onAllNodesWithText("Frank").assertCountEquals(0)
  }

  /** Verifies that already selected members are filtered out of search results */
  @Test
  fun search_filters_out_already_selected_members() {
    setContent()
    searchField().performTextInput("ali")
    compose.waitForIdle()
    compose.onNodeWithText("Alice").performClick()

    searchField().performTextInput("ali")
    compose.waitForIdle()
    compose.onAllNodesWithText("Alice").filterToOne(hasAnyAncestor(isPopup())).assertDoesNotExist()
  }

  /** Verifies that clearing the search query closes the dropdown */
  @Test
  fun empty_query_closes_dropdown() {
    setContent()
    searchField().performTextInput("x")
    compose.waitForIdle()
    compose.onAllNodesWithText("x", substring = true)
    searchField().performTextReplacement("")
    compose.waitForIdle()
    compose.onAllNodesWithText("Alice").assertCountEquals(0)
    compose.onAllNodesWithText("Bob").assertCountEquals(0)
    compose.onAllNodesWithText("Eve").assertCountEquals(0)
  }

  /**
   * Verifies that tapping the "Create Discussion" button calls the onCreate callback with the
   * correct title, description, creator, and member list.
   */
  @Test
  fun create_button_calls_viewmodel_createDiscussion() = runTest {
      coEvery { vm.createDiscussion(any(), any(), any()) } just runs
      setContent()
      titleField().performTextInput("New Discussion")
      createBtn().performClick()
      coVerify(exactly = 1) { vm.createDiscussion(any(), any(), any()) }
  }

  /**
   * Verifies that when the createDiscussion lambda throws an exception, the error snackbar with the
   * message "Failed to create discussion" is displayed.
   */
  @Test
  fun createButton_shows_error_snackbar_on_failure() = runTest {
      val failingOnCreate: suspend (String, String, Account, List<Account>) -> Unit =
          { _, _, _, _ ->
              throw Exception("Simulated failure")
          }

      compose.setContent {
          AddDiscussionScreen(
              onBack = {},
              onCreate = failingOnCreate,
              viewModel = vm,
              currentUser = me
          )
      }

      compose.onNodeWithText("Title", substring = true).performTextInput("Test Discussion")

      compose.onNodeWithText("Create Discussion").performClick()

      compose.waitForIdle()

      compose.onNodeWithText("Failed to create discussion").assertExists()
  }
}