package com.github.meeplemeet.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.meeplemeet.MeepleMeetApp
import com.github.meeplemeet.model.repositories.FirestoreRepository
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.viewmodels.AuthViewModel
import com.github.meeplemeet.model.viewmodels.FirestoreViewModel
import com.github.meeplemeet.ui.navigation.NavigationTestTags
import com.github.meeplemeet.utils.FirestoreTests
import com.github.meeplemeet.utils.NavigationTestHelpers.addDiscussion
import com.github.meeplemeet.utils.NavigationTestHelpers.checkBottomBarIsDisplayed
import com.github.meeplemeet.utils.NavigationTestHelpers.checkBottomBarIsNotDisplayed
import com.github.meeplemeet.utils.NavigationTestHelpers.checkDiscoverScreenIsDisplayed
import com.github.meeplemeet.utils.NavigationTestHelpers.checkDiscussionAddScreenIsDisplayed
import com.github.meeplemeet.utils.NavigationTestHelpers.checkDiscussionInfoScreenIsDisplayed
import com.github.meeplemeet.utils.NavigationTestHelpers.checkDiscussionScreenIsDisplayed
import com.github.meeplemeet.utils.NavigationTestHelpers.checkDiscussionsOverviewIsDisplayed
import com.github.meeplemeet.utils.NavigationTestHelpers.checkProfileScreenIsDisplayed
import com.github.meeplemeet.utils.NavigationTestHelpers.checkSessionsScreenIsDisplayed
import com.github.meeplemeet.utils.NavigationTestHelpers.checkSignInScreenIsDisplayed
import com.github.meeplemeet.utils.NavigationTestHelpers.checkSignUpScreenIsDisplayed
import com.github.meeplemeet.utils.NavigationTestHelpers.clickOnLogout
import com.github.meeplemeet.utils.NavigationTestHelpers.clickOnTab
import com.github.meeplemeet.utils.NavigationTestHelpers.deleteDiscussion
import com.github.meeplemeet.utils.NavigationTestHelpers.leaveDiscussion
import com.github.meeplemeet.utils.NavigationTestHelpers.navigateBack
import com.github.meeplemeet.utils.NavigationTestHelpers.navigateToAddDiscussionScreen
import com.github.meeplemeet.utils.NavigationTestHelpers.navigateToDiscussionInfoScreen
import com.github.meeplemeet.utils.NavigationTestHelpers.navigateToDiscussionScreen
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Integration UI tests for MeepleMeetApp navigation.
 * - Runs the real MeepleMeetApp() inside the compose rule (same as MainActivity).
 * - Exercises bottom bar visibility, tab clicks, navigation to all screens, and repeated
 *   navigation.
 *
 * Notes:
 * - These tests maximize coverage of navigation interactions.
 * - If a screen title differs from MeepleMeetScreen.name (e.g. you display "Sessions Overview"
 *   instead of "Sessions"), update the asserted strings accordingly.
 * - Update test tags for better testability where applicable.
 */
class NavigationTest : FirestoreTests() {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private lateinit var authVM: AuthViewModel
  private lateinit var firestoreVM: FirestoreViewModel
  private lateinit var repository: FirestoreRepository

  private lateinit var testAccount: Account
  private lateinit var testDiscussion1Uid: String
  private lateinit var testDiscussion2Uid: String

  // ---------- Setup ----------

  @Before
  fun setUp() {
    // Create repository and ViewModels
    repository = FirestoreRepository()
    authVM = AuthViewModel()
    firestoreVM = FirestoreViewModel(repository)

    // Create test data directly in Firestore
    runBlocking {
      // Create test account
      testAccount =
          repository.createAccount(
              userHandle = "test_user",
              name = "Test User",
              email = "test@example.com",
              photoUrl = null)

      // Create test discussions
      val (_, discussion1) =
          repository.createDiscussion(
              name = "Fake Discussion 1",
              description = "Testing navigation from overview",
              creatorId = testAccount.uid)
      testDiscussion1Uid = discussion1.uid

      val (_, discussion2) =
          repository.createDiscussion(
              name = "Fake Discussion 2",
              description = "Testing navigation with multiple discussions",
              creatorId = testAccount.uid)
      testDiscussion2Uid = discussion2.uid
    }

    // Launch the app UI (starts at SignIn screen)
    composeTestRule.setContent { MeepleMeetApp(authVM = authVM, firestoreVM = firestoreVM) }
    composeTestRule.waitForIdle()
  }

  // ===== Test helpers =====

  /**
   * Simulate login by loading the test account into the FirestoreViewModel. This triggers the
   * navigation to DiscussionsOverview in MainActivity.
   */
  private fun login() {
    firestoreVM.getAccount(testAccount.uid)

    // Wait until the account is actually loaded in the ViewModel
    composeTestRule.waitUntil(timeoutMillis = 5_000) { firestoreVM.account.value != null }

    composeTestRule.waitForIdle()
  }

