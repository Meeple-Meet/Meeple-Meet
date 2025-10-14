package com.github.meeplemeet.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.github.meeplemeet.MeepleMeetApp
import com.github.meeplemeet.model.repositories.AuthRepository
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.structures.Discussion
import com.github.meeplemeet.model.structures.DiscussionPreview
import com.github.meeplemeet.model.viewmodels.AuthUIState
import com.github.meeplemeet.model.viewmodels.AuthViewModel
import com.github.meeplemeet.ui.navigation.NavigationTestTags
import com.github.meeplemeet.utils.NavigationTestHelpers.checkBottomBarIsDisplayed
import com.github.meeplemeet.utils.NavigationTestHelpers.checkBottomBarIsNotDisplayed
import com.github.meeplemeet.utils.NavigationTestHelpers.checkDiscoverScreenIsDisplayed
import com.github.meeplemeet.utils.NavigationTestHelpers.checkDiscussionAddScreenIsDisplayed
import com.github.meeplemeet.utils.NavigationTestHelpers.checkDiscussionScreenIsDisplayed
import com.github.meeplemeet.utils.NavigationTestHelpers.checkDiscussionsOverviewIsDisplayed
import com.github.meeplemeet.utils.NavigationTestHelpers.checkProfileScreenIsDisplayed
import com.github.meeplemeet.utils.NavigationTestHelpers.checkSessionsScreenIsDisplayed
import com.github.meeplemeet.utils.NavigationTestHelpers.checkSignInScreenIsDisplayed
import com.github.meeplemeet.utils.NavigationTestHelpers.checkSignUpScreenIsDisplayed
import com.github.meeplemeet.utils.NavigationTestHelpers.clickOnTab
import com.github.meeplemeet.utils.NavigationTestHelpers.navigateToDiscussionScreen
import com.google.firebase.Timestamp
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
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
class NavigationTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private lateinit var authVM: AuthViewModel

  private val fakeAccount =
      Account(
          uid = "fake-uid-123",
          handle = "fake_handle",
          name = "Fake User",
          email = "fake@example.com",
          previews = emptyMap(),
          photoUrl = null,
          description = "Test account")

  private val fakeDiscussion1 =
      Discussion(
          uid = "discussion-1",
          creatorId = fakeAccount.uid,
          name = "Fake Discussion 1",
          description = "Testing navigation from overview",
          messages = emptyList(),
          participants = listOf(fakeAccount.uid),
          admins = listOf(fakeAccount.uid),
          createdAt = Timestamp.now())

  private val fakeDiscussion2 =
      Discussion(
          uid = "discussion-2",
          creatorId = fakeAccount.uid,
          name = "Fake Discussion 2",
          description = "Testing navigation with multiple discussions",
          messages = emptyList(),
          participants = listOf(fakeAccount.uid),
          admins = listOf(fakeAccount.uid),
          createdAt = Timestamp.now())

  private val fakePreview1 =
      DiscussionPreview(
          uid = fakeDiscussion1.uid,
          lastMessage = "Yo!",
          lastMessageSender = fakeAccount.uid,
          lastMessageAt = Timestamp.now(),
          unreadCount = 0)

  private val fakePreview2 =
      DiscussionPreview(
          uid = fakeDiscussion2.uid,
          lastMessage = "Another one!",
          lastMessageSender = fakeAccount.uid,
          lastMessageAt = Timestamp.now(),
          unreadCount = 0)

  private val fakePreviews =
      mapOf(fakeDiscussion1.uid to fakePreview1, fakeDiscussion2.uid to fakePreview2)

  // ---------- Setup ----------

  @Before
  fun setUp() {
    // Launch the full app UI
    authVM = AuthViewModel(AuthRepository())

    composeTestRule.setContent { MeepleMeetApp(authVM = authVM) }
    composeTestRule.waitForIdle()
  }

  // ===== VM State helpers =====
  private fun setAuthVMState(vm: AuthViewModel, newState: AuthUIState) {
    val field =
        vm::class.java.declaredFields.firstOrNull { f ->
          f.isAccessible = true
          val value =
              try {
                f.get(vm)
              } catch (_: Throwable) {
                null
              }
          value is MutableStateFlow<*> && value.value is AuthUIState
        } ?: error("MutableStateFlow<AuthUIState> not found on AuthViewModel")

    @Suppress("UNCHECKED_CAST") val flow = field.get(vm) as MutableStateFlow<AuthUIState>
    flow.value = newState
  }

  private fun login() {
    setAuthVMState(authVM, AuthUIState(account = fakeAccount))
    composeTestRule.waitForIdle()
  }

  private fun logout() {
    setAuthVMState(authVM, AuthUIState(account = null))
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
    pressSystemBack(shouldTerminate = true) // FIXME
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
    pressSystemBack(shouldTerminate = true) // FIXME
  }

  // ---------- Discussions navigation ----------
  @Test
  fun clickingOnDiscussionPreview_openDiscussionScreen() {
    login()
    composeTestRule.checkDiscussionsOverviewIsDisplayed()
    composeTestRule.navigateToDiscussionScreen(fakeDiscussion1.name)
    composeTestRule.checkDiscussionScreenIsDisplayed(fakeDiscussion1.name)
  }

  @Test
  fun clickingOnAdd_openDiscussionAddScreen() {
    login()
    composeTestRule.checkDiscussionsOverviewIsDisplayed()
    composeTestRule
        .onNodeWithTag("Add discussion")
        .assertIsDisplayed()
        .performClick() // TODO use better tag later
    composeTestRule.checkDiscussionAddScreenIsDisplayed()
    composeTestRule.checkBottomBarIsNotDisplayed()
  }

  // ---------- Defensive checks ----------

  @Test
  fun noUnexpectedErrorText_onStart() {
    // Ensure the generic error "An unknown error occurred" is NOT shown at start
    composeTestRule.onAllNodesWithText("An unknown error occurred").assertCountEquals(0)
  }
}
