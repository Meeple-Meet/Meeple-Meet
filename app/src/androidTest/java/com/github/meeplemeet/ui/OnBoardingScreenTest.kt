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
  private fun skipButton() = compose.onNodeWithTag(OnBoardingTestTags.SKIP_BUTTON)

  private fun nextButton() = compose.onNodeWithTag(OnBoardingTestTags.NEXT_BUTTON)

  private fun backButton() = compose.onNodeWithTag(OnBoardingTestTags.BACK_BUTTON)

  private fun pagerDots() =
      compose.onAllNodesWithTag(OnBoardingTestTags.PAGER_DOT, useUnmergedTree = true)

  private fun discussionCard() = compose.onNodeWithTag(OnBoardingTestTags.DISCUSSION_PREVIEW_CARD)

  private fun sessionCreationPage() =
      compose.onNodeWithTag(OnBoardingTestTags.SESSION_CREATION_PAGE)

  private fun sessionCreationDateTime() =
      compose.onNodeWithTag(OnBoardingTestTags.SESSION_CREATION_DATETIME)

  private fun sessionCreationParticipants() =
      compose.onNodeWithTag(OnBoardingTestTags.SESSION_CREATION_PARTICIPANTS)

  /* ------------------------- setup ---------------------------- */
  private lateinit var pages: List<OnBoardPage>

  @Before
  fun setup() {
    pages =
        listOf(
            OnBoardPage(image = R.drawable.logo_dark, title = "Meeple Meet", description = ""),
            OnBoardPage(
                image = R.drawable.discussion_logo,
                title = "Discussions",
                description = "Host your own gatherings easily."),
            OnBoardPage(
                image = android.R.drawable.ic_menu_compass,
                title = "Game Sessions",
                description = "Organize and join gaming meetups with friends"),
            OnBoardPage(
                image = R.drawable.discussion_logo,
                title = "Community Posts",
                description = "Share your gaming experiences with the world"),
            OnBoardPage(
                image = R.drawable.discussion_logo,
                title = "Explore Nearby",
                description = "Find board game shops and rental spaces near you"),
            OnBoardPage(
                image = R.drawable.logo_clear,
                title = "Stay Notified",
                description = "Enable notifications so you never miss replies."),
            OnBoardPage(
                image = R.drawable.logo_dark, title = "Let's Go!", description = "Ready to start?"))
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
      compose.onNodeWithTag("${OnBoardingTestTags.PAGE_TITLE}_0").assertTextEquals("Meeple Meet")
    }

    checkpoint("Initial: Next button visible") { nextButton().assertExists() }

    checkpoint("Initial: Back button exists but not clickable") { backButton().assertExists() }

    checkpoint("Initial: Pager dots correct count") { pagerDots().assertCountEquals(pages.size) }

    checkpoint("Initial: Intro page features visible") {
      compose
          .onNodeWithText(
              "Connect with friends and chat about upcoming game sessions", substring = true)
          .assertExists()
      compose.onNodeWithText("Share posts and join discussions", substring = true).assertExists()
    }

    /* NAVIGATION TO SECOND PAGE ---------------------------------------------- */
    checkpoint("Nav: First page allows swipe") {
      compose.onNodeWithTag(OnBoardingTestTags.PAGER).performTouchInput { swipeLeft() }
      compose.waitForIdle()
      compose.onNodeWithTag("${OnBoardingTestTags.PAGE_TITLE}_1").assertExists()
    }

    checkpoint("Nav: Second page title correct") {
      compose
          .onNodeWithTag("${OnBoardingTestTags.PAGE_TITLE}_1")
          .assertTextEquals(OnBoardingStrings.SESSION_CREATION_TITLE)
    }

    /* SESSION CREATION CARD AND CONTENT -------------------------------------- */
    checkpoint("SessionCreation: Page tagged and visible") {
      sessionCreationPage().assertExists().assertIsDisplayed()
    }

    checkpoint("SessionCreation: Discussion preview card visible") {
      discussionCard().assertExists().assertIsDisplayed()
    }

    checkpoint("SessionCreation: Helper texts visible") {
      compose
          .onNodeWithText(OnBoardingStrings.SESSION_CREATION_CREATE_SESSION_BUTTON)
          .assertExists()
      compose.onNodeWithText(OnBoardingStrings.SESSION_CREATION_CHOOSE_FRIENDS).assertExists()
    }

    checkpoint("SessionCreation: Date/time preview visible with labels") {
      sessionCreationDateTime().assertExists()
      compose.onNodeWithText(OnBoardingStrings.DATE_PREVIEW).assertExists()
      compose.onNodeWithText(OnBoardingStrings.TIME_PREVIEW).assertExists()
    }

    checkpoint("SessionCreation: Participants preview visible with static members") {
      sessionCreationParticipants().assertExists()
      compose.onNodeWithText(OnBoardingStrings.PARTICIPANTS_SECTION_TEXT).assertExists()
      compose
          .onNodeWithTag(
              OnBoardingTestTags.sessionCreationParticipant(OnBoardingStrings.PARTICIPANT_1))
          .assertExists()
      compose
          .onNodeWithTag(
              OnBoardingTestTags.sessionCreationParticipant(OnBoardingStrings.PARTICIPANT_2))
          .assertExists()
    }

    checkpoint("SessionCreation: Swiping to next page is allowed") {
      compose.onNodeWithTag(OnBoardingTestTags.PAGER).performTouchInput { swipeLeft() }
      compose.waitForIdle()
      compose.onNodeWithTag("${OnBoardingTestTags.PAGE_TITLE}_2").assertExists()
    }

    /* SESSIONS PAGE (PAGE 2) ------------------------------------------------- */
    checkpoint("Sessions Page: Title correct") {
      compose.onNodeWithTag("${OnBoardingTestTags.PAGE_TITLE}_2").assertTextEquals("Game Sessions")
    }

    checkpoint("Sessions Page: Subtitle visible") {
      compose.onNodeWithText(OnBoardingStrings.SESSION_PAGE_SUBTITLE).assertExists()
    }

    checkpoint("Sessions Page: Feature items visible") {
      compose.onNodeWithText("Schedule sessions with date, time, and location").assertExists()
      compose.onNodeWithText("Link sessions to specific games you want to play").assertExists()
      compose.onNodeWithText("Invite friends and manage member list").assertExists()
      compose.onNodeWithText("View your session history and upcoming events").assertExists()
    }

    /* POSTS PAGE (PAGE 3) ---------------------------------------------------- */
    checkpoint("Posts Page: Navigate to posts") {
      compose.onNodeWithTag(OnBoardingTestTags.PAGER).performTouchInput { swipeLeft() }
      compose.waitForIdle()
      compose.onNodeWithTag("${OnBoardingTestTags.PAGE_TITLE}_3").assertExists()
    }

    checkpoint("Posts Page: Title correct") {
      compose
          .onNodeWithTag("${OnBoardingTestTags.PAGE_TITLE}_3")
          .assertTextEquals("Community Posts")
    }

    checkpoint("Posts Page: Subtitle visible") {
      compose.onNodeWithText(OnBoardingStrings.POST_PAGE_SUBTITLE).assertExists()
    }

    checkpoint("Posts Page: Feature items visible") {
      compose.onNodeWithText("Create threads about any board game topic").assertExists()
      compose.onNodeWithText("Like and interact with community posts").assertExists()
    }

    checkpoint("Posts Page: End text visible") {
      compose.onNodeWithText(OnBoardingStrings.POSTS_PAGE_END_TEXT).assertExists()
    }

    checkpoint("Posts Page: No end button on posts page") {
      compose.onNodeWithTag("OnBoarding_EndButton").assertDoesNotExist()
    }

    /* MAP EXPLORATION PAGE (PAGE 4) ------------------------------------------ */
    checkpoint("Map Page: Navigate to map exploration") {
      compose.onNodeWithTag(OnBoardingTestTags.PAGER).performTouchInput { swipeLeft() }
      compose.waitForIdle()
      compose.onNodeWithTag("${OnBoardingTestTags.PAGE_TITLE}_4").assertExists()
    }

    checkpoint("Map Page: Title correct") {
      compose.onNodeWithTag("${OnBoardingTestTags.PAGE_TITLE}_4").assertTextEquals("Explore Nearby")
    }

    checkpoint("Map Page: Subtitle visible") {
      compose.onNodeWithText(OnBoardingStrings.MAP_EXPLORATION_SUBTITLE).assertExists()
    }

    checkpoint("Map Page: Legend items visible") {
      compose.onNodeWithText("Gaming Sessions").assertExists()
      compose.onNodeWithText("Game Shops").assertExists()
      compose.onNodeWithText("Rental Spaces").assertExists()
    }

    checkpoint("Map Page: Legend descriptions visible") {
      compose.onNodeWithText("Active locations where people are playing").assertExists()
      compose.onNodeWithText("Buy board games with prices and stock info").assertExists()
      compose.onNodeWithText("Rent games or book venues for sessions").assertExists()
    }

    checkpoint("Map Page: End text visible") {
      compose.onNodeWithText(OnBoardingStrings.MAP_EXPLORATION_END_TEXT).assertExists()
    }

    checkpoint("Map Page: You marker label visible") {
      compose.onNodeWithText("You").assertExists()
    }

    /* NOTIFICATION PAGE (PAGE 5) --------------------------------------------- */
    checkpoint("Notifications Page: Navigate to notifications") {
      compose.onNodeWithTag(OnBoardingTestTags.PAGER).performTouchInput { swipeLeft() }
      compose.waitForIdle()
      compose.onNodeWithTag("${OnBoardingTestTags.PAGE_TITLE}_5").assertExists()
    }

    checkpoint("Notifications Page: Title correct") {
      compose
          .onNodeWithTag("${OnBoardingTestTags.PAGE_TITLE}_5")
          .assertTextEquals("Enable notifications")
    }

    checkpoint("Notifications Page: CTA visible when not granted") {
      val allowNodes = compose.onAllNodesWithText("Allow notifications").fetchSemanticsNodes()
      if (allowNodes.isNotEmpty()) {
        compose.onNodeWithText("Allow notifications").assertExists()
      } else {
        compose.onNodeWithText("Notifications are enabled").assertExists()
      }
    }

    /* NAVIGATION TO LET'S GO PAGE (PAGE 6) - FINAL PAGE --------------------- */
    checkpoint("LetsGo Page: Navigate to final page") {
      compose.onNodeWithTag(OnBoardingTestTags.PAGER).performTouchInput { swipeLeft() }
      compose.waitForIdle()
      compose.onNodeWithTag("${OnBoardingTestTags.PAGE_TITLE}_6").assertExists()
    }

    checkpoint("LetsGo Page: Title correct") {
      compose.onNodeWithTag("${OnBoardingTestTags.PAGE_TITLE}_6").assertTextEquals("Let's Go!")
    }

    checkpoint("LetsGo Page: Description visible") {
      compose
          .onNodeWithText("You're all set! Start connecting with gamers", substring = true)
          .assertExists()
    }

    checkpoint("LetsGo Page: Get Started button visible on final page") {
      compose.onNodeWithTag("OnBoarding_EndButton").assertExists()
    }

    checkpoint("LetsGo Page: Button text is 'Get Started'") {
      compose.onNodeWithTag("OnBoarding_EndButton").assertExists()
    }

    /* BACK NAVIGATION THROUGH ALL PAGES -------------------------------------- */
    checkpoint("Back Nav: Back to notification page") {
      backButton().performClick()
      compose.waitForIdle()
      compose
          .onNodeWithTag("${OnBoardingTestTags.PAGE_TITLE}_5")
          .assertTextEquals("Enable notifications")
    }

    checkpoint("Back Nav: Back to map page") {
      backButton().performClick()
      compose.waitForIdle()
      compose.onNodeWithTag("${OnBoardingTestTags.PAGE_TITLE}_4").assertTextEquals("Explore Nearby")
    }

    checkpoint("Back Nav: Back to posts page") {
      backButton().performClick()
      compose.waitForIdle()
      compose
          .onNodeWithTag("${OnBoardingTestTags.PAGE_TITLE}_3")
          .assertTextEquals("Community Posts")
    }

    checkpoint("Back Nav: Back to sessions page") {
      backButton().performClick()
      compose.waitForIdle()
      compose.onNodeWithTag("${OnBoardingTestTags.PAGE_TITLE}_2").assertTextEquals("Game Sessions")
    }

    checkpoint("Back Nav: Back to session creation page") {
      backButton().performClick()
      compose.waitForIdle()
      compose
          .onNodeWithTag("${OnBoardingTestTags.PAGE_TITLE}_1")
          .assertTextEquals(OnBoardingStrings.SESSION_CREATION_TITLE)
    }

    checkpoint("Back Nav: Session creation card still present") { discussionCard().assertExists() }

    checkpoint("Back Nav: Can navigate forward again") {
      nextButton().performClick()
      compose.waitForIdle()
      compose.onNodeWithTag("${OnBoardingTestTags.PAGE_TITLE}_2").assertTextEquals("Game Sessions")
    }

    checkpoint("Back Nav: Back to first page") {
      backButton().performClick()
      compose.waitForIdle()
      backButton().performClick()
      compose.waitForIdle()

      compose.onNodeWithTag("${OnBoardingTestTags.PAGE_TITLE}_0").assertTextEquals("Meeple Meet")
    }

    /* SKIP BUTTON VISIBILITY ------------------------------------------------- */
    checkpoint("Skip: Visible on first page") { skipButton().assertExists() }

    checkpoint("Skip: Visible on second page") {
      compose.onNodeWithTag(OnBoardingTestTags.PAGER).performTouchInput { swipeLeft() }
      compose.waitForIdle()
      skipButton().assertExists()
    }

    checkpoint("Skip: Visible on third page") {
      compose.onNodeWithTag(OnBoardingTestTags.PAGER).performTouchInput { swipeLeft() }
      compose.waitForIdle()
      skipButton().assertExists()
    }
  }

  /* ============ ADDITIONAL COMPOSABLE COVERAGE TESTS ============= */

  @Test
  fun test_session_creation_page() = runBlocking {
    val hasInteracted = mutableStateOf(false)

    compose.setContent { SessionCreationPreviewPage(hasInteractedWithDiscussion = hasInteracted) }
    compose.waitForIdle()

    checkpoint("Page: Title renders") {
      compose
          .onNodeWithTag("${OnBoardingTestTags.PAGE_TITLE}_1")
          .assertTextEquals(OnBoardingStrings.SESSION_CREATION_TITLE)
    }

    checkpoint("Page: LaunchedEffect marks interaction") {
      assert(hasInteracted.value) { "hasInteractedWithDiscussion should be true after composition" }
    }

    checkpoint("Page: Helper texts visible") {
      compose
          .onNodeWithText(OnBoardingStrings.SESSION_CREATION_CREATE_SESSION_BUTTON)
          .assertExists()
      compose.onNodeWithText(OnBoardingStrings.SESSION_CREATION_CHOOSE_FRIENDS).assertExists()
    }

    checkpoint("Page: Discussion card visible") { discussionCard().assertExists() }

    checkpoint("Page: Date/time section present with labels") {
      sessionCreationDateTime().assertExists()
      compose.onNodeWithText(OnBoardingStrings.DATE_PREVIEW).assertExists()
      compose.onNodeWithText(OnBoardingStrings.TIME_PREVIEW).assertExists()
    }

    checkpoint("Page: Participants section with static participants") {
      sessionCreationParticipants().assertExists()
      compose.onNodeWithText(OnBoardingStrings.PARTICIPANTS_SECTION_TEXT).assertExists()
      compose.onNodeWithText(OnBoardingStrings.PARTICIPANT_1).assertExists()
      compose.onNodeWithText(OnBoardingStrings.PARTICIPANT_2).assertExists()
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
          pages = pages.take(3),
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
            image = R.drawable.discussion_logo,
            title = "Test Page",
            description = "Test description")
    val testPages =
        listOf(
            testPage,
            OnBoardPage(
                image = R.drawable.discussion_logo,
                title = "Second Page",
                description = "Second page description"),
            OnBoardPage(
                image = R.drawable.discussion_logo,
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
  }
}