  /** Simulate logout by clearing the account from the ViewModels. */
  private fun logout() {
    firestoreVM.signOut()
    authVM.logout()
    composeTestRule.waitForIdle()
  }

  private fun pressSystemBack(shouldTerminate: Boolean = false) {
    composeTestRule.activityRule.scenario.onActivity { activity ->
      activity.onBackPressedDispatcher.onBackPressed()
    }
    composeTestRule.waitUntil { composeTestRule.activity.isFinishing == shouldTerminate }
    assertEquals(shouldTerminate, composeTestRule.activity.isFinishing)
  }

  // ---------- Auth screens navigation ----------

  @Test
  fun startScreen_isSignIn_and_bottomBarNotDisplayed() {
    composeTestRule.checkSignInScreenIsDisplayed()
    composeTestRule.checkBottomBarIsNotDisplayed()
  }

  @Test
  fun signUpLink_navigatesToSignUp_and_bottomBarStillNotDisplayed() {
    composeTestRule.checkSignInScreenIsDisplayed()
    composeTestRule
        .onNodeWithTag(SignInScreenTestTags.SIGN_UP_BUTTON)
        .assertIsDisplayed()
        .performClick()
    composeTestRule.checkSignUpScreenIsDisplayed()
    composeTestRule.checkBottomBarIsNotDisplayed()
  }

  @Test
  fun backFromSignUp_toSignIn_keepsBottomBarNotDisplayed() {
    composeTestRule.checkSignInScreenIsDisplayed()
    composeTestRule.onNodeWithTag(SignInScreenTestTags.SIGN_UP_BUTTON).performClick()

    composeTestRule.checkSignUpScreenIsDisplayed()
    composeTestRule.onNodeWithTag(SignUpScreenTestTags.SIGN_IN_BUTTON).performClick()

    composeTestRule.checkSignInScreenIsDisplayed()
    composeTestRule.checkBottomBarIsNotDisplayed()
  }

  @Test
  fun signInFlow_navigatesToDiscussionsOverview() {
    composeTestRule.checkSignInScreenIsDisplayed()

    // Simulate login
    login()
    composeTestRule.checkDiscussionsOverviewIsDisplayed()
    composeTestRule.checkBottomBarIsDisplayed()
  }

  @Test
  fun signUpFlow_navigatesToDiscussionsOverview() {
    composeTestRule.checkSignInScreenIsDisplayed()
    composeTestRule.onNodeWithTag(SignInScreenTestTags.SIGN_UP_BUTTON).performClick()
    composeTestRule.checkSignUpScreenIsDisplayed()

    // Simulate login
    login()
    composeTestRule.checkDiscussionsOverviewIsDisplayed()
    composeTestRule.checkBottomBarIsDisplayed()
  }

  // ---------- BottomBar navigation ----------

  @Test
  fun tabsAreClickable() {
    login()
    composeTestRule.checkDiscussionsOverviewIsDisplayed()

    composeTestRule.clickOnTab(NavigationTestTags.DISCOVER_TAB)
    composeTestRule.clickOnTab(NavigationTestTags.PROFILE_TAB)
    composeTestRule.clickOnTab(NavigationTestTags.SESSIONS_TAB)
    composeTestRule.clickOnTab(NavigationTestTags.DISCUSSIONS_TAB)
  }

  @Test
  fun canNavigateToAllTabs() {
    login()
    composeTestRule.checkDiscussionsOverviewIsDisplayed()

    composeTestRule.clickOnTab(NavigationTestTags.DISCOVER_TAB)
    composeTestRule.checkDiscoverScreenIsDisplayed()
    composeTestRule.checkBottomBarIsDisplayed()

    composeTestRule.clickOnTab(NavigationTestTags.SESSIONS_TAB)
    composeTestRule.checkSessionsScreenIsDisplayed()
    composeTestRule.checkBottomBarIsDisplayed()

    composeTestRule.clickOnTab(NavigationTestTags.PROFILE_TAB)
    composeTestRule.checkProfileScreenIsDisplayed()
    composeTestRule.checkBottomBarIsDisplayed()

    composeTestRule.clickOnTab(NavigationTestTags.DISCUSSIONS_TAB)
    composeTestRule.checkDiscussionsOverviewIsDisplayed()
    composeTestRule.checkBottomBarIsDisplayed()
  }

