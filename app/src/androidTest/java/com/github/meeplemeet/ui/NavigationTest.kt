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
import com.github.meeplemeet.model.viewmodels.FirestoreViewModel
import com.github.meeplemeet.ui.navigation.NavigationTestTags
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
import com.google.firebase.Timestamp
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Before
import org.junit.Ignore
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
  private lateinit var dbVM: FirestoreViewModel

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
    // Create ViewModels with logged-out state
    authVM = AuthViewModel(AuthRepository())
    dbVM = FirestoreViewModel()

    // CRITICAL: Start with BOTH VMs in logged-out state
    setAuthVMState(authVM, AuthUIState(account = null))
    setFirestoreVMAccount(dbVM, null)

    // Launch the full app UI
    composeTestRule.setContent { MeepleMeetApp(authVM = authVM, firestoreVM = dbVM) }
    composeTestRule.waitForIdle()
  }

  // ===== VM State helpers =====

  /**
   * Set the AuthViewModel UI state. Uses reflection to access the private _uiState
   * MutableStateFlow.
   */
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

  /**
   * Set the FirestoreViewModel account state. Uses reflection to access the private _account
   * MutableStateFlow.
   */
  private fun setFirestoreVMAccount(vm: FirestoreViewModel, account: Account?) {
    val field = vm::class.java.declaredFields.first { it.name == "_account" }
    field.isAccessible = true

    @Suppress("UNCHECKED_CAST") val flow = field.get(vm) as MutableStateFlow<Account?>
    flow.value = account
  }

  /**
   * Simulate login by setting both ViewModels to logged-in state. This mimics what happens in the
   * real app when Firebase auth completes.
   */
  private fun login() {
    // Set AuthViewModel account
    setAuthVMState(authVM, AuthUIState(account = fakeAccount))

    // Set FirestoreViewModel account and discussion data
    populateFirestoreVM(dbVM)

    // Wait for navigation to complete
    composeTestRule.waitForIdle()
  }

  /** Simulate logout by clearing both ViewModels. */
  private fun logout() {
    setAuthVMState(authVM, AuthUIState(account = null))
    setFirestoreVMAccount(dbVM, null)
    composeTestRule.waitForIdle()
  }

  /**
   * Populate FirestoreViewModel with fake data for testing. This sets up the account, discussions,
   * and previews.
   */
  private fun populateFirestoreVM(vm: FirestoreViewModel) {
    val fields = vm::class.java.declaredFields
    fields.forEach { it.isAccessible = true }

    val accountField = fields.first { it.name == "_account" }
    val discussionField = fields.first { it.name == "_discussion" }
    val previewStatesField = fields.first { it.name == "previewStates" }
    val discussionFlowsField = fields.first { it.name == "discussionFlows" }

    @Suppress("UNCHECKED_CAST")
    (accountField.get(vm) as MutableStateFlow<Account?>).value = fakeAccount

    @Suppress("UNCHECKED_CAST")
    (discussionField.get(vm) as MutableStateFlow<Discussion?>).value = fakeDiscussion1

    @Suppress("UNCHECKED_CAST")
    val previewStates =
        previewStatesField.get(vm) as MutableMap<String, StateFlow<Map<String, DiscussionPreview>>>

    @Suppress("UNCHECKED_CAST")
    val discussionFlows = discussionFlowsField.get(vm) as MutableMap<String, StateFlow<Discussion?>>

    previewStates[fakeAccount.uid] = MutableStateFlow(fakePreviews)
    discussionFlows[fakeDiscussion1.uid] = MutableStateFlow(fakeDiscussion1)
    discussionFlows[fakeDiscussion2.uid] = MutableStateFlow(fakeDiscussion2)
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
    composeTestRule.navigateToDiscussionScreen(fakeDiscussion1.name)
    composeTestRule.checkDiscussionScreenIsDisplayed(fakeDiscussion1.name)
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

  @Ignore("FIXME")
  @Test
  fun createDiscussion_navigateToDiscussionsOverview() {
    login()
    composeTestRule.checkDiscussionsOverviewIsDisplayed()
    composeTestRule.navigateToAddDiscussionScreen()
    composeTestRule.checkDiscussionAddScreenIsDisplayed()

    // Simulate adding a discussion
    composeTestRule.addDiscussion()
    composeTestRule.checkDiscussionsOverviewIsDisplayed()
    composeTestRule.checkBottomBarIsDisplayed()
  }

  // DiscussionScreen navigation

  @Test
  fun canGoBack_fromDiscussionScreen_toDiscussionsOverview() {
    login()
    composeTestRule.checkDiscussionsOverviewIsDisplayed()
    composeTestRule.navigateToDiscussionScreen(fakeDiscussion1.name)
    composeTestRule.checkDiscussionScreenIsDisplayed(fakeDiscussion1.name)

    // Back to overview
    pressSystemBack(shouldTerminate = false)
    composeTestRule.checkDiscussionsOverviewIsDisplayed()
  }

  @Test
  fun backButton_fromDiscussionScreen_toDiscussionsOverview() {
    login()
    composeTestRule.checkDiscussionsOverviewIsDisplayed()
    composeTestRule.navigateToDiscussionScreen(fakeDiscussion1.name)
    composeTestRule.checkDiscussionScreenIsDisplayed(fakeDiscussion1.name)

    // Back to overview
    composeTestRule.navigateBack()
    composeTestRule.checkDiscussionsOverviewIsDisplayed()
  }

  // DiscussionInfo navigation

  @Ignore("FIXME")
  @Test
  fun clickingOnDiscussionInfo_opensDiscussionInfoScreen() {
    login()
    composeTestRule.checkDiscussionsOverviewIsDisplayed()
    composeTestRule.navigateToDiscussionScreen(fakeDiscussion1.name)
    composeTestRule.checkDiscussionScreenIsDisplayed(fakeDiscussion1.name)

    // Open info
    composeTestRule.navigateToDiscussionInfoScreen(fakeDiscussion1.name)
    composeTestRule.checkDiscussionInfoScreenIsDisplayed(fakeDiscussion1.name)
    composeTestRule.checkBottomBarIsNotDisplayed()
  }

  @Ignore("FIXME")
  @Test
  fun canGoBack_fromDiscussionInfo_toDiscussionScreen() {
    login()
    composeTestRule.checkDiscussionsOverviewIsDisplayed()
    composeTestRule.navigateToDiscussionScreen(fakeDiscussion1.name)
    composeTestRule.checkDiscussionScreenIsDisplayed(fakeDiscussion1.name)

    // Open info
    composeTestRule.navigateToDiscussionInfoScreen(fakeDiscussion1.name)
    composeTestRule.checkDiscussionInfoScreenIsDisplayed(fakeDiscussion1.name)

    // Back to discussion
    pressSystemBack(shouldTerminate = false)
    composeTestRule.checkDiscussionScreenIsDisplayed(fakeDiscussion1.name)
  }

  @Ignore("FIXME")
  @Test
  fun backButton_fromDiscussionInfo_toDiscussionScreen() {
    login()
    composeTestRule.checkDiscussionsOverviewIsDisplayed()
    composeTestRule.navigateToDiscussionScreen(fakeDiscussion1.name)
    composeTestRule.checkDiscussionScreenIsDisplayed(fakeDiscussion1.name)

    // Open info
    composeTestRule.navigateToDiscussionInfoScreen(fakeDiscussion1.name)
    composeTestRule.checkDiscussionInfoScreenIsDisplayed(fakeDiscussion1.name)

    // Back to discussion
    composeTestRule.navigateBack()
    composeTestRule.checkDiscussionScreenIsDisplayed(fakeDiscussion1.name)
  }

  @Ignore("FIXME")
  @Test
  fun deleteDiscussion_fromInfo_toOverview() {
    login()
    composeTestRule.checkDiscussionsOverviewIsDisplayed()
    composeTestRule.navigateToDiscussionScreen(fakeDiscussion1.name)
    composeTestRule.checkDiscussionScreenIsDisplayed(fakeDiscussion1.name)

    // Open info
    composeTestRule.navigateToDiscussionInfoScreen(fakeDiscussion1.name)
    composeTestRule.checkDiscussionInfoScreenIsDisplayed(fakeDiscussion1.name)

    // Simulate delete
    composeTestRule.deleteDiscussion()
    composeTestRule.checkDiscussionsOverviewIsDisplayed()
  }

  @Ignore("FIXME")
  @Test
  fun leaveDiscussion_fromInfo_toOverview() {
    login()
    composeTestRule.checkDiscussionsOverviewIsDisplayed()
    composeTestRule.navigateToDiscussionScreen(fakeDiscussion1.name)
    composeTestRule.checkDiscussionScreenIsDisplayed(fakeDiscussion1.name)

    // Open info
    composeTestRule.navigateToDiscussionInfoScreen(fakeDiscussion1.name)
    composeTestRule.checkDiscussionInfoScreenIsDisplayed(fakeDiscussion1.name)

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
