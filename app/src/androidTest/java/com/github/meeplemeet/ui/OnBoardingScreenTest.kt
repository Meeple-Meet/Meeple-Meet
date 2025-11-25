package com.github.meeplemeet.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.meeplemeet.R
import com.github.meeplemeet.ui.auth.OnBoardPage
import com.github.meeplemeet.ui.auth.OnBoardingScreen
import com.github.meeplemeet.ui.auth.OnBoardingTestTags
import com.github.meeplemeet.utils.Checkpoint
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnBoardingScreenTest {

  @get:Rule val compose = createComposeRule()
  @get:Rule val ck = Checkpoint.Rule()

  private fun checkpoint(name: String, block: () -> Unit) = ck.ck(name, block)

  /* ---------------- semantic helpers ---------------- */
  private fun skipButton() = compose.onNodeWithTag(OnBoardingTestTags.SkipButton)

  private fun nextButton() = compose.onNodeWithTag(OnBoardingTestTags.NextButton)

  private fun backButton() = compose.onNodeWithTag(OnBoardingTestTags.BackButton)

  private fun pagerDots() =
      compose.onAllNodesWithTag(OnBoardingTestTags.PagerDot, useUnmergedTree = true)

  /* ------------------------- setup ---------------------------- */
  private var currentPage by mutableStateOf(0)
  private lateinit var pages: List<OnBoardPage>

  @Before
  fun setup() = runBlocking {
    pages =
        listOf(
            OnBoardPage(
                image = R.drawable.logo_discussion,
                title = "Welcome",
                description = "Discover events and meet new people."),
            OnBoardPage(
                image = android.R.drawable.ic_menu_camera,
                title = "Create",
                description = "Host your own gatherings easily."),
            OnBoardPage(
                image = android.R.drawable.ic_menu_compass,
                title = "Explore",
                description = "Find activities near you."))

    compose.setContent {
      OnBoardingScreen(
          pages = pages, onSkip = { /* skip callback */}, onFinished = { /* finished callback */})
    }
  }

  /* ---------------------- tests ------------------------------- */
  @Test
  fun full_smoke_onboarding_screen() = runBlocking {

    /* 1  Initial state ------------------------------------------------------------- */
    checkpoint("Skip button visible") { skipButton().assertExists() }
    checkpoint("First page title visible") {
      compose.onNodeWithTag("${OnBoardingTestTags.PageTitle}_0").assertTextEquals("Welcome")
    }

    /* 2  Next button enabled only after interaction on first page ----------------- */
    checkpoint("Next button visible") { nextButton().assertExists() }

    /* 3  Pager dots visible and first selected ------------------------------------ */
    checkpoint("Pager dots initial state") { pagerDots().assertCountEquals(pages.size) }

    /* 4  Navigate to second page --------------------------------------------------- */
    // Click discussion and close dialog to enable swipe
    compose.onNodeWithTag(OnBoardingTestTags.DiscussionPreviewCard).performClick()
    compose.onNodeWithTag(OnBoardingTestTags.CloseDialog).performClick()

    // Swipe to second page
    compose.onNodeWithTag(OnBoardingTestTags.Pager).performTouchInput { swipeLeft() }

    // Wait until Compose sees the second page
    compose.onNodeWithTag("${OnBoardingTestTags.PageTitle}_1").assertTextEquals("Create")
    checkpoint("Second page visible") {
      compose.onNodeWithTag("${OnBoardingTestTags.PageTitle}_1").assertTextEquals("Create")
    }

    /* 5  Back button works -------------------------------------------------------- */
    backButton().performClick()
    compose.waitForIdle()
    checkpoint("Back to first page") {
      compose.onNodeWithTag("${OnBoardingTestTags.PageTitle}_0").assertTextEquals("Welcome")
    }

    /* 6  Navigate to last page ---------------------------------------------------- */
    // Swipe to second page and then to last page
    compose.onNodeWithTag(OnBoardingTestTags.Pager).performTouchInput { swipeLeft() }
    compose.onNodeWithTag(OnBoardingTestTags.Pager).performTouchInput { swipeLeft() }

    // Wait until Compose sees the last page
    compose.onNodeWithTag("${OnBoardingTestTags.PageTitle}_2").assertTextEquals("Explore")
    checkpoint("Last page visible") {
      compose.onNodeWithTag("${OnBoardingTestTags.PageTitle}_2").assertTextEquals("Explore")
    }

    /* 7  Skip button always visible ------------------------------------------------ */
    checkpoint("Skip button still visible") { skipButton().assertExists() }
  }
}