  //
  @Test
  fun canNavigateBackUsingSystemBack() {
    login()
    composeTestRule.checkDiscussionsOverviewIsDisplayed()

    composeTestRule.clickOnTab(NavigationTestTags.DISCOVER_TAB)
    composeTestRule.checkDiscoverScreenIsDisplayed()

    composeTestRule.clickOnTab(NavigationTestTags.SESSIONS_TAB)
    composeTestRule.checkSessionsScreenIsDisplayed()

    composeTestRule.clickOnTab(NavigationTestTags.PROFILE_TAB)
    composeTestRule.checkProfileScreenIsDisplayed()

    // Back to Sessions
    pressSystemBack(shouldTerminate = false)
    composeTestRule.checkSessionsScreenIsDisplayed()

    // Back to Discover
    pressSystemBack(shouldTerminate = false)
    composeTestRule.checkDiscoverScreenIsDisplayed()

    // Back to Discussions
    pressSystemBack(shouldTerminate = false)
    composeTestRule.checkDiscussionsOverviewIsDisplayed()

    // Back should terminate app (from top-level)
    pressSystemBack(shouldTerminate = true)
  }

  @Test
  fun many_nav_between_two_tabs_then_system_back_pops_only_once() {
    login()
    composeTestRule.checkDiscussionsOverviewIsDisplayed()

    val tabA = NavigationTestTags.DISCOVER_TAB
    val tabB = NavigationTestTags.SESSIONS_TAB

    repeat(20) {
      composeTestRule.clickOnTab(tabA)
      composeTestRule.checkDiscoverScreenIsDisplayed()
      composeTestRule.clickOnTab(tabB)
      composeTestRule.checkSessionsScreenIsDisplayed()
    }

    // Now back should go to Discover only once
    pressSystemBack(shouldTerminate = false)
    composeTestRule.checkDiscoverScreenIsDisplayed()

    // Back again should go to Discussions
    pressSystemBack(shouldTerminate = false)
    composeTestRule.checkDiscussionsOverviewIsDisplayed()

    // Back again should terminate app
    pressSystemBack(shouldTerminate = true)
  }

  // ---------- Discussions navigation ----------

  // DiscussionsOverview navigation

  @Test
  fun clickingOnDiscussionPreview_openDiscussionScreen() {
    login()
    composeTestRule.checkDiscussionsOverviewIsDisplayed()
    composeTestRule.navigateToDiscussionScreen("Fake Discussion 1")
    composeTestRule.checkDiscussionScreenIsDisplayed("Fake Discussion 1")
    composeTestRule.checkBottomBarIsNotDisplayed()
  }

  @Test
  fun clickingOnAdd_openDiscussionAddScreen() {
    login()
    composeTestRule.checkDiscussionsOverviewIsDisplayed()
    composeTestRule.navigateToAddDiscussionScreen()
    composeTestRule.checkDiscussionAddScreenIsDisplayed()
    composeTestRule.checkBottomBarIsNotDisplayed()
  }

  // DiscussionAdd navigation

  @Test
  fun canGoBack_fromDiscussionAdd_toDiscussionsOverview() {
    login()
    composeTestRule.checkDiscussionsOverviewIsDisplayed()
    composeTestRule.navigateToAddDiscussionScreen()
    composeTestRule.checkDiscussionAddScreenIsDisplayed()

    // Back to overview
    pressSystemBack(shouldTerminate = false)
    composeTestRule.checkDiscussionsOverviewIsDisplayed()
    composeTestRule.checkBottomBarIsDisplayed()
  }

  @Test
  fun backButton_fromDiscussionAdd_toDiscussionsOverview() {
    login()
    composeTestRule.checkDiscussionsOverviewIsDisplayed()
    composeTestRule.navigateToAddDiscussionScreen()
    composeTestRule.checkDiscussionAddScreenIsDisplayed()

    // Back to overview
    composeTestRule.navigateBack()
    composeTestRule.checkDiscussionsOverviewIsDisplayed()
    composeTestRule.checkBottomBarIsDisplayed()
  }

  @Test
  fun createDiscussion_navigateToDiscussionsOverview() {
    login()
    composeTestRule.checkDiscussionsOverviewIsDisplayed()
    composeTestRule.navigateToAddDiscussionScreen()
    composeTestRule.checkDiscussionAddScreenIsDisplayed()

    // Simulate adding a discussion
    composeTestRule.addDiscussion("New Discussion", "Created during test")
    composeTestRule.checkDiscussionsOverviewIsDisplayed()
    composeTestRule.checkBottomBarIsDisplayed()
  }

  // DiscussionScreen navigation

  @Test
  fun canGoBack_fromDiscussionScreen_toDiscussionsOverview() {
    login()
    composeTestRule.checkDiscussionsOverviewIsDisplayed()
    composeTestRule.navigateToDiscussionScreen("Fake Discussion 1")
    composeTestRule.checkDiscussionScreenIsDisplayed("Fake Discussion 1")

    // Back to overview
    pressSystemBack(shouldTerminate = false)
    composeTestRule.checkDiscussionsOverviewIsDisplayed()
  }

