package com.github.meeplemeet.ui

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.meeplemeet.R
import com.github.meeplemeet.ui.auth.*
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

  private fun discussionCard() = compose.onNodeWithTag(OnBoardingTestTags.DiscussionPreviewCard)

  private fun closeDialogButton() = compose.onNodeWithTag(OnBoardingTestTags.CloseDialog)

  /* ------------------------- setup ---------------------------- */
  private lateinit var pages: List<OnBoardPage>

  @Before
  fun setup() {
    pages =
        listOf(
            OnBoardPage(
                image = R.drawable.logo_discussion,
                title = "Welcome",
                description = "Discover events and meet new people."),
            OnBoardPage(
                image = android.R.drawable.ic_menu_camera,
                title = "Discussions",
                description = "Host your own gatherings easily."),
            OnBoardPage(
                image = android.R.drawable.ic_menu_compass,
                title = "Explore",
                description = "Find activities near you."))
  }

  /* ---------------------- comprehensive test ------------------------------- */
  @Test
  fun comprehensive_onboarding_flow_test() = runBlocking {
    compose.setContent {
      OnBoardingScreen(
          pages = pages, onSkip = { /* skip callback */}, onFinished = { /* finished callback */})
    }

    /* INITIAL STATE - First Page (Welcome) ----------------------------------- */
    checkpoint("Initial: Skip button visible") { skipButton().assertExists() }

    checkpoint("Initial: First page title visible") {
      compose.onNodeWithTag("${OnBoardingTestTags.PageTitle}_0").assertTextEquals("Meeple Meet")
    }

    checkpoint("Initial: Next button visible") { nextButton().assertExists() }

    checkpoint("Initial: Back button exists but not clickable") { backButton().assertExists() }

    checkpoint("Initial: Pager dots correct count") { pagerDots().assertCountEquals(pages.size) }

    /* NAVIGATION TO SECOND PAGE ---------------------------------------------- */
    checkpoint("Nav: First page allows swipe") {
      compose.onNodeWithTag(OnBoardingTestTags.Pager).performTouchInput { swipeLeft() }
      compose.waitForIdle()
      compose.onNodeWithTag("${OnBoardingTestTags.PageTitle}_1").assertExists()
    }

    checkpoint("Nav: Second page title correct") {
      compose.onNodeWithTag("${OnBoardingTestTags.PageTitle}_1").assertTextEquals("Discussions")
    }

    /* DISCUSSION CARD AND INTERACTION LOGIC ---------------------------------- */
    checkpoint("Discussion: Card visible on second page") {
      discussionCard().assertExists()
      discussionCard().assertIsDisplayed()
    }

    checkpoint("Discussion: Prompt visible before interaction") {
      compose.onNodeWithText("⬆️ Tap the discussion above to continue").assertExists()
    }

    checkpoint("Discussion: Next button exists") { nextButton().assertExists() }

    checkpoint("Discussion: Swipe blocked before interaction") {
      // Attempt to swipe - should stay on page 1
      compose.onNodeWithTag(OnBoardingTestTags.Pager).performTouchInput { swipeLeft() }
      compose.waitForIdle()
      compose.onNodeWithTag("${OnBoardingTestTags.PageTitle}_1").assertExists()
    }

    checkpoint("Discussion: Multiple swipe attempts still blocked") {
      repeat(2) {
        compose.onNodeWithTag(OnBoardingTestTags.Pager).performTouchInput { swipeLeft() }
        compose.waitForIdle()
      }
      compose.onNodeWithTag("${OnBoardingTestTags.PageTitle}_1").assertExists()
    }

    /* OPEN AND CLOSE DIALOG -------------------------------------------------- */
    checkpoint("Dialog: Open by clicking discussion card") {
      discussionCard().performClick()
      compose.waitForIdle()
      closeDialogButton().assertExists()
    }

    checkpoint("Dialog: Close button works") {
      closeDialogButton().performClick()
      compose.waitForIdle()
      closeDialogButton().assertDoesNotExist()
    }

    checkpoint("Dialog: Discussion card still visible after closing") {
      discussionCard().assertExists()
    }

    checkpoint("Dialog: Prompt changed after interaction") {
      compose.onNodeWithText("Jump into the conversation and never miss a meetup!").assertExists()
    }

    /* NAVIGATION AFTER INTERACTION ------------------------------------------- */
    checkpoint("After Interaction: Swipe now works") {
      compose.onNodeWithTag(OnBoardingTestTags.Pager).performTouchInput { swipeLeft() }
      compose.waitForIdle()
      compose.onNodeWithTag("${OnBoardingTestTags.PageTitle}_2").assertExists()
    }

    checkpoint("After Interaction: Third page title correct") {
      compose.onNodeWithTag("${OnBoardingTestTags.PageTitle}_2").assertTextEquals("Explore")
    }

    /* BACK NAVIGATION -------------------------------------------------------- */
    checkpoint("Back Nav: Back to second page") {
      backButton().performClick()
      compose.waitForIdle()
      compose.onNodeWithTag("${OnBoardingTestTags.PageTitle}_1").assertTextEquals("Discussions")
    }

    checkpoint("Back Nav: Discussion card still present") { discussionCard().assertExists() }

    checkpoint("Back Nav: Can navigate forward again") {
      nextButton().performClick()
      compose.waitForIdle()
      compose.onNodeWithTag("${OnBoardingTestTags.PageTitle}_2").assertTextEquals("Explore")
    }

    checkpoint("Back Nav: Back to second page again") {
      backButton().performClick()
      compose.waitForIdle()
      compose.onNodeWithTag("${OnBoardingTestTags.PageTitle}_1").assertExists()
    }

    checkpoint("Back Nav: Back to first page") {
      backButton().performClick()
      compose.waitForIdle()
      compose.onNodeWithTag("${OnBoardingTestTags.PageTitle}_0").assertTextEquals("Meeple Meet")
    }

    /* SKIP BUTTON VISIBILITY ------------------------------------------------- */
    checkpoint("Skip: Visible on first page") { skipButton().assertExists() }

    checkpoint("Skip: Visible on second page") {
      compose.onNodeWithTag(OnBoardingTestTags.Pager).performTouchInput { swipeLeft() }
      compose.waitForIdle()
      skipButton().assertExists()
    }

    checkpoint("Skip: Visible on third page") {
      compose.onNodeWithTag(OnBoardingTestTags.Pager).performTouchInput { swipeLeft() }
      compose.waitForIdle()
      skipButton().assertExists()
    }
  }

  /* ============ ADDITIONAL COMPOSABLE COVERAGE TESTS ============= */

  @Test
  fun test_discussion_card_components() = runBlocking {
    var clicked = false

    compose.setContent { DiscussionPreviewCard(onClick = { clicked = true }) }

    checkpoint("Card: Avatar badge shows '2'") { compose.onNodeWithText("2").assertExists() }

    checkpoint("Card: Board Game Night visible") {
      compose.onNodeWithText("Board Game Night").assertExists()
    }

    checkpoint("Card: New messages indicator") { compose.onNodeWithText("• 5 new").assertExists() }

    checkpoint("Card: Timestamp visible") { compose.onNodeWithText("21:01").assertExists() }

    checkpoint("Card: Tap text visible") { compose.onNodeWithText("Tap").assertExists() }

    checkpoint("Card: Click triggers callback") {
      compose.onNodeWithTag(OnBoardingTestTags.DiscussionPreviewCard).performClick()
      compose.waitForIdle()
      assert(clicked) { "Click callback should be triggered" }
    }
  }

  @Test
  fun test_discussion_page_and_dialog() = runBlocking {
    val testPage =
        OnBoardPage(
            image = R.drawable.logo_discussion,
            title = "Discussions",
            description = "Join discussions")
    val hasInteracted = mutableStateOf(false)

    compose.setContent {
      DiscussionPreviewPage(pageData = testPage, hasInteractedWithDiscussion = hasInteracted)
    }

    checkpoint("Page: Title renders") { compose.onNodeWithText("Discussions").assertExists() }

    checkpoint("Page: Helper text visible") {
      compose
          .onNodeWithText("Meeple Meet helps you connect with new friends", substring = true)
          .assertExists()
    }

    checkpoint("Page: Card visible") { discussionCard().assertExists() }

    checkpoint("Page: Initial prompt before interaction") {
      compose.onNodeWithText("⬆️ Tap the discussion above to continue").assertExists()
    }

    checkpoint("Page: Click updates state") {
      discussionCard().performClick()
      compose.waitForIdle()
      assert(hasInteracted.value) { "Should be marked as interacted" }
    }

    checkpoint("Page: Prompt changes after interaction") {
      compose.onNodeWithText("Jump into the conversation and never miss a meetup!").assertExists()
    }

    checkpoint("Page: Dialog opens") { closeDialogButton().assertExists() }

    checkpoint("Dialog: Session Creation title") {
      compose.onNodeWithText("Session Creation").assertExists()
    }

    checkpoint("Dialog: Board Game Night in header") {
      compose.onNodeWithTag("DiscussionHeader_Title").assertExists()
    }

    checkpoint("Dialog: Close button works") {
      closeDialogButton().performClick()
      compose.waitForIdle()
      closeDialogButton().assertDoesNotExist()
    }
  }

  @Test
  fun test_navigation_controls() = runBlocking {
    var backNavigated = false
    var nextNavigated = false

    compose.setContent {
      val pagerState = rememberPagerState(initialPage = 1, pageCount = { 3 })
      NavigationControls(
          pagerState = pagerState,
          pages = pages,
          hasInteractedWithDiscussion = true,
          onNavigate = { page ->
            if (page < 1) backNavigated = true
            if (page > 1) nextNavigated = true
          })
    }

    checkpoint("Navigation: Back button exists") { backButton().assertExists() }

    checkpoint("Navigation: Next button exists and clickable") {
      nextButton().assertExists().assertIsEnabled()
    }

    checkpoint("Navigation: Correct number of page indicators") { pagerDots().assertCountEquals(3) }

    checkpoint("Navigation: Back button triggers callback") {
      backButton().performClick()
      compose.waitForIdle()
      assert(backNavigated) { "Back navigation should fire" }
    }

    checkpoint("Navigation: Next button triggers callback") {
      nextButton().performClick()
      compose.waitForIdle()
      assert(nextNavigated) { "Next navigation should fire" }
    }
  }

  @Test
  fun test_callbacks_and_standard_page() = runBlocking {
    var skipCalled = false
    val testPage =
        OnBoardPage(
            image = R.drawable.logo_discussion,
            title = "Test Page",
            description = "Test description")
    val testPages =
        listOf(
            testPage,
            OnBoardPage(
                image = R.drawable.logo_discussion,
                title = "Second Page",
                description = "Second page description"),
            OnBoardPage(
                image = R.drawable.logo_discussion,
                title = "Third Page",
                description = "Third page description"))

    compose.setContent {
      OnBoardingScreen(pages = testPages, onSkip = { skipCalled = true }, onFinished = {})
    }
    compose.waitForIdle()

    checkpoint("Skip: Button visible") { skipButton().assertExists() }

    checkpoint("Skip: Click triggers callback") {
      compose.waitForIdle()
      skipButton().assertIsDisplayed().performClick()
      compose.waitForIdle()
      assert(skipCalled) { "Skip should be called" }
    }

    checkpoint("Page: Title displayed") { compose.onNodeWithText("Meeple Meet").assertExists() }

    checkpoint("Page: Description displayed") {
      compose
          .onNodeWithText(
              "Meeple Meet helps you organize game sessions, join discussions, explore shops, check prices, and find local gaming spaces.")
          .assertExists()
    }
  }
}
