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

class AddDiscussionScreenMockedVMTest {
  @get:Rule val compose = createComposeRule()
  private val nav: NavigationActions = mockk(relaxed = true)
  private val me = Account("uid", "Frank", "frank@example.com", "frank")
  private lateinit var mockVm: FirestoreViewModel

  @Before
  fun setup() {
    mockVm = mockk(relaxed = true)
    compose.setContent {
      AddDiscussionScreen(onBack = { nav.goBack() }, onCreate = { nav.goBack() }, mockVm, me)
    }
  }

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