  @Test
  fun backButton_fromDiscussionScreen_toDiscussionsOverview() {
    login()
    composeTestRule.checkDiscussionsOverviewIsDisplayed()
    composeTestRule.navigateToDiscussionScreen("Fake Discussion 1")
    composeTestRule.checkDiscussionScreenIsDisplayed("Fake Discussion 1")

    // Back to overview
    composeTestRule.navigateBack()
    composeTestRule.checkDiscussionsOverviewIsDisplayed()
  }

  // DiscussionInfo navigation

  @Test
  fun clickingOnDiscussionInfo_opensDiscussionInfoScreen() {
    login()
    composeTestRule.checkDiscussionsOverviewIsDisplayed()
    composeTestRule.navigateToDiscussionScreen("Fake Discussion 1")
    composeTestRule.checkDiscussionScreenIsDisplayed("Fake Discussion 1")

    // Open info
    composeTestRule.navigateToDiscussionInfoScreen("Fake Discussion 1")
    composeTestRule.checkDiscussionInfoScreenIsDisplayed("Fake Discussion 1")
    composeTestRule.checkBottomBarIsNotDisplayed()
  }

  @Test
  fun canGoBack_fromDiscussionInfo_toDiscussionScreen() {
    login()
    composeTestRule.checkDiscussionsOverviewIsDisplayed()
    composeTestRule.navigateToDiscussionScreen("Fake Discussion 1")
    composeTestRule.checkDiscussionScreenIsDisplayed("Fake Discussion 1")

    // Open info
    composeTestRule.navigateToDiscussionInfoScreen("Fake Discussion 1")
    composeTestRule.checkDiscussionInfoScreenIsDisplayed("Fake Discussion 1")

    // Back to discussion
    pressSystemBack(shouldTerminate = false)
    composeTestRule.checkDiscussionScreenIsDisplayed("Fake Discussion 1")
  }

  @Test
  fun backButton_fromDiscussionInfo_toDiscussionScreen() {
    login()
    composeTestRule.checkDiscussionsOverviewIsDisplayed()
    composeTestRule.navigateToDiscussionScreen("Fake Discussion 1")
    composeTestRule.checkDiscussionScreenIsDisplayed("Fake Discussion 1")

    // Open info
    composeTestRule.navigateToDiscussionInfoScreen("Fake Discussion 1")
    composeTestRule.checkDiscussionInfoScreenIsDisplayed("Fake Discussion 1")

    // Back to discussion
    composeTestRule.navigateBack()
    composeTestRule.checkDiscussionScreenIsDisplayed("Fake Discussion 1")
  }

  @Test
  fun deleteDiscussion_fromInfo_toOverview() {
    login()
    composeTestRule.checkDiscussionsOverviewIsDisplayed()
    composeTestRule.navigateToDiscussionScreen("Fake Discussion 1")
    composeTestRule.checkDiscussionScreenIsDisplayed("Fake Discussion 1")

    // Open info
    composeTestRule.navigateToDiscussionInfoScreen("Fake Discussion 1")
    composeTestRule.checkDiscussionInfoScreenIsDisplayed("Fake Discussion 1")

    // Simulate delete
    composeTestRule.deleteDiscussion()
    composeTestRule.checkDiscussionsOverviewIsDisplayed()
  }

  @Test
  fun leaveDiscussion_fromInfo_toOverview() {
    login()
    composeTestRule.checkDiscussionsOverviewIsDisplayed()
    composeTestRule.navigateToDiscussionScreen("Fake Discussion 1")
    composeTestRule.checkDiscussionScreenIsDisplayed("Fake Discussion 1")

    // Open info
    composeTestRule.navigateToDiscussionInfoScreen("Fake Discussion 1")
    composeTestRule.checkDiscussionInfoScreenIsDisplayed("Fake Discussion 1")

    // Simulate leave
    composeTestRule.leaveDiscussion()
    composeTestRule.checkDiscussionsOverviewIsDisplayed()
  }

  // ---------- Discussions navigation ----------

  @Test
  fun logout_fromProfile_toSignIn() {
    login()
    composeTestRule.checkDiscussionsOverviewIsDisplayed()

    composeTestRule.clickOnTab(NavigationTestTags.PROFILE_TAB)
    composeTestRule.checkProfileScreenIsDisplayed()

    // Simulate logout
    composeTestRule.clickOnLogout()
    composeTestRule.checkSignInScreenIsDisplayed()
  }

  // ---------- Defensive checks ----------

  @Test
  fun noUnexpectedErrorText_onStart() {
    // Ensure the generic error "An unknown error occurred" is NOT shown at start
    composeTestRule.onAllNodesWithText("An unknown error occurred").assertCountEquals(0)
  }
}
