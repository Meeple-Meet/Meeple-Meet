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

@RunWith(AndroidJUnit4::class)
class AddDiscussionScreenTest {

  @get:Rule val compose = createComposeRule()

  private val nav: NavigationActions = mockk(relaxed = true)
  private lateinit var me: Account

  private lateinit var otherAcc: Account

  /* ---------- semantic helpers ---------- */
  private fun titleField() = compose.onNodeWithText("Title", substring = true)

  private fun descField() = compose.onNodeWithText("Description", substring = true)

  private fun searchField() = compose.onNodeWithText("Add Members", substring = true)

  private fun createBtn() = compose.onNodeWithText("Create Discussion")

  private fun backBtn() = compose.onNodeWithContentDescription("Back")

  private fun discardBtn() = compose.onNodeWithText("Discard")

  /* ---------- seeded repository + real VM ---------- */
  private lateinit var repo: com.github.meeplemeet.model.repositories.FirestoreRepository
  private lateinit var realVm: FirestoreViewModel

  @Before
  fun setup() = runBlocking {
    repo = com.github.meeplemeet.model.repositories.FirestoreRepository()
    /* seed accounts whose HANDLES we will search */
    repo.createAccount("alice", "Alice", "alice@example.com", null)
    repo.createAccount("bob", "Bob", "bob@example.com", null)
    otherAcc = repo.createAccount("john", "John", "john@example.com", null)
    repo.createAccount("johna", "Johna", "johna@example.com", null)
    repo.createAccount("johnny", "Johnny", "johnny@example.com", null)
    me = repo.createAccount("frank", "Frank", "frank@example.com", null)

    realVm = FirestoreViewModel(repo)
    compose.setContent {
      AddDiscussionScreen(onBack = { nav.goBack() }, onCreate = { nav.goBack() }, realVm, me)
    }
  }

  /* ---------- ORIGINAL UI TESTS (unchanged) ---------- */
  @Test
  fun initial_state_empty_fields_create_disabled() {
    titleField().assertTextContains("")
    descField().assertTextContains("")
    createBtn().assertIsNotEnabled()
  }

  @Test
  fun typing_title_enables_create() {
    titleField().performTextInput("Kotlin")
    createBtn().assertIsEnabled()
  }

  @Test
  fun back_arrow_calls_navigation() {
    backBtn().performClick()
    coVerify { nav.goBack() }
  }

  @Test
  fun discard_button_calls_navigation() {
    discardBtn().performClick()
    coVerify { nav.goBack() }
  }

  @Test
  fun clear_icon_resets_query() {
    searchField().performTextInput("xyz")
    compose.onNodeWithContentDescription("Clear").performClick()
    searchField().assertTextContains("")
  }

  @Test
  fun remove_member_from_selected_list() {
    searchField().performTextInput("ali")
    compose.waitForIdle()
    compose.onNodeWithText("alice").performClick()
    compose.onNodeWithContentDescription("Remove").performClick()
    compose.onNodeWithText("alice").assertDoesNotExist()
  }

  @Test
  fun search_filters_out_current_user() {
    searchField().performTextInput("fra")
    compose.waitForIdle()
    compose.onAllNodesWithText("frank").assertCountEquals(0)
  }

  @Test
  fun search_filters_out_already_selected_members() {
    searchField().performTextInput("ali")
    compose.waitForIdle()
    compose.onNodeWithText("alice").performClick()

    searchField().performTextInput("ali")
    compose.waitForIdle()
    compose.onAllNodesWithText("alice").filterToOne(hasAnyAncestor(isPopup())).assertDoesNotExist()
  }

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

  /* ---------- NEW SEARCH-BY-HANDLE TESTS ---------- */
  @OptIn(ExperimentalTestApi::class)
  @Test
  fun searchByHandleShowsCorrectHandles() {
    searchField().performTextInput("john")

    /* wait until at least one suggestion appears (max 5 s) */
    compose.waitUntilAtLeastOneExists(
        hasText("john") and hasAnyAncestor(isPopup()), timeoutMillis = 5_000)

    /* now assert inside the dropdown only */
    compose.onNode(hasText("john") and hasAnyAncestor(isPopup())).assertExists()
    compose.onNode(hasText("johna") and hasAnyAncestor(isPopup())).assertExists()
    compose.onNode(hasText("johnny") and hasAnyAncestor(isPopup())).assertExists()

    /* alice & bob must NOT be inside the dropdown */
    compose.onAllNodesWithText("alice").filterToOne(hasAnyAncestor(isPopup())).assertDoesNotExist()
    compose.onAllNodesWithText("bob").filterToOne(hasAnyAncestor(isPopup())).assertDoesNotExist()
  }

  @Test
  fun searchByHandleEmptyForNoMatch() {
    searchField().performTextInput("xyz")
    compose.waitForIdle()

    compose.onNodeWithText("alice").assertDoesNotExist()
    compose.onNodeWithText("bob").assertDoesNotExist()
    compose.onNodeWithText("john").assertDoesNotExist()
  }

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
