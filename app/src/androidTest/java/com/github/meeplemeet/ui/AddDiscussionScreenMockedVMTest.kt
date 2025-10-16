/** Sections of this file were generated using ChatGPT */
package com.github.meeplemeet.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.viewmodels.FirestoreViewModel
import com.github.meeplemeet.ui.navigation.NavigationActions
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * UI tests for [AddDiscussionScreen] using a mocked [FirestoreViewModel].
 *
 * These tests focus on verifying UI interactions and that the ViewModel methods are called
 * correctly without requiring a real backend or repository.
 */
class AddDiscussionScreenMockedVMTest {

  /** Compose test rule to set up and interact with the Compose UI. */
  @get:Rule val compose = createComposeRule()

  /** Mocked navigation actions for testing back and create callbacks. */
  private val nav: NavigationActions = mockk(relaxed = true)

  /** The current account used in the UI tests. */
  private val me = Account("uid", "Frank", "frank@example.com", "frank")

  /** The mocked ViewModel used to verify interactions. */
  private lateinit var mockVm: FirestoreViewModel

  /**
   * Set up the Compose content before each test.
   *
   * Initializes a relaxed mocked [FirestoreViewModel] and sets the [DiscussionAddScreen] content
   * with the mocked ViewModel and account.
   */
  @Before
  fun setup() {
    mockVm = mockk(relaxed = true)
    compose.setContent {
      AddDiscussionScreen(
          onBack = { nav.goBack() },
          onCreate = { nav.goBack() },
          viewModel = mockVm,
          currentUser = me)
    }
  }

  /**
   * Test that typing a title and clicking the "Create Discussion" button triggers the
   * [FirestoreViewModel.createDiscussion] method with the correct parameters.
   *
   * Uses [coEvery] to stub the createDiscussion call and [coVerify] to assert that it was invoked
   * with expected arguments.
   */
  @OptIn(ExperimentalTestApi::class)
  @Test
  fun create_button_calls_viewmodel_createDiscussion() = runTest {
    coEvery { mockVm.createDiscussion(any(), any(), any(), any()) } just runs

    compose.onNodeWithText("Title", substring = true).performTextInput("New Group")
    compose.onNodeWithText("Create Discussion").performClick()
    compose.waitForIdle()

    coVerify { mockVm.createDiscussion(eq("New Group"), eq(""), any()) }
  }
}
